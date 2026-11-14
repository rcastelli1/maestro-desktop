package br.com.agenteslocal.codex;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class PluginService {
    public record Plugin(String id, String name, String marketplace, String version,
                         String iconPath, boolean installed, boolean enabled) { }

    public record Listing(List<Plugin> installed, List<Plugin> available) { }

    private record CachedMetadata(String displayName, String iconPath) { }

    public Listing list(String codex) throws IOException, InterruptedException {
        String output = command(codex, "plugin", "list", "--available", "--json");
        try {
            return parseListing(output);
        } catch (IOException error) {
            throw new IOException(error.getMessage() + " (saída: " + output.length()
                    + " caracteres; CLI: " + Path.of(codex).getFileName() + ")", error);
        }
    }

    Listing parseListing(String output) throws IOException {
        // Algumas versões do CLI escrevem avisos no stdout antes do JSON.
        // Procure o objeto completo, respeitando chaves dentro de strings.
        for (int start = output.indexOf('{'); start >= 0; start = output.indexOf('{', start + 1)) {
            int depth = 0;
            boolean quoted = false;
            boolean escaped = false;
            for (int index = start; index < output.length(); index++) {
                char current = output.charAt(index);
                if (quoted) {
                    if (escaped) escaped = false;
                    else if (current == '\\') escaped = true;
                    else if (current == '"') quoted = false;
                } else if (current == '"') quoted = true;
                else if (current == '{') depth++;
                else if (current == '}' && --depth == 0) {
                    try {
                        JsonObject root = JsonParser.parseString(output.substring(start, index + 1)).getAsJsonObject();
                        if (root.has("installed") && root.get("installed").isJsonArray()
                                && root.has("available") && root.get("available").isJsonArray())
                            return new Listing(plugins(root, "installed"), plugins(root, "available"));
                    } catch (RuntimeException ignored) { }
                    break;
                }
            }
        }
        throw new IOException("O Codex não retornou JSON de plugins reconhecível. Verifique o CLI em Modelos.");
    }

    public void install(String codex, String pluginId) throws IOException, InterruptedException {
        command(codex, "plugin", "add", pluginId, "--json");
    }

    public void remove(String codex, String pluginId) throws IOException, InterruptedException {
        command(codex, "plugin", "remove", pluginId, "--json");
    }

    public void addMarketplace(String codex, String source) throws IOException, InterruptedException {
        command(codex, "plugin", "marketplace", "add", source, "--json");
    }

    private List<Plugin> plugins(JsonObject root, String key) {
        JsonArray entries = root.has(key) && root.get(key).isJsonArray()
                ? root.getAsJsonArray(key) : new JsonArray();
        List<Plugin> plugins = new ArrayList<>();
        for (JsonElement entry : entries) {
            if (!entry.isJsonObject()) continue;
            JsonObject item = entry.getAsJsonObject();
            String id = value(item, "pluginId");
            String marketplace = value(item, "marketplaceName");
            String version = value(item, "version");
            boolean installed = key.equals("installed") || booleanValue(item, "installed");
            String name = value(item, "name");
            CachedMetadata metadata = installed ? cachedMetadata(id, marketplace, version)
                    : new CachedMetadata("", "");
            if (!metadata.displayName().isBlank()) name = metadata.displayName();
            plugins.add(new Plugin(id, name, marketplace, version, metadata.iconPath(),
                    installed, booleanValue(item, "enabled")));
        }
        return plugins;
    }

    private CachedMetadata cachedMetadata(String id, String marketplace, String version) {
        String slug = id.split("@", 2)[0];
        if (!safePathPart(slug) || !safePathPart(marketplace) || !safePathPart(version))
            return new CachedMetadata("", "");
        String configuredHome = System.getenv("CODEX_HOME");
        Path codexHome = configuredHome == null || configuredHome.isBlank()
                ? Path.of(System.getProperty("user.home"), ".codex") : Path.of(configuredHome);
        Path plugin = codexHome.resolve("plugins").resolve("cache")
                .resolve(marketplace).resolve(slug).resolve(version).toAbsolutePath().normalize();
        for (Path manifest : List.of(plugin.resolve("plugin.json"),
                plugin.resolve(".codex-plugin").resolve("plugin.json"))) {
            if (!Files.isRegularFile(manifest)) continue;
            try {
                JsonObject root = JsonParser.parseString(Files.readString(manifest, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                JsonObject metadata = root;
                if (root.has("extensions") && root.get("extensions").isJsonObject()) {
                    JsonObject extensions = root.getAsJsonObject("extensions");
                    if (extensions.has("com.openai") && extensions.get("com.openai").isJsonObject())
                        metadata = extensions.getAsJsonObject("com.openai");
                }
                if (metadata.has("interface") && metadata.get("interface").isJsonObject()) {
                    JsonObject ui = metadata.getAsJsonObject("interface");
                    String displayName = value(ui, "displayName");
                    String iconPath = cachedIconPath(plugin, ui);
                    if (!displayName.isBlank() || !iconPath.isBlank())
                        return new CachedMetadata(displayName, iconPath);
                }
            } catch (IOException | RuntimeException ignored) { }
        }
        return new CachedMetadata("", "");
    }

    private String cachedIconPath(Path plugin, JsonObject ui) {
        for (String key : List.of("composerIcon", "logo")) {
            String relative = value(ui, key);
            if (!relative.startsWith("./")) continue;
            Path icon = plugin.resolve(relative.substring(2)).normalize();
            String filename = icon.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            if (icon.startsWith(plugin) && Files.isRegularFile(icon)
                    && (filename.endsWith(".png") || filename.endsWith(".jpg")
                    || filename.endsWith(".jpeg") || filename.endsWith(".gif")))
                return icon.toString();
        }
        return "";
    }

    private boolean safePathPart(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]+")
                && !value.equals(".") && !value.equals("..");
    }

    private String value(JsonObject item, String key) {
        return item.has(key) && !item.get(key).isJsonNull() ? item.get(key).getAsString() : "";
    }

    private boolean booleanValue(JsonObject item, String key) {
        return item.has(key) && !item.get(key).isJsonNull() && item.get(key).getAsBoolean();
    }

    private String command(String codex, String... arguments) throws IOException, InterruptedException {
        if (codex == null || codex.isBlank()) throw new IOException("Codex CLI não encontrado.");
        List<String> parts = new ArrayList<>();
        parts.add(codex);
        parts.addAll(List.of(arguments));
        boolean listing = arguments.length >= 2 && "plugin".equals(arguments[0])
                && "list".equals(arguments[1]);
        int timeout = listing ? 12 : 60;
        Path stdout = Files.createTempFile("maestro-plugin-", ".out");
        Path stderr = null;
        try {
            stderr = Files.createTempFile("maestro-plugin-", ".err");
            Process process = new ProcessBuilder(parts)
                    .redirectOutput(stdout.toFile())
                    .redirectError(stderr.toFile())
                    .start();
            boolean finished;
            try {
                finished = process.waitFor(timeout, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                process.destroyForcibly();
                throw error;
            }
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("O comando de plugins excedeu " + timeout + " segundos.");
            }
            String output = Files.readString(stdout, StandardCharsets.UTF_8);
            String diagnostics = Files.readString(stderr, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                String error = diagnostics.trim();
                throw new IOException(error.isBlank() ? output.trim() : error);
            }
            return listing ? output + "\n" + diagnostics : output;
        } finally {
            Files.deleteIfExists(stdout);
            if (stderr != null) Files.deleteIfExists(stderr);
        }
    }
}
