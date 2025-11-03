package Crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;


public final class AsymCryptoUtil {
    private AsymCryptoUtil() {}

    

    public static PublicKey readPublicKeyFromPem(Path pemFile) throws Exception {
        String pem = Files.readString(pemFile, StandardCharsets.UTF_8)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    
    public static PrivateKey readPrivateKeyFromPem(Path pemFile) throws Exception {
        String raw = Files.readString(pemFile, StandardCharsets.UTF_8);
        if (raw.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalArgumentException(
                "La clave privada debe ser PKCS#8 (-----BEGIN PRIVATE KEY-----). " +
                "Convierte con: openssl pkcs8 -topk8 -in rsa_pkcs1.pem -out private.pem -nocrypt");
        }
        String pem = raw.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    

    public static PublicKey readPublicKeyFromBytes(byte[] derBytes) throws Exception {
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(derBytes));
    }

    public static PrivateKey readPrivateKeyFromBytes(byte[] derBytes) throws Exception {
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(derBytes));
    }

    

    public static PublicKey loadPublicKeyPem(Path pemFile) throws Exception {
        return readPublicKeyFromPem(pemFile);
    }

    public static PrivateKey loadPrivateKeyPem(Path pemFile) throws Exception {
        return readPrivateKeyFromPem(pemFile);
    }

    

    
    public static String fingerprintPublicKeyHex(PublicKey pub) throws Exception {
        byte[] enc = pub.getEncoded();
        byte[] dig = MessageDigest.getInstance("SHA-256").digest(enc);
        return bytesToHex(dig);
    }

    
    public static String fingerprintPublicKeyB64(PublicKey pub) throws Exception {
        byte[] enc = pub.getEncoded();
        byte[] dig = MessageDigest.getInstance("SHA-256").digest(enc);
        return Base64.getEncoder().encodeToString(dig);
    }

    
    public static String fingerprintPublicKeyHex(Path pemFile) throws Exception {
        return fingerprintPublicKeyHex(readPublicKeyFromPem(pemFile));
    }

    

