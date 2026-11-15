package br.com.agenteslocal.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Standalone integration check; run after compiling the application. */
public final class BackupSecurityCheck {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("maestro-backup-check-");
        char[] password = new char[32];
        Arrays.fill(password, 'p');
        try {
            Path home = root.resolve("home");
            Files.createDirectories(home.resolve("projects/example/attachments"));
            Files.createDirectories(home.resolve("remote-inflight"));
            Files.writeString(home.resolve("workspace.json"), "dados privados do projeto");
            Files.writeString(home.resolve("projects/example/attachments/note.txt"), "anexo do projeto");
            Files.writeString(home.resolve("firebase-remote.json"), "CHAVE_REMOTA_NUNCA_EXPORTAR");
            Files.writeString(home.resolve("mobile-access-token"), "TOKEN_LOCAL_NUNCA_EXPORTAR");
            Files.writeString(home.resolve("push-devices.json"), "PUSH_TOKEN_NUNCA_EXPORTAR");
            Files.writeString(home.resolve("remote-inflight/pedido"), "MARCADOR_NUNCA_EXPORTAR");

            Path protectedBackup = root.resolve("novo.maestrobackup");
            new WorkspaceRepository(home).exportBackup(protectedBackup, password);
            byte[] encrypted = Files.readAllBytes(protectedBackup);
            check(new String(encrypted, StandardCharsets.ISO_8859_1).startsWith("MAESTROBK1\n"),
                    "cabeçalho ausente");
            check(!new String(encrypted, StandardCharsets.ISO_8859_1).contains("dados privados"),
                    "texto do projeto apareceu fora da criptografia");

            Path restored = root.resolve("restaurado.zip");
            BackupCrypto.openZip(protectedBackup, restored, password);
            try (ZipFile zip = new ZipFile(restored.toFile())) {
                List<String> entries = new ArrayList<>();
                zip.stream().forEach(entry -> entries.add(entry.getName()));
                check(entries.contains("workspace.json"), "workspace ausente");
                check(entries.contains("projects/example/attachments/note.txt"), "anexo ausente");
                check(entries.size() == 2, "backup incluiu arquivos inesperados: " + entries);
                String workspace = new String(zip.getInputStream(zip.getEntry("workspace.json")).readAllBytes(),
                        StandardCharsets.UTF_8);
                check(workspace.equals("dados privados do projeto"), "workspace alterado");
            }

            Path oldZip = root.resolve("antigo.zip");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(oldZip))) {
                zip.putNextEntry(new ZipEntry("firebase-remote.json"));
                zip.write("SEGREDO_DO_ZIP_ANTIGO".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            Path protectedOld = root.resolve("antigo.maestrobackup");
            BackupCrypto.protectZip(oldZip, protectedOld, password);
            check(Files.exists(oldZip), "o ZIP antigo foi removido");
            Path openedOld = root.resolve("antigo-aberto.zip");
            BackupCrypto.openZip(protectedOld, openedOld, password);
            check(Arrays.equals(Files.readAllBytes(oldZip), Files.readAllBytes(openedOld)),
                    "o ZIP antigo não foi preservado");

            Path existing = root.resolve("nao-sobrescrever.zip");
            Files.writeString(existing, "original");
            char[] incorrectPassword = new char[32];
            Arrays.fill(incorrectPassword, 'x');
            expectFailure(() -> BackupCrypto.openZip(protectedBackup, existing, incorrectPassword));
            Arrays.fill(incorrectPassword, '\0');
            check(Files.readString(existing).equals("original"), "senha errada sobrescreveu arquivo");
            byte[] tampered = Files.readAllBytes(protectedBackup);
            tampered[tampered.length - 1] ^= 1;
            Path changed = root.resolve("alterado.maestrobackup");
            Files.write(changed, tampered);
            expectFailure(() -> BackupCrypto.openZip(changed, root.resolve("alterado.zip"), password));
            check(!Files.exists(root.resolve("alterado.zip")), "arquivo adulterado foi aceito");
            System.out.println("BackupSecurityCheck: OK");
        } finally {
            Arrays.fill(password, '\0');
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList())
                    Files.deleteIfExists(path);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectFailure(Action action) throws Exception {
        try { action.run(); }
        catch (IOException expected) { return; }
        throw new AssertionError("Operação insegura foi aceita");
    }

    @FunctionalInterface private interface Action { void run() throws Exception; }
}
