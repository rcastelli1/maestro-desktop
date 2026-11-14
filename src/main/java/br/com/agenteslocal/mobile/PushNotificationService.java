package br.com.agenteslocal.mobile;

import br.com.agenteslocal.model.WorkspaceData.Agent;
import br.com.agenteslocal.model.WorkspaceData.ChatMessage;
import br.com.agenteslocal.model.WorkspaceData.ChatThread;
import br.com.agenteslocal.model.WorkspaceData.Project;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Sends one Android push per agent chat message without delaying the desktop UI. */
public final class PushNotificationService implements AutoCloseable {
    private static final URI SEND_URL = URI.create("https://exp.host/--/api/v2/push/send");
    private static final URI RECEIPTS_URL = URI.create("https://exp.host/--/api/v2/push/getReceipts");
    private static final int MAX_DEVICES = 8;
    private final Path devicesFile;
    private final Set<String> devices = new LinkedHashSet<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "maestro-push-notifications");
        thread.setDaemon(true);
        return thread;
    });

    public PushNotificationService(Path home) throws IOException {
        devicesFile = home.resolve("push-devices.json");
        if (Files.isRegularFile(devicesFile)) {
            try {
                JsonObject saved = JsonParser.parseString(Files.readString(devicesFile)).getAsJsonObject();
                for (JsonElement element : saved.getAsJsonArray("tokens")) {
                    String token = element.getAsString();
                    if (validToken(token) && devices.size() < MAX_DEVICES) devices.add(token);
                }
            } catch (RuntimeException error) {
                throw new IOException("A lista de celulares para notificações está inválida.", error);
            }
        }
    }

    public synchronized void register(String token) throws IOException {
        if (!validToken(token)) throw new IllegalArgumentException("Token de notificações inválido.");
        if (devices.contains(token)) return;
        if (devices.size() >= MAX_DEVICES) throw new IllegalStateException("Limite de celulares para notificações atingido.");
        devices.add(token);
        save();
    }

    public synchronized void unregister(String token) throws IOException {
        if (devices.remove(token)) save();
    }

    public synchronized int deviceCount() { return devices.size(); }

    public void message(Project project, ChatThread chat, Agent agent, ChatMessage message) {
        if (project == null || chat == null || agent == null || message == null
                || !"agent".equals(message.kind)) return;
        List<String> targets;
        synchronized (this) { targets = new ArrayList<>(devices); }
        if (targets.isEmpty()) return;
        String preview = message.text == null ? "" : message.text.replaceAll("\\s+", " ").trim();
        if (preview.isEmpty()) preview = "Enviou uma mensagem";
        if (preview.length() > 190) preview = preview.substring(0, 189) + "…";
        JsonObject data = new JsonObject();
        // A data-only push is handled by Expo's Android service even with the app closed.
        // Expo presents it when data.title and data.message are supplied.
        data.addProperty("title", agent.name);
        data.addProperty("message", preview);
        data.addProperty("maestroPush", "1");
        data.addProperty("channelId", "agent-messages");
        data.addProperty("projectId", project.id);
        data.addProperty("chatId", chat.id);
        data.addProperty("agentId", agent.id);
        data.addProperty("messageId", message.id);
        data.addProperty("agentVisualId", agent.visualId == null ? "orbe" : agent.visualId);
        data.addProperty("agentColor", agent.color == null ? "#6c9ed6" : agent.color);
        data.addProperty("agentEyeColor", agent.eyeColor == null ? "#14171b" : agent.eyeColor);
        for (String token : targets) {
            JsonObject push = new JsonObject();
            push.addProperty("to", token);
            push.addProperty("contentAvailable", true);
            push.addProperty("priority", "high");
            push.addProperty("ttl", 86400);
            push.add("data", data);
            worker.execute(() -> send(token, push));
        }
    }

    private void send(String token, JsonObject payload) {
        try {
            JsonObject result = post(SEND_URL, payload);
            JsonObject ticket = result.has("data") && result.get("data").isJsonObject()
                    ? result.getAsJsonObject("data") : null;
            if (ticket == null || !"ok".equals(string(ticket, "status"))) return;
            String receiptId = string(ticket, "id");
            if (!receiptId.isBlank()) worker.schedule(() -> checkReceipt(token, receiptId), 15, TimeUnit.MINUTES);
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            // Push failures never block chat messages. The next reply can still be delivered.
        }
    }

    private void checkReceipt(String token, String id) {
        try {
            JsonArray ids = new JsonArray(); ids.add(id);
            JsonObject request = new JsonObject(); request.add("ids", ids);
            JsonObject response = post(RECEIPTS_URL, request);
            JsonObject receipt = response.getAsJsonObject("data").getAsJsonObject(id);
            if (receipt == null || !"error".equals(string(receipt, "status"))) return;
            JsonObject details = receipt.getAsJsonObject("details");
            if (details != null && "DeviceNotRegistered".equals(string(details, "error"))) unregister(token);
        } catch (IOException | InterruptedException | RuntimeException error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }

    private JsonObject post(URI url, JsonObject body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("Expo Push respondeu com HTTP " + response.statusCode());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private synchronized void save() throws IOException {
        JsonArray tokens = new JsonArray();
        devices.forEach(tokens::add);
        JsonObject contents = new JsonObject(); contents.add("tokens", tokens);
        Path temporary = devicesFile.resolveSibling(devicesFile.getFileName() + ".tmp");
        Files.writeString(temporary, contents.toString(), StandardCharsets.UTF_8);
        try { Files.move(temporary, devicesFile, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException error) {
            Files.move(temporary, devicesFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean validToken(String token) {
        return token != null && token.length() <= 256
                && token.matches("(?:Expo|Exponent)PushToken\\[[A-Za-z0-9_-]+\\]");
    }

    private static String string(JsonObject object, String key) {
        try { return object.get(key).getAsString(); }
        catch (RuntimeException error) { return ""; }
    }

    @Override public void close() { worker.shutdownNow(); }
}
