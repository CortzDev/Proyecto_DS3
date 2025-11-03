package Crypto;

import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class EncriptacionUtil {
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES";
    private static final String SECRET_KEY = "MySecretKey12345";
    
    
    public static String encriptar(String textoPlano) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        
        byte[] textoCifrado = cipher.doFinal(textoPlano.getBytes());
        return Base64.getEncoder().encodeToString(textoCifrado);
    }
    
    
    public static String desencriptar(String textoEncriptado) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        
        byte[] textoDecodificado = Base64.getDecoder().decode(textoEncriptado);
        byte[] textoDescifrado = cipher.doFinal(textoDecodificado);
        return new String(textoDescifrado);
    }
    

    public static SecretKey generarClave() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
        keyGenerator.init(128);
        return keyGenerator.generateKey();
    }

    public static String claveAString(SecretKey clave) {
        return Base64.getEncoder().encodeToString(clave.getEncoded());
    }
    

    public static SecretKey stringAClave(String claveString) {
        byte[] claveBytes = Base64.getDecoder().decode(claveString);
        return new SecretKeySpec(claveBytes, ALGORITHM);
    }
}
