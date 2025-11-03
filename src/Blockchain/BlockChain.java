package Blockchain;

import Blockchain.Block;
import Invoice.FacturaItem;
import Invoice.Factura;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;


public class BlockChain {
    
    public static final int DIFFICULTY = 4;

    
    private int difficulty = DIFFICULTY;

    private final List<Block> chain = new ArrayList<>();

    public BlockChain() {
        createGenesisBlock();
    }

    
    public BlockChain setDifficulty(int newDifficulty) {
        if (newDifficulty < 0) throw new IllegalArgumentException("La dificultad no puede ser negativa");
        this.difficulty = newDifficulty;
        return this;
    }

    
    public int getDifficulty() {
        return difficulty;
    }

    
    
    private void createGenesisBlock() {
        Block genesis = new Block(0, "0", (Factura) null);
        
        genesis.mineBlock(0);   
        chain.add(genesis);
    }


    

    
    public void addBlock(Block block) {
        if (block == null) return;
        if (!chain.isEmpty()) {
            Block prev = chain.get(chain.size() - 1);
            
            if (!Objects.equals(block.previousHash, prev.getHash())) {
                System.err.println("WARN: previousHash no coincide al agregar bloque #" + block.index);
            }
        }
        chain.add(block);
    }

    
    public Block createNewBlock(Factura factura) {
        int newIndex = chain.size();
        String prevHash = getLastHash();
        Block b = new Block(newIndex, prevHash, factura);
        b.mineBlock(difficulty);
        return b;
    }

    public String getLastHash() {
        if (chain.isEmpty()) return "0";
        return chain.get(chain.size() - 1).getHash();
    }

    public Block getLastBlock() {
        if (chain.isEmpty()) return null;
        return chain.get(chain.size() - 1);
    }

    public Block getBlock(int index) {
        if (index < 0 || index >= chain.size()) return null;
        return chain.get(index);
    }

