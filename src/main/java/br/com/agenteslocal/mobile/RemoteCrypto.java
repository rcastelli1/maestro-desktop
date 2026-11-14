package br.com.agenteslocal.mobile;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-GCM envelope shared with the Expo mobile app. Wire format: IV || ciphertext || tag. */
public final class RemoteCrypto {
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RemoteCrypto() { }

    public static String newKey() {
        byte[] key = new byte[KEY_BYTES];
        RANDOM.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    public static String encrypt(String encodedKey, byte[] plaintext) throws GeneralSecurityException {
        byte[] key = decodeKey(encodedKey);
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(plaintext);
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                .put(iv).put(encrypted).array());
    }

    public static byte[] decrypt(String encodedKey, String envelope) throws GeneralSecurityException {
        byte[] key = decodeKey(encodedKey);
        byte[] combined;
        try { combined = Base64.getDecoder().decode(envelope); }
        catch (IllegalArgumentException error) { throw new GeneralSecurityException("Envelope invalido.", error); }
        if (combined.length < IV_BYTES + TAG_BITS / 8) throw new GeneralSecurityException("Envelope curto.");
        byte[] iv = new byte[IV_BYTES];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        try { return cipher.doFinal(combined, IV_BYTES, combined.length - IV_BYTES); }
        catch (AEADBadTagException error) { throw new GeneralSecurityException("Mensagem alterada ou chave incorreta.", error); }
    }

    private static byte[] decodeKey(String encodedKey) throws GeneralSecurityException {
        byte[] key;
        try { key = Base64.getDecoder().decode(encodedKey); }
        catch (IllegalArgumentException error) { throw new GeneralSecurityException("Chave de pareamento invalida.", error); }
        if (key.length != KEY_BYTES) throw new GeneralSecurityException("Chave de pareamento invalida.");
        return key;
    }
}