    private static OAEPParameterSpec oaepSHA256() {
        return new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    }

    
    
    
    
    
    

    
    public static byte[] encryptHybridToBlob(byte[] clear, Path publicKeyPem) throws Exception {
        PublicKey pub = readPublicKeyFromPem(publicKeyPem);
        return encryptHybridToBlob(clear, pub);
    }

    
    public static byte[] encryptHybridToBlob(byte[] clear, PublicKey pub) throws Exception {
        
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey aes = kg.generateKey();

        
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        
        Cipher cAes = Cipher.getInstance("AES/GCM/NoPadding");
        cAes.init(Cipher.ENCRYPT_MODE, aes, new GCMParameterSpec(128, iv));
        byte[] ct = cAes.doFinal(clear);

        
        Cipher cRsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cRsa.init(Cipher.ENCRYPT_MODE, pub, oaepSHA256());
        byte[] kek = cRsa.doFinal(aes.getEncoded());

        
        String blob = "ALG:RSA-OAEP/AES-GCM\n"
                    + "IV:"  + Base64.getEncoder().encodeToString(iv)  + "\n"
                    + "KEY:" + Base64.getEncoder().encodeToString(kek) + "\n"
                    + "CT:"  + Base64.getEncoder().encodeToString(ct)  + "\n";
        return blob.getBytes(StandardCharsets.UTF_8);
    }

    
    public static byte[] decryptHybridFromBlob(byte[] blob, Path privateKeyPem) throws Exception {
        PrivateKey priv = readPrivateKeyFromPem(privateKeyPem);
        return decryptHybridFromBlob(blob, priv);
    }

    
    public static byte[] decryptHybridFromBlob(byte[] blob, PrivateKey priv) throws Exception {
        String s = new String(blob, StandardCharsets.UTF_8);

        String ivB64  = readField(s, "IV:");
        String keyB64 = readField(s, "KEY:");
        String ctB64  = readField(s, "CT:");

        byte[] iv  = Base64.getDecoder().decode(ivB64);
        byte[] kek = Base64.getDecoder().decode(keyB64);
        byte[] ct  = Base64.getDecoder().decode(ctB64);

        
        Cipher cRsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cRsa.init(Cipher.DECRYPT_MODE, priv, oaepSHA256());
        byte[] aesRaw = cRsa.doFinal(kek);
        SecretKeySpec aes = new SecretKeySpec(aesRaw, "AES");

        
        Cipher cAes = Cipher.getInstance("AES/GCM/NoPadding");
        cAes.init(Cipher.DECRYPT_MODE, aes, new GCMParameterSpec(128, iv));
        return cAes.doFinal(ct);
    }
    
    

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        if ((len & 1) != 0) throw new IllegalArgumentException("HEX inválido");
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i/2] = (byte) Integer.parseInt(hex.substring(i, i+2), 16);
        }
        return out;
    }

    
    
    public static String encryptHybridToJsonHex(byte[] clear, PublicKey pub) throws Exception {
        
        javax.crypto.KeyGenerator kg = javax.crypto.KeyGenerator.getInstance("AES");
        kg.init(256);
        javax.crypto.SecretKey aes = kg.generateKey();

        
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);

        
        javax.crypto.Cipher cAes = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cAes.init(javax.crypto.Cipher.ENCRYPT_MODE, aes, new javax.crypto.spec.GCMParameterSpec(128, iv));
        byte[] ct = cAes.doFinal(clear); 

        
        javax.crypto.Cipher cRsa = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cRsa.init(javax.crypto.Cipher.ENCRYPT_MODE, pub,
                new javax.crypto.spec.OAEPParameterSpec("SHA-256","MGF1",
                        java.security.spec.MGF1ParameterSpec.SHA256,
                        javax.crypto.spec.PSource.PSpecified.DEFAULT));
        byte[] kek = cRsa.doFinal(aes.getEncoded());

        StringBuilder sb = new StringBuilder(128 + ct.length*2);
        sb.append("{")
          .append("\"alg\":\"RSA-OAEP/AES-GCM\",")
          .append("\"ivHex\":\"").append(bytesToHex(iv)).append("\",")
          .append("\"keyHex\":\"").append(bytesToHex(kek)).append("\",")
          .append("\"ctHex\":\"").append(bytesToHex(ct)).append("\"")
          .append("}");
        return sb.toString();
    }

    
    public static byte[] decryptHybridFromJsonHex(String json, PrivateKey priv) throws Exception {
        String ivHex  = jsonExtract(json, "ivHex");
        String keyHex = jsonExtract(json, "keyHex");
        String ctHex  = jsonExtract(json, "ctHex");

        byte[] iv  = hexToBytes(ivHex);
        byte[] kek = hexToBytes(keyHex);
        byte[] ct  = hexToBytes(ctHex);

        
        javax.crypto.Cipher cRsa = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cRsa.init(javax.crypto.Cipher.DECRYPT_MODE, priv,
                new javax.crypto.spec.OAEPParameterSpec("SHA-256","MGF1",
                        java.security.spec.MGF1ParameterSpec.SHA256,
                        javax.crypto.spec.PSource.PSpecified.DEFAULT));
        byte[] aesRaw = cRsa.doFinal(kek);
        javax.crypto.spec.SecretKeySpec aes = new javax.crypto.spec.SecretKeySpec(aesRaw, "AES");

        
        javax.crypto.Cipher cAes = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cAes.init(javax.crypto.Cipher.DECRYPT_MODE, aes, new javax.crypto.spec.GCMParameterSpec(128, iv));
        return cAes.doFinal(ct);
    }

    
    private static String jsonExtract(String json, String key) {
        String pat = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pat).matcher(json);
        if (!m.find()) throw new IllegalArgumentException("Campo faltante en JSON: " + key);
        return m.group(1);
    }


        private static String readField(String s, String prefix) {
            for (String line : s.split("\\R")) {
                if (line.startsWith(prefix)) return line.substring(prefix.length()).trim();
            }
            throw new IllegalArgumentException("Campo faltante en blob: " + prefix);
        }

        private static String bytesToHex(byte[] in) {
            StringBuilder sb = new StringBuilder(in.length * 2);
            for (byte b : in) sb.append(String.format("%02x", b));
            return sb.toString();
        }
    
    
    
}
