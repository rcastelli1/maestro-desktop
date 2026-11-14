package br.com.agenteslocal.codex;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Reads ChatGPT plan limits through the authenticated local Codex app-server. */
public final class CodexUsageService {
    public record Window(double usedPercent, int durationMinutes, Long resetsAt) { }
    public record ResetCredit(String id, String title, String description, Long expiresAt) { }
    public record UsageSnapshot(Window shortWindow, Window weeklyWindow, Integer availableResets,
                                List<ResetCredit> resets) { }

    public UsageSnapshot read(String executable) throws IOException {
        JsonObject result = request(executable, "account/rateLimits/read", null);
        JsonObject bucket = object(result, "rateLimits");
        JsonObject buckets = object(result, "rateLimitsByLimitId");
        if (buckets != null) {
            JsonObject codex = object(buckets, "codex");
            if (codex != null) bucket = codex;
            else if (bucket == null)
                for (Map.Entry<String, JsonElement> entry : buckets.entrySet()) {
                    if (entry.getValue().isJsonObject()) { bucket = entry.getValue().getAsJsonObject(); break; }
                }
        }
        Window primary = window(bucket == null ? null : object(bucket, "primary"));
        Window secondary = window(bucket == null ? null : object(bucket, "secondary"));
        Window shortWindow = primary;
        Window weeklyWindow = secondary;
        if (primary != null && primary.durationMinutes() >= 7_200) {
            weeklyWindow = primary;
            shortWindow = secondary;
        }

        JsonObject resetInfo = object(result, "rateLimitResetCredits");
        Integer count = integer(resetInfo, "availableCount");
        List<ResetCredit> credits = new ArrayList<>();
        if (resetInfo != null && resetInfo.has("credits") && resetInfo.get("credits").isJsonArray()) {
            for (JsonElement element : resetInfo.getAsJsonArray("credits")) {
                if (!element.isJsonObject()) continue;
                JsonObject credit = element.getAsJsonObject();
                if (!"available".equalsIgnoreCase(string(credit, "status"))) continue;
                credits.add(new ResetCredit(string(credit, "id"), string(credit, "title"),
                        string(credit, "description"), longValue(credit, "expiresAt")));
            }
        }
        return new UsageSnapshot(shortWindow, weeklyWindow, count, List.copyOf(credits));
    }

    public String consume(String executable, String creditId) throws IOException {
        JsonObject params = new JsonObject();
        params.addProperty("idempotencyKey", UUID.randomUUID().toString());
        if (creditId != null && !creditId.isBlank()) params.addProperty("creditId", creditId);
        return string(request(executable, "account/rateLimitResetCredit/consume", params), "outcome");
    }

    private JsonObject request(String executable, String method, JsonObject params) throws IOException {
        if (executable == null || executable.isBlank()) throw new IOException("Codex CLI não encontrado.");
        Process process = new ProcessBuilder(executable, "app-server")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        var readerThread = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "codex-usage-response");
            thread.setDaemon(true);
            return thread;
        });
        try {
            var response = readerThread.submit(() -> {
                try (var input = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                     var output = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                    JsonObject client = new JsonObject();
                    client.addProperty("name", "maestro_local");
                    client.addProperty("title", "Maestro");
                    client.addProperty("version", "0.1.0");
                    JsonObject initParams = new JsonObject();
                    initParams.add("clientInfo", client);
                    send(output, "initialize", 1, initParams);
                    receive(input, 1);
                    JsonObject initialized = new JsonObject();
                    initialized.addProperty("method", "initialized");
                    initialized.add("params", new JsonObject());
                    output.write(initialized.toString());
                    output.newLine();
                    output.flush();
                    send(output, method, 2, params);
                    return receive(input, 2);
                }
            });
            try { return response.get(20, TimeUnit.SECONDS); }
            catch (TimeoutException exception) {
                response.cancel(true);
                throw new IOException("O Codex demorou para responder à consulta de uso.", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("A consulta de uso foi interrompida.", exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof IOException io) throw io;
                throw new IOException("Não foi possível consultar o uso do Codex.", cause);
            }
        } finally {
            process.destroyForcibly();
            readerThread.shutdownNow();
        }
    }

    private void send(BufferedWriter output, String method, int id, JsonObject params) throws IOException {
        JsonObject message = new JsonObject();
        message.addProperty("method", method);
        message.addProperty("id", id);
        if (params != null) message.add("params", params);
        output.write(message.toString());
        output.newLine();
        output.flush();
    }

    private JsonObject receive(BufferedReader input, int id) throws IOException {
        String line;
        while ((line = input.readLine()) != null) {
            JsonObject message;
            try { message = JsonParser.parseString(line).getAsJsonObject(); }
            catch (RuntimeException ignored) { continue; }
            Integer responseId = integer(message, "id");
            if (responseId == null || responseId != id) continue;
            JsonObject error = object(message, "error");
            if (error != null) throw new IOException("Codex: " + string(error, "message"));
            JsonObject result = object(message, "result");
            if (result == null) throw new IOException("O Codex não retornou dados de uso.");
            return result;
        }
        throw new IOException("O Codex encerrou a consulta sem responder.");
    }

    private Window window(JsonObject object) {
        if (object == null) return null;
        Double percent = decimal(object, "usedPercent");
        Integer duration = integer(object, "windowDurationMins");
        if (percent == null || duration == null) return null;
        return new Window(Math.max(0, Math.min(100, percent)), duration, longValue(object, "resetsAt"));
    }

    private JsonObject object(JsonObject source, String name) {
        if (source == null || !source.has(name) || !source.get(name).isJsonObject()) return null;
        return source.getAsJsonObject(name);
    }

    private String string(JsonObject source, String name) {
        if (source == null || !source.has(name) || source.get(name).isJsonNull()) return "";
        try { return source.get(name).getAsString(); }
        catch (RuntimeException ignored) { return ""; }
    }

    private Integer integer(JsonObject source, String name) {
        if (source == null || !source.has(name) || source.get(name).isJsonNull()) return null;
        try { return source.get(name).getAsInt(); }
        catch (RuntimeException ignored) { return null; }
    }

    private Long longValue(JsonObject source, String name) {
        if (source == null || !source.has(name) || source.get(name).isJsonNull()) return null;
        try { return source.get(name).getAsLong(); }
        catch (RuntimeException ignored) { return null; }
    }

    private Double decimal(JsonObject source, String name) {
        if (source == null || !source.has(name) || source.get(name).isJsonNull()) return null;
        try { return source.get(name).getAsDouble(); }
        catch (RuntimeException ignored) { return null; }
    }
}
