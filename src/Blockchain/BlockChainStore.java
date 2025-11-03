package Blockchain;

import Blockchain.Block;
import Invoice.Factura;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;

import Crypto.AsymCryptoUtil;
import DAO.BlockPostgresDAO;
import Postgres.DbPostgres;

import java.security.PublicKey;
import java.security.PrivateKey;


public class BlockChainStore {
    private final BlockChain chain = new BlockChain(); 
    private final BlockPostgresDAO dao = new BlockPostgresDAO();
    
    
    public void clearCachedKeys() {
        this.cachedPubKey = null;
        this.cachedPrivKey = null;
    }


    
    private PublicKey  cachedPubKey;   
    private PrivateKey cachedPrivKey;  

    public BlockChain getChain() { return chain; }
    public String getLastHash() { return chain.getLastHash(); }

    
    public void appendBlock(Block block) { chain.addBlock(block); }

    
    public void appendBlockAndPersist(boolean writeJsonBackup) throws Exception {
        try (Connection conn = DbPostgres.open()) {
            dao.createTableIfNotExists(conn);
            dao.upsertChain(conn, chain); 
        }
        if (writeJsonBackup) saveBackupJSON(); 
    }

    
    public void loadFromPostgresIfAny() throws Exception {
        try (Connection conn = DbPostgres.open()) {
            dao.createTableIfNotExists(conn);
            if (!dao.isEmpty(conn)) {
                BlockChain db = dao.readChain(conn);
                chain.fromJSON(db.toJSON()); 
            }
        }
    }

    
    public boolean validateFromPostgres() throws Exception {
        try (Connection conn = DbPostgres.open()) {
            dao.createTableIfNotExists(conn);
            return dao.validateFromDB(conn);
        }
    }

    
    public int repairInMemoryAndSaveToPostgres() throws Exception {
        int fixed = chain.repair(); 
        try (Connection conn = DbPostgres.open()) {
            dao.createTableIfNotExists(conn);
            dao.upsertChain(conn, chain);
        }
        return fixed;
    }

    

    
    public void saveBackupJSON() throws IOException {
        String json = chain.toJSON();
        Files.writeString(Path.of("blockchain.json"), json, StandardCharsets.UTF_8);
    }

    
    public void saveBackupJSONWithRotation() throws IOException {
        Path p = Path.of("blockchain.json");
        if (Files.exists(p)) {
            Path bak = Path.of("blockchain.autobak_" + System.currentTimeMillis() + ".json");
            Files.copy(p, bak, StandardCopyOption.COPY_ATTRIBUTES);
            
        }
        saveBackupJSON();
    }

    
    public void exportCSV(Path out) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("index,prevHash,hash,nonce,cliente,numero,total\n");
        for (Block b : chain.getChain()) {
            String cliente = "";
            String numero  = "";
            String total   = "";
            if (!b.isEncrypted()) {
                Factura f = b.getFactura();
                if (f != null) {
                    try { cliente = (f.getCliente() != null) ? f.getCliente() : ""; } catch (Throwable ignored) {}
                    try { numero  = (f.getNumero()  != null) ? f.getNumero()  : ""; } catch (Throwable ignored) {}
                    try { total   = (f.getTotal()   != null) ? f.getTotal().toPlainString() : ""; } catch (Throwable ignored) {}
                }
            }

            sb.append(b.index).append(',')
              .append('"').append(b.previousHash).append('"').append(',')
              .append('"').append(b.getHash()).append('"').append(',')
              .append(b.getNonce()).append(',')
              .append('"').append(cliente).append('"').append(',')
              .append('"').append(numero).append('"').append(',')
              .append(total).append('\n');
        }
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
    }

    
    public void migrateIfLegacy() throws IOException {
        Path p = Path.of("blockchain.json");
        if (!Files.exists(p)) return;

        String json = Files.readString(p, StandardCharsets.UTF_8);
        if (json.contains("\"nonce\"")) return; 

        
        chain.fromJSON(json);

        
        BlockChain rebuilt = new BlockChain();
        var blocks = chain.getChain();
        for (int i = 1; i < blocks.size(); i++) {
            Block old = blocks.get(i);
            Block neo = new Block(i, rebuilt.getLastHash(), old.getFactura());
            neo.mineBlock(BlockChain.DIFFICULTY);
            rebuilt.addBlock(neo);
        }

        
        saveBackupJSONWithRotation();
        Files.writeString(p, rebuilt.toJSON(), StandardCharsets.UTF_8);

        
        chain.fromJSON(rebuilt.toJSON());
    }

    

    
    public void saveEncryptedAsymmetric(Path publicKeyPem, Path outFile) throws Exception {
        String json = chain.toJSON();
        byte[] blob = AsymCryptoUtil.encryptHybridToBlob(
                json.getBytes(StandardCharsets.UTF_8), publicKeyPem
        );
        Files.write(outFile, blob);
    }

    
    public void loadEncryptedAsymmetric(Path privateKeyPem, Path inFile) throws Exception {
        byte[] blob = Files.readAllBytes(inFile);
        byte[] clear = AsymCryptoUtil.decryptHybridFromBlob(blob, privateKeyPem);
        String json = new String(clear, StandardCharsets.UTF_8);
        chain.fromJSON(json);
    }

    
    public void saveEncryptedAsymmetric(String publicKeyPemPath, String outFilePath) throws Exception {
        saveEncryptedAsymmetric(Path.of(publicKeyPemPath), Path.of(outFilePath));
    }
    public void loadEncryptedAsymmetric(String privateKeyPemPath, String inFilePath) throws Exception {
        loadEncryptedAsymmetric(Path.of(privateKeyPemPath), Path.of(inFilePath));
    }

    
    public void resetChain() {
        BlockChain fresh = new BlockChain(); 
        chain.fromJSON(fresh.toJSON());      
    }

    

    
    public void loadBlockEncryptionKey(Path publicKeyPem) throws Exception {
        this.cachedPubKey = AsymCryptoUtil.loadPublicKeyPem(publicKeyPem);
    }

    
    public void loadBlockDecryptionKey(Path privateKeyPem) throws Exception {
        this.cachedPrivKey = AsymCryptoUtil.loadPrivateKeyPem(privateKeyPem);
    }

    
    public Block addFacturaBlockEncrypted(Factura factura,
                                          Path publicKeyPem,
                                          int difficulty,
                                          boolean persist,
                                          boolean writeJsonBackup) throws Exception {
        if (factura == null) throw new IllegalArgumentException("Factura nula");
        PublicKey pub = (publicKeyPem != null)
                ? AsymCryptoUtil.loadPublicKeyPem(publicKeyPem)
                : this.cachedPubKey;
        if (pub == null) throw new IllegalStateException("No hay clave pública cargada para cifrar");

        int nextIndex = chain.getChainLength();  
        String prevHash = chain.getLastHash();

        Block b = new Block(nextIndex, prevHash, factura);
        b.encryptFacturaWithPublicKey(pub);      
        b.mineBlock(difficulty);
        chain.addBlock(b);

        
        try {
            Path outDir = Path.of("blocks_enc");
            saveBlockAsEncryptedJsonHex(outDir, b, pub);
        } catch (Throwable t) {
            System.err.println("Advertencia: no fue posible escribir blocks_enc: " + t.getMessage());
        }

        if (persist) {
            appendBlockAndPersist(writeJsonBackup);
        }
        return b;
    }

    
    public String decryptFacturaJson(Block block, Path privKeyPem) throws Exception {
        if (block == null) throw new IllegalArgumentException("Bloque nulo");
        PrivateKey priv = (privKeyPem != null)
                ? AsymCryptoUtil.loadPrivateKeyPem(privKeyPem)
                : this.cachedPrivKey;
        if (priv == null) throw new IllegalStateException("No hay clave privada cargada para descifrar");
        return block.decryptFacturaWithPrivateKey(priv);
    }

    

    public Block addFacturaBlockEncrypted(Factura factura, int difficulty) throws Exception {
        return addFacturaBlockEncrypted(factura, null, difficulty, true, false);
    }

    public Block addFacturaBlockEncrypted(Factura factura, Path publicKeyPem, int difficulty) throws Exception {
        return addFacturaBlockEncrypted(factura, publicKeyPem, difficulty, true, false);
    }

    public String decryptFacturaJson(Block block) throws Exception {
        return decryptFacturaJson(block, null);
    }

    

    
    public boolean hasBlockEncryptionKey() {
        return this.cachedPubKey != null;
    }

    
    public boolean hasBlockDecryptionKey() {
        return this.cachedPrivKey != null;
    }

    
    public void saveEncryptedBlockFileByIndex(int index, Path outDir, Path publicKeyPemOrNull) throws Exception {
        if (index < 0 || index >= chain.getChainLength()) {
            throw new IllegalArgumentException("Índice de bloque fuera de rango: " + index);
        }
        PublicKey pub = (publicKeyPemOrNull != null)
                ? AsymCryptoUtil.loadPublicKeyPem(publicKeyPemOrNull)
                : this.cachedPubKey;

        if (pub == null) {
            throw new IllegalStateException("No hay clave pública cargada para cifrar.");
        }

        Block b = chain.getChain().get(index);
        saveBlockAsEncryptedJsonHex(outDir, b, pub);
    }

    

    
    public void exportBlocksAsEncryptedJsonHex(Path dir, Path publicKeyPem, boolean includeGenesis) throws Exception {
        if (!Files.exists(dir)) Files.createDirectories(dir);
        PublicKey pub = AsymCryptoUtil.loadPublicKeyPem(publicKeyPem);

        var list = chain.getChain();
        for (int i = 0; i < list.size(); i++) {
            if (!includeGenesis && i == 0) continue;
            Block b = list.get(i);

            String blockJson = b.toJSON(); 
            String encJson   = AsymCryptoUtil.encryptHybridToJsonHex(
                    blockJson.getBytes(StandardCharsets.UTF_8), pub
            );

            Path out = dir.resolve(String.format("block_%d.enc.json", i));
            Files.writeString(out, encJson, StandardCharsets.UTF_8);
        }
    }

    
    public void exportBlocksAsPlainJson(Path dir, boolean includeGenesis) throws Exception {
        if (!Files.exists(dir)) Files.createDirectories(dir);
        var list = chain.getChain();
        for (int i = 0; i < list.size(); i++) {
            if (!includeGenesis && i == 0) continue;
            Block b = list.get(i);
            String blockJson = b.toJSON();
            Path out = dir.resolve(String.format("block_%d.json", i));
            Files.writeString(out, blockJson, StandardCharsets.UTF_8);
        }
    }

    

    
    public void saveBlockAsEncryptedJsonHex(Path outDir, Block b, PublicKey pub) throws Exception {
        if (b == null) throw new IllegalArgumentException("Bloque nulo");
        if (pub == null) throw new IllegalArgumentException("PublicKey nula");

        if (!Files.exists(outDir)) Files.createDirectories(outDir);

        String blockJson = b.toJSON(); 
        String encJson   = AsymCryptoUtil.encryptHybridToJsonHex(
                blockJson.getBytes(StandardCharsets.UTF_8), pub
        );

        Path out = outDir.resolve(String.format("block_%d.enc.json", b.index));
        Files.writeString(out, encJson, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    
    public String decryptBlockFileToJson(int index, Path outDir, Path privKeyPem) throws Exception {
        if (index < 0) throw new IllegalArgumentException("Índice inválido");
        Path p = outDir.resolve(String.format("block_%d.enc.json", index));
        if (!Files.exists(p)) throw new java.io.FileNotFoundException("No existe: " + p.toAbsolutePath());

        String encJson = Files.readString(p, StandardCharsets.UTF_8);
        PrivateKey priv = (privKeyPem != null)
                ? AsymCryptoUtil.loadPrivateKeyPem(privKeyPem)
                : this.cachedPrivKey;
        if (priv == null) throw new IllegalStateException("No hay clave privada cargada para descifrar");

        byte[] clear = AsymCryptoUtil.decryptHybridFromJsonHex(encJson, priv);
        return new String(clear, StandardCharsets.UTF_8);
    }

    
    public String getBlockJsonDecryptedIfPossible(Block block, Path outDir, Path privKeyPem) throws Exception {
        if (block == null) throw new IllegalArgumentException("Bloque nulo");
        if (outDir != null) {
            try {
                return decryptBlockFileToJson(block.index, outDir, privKeyPem);
            } catch (java.io.FileNotFoundException ignored) {
                
            }
        }
        PrivateKey priv = (privKeyPem != null)
                ? AsymCryptoUtil.loadPrivateKeyPem(privKeyPem)
                : this.cachedPrivKey;
        if (priv == null) throw new IllegalStateException("No hay clave privada cargada para descifrar");
        
        return block.decryptFacturaWithPrivateKey(priv);
    }
}
