package DAO;

import Blockchain.BlockChain;
import Blockchain.Block;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BlockPostgresDAO {

    
    
    
    public void createTableIfNotExists(Connection c) throws SQLException {
        String ddl = """
        CREATE TABLE IF NOT EXISTS blockchain_blocks (
          index_int  INTEGER PRIMARY KEY,
          prev_hash  TEXT NOT NULL,
          hash       TEXT NOT NULL,
          nonce      BIGINT NOT NULL,
          data_json  JSONB NOT NULL,
          created_at TIMESTAMPTZ DEFAULT now()
        )
        """;
        try (Statement st = c.createStatement()) {
            st.execute(ddl);
        }
    }

    public boolean isEmpty(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM blockchain_blocks")) {
            rs.next();
            return rs.getLong(1) == 0L;
        } catch (SQLException e) {
            
            return true;
        }
    }

    
    
    
    
    public int normalizeExistingRows(Connection c) throws SQLException {
        String fix = """
        UPDATE blockchain_blocks
        SET data_json = (data_json::text)::jsonb
        WHERE jsonb_typeof(data_json) = 'string'
        """;
        try (Statement st = c.createStatement()) {
            return st.executeUpdate(fix);
        }
    }

    
    
    
    
    public void upsertBlock(Connection c, Block b) throws SQLException {
        String sql = """
        INSERT INTO blockchain_blocks(index_int, prev_hash, hash, nonce, data_json)
        VALUES (?, ?, ?, ?, ?::jsonb)
        ON CONFLICT (index_int) DO UPDATE SET
          prev_hash = EXCLUDED.prev_hash,
          hash      = EXCLUDED.hash,
          nonce     = EXCLUDED.nonce,
          data_json = EXCLUDED.data_json
        """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, b.index);
            ps.setString(2, b.previousHash);
            ps.setString(3, b.getHash());
            ps.setLong(4, b.getNonce());
            ps.setString(5, b.toJSON());      
            ps.executeUpdate();
        }
    }

    
    public void upsertChain(Connection c, BlockChain chain) throws SQLException {
        boolean old = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            for (Block b : chain.getChain()) upsertBlock(c, b);
            c.commit();
        } catch (SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(old);
        }
    }

    
    
    
    
    public BlockChain readChain(Connection c) throws Exception {
        
        String sql = """
        SELECT CASE
                 WHEN jsonb_typeof(data_json)='string'
                 THEN ((data_json::text)::jsonb)::text
                 ELSE data_json::text
               END AS data_json_text
        FROM blockchain_blocks
        ORDER BY index_int ASC
        """;

        List<String> blockJsons = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                blockJsons.add(rs.getString("data_json_text"));
            }
        }

        if (blockJsons.isEmpty()) return new BlockChain(); 

        
        String blocksJoined = String.join(",", blockJsons);
        String json = """
        {
          "chainLength": %d,
          "difficulty": %d,
          "blocks": [ %s ]
        }
        """.formatted(blockJsons.size(), BlockChain.DIFFICULTY, blocksJoined);

        BlockChain bc = new BlockChain();
        bc.fromJSON(json); 
        return bc;
    }

    
    
    
    
public boolean validateFromDB(Connection c) throws Exception {
    String sql = """
        WITH norm AS (
          SELECT 
            index_int,
            prev_hash,
            hash,
            nonce,
            CASE
              WHEN jsonb_typeof(data_json)='string'
              THEN (data_json::text)::jsonb
              ELSE data_json
            END AS j
          FROM blockchain_blocks
        )
        SELECT 
          index_int,
          prev_hash,
          hash,
          nonce,
          (j->>'hash')           AS json_hash,
          (j->>'previousHash')   AS json_prev,
          (j->>'index')::int     AS json_index,
          (j->>'nonce')::bigint  AS json_nonce
        FROM norm
        ORDER BY index_int ASC
    """;

    try (PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        final int difficulty = BlockChain.DIFFICULTY;                 
        final String target = "0".repeat(Math.max(0, difficulty));    
        String prevHashChain = "0";
        int expectedIndex = 0;

        while (rs.next()) {
            int    indexInt   = rs.getInt("index_int");
            String prevCol    = rs.getString("prev_hash");
            String hashCol    = rs.getString("hash");
            long   nonceCol   = rs.getLong("nonce");
            String jsonHash   = rs.getString("json_hash");
            String jsonPrev   = rs.getString("json_prev");
            int    jsonIndex  = rs.getInt("json_index");
            long   jsonNonce  = rs.getLong("json_nonce");

            
            if (indexInt != expectedIndex) return false;

            
            if (!java.util.Objects.equals(prevCol, prevHashChain)) return false;

            
            if (jsonIndex != indexInt) return false;
            if (!java.util.Objects.equals(jsonPrev, prevCol)) return false;
            if (jsonNonce != nonceCol) return false;
            if (!java.util.Objects.equals(hashCol, jsonHash)) return false;

            
            if (difficulty > 0 && indexInt > 0) {
                if (hashCol == null || !hashCol.startsWith(target)) return false;
            }

            
            prevHashChain = hashCol;
            expectedIndex++;
        }
        return true; 
    }
}


public boolean validateFromDB_RemineOnce(Connection c) throws Exception {
    final String sql = """
        SELECT index_int, prev_hash, hash, nonce,
               CASE
                 WHEN jsonb_typeof(data_json)='string'
                 THEN ((data_json::text)::jsonb)::text
                 ELSE data_json::text
               END AS data_json_text
        FROM blockchain_blocks
        ORDER BY index_int ASC
    """;

    try (PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        final int difficulty = BlockChain.DIFFICULTY;
        final String target = "0".repeat(Math.max(0, difficulty));

        String prevHashChain = "0";
        int expectedIndex = 0;
        boolean any = false;

        while (rs.next()) {
            any = true;

            int    index       = rs.getInt("index_int");
            String prevStored  = rs.getString("prev_hash");
            String hashStored  = rs.getString("hash");
            long   nonceStored = rs.getLong("nonce");
            String blockJson   = rs.getString("data_json_text");

            
            if (index != expectedIndex) return false;

            
            if (!java.util.Objects.equals(prevStored, prevHashChain)) return false;

            
            Block b = rehydrateBlock(blockJson);  
            if (b.getNonce() != nonceStored) return false;
            if (!java.util.Objects.equals(b.previousHash, prevStored)) return false;

            
            String recomputed = b.calcularHash();
            if (!java.util.Objects.equals(recomputed, hashStored)) return false;

            
            if (difficulty > 0 && index > 0 && (hashStored == null || !hashStored.startsWith(target))) {
                return false;
            }

            
            prevHashChain = hashStored;
            expectedIndex++;
        }

        
        return any ? true : true;
    }
}


    
    
    
    private Block rehydrateBlock(String blockJson) throws Exception {
        BlockChain tmp = new BlockChain(); 
        String json = """
        {"chainLength":2,"difficulty":%d,"blocks":[%s,%s]}
        """.formatted(BlockChain.DIFFICULTY, tmp.getBlock(0).toJSON(), blockJson);
        tmp.fromJSON(json);
        return tmp.getBlock(1);
    }
}
