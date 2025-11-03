package Crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class Cifrado {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    public static String cifrar(String texto, SecretKey key) throws Exception {
        byte[] iv = new byte[IV_BYTES];
        SecureRandom.getInstanceStrong().nextBytes(iv);
        Cipher c = Cipher.getInstance(TRANSFORMATION);
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] ct = c.doFinal(texto.getBytes(StandardCharsets.UTF_8));
        ByteBuffer bb = ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct);
        return Base64.getEncoder().encodeToString(bb.array());
    }

    public static String descifrar(String base64, SecretKey key) throws Exception {
        byte[] all = Base64.getDecoder().decode(base64);
        ByteBuffer bb = ByteBuffer.wrap(all);
        byte[] iv = new byte[IV_BYTES];
        bb.get(iv);
        byte[] ct = new byte[bb.remaining()];
        bb.get(ct);
        Cipher c = Cipher.getInstance(TRANSFORMATION);
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] pt = c.doFinal(ct);
        return new String(pt, StandardCharsets.UTF_8);
    }

    
    public static SecretKey deriveKey(String passphrase) {
        
        String h = SHA256.hash(passphrase);
        byte[] key = h.substring(0, 32).getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(key, "AES");
    }
}
