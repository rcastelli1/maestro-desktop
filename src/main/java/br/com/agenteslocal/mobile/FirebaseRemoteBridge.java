package br.com.agenteslocal.mobile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Pulls encrypted mobile requests through Firestore. No inbound port is needed for this path. */
public final class FirebaseRemoteBridge implements AutoCloseable {
    private final FirebaseRemoteClient client;
    private final FirebaseMailbox mailbox;
    private final MobileBridgeServer.Handler handler;
    private final String pairingKey;
    private final Path inflightDirectory;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "maestro-firebase-bridge");
        thread.setDaemon(true);
        return thread;
    });
    private volatile String lastError = "";
    private volatile boolean checked;
    private volatile long lastRequestAtNanos;

    public FirebaseRemoteBridge(FirebaseRemoteClient client, MobileBridgeServer.Handler handler,
                                String pairingKey, Path home) throws IOException, GeneralSecurityException {
        this.client = client;
        this.mailbox = new FirebaseMailbox(client);
        this.handler = handler;
        this.pairingKey = pairingKey;
        RemoteCrypto.decrypt(pairingKey, RemoteCrypto.encrypt(pairingKey, new byte[0]));
        this.inflightDirectory = home.resolve("remote-inflight");
        Files.createDirectories(inflightDirectory);
    }

    public void start() { executor.execute(this::pollAndSchedule); }
    public String lastError() { return lastError; }
    public boolean checked() { return checked; }
    public CompletableFuture<Void> checkNow() {
        CompletableFuture<Void> complete = new CompletableFuture<>();
        executor.execute(() -> {
            pollSafely();
            complete.complete(null);
        });
        return complete;
    }

    private void pollAndSchedule() {
        pollSafely();
        if (executor.isShutdown()) return;
        long lastRequest = lastRequestAtNanos;
        long idleFor = lastRequest == 0 ? Long.MAX_VALUE : System.nanoTime() - lastRequest;
        long delay = idleFor < TimeUnit.SECONDS.toNanos(30) ? 1
                : idleFor < TimeUnit.MINUTES.toNanos(2) ? 2 : 5;
        try { executor.schedule(this::pollAndSchedule, delay, TimeUnit.SECONDS); }
        catch (RejectedExecutionException ignored) { }
    }

    private void pollSafely() {
        try {
            for (String id : mailbox.listIds("requests")) {
                if (Thread.currentThread().isInterrupted()) return;
                lastRequestAtNanos = System.nanoTime();
                process(id);
            }
            lastError = "";
            checked = true;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            lastError = error.getClass().getSimpleName() + ": " + error.getMessage();
        }
    }

    private void process(String id) throws Exception {
        if (mailbox.exists("responses", id)) {
            mailbox.delete("requests", id);
            Files.deleteIfExists(inflightDirectory.resolve(id));
            return;
        }
        FirebaseMailbox.Envelope sealed = mailbox.read("requests", id);
        if (sealed == null) return;
        JsonObject request;
        try {
            request = JsonParser.parseString(new String(RemoteCrypto.decrypt(pairingKey, sealed.data()),
                    StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (GeneralSecurityException | RuntimeException error) {
            writeError(id, 400, "Pedido remoto invalido ou chave de pareamento incorreta.");
            mailbox.delete("requests", id, sealed.partsCount());
            return;
        }
        String method = field(request, "method");
        String path = field(request, "path");
        long createdAt = longField(request, "createdAt");
        long now = System.currentTimeMillis();
        if (!"1".equals(field(request, "version")) || !id.equals(field(request, "requestId"))
                || !("GET".equals(method) || "POST".equals(method))
                || !path.startsWith("/api/") || path.length() > 500
                || !request.has("body") || !request.get("body").isJsonObject()
                || createdAt > now + TimeUnit.MINUTES.toMillis(2)
                || createdAt < now - TimeUnit.MINUTES.toMillis(5)) {
            writeError(id, 400, "Formato do pedido remoto invalido.");
            mailbox.delete("requests", id, sealed.partsCount());
            return;
        }
        Path marker = inflightDirectory.resolve(id);
        if ("POST".equals(method)) {
            if (Files.exists(marker)) {
                writeError(id, 409, "O comando anterior pode ter sido executado. Confira o resultado no computador antes de repetir.");
                mailbox.delete("requests", id, sealed.partsCount());
                Files.deleteIfExists(marker);
                return;
            }
            Files.createFile(marker);
        }
        CompletableFuture<MobileBridgeServer.Response> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { result.complete(handler.handle(new MobileBridgeServer.Request(method, path,
                    request.getAsJsonObject("body")))); }
            catch (MobileBridgeServer.ApiException error) {
                result.complete(jsonError(error.status, error.getMessage()));
            } catch (Exception error) { result.complete(jsonError(500, "Falha ao executar o pedido remoto.")); }
        });
        MobileBridgeServer.Response response = result.get(45, TimeUnit.SECONDS);
        JsonObject payload = new JsonObject();
        payload.addProperty("version", 1);
        payload.addProperty("requestId", id);
        payload.addProperty("status", response.status());
        payload.addProperty("mailboxVersion", 2);
        payload.addProperty("contentType", response.contentType());
        if (response.contentType().startsWith("application/json")) {
            payload.add("body", JsonParser.parseString(new String(response.content(), StandardCharsets.UTF_8)));
        } else {
            JsonObject binary = new JsonObject();
            binary.addProperty("base64", Base64.getEncoder().encodeToString(response.content()));
            binary.addProperty("contentType", response.contentType());
            payload.add("body", binary);
        }
        mailbox.write("responses", id, RemoteCrypto.encrypt(pairingKey,
                payload.toString().getBytes(StandardCharsets.UTF_8)),
                "2".equals(field(request, "mailboxVersion")));
        mailbox.delete("requests", id, sealed.partsCount());
        Files.deleteIfExists(marker);
    }

    private void writeError(String id, int status, String message) throws Exception {
        JsonObject body = new JsonObject(); body.addProperty("error", message);
        JsonObject payload = new JsonObject();
        payload.addProperty("version", 1);
        payload.addProperty("requestId", id);
        payload.addProperty("status", status);
        payload.add("body", body);
        mailbox.write("responses", id, RemoteCrypto.encrypt(pairingKey,
                payload.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static MobileBridgeServer.Response jsonError(int status, String message) {
        JsonObject body = new JsonObject(); body.addProperty("error", message);
        return MobileBridgeServer.Response.json(status, body);
    }

    private static String field(JsonObject object, String name) {
        try { return object.get(name).getAsString(); }
        catch (RuntimeException error) { return ""; }
    }

    private static long longField(JsonObject object, String name) {
        try { return object.get(name).getAsLong(); }
        catch (RuntimeException error) { return 0; }
    }

    @Override public void close() { executor.shutdownNow(); }
}
