package br.com.agenteslocal.mobile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Small authenticated LAN bridge. All workspace access runs on the JavaFX thread. */
public final class MobileBridgeServer implements AutoCloseable {
    public static final int PORT = 8787;
    private static final int MAX_REQUEST_BYTES = 12_000_000;
    private final Gson gson = new Gson();
    private final Path tokenFile;
    private final Handler handler;
    private volatile String token;
    private HttpServer server;
    private String error = "";

    public interface Handler { Response handle(Request request) throws Exception; }
    public record Request(String method, String path, JsonObject body) { }
    public record Response(int status, String contentType, byte[] content) {
        public static Response json(int status, JsonObject body) {
            return new Response(status, "application/json; charset=utf-8",
                    new Gson().toJson(body).getBytes(StandardCharsets.UTF_8));
        }
        public static Response bytes(String type, byte[] content) {
            return new Response(200, type, content);
        }
    }
    public static final class ApiException extends RuntimeException {
        public final int status;
        public ApiException(int status, String message) { super(message); this.status = status; }
    }

    public MobileBridgeServer(Path home, Handler handler) throws IOException {
        this.tokenFile = home.resolve("mobile-access-token");
        this.handler = handler;
        Files.createDirectories(home);
        String existing = Files.exists(tokenFile) ? Files.readString(tokenFile).trim() : "";
        if (existing.matches("[A-Za-z0-9_-]{16,64}")) token = existing;
        else regenerateToken();
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
            server.createContext("/api/", this::handle);
            server.setExecutor(Executors.newFixedThreadPool(4, runnable -> {
                Thread thread = new Thread(runnable, "maestro-mobile-api");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
            error = "";
        } catch (IOException exception) {
            error = exception.getMessage();
            server = null;
        }
    }

    public boolean running() { return server != null; }
    public String error() { return error; }
    public String token() { return token; }

    public void regenerateToken() throws IOException {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        String next = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Files.writeString(tokenFile, next, StandardCharsets.UTF_8);
        token = next;
    }

    public List<String> localUrls() {
        List<String> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface adapter = interfaces.nextElement();
                if (!adapter.isUp() || adapter.isLoopback()) continue;
                Enumeration<InetAddress> ips = adapter.getInetAddresses();
                while (ips.hasMoreElements()) {
                    InetAddress ip = ips.nextElement();
                    if (ip instanceof Inet4Address && ip.isSiteLocalAddress())
                        addresses.add("http://" + ip.getHostAddress() + ":" + PORT);
                }
            }
        } catch (Exception ignored) { }
        return addresses.stream().distinct().toList();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            InetAddress client = exchange.getRemoteAddress().getAddress();
            if (!client.isLoopbackAddress() && !client.isSiteLocalAddress() && !client.isLinkLocalAddress()) {
                send(exchange, error(403, "Acesso permitido apenas pela rede local."));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(exchange.getRequestMethod()) && "/api/health".equals(path)) {
                JsonObject status = new JsonObject();
                status.addProperty("name", "maestro");
                status.addProperty("ready", true);
                send(exchange, Response.json(200, status));
                return;
            }
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            String supplied = header != null && header.startsWith("Bearer ") ? header.substring(7) : "";
            if (!MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8),
                    token.getBytes(StandardCharsets.UTF_8))) {
                send(exchange, error(401, "Chave de pareamento inválida."));
                return;
            }
            byte[] input = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
            if (input.length > MAX_REQUEST_BYTES) {
                send(exchange, error(413, "Arquivo ou mensagem grande demais."));
                return;
            }
            JsonObject body = new JsonObject();
            if (input.length > 0) {
                try { body = JsonParser.parseString(new String(input, StandardCharsets.UTF_8)).getAsJsonObject(); }
                catch (RuntimeException exception) {
                    send(exchange, error(400, "JSON inválido."));
                    return;
                }
            }
            Request request = new Request(exchange.getRequestMethod(), path, body);
            CompletableFuture<Response> result = new CompletableFuture<>();
            Platform.runLater(() -> {
                try { result.complete(handler.handle(request)); }
                catch (ApiException exception) { result.complete(error(exception.status, exception.getMessage())); }
                catch (Exception exception) { result.complete(error(500, "Falha ao processar a solicitação.")); }
            });
            try { send(exchange, result.get(20, TimeUnit.SECONDS)); }
            catch (TimeoutException exception) { send(exchange, error(504, "O aplicativo demorou a responder.")); }
            catch (ExecutionException exception) { send(exchange, error(500, "Falha ao processar a solicitação.")); }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                send(exchange, error(503, "Servidor interrompido."));
            }
        } finally { exchange.close(); }
    }

    private Response error(int status, String message) {
        JsonObject body = new JsonObject();
        body.addProperty("error", message);
        return Response.json(status, body);
    }

    private void send(HttpExchange exchange, Response response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", response.contentType());
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(response.status(), response.content().length);
        exchange.getResponseBody().write(response.content());
    }

    @Override public void close() {
        if (server != null) server.stop(0);
        server = null;
    }
}
