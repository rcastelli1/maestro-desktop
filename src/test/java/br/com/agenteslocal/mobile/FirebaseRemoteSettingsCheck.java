package br.com.agenteslocal.mobile;

import com.google.gson.Gson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

/** Verifies migration of a local Firebase login without exposing real credentials. */
public final class FirebaseRemoteSettingsCheck {
    public static void main(String[] args) throws Exception {
        Path home = Path.of(System.getenv("AGENTES_LOCAL_HOME"));
        Files.createDirectories(home);
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        Files.writeString(home.resolve("firebase-remote.json"), new Gson().toJson(Map.of(
                "apiKey", "A".repeat(24), "projectId", "maestro-test",
                "pairingKey", key, "refreshToken", "existing-session")));
        FirebaseRemoteSettings settings = FirebaseRemoteSettings.load(home);
        if (settings.configured()) throw new AssertionError("UID antigo deveria exigir migração");
        Path config = home.resolve("firebaseConfig.local.ts");
        Files.writeString(config, "export const firebaseConfig = {\n"
                + "  apiKey: '" + "A".repeat(24) + "',\n"
                + "  projectId: 'maestro-test',\n"
                + "  ownerUid: 'User1234567890',\n};\n");
        settings.importWebConfig(config);
        if (!settings.signedIn() || !settings.pairingKey().equals(key)
                || !settings.clientConfig().ownerUid().equals("User1234567890"))
            throw new AssertionError("Migração perdeu login ou chave remota");
        FirebaseRemoteSettings restored = FirebaseRemoteSettings.load(home);
        if (!restored.signedIn() || !restored.clientConfig().ownerUid().equals("User1234567890"))
            throw new AssertionError("UID não foi persistido");
        System.out.println("Migração do UID Firebase e sessão local: OK");
    }
}
