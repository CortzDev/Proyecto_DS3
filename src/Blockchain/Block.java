package Blockchain;

import Invoice.FacturaItem;
import Invoice.Factura;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Arrays;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class Block {
    public final int    index;
    public final String previousHash;


    private final String timestampISO;
    private long   nonce;
    private String hash;
    private final Factura factura;

  
    private String ciphertextB64;  
    private String ivB64;         
    private String tagB64;          
    private String aesKeyEncB64;    
    private String cipherSpec = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding|AES/GCM/NoPadding";
    private String keyFingerprint; 

    public Block(int index, String previousHash, Factura factura) {
        this.index = index;
        this.previousHash = (previousHash == null ? "0" : previousHash);
        this.timestampISO = isoNowUtcZ();
        this.nonce = 0L;
        this.hash  = "";
        this.factura = factura;
    }

    public Block(int index, String previousHash, String timestampISO, long nonce, String hash, Factura factura) {
        this.index = index;
        this.previousHash = (previousHash == null ? "0" : previousHash);
        this.timestampISO = (timestampISO == null ? isoNowUtcZ() : normalizeToUtcZ(timestampISO));
        this.nonce = nonce;
        this.hash  = (hash == null ? "" : hash);
        this.factura = factura;
    }


    public String getHash() { return hash; }
    public long   getNonce() { return nonce; }
    public String getTimestampISO() { return timestampISO; }
    public String getPreviousHash() { return previousHash; }

    public Factura getFactura() {
        return isEncrypted() ? null : factura;
    }

    public Factura getFacturaRawUnsafe() {
        return this.factura;
    }

    public boolean isEncrypted() {
        return ciphertextB64 != null && ivB64 != null && tagB64 != null && aesKeyEncB64 != null;
    }
    public String getCiphertextB64() { return ciphertextB64; }
    public String getIvB64() { return ivB64; }
    public String getTagB64() { return tagB64; }
    public String getAesKeyEncB64() { return aesKeyEncB64; }
    public String getCipherSpec() { return cipherSpec; }
    public String getKeyFingerprint() { return keyFingerprint; }

    public boolean isReadyToEncrypt() {
        return (this.factura != null) && !isEncrypted();
    }

    public void setEncryptedPayloadFromDb(String cipherSpec,
                                          String ciphertextB64,
                                          String ivB64,
                                          String tagB64,
                                          String aesKeyEncB64,
                                          String keyFingerprint) {
        this.cipherSpec   = (cipherSpec == null || cipherSpec.isBlank())
                ? "RSA/ECB/OAEPWithSHA-256AndMGF1Padding|AES/GCM/NoPadding"
                : cipherSpec;
        this.ciphertextB64 = ciphertextB64;
        this.ivB64         = ivB64;
        this.tagB64        = tagB64;
        this.aesKeyEncB64  = aesKeyEncB64;
        this.keyFingerprint = keyFingerprint;
    }
    
    public String calcularHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            StringBuilder sb = new StringBuilder();
            sb.append(index).append('|')
              .append(ns(previousHash)).append('|')
              .append(ns(timestampISO)).append('|')
              .append(nonce).append('|');

            if (isEncrypted()) {
                sb.append("ENC{")
                  .append("spec=").append(ns(cipherSpec)).append(';')
                  .append("ct=").append(ns(ciphertextB64)).append(';')
                  .append("iv=").append(ns(ivB64)).append(';')
                  .append("tag=").append(ns(tagB64)).append(';')
                  .append("k=").append(ns(aesKeyEncB64))
                  .append('}');
            } else {
                if (factura != null) {
                    sb.append("F{");
                    sb.append("num=").append(ns(getNumeroSafe(factura))).append(';');
                    sb.append("cli=").append(ns(getClienteSafe(factura))).append(';');
                    sb.append("tot=").append(dec(getTotalSafe(factura))).append(';');
                    sb.append("fp=").append(ns(getFormaPagoSafe(factura))).append(';');

                    List<FacturaItem> items = safeItems(factura);
                    sb.append("items=[");
                    for (int i = 0; i < items.size(); i++) {
                        FacturaItem it = items.get(i);
                        sb.append('{')
                          .append("id=").append(ns(getProdIdSafe(it))).append(',')
                          .append("cant=").append(it.getCantidad()).append(',')
                          .append("p=").append(dec(it.getPrecioUnitario()))
                          .append('}');
                        if (i < items.size() - 1) sb.append(',');
                    }
                    sb.append("]}");
                } else {
                    sb.append("GENESIS");
                }
            }

            byte[] hashBytes = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return toHex(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error al calcular hash: " + e.getMessage(), e);
        }
    }

    public void mineBlock(int difficulty) {
        final String target = "0".repeat(Math.max(0, difficulty));
        String h = calcularHash();
        while (difficulty > 0 && !h.startsWith(target)) {
            nonce++;
            h = calcularHash();
        }
        this.hash = h;
    }

    public String toJSON() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"index\": ").append(index).append(",\n");
        sb.append("  \"timestamp\": \"").append(esc(timestampISO)).append("\",\n");
        sb.append("  \"previousHash\": \"").append(esc(previousHash)).append("\",\n");
        sb.append("  \"hash\": \"").append(esc(hash)).append("\",\n");
        sb.append("  \"nonce\": ").append(nonce).append(",\n");

        if (isEncrypted()) {
            sb.append("  \"factura\": {},\n");
            sb.append("  \"facturaEnc\": {\n");
            sb.append("    \"cipherSpec\": \"").append(esc(cipherSpec)).append("\",\n");
            sb.append("    \"ciphertextB64\": \"").append(esc(ns(ciphertextB64))).append("\",\n");
            sb.append("    \"ivB64\": \"").append(esc(ns(ivB64))).append("\",\n");
            sb.append("    \"tagB64\": \"").append(esc(ns(tagB64))).append("\",\n");
            sb.append("    \"aesKeyEncB64\": \"").append(esc(ns(aesKeyEncB64))).append("\",\n");
            sb.append("    \"keyFingerprint\": \"").append(esc(ns(keyFingerprint))).append("\"\n");
            sb.append("  }\n");
        } else {
            sb.append("  \"factura\": ");
            if (factura == null) {
                sb.append("{}\n");
            } else {
                sb.append("{\n");
                sb.append("    \"numero\": \"").append(esc(getNumeroSafe(factura))).append("\",\n");
                sb.append("    \"fecha\": \"").append(esc(getFechaSafe(factura))).append("\",\n");
                sb.append("    \"clienteNombre\": \"").append(esc(getClienteSafe(factura))).append("\",\n");
                sb.append("    \"clienteId\": \"").append(esc(getClienteIdSafe(factura))).append("\",\n");
                sb.append("    \"direccion\": \"").append(esc(getDireccionSafe(factura))).append("\",\n");
                sb.append("    \"formaPago\": \"").append(esc(getFormaPagoSafe(factura))).append("\",\n");
                sb.append("    \"vendedor\": \"").append(esc(getVendedorSafe(factura))).append("\",\n");
                sb.append("    \"tienda\": \"").append(esc(getSucursalSafe(factura))).append("\",\n");
                sb.append("    \"tasaImpuesto\": ").append(dec(getTasaImpuestoSafe(factura))).append(",\n");
                sb.append("    \"items\": [\n");
                List<FacturaItem> items = safeItems(factura);
                for (int i = 0; i < items.size(); i++) {
                    FacturaItem it = items.get(i);
                    sb.append("      {")
                      .append("\"productoId\": \"").append(esc(getProdIdSafe(it))).append("\", ")
                      .append("\"descripcion\": \"").append(esc(ns(it.getDescripcion()))).append("\", ")
                      .append("\"cantidad\": ").append(it.getCantidad()).append(", ")
                      .append("\"precioUnitario\": ").append(dec(it.getPrecioUnitario()))
                      .append("}");
                    if (i < items.size() - 1) sb.append(",");
                    sb.append("\n");
                }
                sb.append("    ]\n");
                sb.append("  }\n");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String facturaJsonCanonica() {
        if (factura == null) return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"numero\":\"").append(esc(getNumeroSafe(factura))).append("\",");
        sb.append("\"fecha\":\"").append(esc(getFechaSafe(factura))).append("\",");
        sb.append("\"clienteNombre\":\"").append(esc(getClienteSafe(factura))).append("\",");
        sb.append("\"clienteId\":\"").append(esc(getClienteIdSafe(factura))).append("\",");
        sb.append("\"direccion\":\"").append(esc(getDireccionSafe(factura))).append("\",");
        sb.append("\"formaPago\":\"").append(esc(getFormaPagoSafe(factura))).append("\",");
        sb.append("\"vendedor\":\"").append(esc(getVendedorSafe(factura))).append("\",");
        sb.append("\"tienda\":\"").append(esc(getSucursalSafe(factura))).append("\",");
        sb.append("\"tasaImpuesto\":").append(dec(getTasaImpuestoSafe(factura))).append(",");
        sb.append("\"items\":[");
        List<FacturaItem> items = safeItems(factura);
        for (int i = 0; i < items.size(); i++) {
            FacturaItem it = items.get(i);
            sb.append("{")
              .append("\"productoId\":\"").append(esc(getProdIdSafe(it))).append("\",")
              .append("\"descripcion\":\"").append(esc(ns(it.getDescripcion()))).append("\",")
              .append("\"cantidad\":").append(it.getCantidad()).append(",")
              .append("\"precioUnitario\":").append(dec(it.getPrecioUnitario()))
              .append("}");
            if (i < items.size() - 1) sb.append(",");
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    public void encryptFacturaWithPublicKey(PublicKey rsaPub) throws Exception {
        if (factura == null) throw new IllegalStateException("No hay factura para cifrar");

        String json = facturaJsonCanonica();

        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey aesKey = kg.generateKey();

        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec gcm = new GCMParameterSpec(128, iv);

        javax.crypto.Cipher aes = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(javax.crypto.Cipher.ENCRYPT_MODE, aesKey, gcm);
        byte[] cipherAll = aes.doFinal(json.getBytes(StandardCharsets.UTF_8));

        int tagLen = 16;
        byte[] tag = Arrays.copyOfRange(cipherAll, cipherAll.length - tagLen, cipherAll.length);
        byte[] ciphertext = Arrays.copyOf(cipherAll, cipherAll.length - tagLen);

        javax.crypto.Cipher rsa = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(javax.crypto.Cipher.ENCRYPT_MODE, rsaPub);
        byte[] aesKeyEnc = rsa.doFinal(aesKey.getEncoded());

        this.ivB64 = Base64.getEncoder().encodeToString(iv);
        this.tagB64 = Base64.getEncoder().encodeToString(tag);
        this.ciphertextB64 = Base64.getEncoder().encodeToString(ciphertext);
        this.aesKeyEncB64 = Base64.getEncoder().encodeToString(aesKeyEnc);

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            this.keyFingerprint = bytesToHex(md.digest(rsaPub.getEncoded()));
        } catch (Throwable ignored) {
            this.keyFingerprint = null;
        }

        
        this.hash = calcularHash();
    }

    
    public String decryptFacturaWithPrivateKey(PrivateKey rsaPriv) throws Exception {
        if (!isEncrypted()) return facturaJsonCanonica(); 

        
        javax.crypto.Cipher rsa = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(javax.crypto.Cipher.DECRYPT_MODE, rsaPriv);
        byte[] aesKeyBytes = rsa.doFinal(Base64.getDecoder().decode(aesKeyEncB64));
        javax.crypto.spec.SecretKeySpec aesKey = new javax.crypto.spec.SecretKeySpec(aesKeyBytes, "AES");

        
        byte[] iv = Base64.getDecoder().decode(ivB64);
        byte[] tag = Base64.getDecoder().decode(tagB64);
        byte[] ciphertext = Base64.getDecoder().decode(ciphertextB64);

        byte[] cipherAll = new byte[ciphertext.length + tag.length];
        System.arraycopy(ciphertext, 0, cipherAll, 0, ciphertext.length);
        System.arraycopy(tag, 0, cipherAll, ciphertext.length, tag.length);

        GCMParameterSpec gcm = new GCMParameterSpec(128, iv);
        javax.crypto.Cipher aes = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(javax.crypto.Cipher.DECRYPT_MODE, aesKey, gcm);
        byte[] plain = aes.doFinal(cipherAll);

        return new String(plain, StandardCharsets.UTF_8);
    }

    

    
    private static String isoNowUtcZ() {
        SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    
    public static String normalizeToUtcZ(String ts) {
        if (ts == null || ts.isEmpty()) return isoNowUtcZ();
        try {
            String fixed = ts.replaceFirst("([+-]\\d\\d):(\\d\\d)$", "$1$2");
            OffsetDateTime odt = OffsetDateTime.parse(fixed);
            Instant inst = odt.toInstant();
            return DateTimeFormatter.ISO_INSTANT.format(inst);
        } catch (Throwable ignore) {
            return ts; 
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format("%02x", b));
        return out.toString();
    }

    private static String bytesToHex(byte[] in) {
        StringBuilder sb = new StringBuilder(in.length * 2);
        for (byte b : in) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String ns(Object o) { return (o == null) ? "" : o.toString(); }

    
    private static String dec(BigDecimal bd) {
        if (bd == null) return "0.00";
        return bd.stripTrailingZeros().toPlainString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    

    private static String getNumeroSafe(Factura f) {
        try { return ns(f.getNumero()); } catch (Throwable ignored) { return ""; }
    }
    private static String getClienteSafe(Factura f) {
        try { return ns(f.getCliente()); } catch (Throwable ignored) { return ""; }
    }
    private static String getClienteIdSafe(Factura f) {
        try { return ns(f.getClienteNit()); } catch (Throwable ignored) {
            try { return ns(f.getClienteId()); } catch (Throwable ignored2) { return ""; }
        }
    }
    private static String getDireccionSafe(Factura f) {
        try { return ns(f.getDireccion()); } catch (Throwable ignored) { return ""; }
    }
    private static String getFormaPagoSafe(Factura f) {
        try { return ns(f.getFormaPago()); } catch (Throwable ignored) { return ""; }
    }
    private static String getVendedorSafe(Factura f) {
        try { return ns(f.getVendedor()); } catch (Throwable ignored) { return ""; }
    }
    private static String getSucursalSafe(Factura f) {
        try { return ns(f.getSucursal()); } catch (Throwable ignored) { return ""; }
    }
    private static String getFechaSafe(Factura f) {
        try { return ns(f.getFechaISO()); } catch (Throwable ignored) {
            try { return ns(f.getFecha()); } catch (Throwable ignored2) { return ""; }
        }
    }
    private static BigDecimal getTasaImpuestoSafe(Factura f) {
        try { return f.getTasaImpuesto(); } catch (Throwable ignored) { return new BigDecimal("0.00"); }
    }
    private static BigDecimal getTotalSafe(Factura f) {
        try { return f.getTotal(); } catch (Throwable ignored) { return BigDecimal.ZERO; }
    }
    @SuppressWarnings("unchecked")
    private static List<FacturaItem> safeItems(Factura f) {
        try { return f.getItems(); } catch (Throwable ignored) { return java.util.Collections.emptyList(); }
    }
    private static String getProdIdSafe(FacturaItem it) {
        try { return ns(it.getProductoId()); } catch (Throwable ignored) { return ""; }
    }
}