    public int getChainLength() { return chain.size(); }

    
    public List<Block> getChain() { return new ArrayList<>(chain); }

    

    
    public boolean isValid() {
        return validateReport().isEmpty();
    }

    
    public List<String> validateReport() {
        List<String> issues = new ArrayList<>();

        if (chain.isEmpty()) {
            issues.add("Cadena vacía: falta génesis.");
            return issues;
        }

        
        Block genesis = chain.get(0);
        if (genesis.index != 0) issues.add("Génesis: index debe ser 0.");
        if (!"0".equals(genesis.previousHash)) issues.add("Génesis: previousHash debe ser \"0\".");
        
        String gExpected = genesis.calcularHash();
        if (!Objects.equals(gExpected, genesis.getHash())) {
            issues.add("Génesis: hash no coincide con el contenido calculado.");
        }

        
        final String target = "0".repeat(Math.max(0, difficulty));

        for (int i = 1; i < chain.size(); i++) {
            Block cur  = chain.get(i);
            Block prev = chain.get(i - 1);

            if (cur.index != i) {
                issues.add("Bloque #" + i + ": index inconsistente (" + cur.index + " != " + i + ").");
            }

            
            if (!Objects.equals(cur.previousHash, prev.getHash())) {
                issues.add("Bloque #" + i + ": previousHash no enlaza con hash del bloque anterior.");
            }

            
            String expected = cur.calcularHash();
            if (!Objects.equals(expected, cur.getHash())) {
                issues.add("Bloque #" + i + ": hash no coincide con el contenido (nonce=" + cur.getNonce() + ").");
            }

            
            if (difficulty > 0 && (cur.getHash() == null || !cur.getHash().startsWith(target))) {
                issues.add("Bloque #" + i + ": no cumple dificultad (esperado prefijo \"" + target + "\").");
            }
        }

        return issues;
    }

    
    public int firstInvalidIndex() {
        if (chain.isEmpty()) return 0;
        
        if (chain.get(0).index != 0) return 0;
        if (!"0".equals(chain.get(0).previousHash)) return 0;
        if (!Objects.equals(chain.get(0).calcularHash(), chain.get(0).getHash())) return 0;

        final String target = "0".repeat(Math.max(0, difficulty));
        for (int i = 1; i < chain.size(); i++) {
            Block cur  = chain.get(i);
            Block prev = chain.get(i - 1);
            if (cur.index != i) return i;
            if (!Objects.equals(cur.previousHash, prev.getHash())) return i;
            String expected = cur.calcularHash();
            if (!Objects.equals(expected, cur.getHash())) return i;
            if (difficulty > 0 && (cur.getHash() == null || !cur.getHash().startsWith(target))) return i;
        }
        return -1;
    }

    
    public int repair() {
        int start = Math.max(1, firstInvalidIndex()); 
        if (start == -1) return 0;

        int fixed = 0;
        for (int i = start; i < chain.size(); i++) {
            Block prev = chain.get(i - 1);
            Block cur  = chain.get(i);

            
            Block rebuilt = new Block(
                    i,                      
                    prev.getHash(),         
                    cur.getTimestampISO(),  
                    0L,                     
                    "",                     
                    cur.getFactura()
            );
            rebuilt.mineBlock(difficulty);
            chain.set(i, rebuilt);
            fixed++;
        }
        return fixed;
    }

    

    
    public String toJSON() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"chainLength\": ").append(chain.size()).append(",\n");
        sb.append("  \"difficulty\": ").append(difficulty).append(",\n");
        sb.append("  \"blocks\": [\n");

        for (int i = 0; i < chain.size(); i++) {
            String blockJson = chain.get(i).toJSON();
            String[] lines = blockJson.split("\n");
            for (int j = 0; j < lines.length; j++) {
                sb.append("    ").append(lines[j]);
                if (j < lines.length - 1) sb.append("\n");
            }
            if (i < chain.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }

    
    public void fromJSON(String json) {
        json = json == null ? "" : json.trim();
        if (json.isEmpty() || !json.startsWith("{")) {
            throw new IllegalArgumentException("JSON inválido");
        }

        chain.clear();

        int blocksStart = json.indexOf("\"blocks\"");
        if (blocksStart == -1) throw new IllegalArgumentException("No se encontró 'blocks'");

        int arrayStart = json.indexOf("[", blocksStart);
        int arrayEnd   = findMatchingBracketAware(json, arrayStart, '[', ']');
        if (arrayStart == -1 || arrayEnd == -1) {
            throw new IllegalArgumentException("Array de bloques mal formado");
        }

        
        int diffKey = json.indexOf("\"difficulty\"");
        if (diffKey != -1) {
            double d = extractNumber(json, "\"difficulty\"");
            if (d >= 0 && d <= Integer.MAX_VALUE) {
                this.difficulty = (int) d;
            }
        }

        String blocksArray = json.substring(arrayStart + 1, arrayEnd).trim();
        if (blocksArray.isEmpty()) return;

        List<String> blockJsons = splitTopLevelObjectsAware(blocksArray);
        
        blockJsons.sort(Comparator.comparingInt(this::peekIndex));

        for (String bjson : blockJsons) {
            Block b = parseBlock(bjson);
            if (b != null) chain.add(b);
        }
    }

    

    private int peekIndex(String blockJson) {
        return (int) extractNumber(blockJson, "\"index\"");
    }

    
    private int findMatchingBracketAware(String s, int startIndex, char open, char close) {
        if (startIndex < 0 || startIndex >= s.length() || s.charAt(startIndex) != open) return -1;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = startIndex; i < s.length(); i++) {
            char c = s.charAt(i);

            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '\"') {
                    inString = false;
                }
                continue;
            } else {
                if (c == '\"') {
                    inString = true;
                    continue;
                }
                if (c == open) depth++;
                else if (c == close) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    
    private List<String> splitTopLevelObjectsAware(String arrayContent) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);

            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '\"') {
                    inString = false;
                }
                continue;
            } else {
                if (c == '\"') {
                    inString = true;
                    continue;
                }
                if (c == '{') {
                    if (depth == 0) start = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && start != -1) {
                        out.add(arrayContent.substring(start, i + 1).trim());
                        start = -1;
                    }
                }
            }
        }
        return out;
    }

    private Block parseBlock(String blockJson) {
        try {
            int    index     = (int) extractNumber(blockJson, "\"index\"");
            String timestamp = extractString(blockJson, "\"timestamp\"");
            String previous  = extractString(blockJson, "\"previousHash\"");
            String savedHash = extractString(blockJson, "\"hash\"");
            long   nonce     = (long) extractNumber(blockJson, "\"nonce\"");

            
            Factura factura = null;
            int fKey = blockJson.indexOf("\"factura\"");
            if (fKey != -1) {
                int start = blockJson.indexOf("{", fKey);
                int end   = findMatchingBracketAware(blockJson, start, '{', '}');
                if (start != -1 && end != -1) {
                    String facturaJson = blockJson.substring(start, end + 1).trim();
                    if (!facturaJson.equals("{}") && !facturaJson.equals("null")) {
                        factura = parseFactura(facturaJson);
                    }
                }
            }

            
            Block b = new Block(index, previous, timestamp, nonce, savedHash, factura);

            
            String expected = b.calcularHash();
            if (!Objects.equals(expected, savedHash)) {
                System.err.println("WARN: hash no coincide en bloque #" + index);
            }
            return b;
        } catch (Exception e) {
            System.err.println("Error al parsear bloque: " + e.getMessage());
            return null;
        }
    }

    private Factura parseFactura(String fjson) {
        String numero   = extractString(fjson, "\"numero\"");
        String fechaISO = extractString(fjson, "\"fecha\"");
        String cNombre  = extractString(fjson, "\"clienteNombre\"");
        String cId      = extractString(fjson, "\"clienteId\"");
        String dir      = extractString(fjson, "\"direccion\"");
        String forma    = extractString(fjson, "\"formaPago\"");
        String vendedor = extractString(fjson, "\"vendedor\"");
        String tienda   = extractString(fjson, "\"tienda\"");

        
        String tasaTok = extractNumberToken(fjson, "\"tasaImpuesto\"");
        BigDecimal tasa;
        try {
            tasa = (tasaTok.isEmpty()) ? BigDecimal.ZERO : new BigDecimal(tasaTok);
        } catch (NumberFormatException ex) {
            tasa = BigDecimal.valueOf(extractNumber(fjson, "\"tasaImpuesto\""));
        }

        
        Factura f = new Factura(numero, fechaISO)
                .setCliente(cNombre, cId)
                .setDireccion(dir)
                .setFormaPago(forma)
                .setVendedor(vendedor)
                .setTienda(tienda)
                .setTasaImpuesto(tasa);

        
        int itemsKey = fjson.indexOf("\"items\"");
        if (itemsKey != -1) {
            int arrStart = fjson.indexOf("[", itemsKey);
            int arrEnd   = findMatchingBracketAware(fjson, arrStart, '[', ']');
            String itemsArray = (arrStart != -1 && arrEnd != -1)
                    ? fjson.substring(arrStart + 1, arrEnd).trim()
                    : "";

            if (!itemsArray.isEmpty()) {
                List<String> itemObjs = splitTopLevelObjectsAware(itemsArray);
                for (String ijson : itemObjs) f.addItem(parseFacturaItem(ijson));
            }
        }
        return f;
    }

    private FacturaItem parseFacturaItem(String ijson) {
        String id   = extractString(ijson, "\"productoId\"");
        String desc = extractString(ijson, "\"descripcion\"");
        int cant    = (int) extractNumber(ijson, "\"cantidad\"");

        
        String pTok = extractNumberToken(ijson, "\"precioUnitario\"");
        BigDecimal pUnit;
        try {
            pUnit = pTok.isEmpty() ? BigDecimal.ZERO : new BigDecimal(pTok);
        } catch (NumberFormatException ex) {
            pUnit = BigDecimal.valueOf(extractNumber(ijson, "\"precioUnitario\""));
        }
        if (!pTok.contains(".")) {
            
            try { pUnit = pUnit.setScale(2); } catch (Exception ignored) {}
        }
        return new FacturaItem(id, desc, cant, pUnit);
    }

    
    private String extractString(String json, String key) {
        int keyIndex = json.indexOf(key);
        if (keyIndex == -1) return "";
        int colon = json.indexOf(":", keyIndex + key.length());
        if (colon == -1) return "";
        int startQuote = json.indexOf("\"", colon + 1);
        if (startQuote == -1) return "";
        
        boolean escape = false;
        for (int i = startQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\') { escape = true; continue; }
            if (c == '\"') { return json.substring(startQuote + 1, i); }
        }
        return "";
        }

    
    private double extractNumber(String json, String key) {
        int keyIndex = json.indexOf(key);
        if (keyIndex == -1) return 0;
        int colon = json.indexOf(":", keyIndex + key.length());
        if (colon == -1) return 0;
        int start = colon + 1;
        
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (!(Character.isDigit(c) || c == '.' || c == '-' || c == '+')) break;
            end++;
        }
        try { return Double.parseDouble(json.substring(start, end)); }
        catch (NumberFormatException e) { return 0; }
    }

    
    private String extractNumberToken(String json, String key) {
        int keyIndex = json.indexOf(key);
        if (keyIndex == -1) return "";
        int colon = json.indexOf(":", keyIndex + key.length());
        if (colon == -1) return "";
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (!(Character.isDigit(c) || c == '.' || c == '-' || c == '+')) break;
            end++;
        }
        String tok = json.substring(start, end).trim();
        if (tok.endsWith(",")) tok = tok.substring(0, tok.length()-1).trim();
        return tok;
    }

    

    
    public void printChain() {
        System.out.println("=== BLOCKCHAIN ===");
        System.out.println("Longitud: " + chain.size());
        System.out.println("Válida: " + isValid());
        List<String> report = validateReport();
        if (!report.isEmpty()) {
            System.out.println("Problemas:");
            for (String r : report) System.out.println(" - " + r);
        }
        System.out.println("\nBloques:");
        for (Block b : chain) {
            System.out.println("\n--- Bloque " + b.index + " ---");
            System.out.println("Timestamp: " + b.getTimestampISO());
            System.out.println("Previous:  " + b.previousHash);
            System.out.println("Nonce:     " + b.getNonce());
            System.out.println("Hash:      " + b.getHash());
        }
    }
}
