package br.com.agenteslocal.codex;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads the models the signed-in Codex CLI currently exposes to this user. */
public final class CodexModelCatalog {
    public List<String> availableModels() {
        String override = System.getenv("CODEX_HOME");
        Path home = override == null || override.isBlank()
                ? Path.of(System.getProperty("user.home"), ".codex") : Path.of(override);
        Path cache = home.resolve("models_cache.json");
        if (!Files.isRegularFile(cache)) return List.of();
        try {
            JsonObject data = JsonParser.parseString(Files.readString(cache, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray models = data.getAsJsonArray("models");
            if (models == null) return List.of();
            List<String> names = new ArrayList<>();
            for (JsonElement element : models) {
                if (!element.isJsonObject()) continue;
                JsonObject model = element.getAsJsonObject();
                if (!"list".equals(value(model, "visibility"))) continue;
                String slug = value(model, "slug");
                if (!slug.isBlank() && !names.contains(slug)) names.add(slug);
            }
            return List.copyOf(names);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public String defaultModel() {
        List<String> available = availableModels();
        return available.isEmpty() ? "" : available.getFirst();
    }

    private String value(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
}
