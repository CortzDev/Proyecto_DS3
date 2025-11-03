package DAO;

import Model.TransaccionCompleja;
import Model.TipoTx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class TransaccionPostgresDAO {

    
    public void createTableIfNotExists(Connection c) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS transacciones (
              id              TEXT PRIMARY KEY,
              producto_id     TEXT NOT NULL,
              tipo            TEXT NOT NULL,
              cantidad        INT  NOT NULL CHECK (cantidad > 0),
              precio_unitario NUMERIC(12,2) NOT NULL,
              total           NUMERIC(14,2) NOT NULL,
              descripcion     TEXT DEFAULT '',
              usuario         TEXT DEFAULT 'Sistema',
              fecha           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
              estado          TEXT DEFAULT 'COMPLETADO',
              factura_numero  TEXT,
              lote_id         TEXT
            );
            -- Índices recomendados
            CREATE INDEX IF NOT EXISTS idx_tx_producto  ON transacciones(producto_id);
            CREATE INDEX IF NOT EXISTS idx_tx_fecha     ON transacciones(fecha);
            CREATE INDEX IF NOT EXISTS idx_tx_tipo      ON transacciones(tipo);
        """;
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        }

        
        
    }

    
    private static String ensureId(TransaccionCompleja t) {
        String id = t.getId();
        if (id == null || id.isBlank()) {
            id = "TX-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
        return id;
    }

    private static BigDecimal scale2(BigDecimal v) {
        if (v == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static Timestamp ts(java.util.Date d) {
        return (d == null) ? new Timestamp(System.currentTimeMillis()) : new Timestamp(d.getTime());
    }

    
    
    public void insert(Connection c, TransaccionCompleja t, String facturaNumero, String loteId) throws SQLException {
        String sql = """
            INSERT INTO transacciones
              (id, producto_id, tipo, cantidad, precio_unitario, total, descripcion, usuario, fecha, estado, factura_numero, lote_id)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
        """;

        String id = ensureId(t);
        BigDecimal precio = scale2(t.getPrecioUnit());
        BigDecimal total  = (t.getTotal() == null)
                ? precio.multiply(BigDecimal.valueOf(t.getCantidad())).setScale(2, RoundingMode.HALF_UP)
                : scale2(t.getTotal());

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1,  id);
            ps.setString(2,  t.getProductoId());
            ps.setString(3,  t.getTipoString());
            ps.setInt   (4,  t.getCantidad());
            ps.setBigDecimal(5, precio);
            ps.setBigDecimal(6, total);
            ps.setString(7,  t.getDescripcion());
            ps.setString(8,  t.getUsuario());
            ps.setTimestamp(9, ts(t.getFechaHora()));
            ps.setString(10, t.getEstado());
            ps.setString(11, facturaNumero);
            ps.setString(12, loteId);
            ps.executeUpdate();
        }
    }

    
    public void insertBatch(Connection c, List<TransaccionCompleja> list, String facturaNumero, String loteId) throws SQLException {
        String sql = """
            INSERT INTO transacciones
              (id, producto_id, tipo, cantidad, precio_unitario, total, descripcion, usuario, fecha, estado, factura_numero, lote_id)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
        """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (TransaccionCompleja t : list) {
                String id = ensureId(t);
                BigDecimal precio = scale2(t.getPrecioUnit());
                BigDecimal total  = (t.getTotal() == null)
                        ? precio.multiply(BigDecimal.valueOf(t.getCantidad())).setScale(2, RoundingMode.HALF_UP)
                        : scale2(t.getTotal());

                ps.setString(1,  id);
                ps.setString(2,  t.getProductoId());
                ps.setString(3,  t.getTipoString());
                ps.setInt   (4,  t.getCantidad());
                ps.setBigDecimal(5, precio);
                ps.setBigDecimal(6, total);
                ps.setString(7,  t.getDescripcion());
                ps.setString(8,  t.getUsuario());
                ps.setTimestamp(9, ts(t.getFechaHora()));
                ps.setString(10, t.getEstado());
                ps.setString(11, facturaNumero);
                ps.setString(12, loteId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    
    
    private TransaccionCompleja mapRow(ResultSet rs) throws SQLException {
        
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        Timestamp tstamp = rs.getTimestamp("fecha", utc);

        TransaccionCompleja t = new TransaccionCompleja(
                rs.getString("id"),
                rs.getString("producto_id"),
                TipoTx.valueOf(rs.getString("tipo").toUpperCase()),
                rs.getInt("cantidad"),
                rs.getBigDecimal("precio_unitario"),
                rs.getString("usuario"),
                rs.getString("descripcion"),
                tstamp != null ? new java.util.Date(tstamp.getTime()) : new java.util.Date()
        );
        t.setEstado(rs.getString("estado"));
        t.setTotal(rs.getBigDecimal("total"));
        return t;
    }

    
    public List<TransaccionCompleja> listAll(Connection c) throws SQLException {
        String sql = """
            SELECT id, producto_id, tipo, cantidad, precio_unitario, total, descripcion, usuario, fecha, estado
            FROM transacciones
            ORDER BY fecha ASC
        """;
        List<TransaccionCompleja> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(mapRow(rs));
        }
        return out;
    }

    
    public List<TransaccionCompleja> listByProducto(Connection c, String productoId) throws SQLException {
        String sql = """
            SELECT id, producto_id, tipo, cantidad, precio_unitario, total, descripcion, usuario, fecha, estado
            FROM transacciones
            WHERE producto_id = ?
            ORDER BY fecha ASC
        """;
        List<TransaccionCompleja> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, productoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        }
        return out;
    }

    
    public List<TransaccionCompleja> listFiltered(Connection c,
                                                  java.util.Date desde,
                                                  java.util.Date hasta,
                                                  String tipo,
                                                  String q) throws SQLException {
        StringBuilder sb = new StringBuilder("""
            SELECT id, producto_id, tipo, cantidad, precio_unitario, total, descripcion, usuario, fecha, estado
            FROM transacciones
            WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();

        if (desde != null) { sb.append(" AND fecha >= ?"); params.add(new Timestamp(desde.getTime())); }
        if (hasta != null) { sb.append(" AND fecha <= ?"); params.add(new Timestamp(hasta.getTime())); }
        if (tipo != null && !tipo.equalsIgnoreCase("TODOS") && !tipo.isBlank()) {
            sb.append(" AND UPPER(tipo) = UPPER(?)"); params.add(tipo.trim());
        }
        if (q != null && !q.isBlank()) {
            sb.append(" AND (producto_id ILIKE ? OR usuario ILIKE ? OR descripcion ILIKE ?)");
            String like = "%" + q.trim() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        sb.append(" ORDER BY fecha ASC");

        List<TransaccionCompleja> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sb.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Timestamp ts)   ps.setTimestamp(i+1, ts);
                else if (p instanceof Integer n) ps.setInt(i+1, n);
                else if (p instanceof BigDecimal bd) ps.setBigDecimal(i+1, bd);
                else                               ps.setString(i+1, p.toString());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        }
        return out;
    }
}
