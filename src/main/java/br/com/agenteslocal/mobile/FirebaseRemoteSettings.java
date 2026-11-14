package br.com.agenteslocal.mobile;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Per-computer remote setup. The account password is never saved. */
public final class FirebaseRemoteSettings {
    private final Path file;
    private String apiKey = "";
    private String projectId = "";
    private String ownerUid = "";
    private String pairingKey = "";
    private String refreshToken = "";

    private FirebaseRemoteSettings(Path file) { this.file = file; }

    public static FirebaseRemoteSettings load(Path home) throws IOException {
        Files.createDirectories(home);
        FirebaseRemoteSettings settings = new FirebaseRemoteSettings(home.resolve("firebase-remote.json"));
        if (Files.isRegularFile(settings.file)) {
            try {
                Saved saved = new Gson().fromJson(Files.readString(settings.file), Saved.class);
                if (saved != null) {
                    settings.apiKey = value(saved.apiKey());
                    settings.projectId = value(saved.projectId());
                    settings.ownerUid = value(saved.ownerUid());
                    settings.pairingKey = value(saved.pairingKey());
                    settings.refreshToken = value(saved.refreshToken());
                }
            } catch (RuntimeException error) { throw new IOException("Configuração remota inválida.", error); }
        }
        if (settings.pairingKey.isBlank()) {
            settings.pairingKey = RemoteCrypto.newKey();
            settings.save();
        }
        return settings;
    }

    public synchronized boolean configured() {
        return !apiKey.isBlank() && !projectId.isBlank() && !ownerUid.isBlank();
    }
    public synchronized boolean signedIn() { return configured() && !refreshToken.isBlank(); }
    public synchronized String projectId() { return projectId; }
    public synchronized String pairingKey() { return pairingKey; }
    public synchronized String refreshToken() { return refreshToken; }

    public synchronized FirebaseRemoteClient.Config clientConfig() {
        return new FirebaseRemoteClient.Config(apiKey, projectId, ownerUid);
    }

    public synchronized void importWebConfig(Path selectedFile) throws IOException {
        if (!Files.isRegularFile(selectedFile) || Files.size(selectedFile) > 20_000)
            throw new IOException("Selecione o arquivo firebaseConfig.local.ts do projeto mobile.");
        String text = Files.readString(selectedFile, StandardCharsets.UTF_8);
        String newApiKey = property(text, "apiKey");
        String newProjectId = property(text, "projectId");
        String newOwnerUid = property(text, "ownerUid");
        new FirebaseRemoteClient.Config(newApiKey, newProjectId, newOwnerUid);
        boolean accountChanged = !apiKey.equals(newApiKey) || !projectId.equals(newProjectId)
                || (!ownerUid.isBlank() && !ownerUid.equals(newOwnerUid));
        apiKey = newApiKey;
        projectId = newProjectId;
        ownerUid = newOwnerUid;
        if (accountChanged) refreshToken = "";
        save();
    }

    public synchronized void setRefreshToken(String next) throws IOException {
        refreshToken = value(next);
        save();
    }

    public synchronized void clearLogin() throws IOException {
        refreshToken = "";
        save();
    }

    private synchronized void save() throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        String json = new Gson().toJson(new Saved(apiKey, projectId, ownerUid, pairingKey, refreshToken));
        Files.writeString(temporary, json, StandardCharsets.UTF_8);
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String property(String text, String name) throws IOException {
        Pattern pattern = Pattern.compile("(?m)^\\s*" + Pattern.quote(name) + "\\s*:\\s*['\"]([^'\"]+)['\"]");
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) throw new IOException("Campo " + name + " não encontrado no arquivo Firebase.");
        return matcher.group(1);
    }

    private static String value(String input) { return input == null ? "" : input; }

    private record Saved(String apiKey, String projectId, String ownerUid,
                         String pairingKey, String refreshToken) { }
}
