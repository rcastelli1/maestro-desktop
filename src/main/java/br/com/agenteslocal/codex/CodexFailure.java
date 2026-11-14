package br.com.agenteslocal.codex;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Extracts a readable cause from a Codex CLI JSONL failure event. */
public final class CodexFailure {
    private CodexFailure() { }

    public static String detail(JsonObject event) {
        String message = detail(event.get("error"), 0);
        if (message.isBlank()) message = detail(event.get("message"), 0);
        return message.length() > 1_000 ? message.substring(0, 1_000) + "…" : message;
    }

    private static String detail(JsonElement value, int depth) {
        if (value == null || value.isJsonNull() || depth > 4) return "";
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            String message = detail(object.get("message"), depth + 1);
            return message.isBlank() ? detail(object.get("error"), depth + 1) : message;
        }
        if (!value.isJsonPrimitive()) return "";
        String raw = value.getAsString().trim();
        if (raw.startsWith("{")) {
            try { return detail(JsonParser.parseString(raw), depth + 1); }
            catch (RuntimeException ignored) { }
        }
        return raw;
    }
}
