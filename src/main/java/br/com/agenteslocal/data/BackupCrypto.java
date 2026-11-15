package br.com.agenteslocal.data;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.ZipFile;

/** Password-protected, authenticated backup format. The encrypted payload is a ZIP file. */
public final class BackupCrypto {
    private static final byte[] MAGIC = "MAESTROBK1\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int ITERATIONS = 600_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    @FunctionalInterface public interface Writer { void write(OutputStream output) throws IOException; }

    private BackupCrypto() { }

    public static void encrypt(Path destination, char[] password, Writer writer) throws IOException {
        requirePassword(password);
        Path output = destination.toAbsolutePath().normalize();
        Path temporary = temporaryBeside(output);
        try {
            byte[] salt = new byte[SALT_BYTES];
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(salt);
            RANDOM.nextBytes(iv);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, password, salt, iv);
            try (OutputStream file = Files.newOutputStream(temporary)) {
                file.write(MAGIC);
                file.write(salt);
                file.write(iv);
                try (CipherOutputStream encrypted = new CipherOutputStream(file, cipher)) {
                    writer.write(encrypted);
                }
            }
            moveIntoPlace(temporary, output);
        } finally { Files.deleteIfExists(temporary); }
    }

    /** Copies an existing ZIP into a protected backup; the original is never changed. */
    public static void protectZip(Path source, Path destination, char[] password) throws IOException {
        if (samePath(source, destination)) throw new IOException("Escolha outro nome para o backup protegido.");
        try (ZipFile ignored = new ZipFile(source.toFile())) {
            encrypt(destination, password, output -> {
                try (InputStream input = Files.newInputStream(source)) { input.transferTo(output); }
            });
        }
    }

    /** Writes a decrypted ZIP only after its authentication tag and ZIP structure pass validation. */
    public static void openZip(Path source, Path destination, char[] password) throws IOException {
        requirePassword(password);
        if (samePath(source, destination)) throw new IOException("Escolha outro nome para o ZIP de saída.");
        Path output = destination.toAbsolutePath().normalize();
        Path temporary = temporaryBeside(output);
        try {
            try (InputStream file = Files.newInputStream(source)) {
                byte[] magic = file.readNBytes(MAGIC.length);
                if (!Arrays.equals(magic, MAGIC)) throw new IOException("Este arquivo não é um backup protegido do Maestro.");
                byte[] salt = file.readNBytes(SALT_BYTES);
                byte[] iv = file.readNBytes(IV_BYTES);
                if (salt.length != SALT_BYTES || iv.length != IV_BYTES)
                    throw new IOException("Backup protegido incompleto.");
                Cipher cipher = cipher(Cipher.DECRYPT_MODE, password, salt, iv);
                try (CipherInputStream decrypted = new CipherInputStream(file, cipher);
                     OutputStream plain = Files.newOutputStream(temporary)) {
                    decrypted.transferTo(plain);
                } catch (IOException error) {
                    throw new IOException("Senha incorreta ou backup protegido danificado.", error);
                }
            }
            try (ZipFile ignored = new ZipFile(temporary.toFile())) {
                // Confirm that the authenticated plaintext has a ZIP directory.
            }
            moveIntoPlace(temporary, output);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static Cipher cipher(int mode, char[] password, byte[] salt, byte[] iv) throws IOException {
        PBEKeySpec specification = new PBEKeySpec(password, salt, ITERATIONS, 256);
        byte[] key = null;
        try {
            key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(specification).getEncoded();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return cipher;
        } catch (GeneralSecurityException error) {
            throw new IOException("Não foi possível preparar a criptografia do backup.", error);
        } finally {
            specification.clearPassword();
            if (key != null) Arrays.fill(key, (byte) 0);
        }
    }

    private static void requirePassword(char[] password) throws IOException {
        if (password == null || password.length < 12)
            throw new IOException("Use uma senha de pelo menos 12 caracteres para o backup.");
    }

    private static Path temporaryBeside(Path output) throws IOException {
        Path parent = output.getParent();
        if (parent == null || !Files.isDirectory(parent)) throw new IOException("Pasta de destino não encontrada.");
        return Files.createTempFile(parent, ".maestro-backup-", ".tmp");
    }

    private static boolean samePath(Path left, Path right) {
        return left.toAbsolutePath().normalize().equals(right.toAbsolutePath().normalize());
    }

    private static void moveIntoPlace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
