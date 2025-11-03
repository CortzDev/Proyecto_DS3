package DAO;

import Model.ProductoComplejo;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductoPostgresDAO {

    public void createTableIfNotExists(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(
                "CREATE TABLE IF NOT EXISTS categorias (" +
                "  id      VARCHAR(64) PRIMARY KEY," +
                "  nombre  VARCHAR(200) NOT NULL UNIQUE" +
                ")"
            );
            st.execute(
                "CREATE TABLE IF NOT EXISTS productos (" +
                "  id            VARCHAR(64) PRIMARY KEY," +
                "  nombre        VARCHAR(200) NOT NULL," +
                "  categoria_id  VARCHAR(64) REFERENCES categorias(id) " +
                "    ON UPDATE CASCADE ON DELETE SET NULL," +
                "  stock         INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0)," +
                "  precio_unit   NUMERIC(12,2) NOT NULL DEFAULT 0" +
                ")"
            );
            st.execute("CREATE INDEX IF NOT EXISTS idx_productos_categoria ON productos(categoria_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_productos_nombre ON productos((lower(nombre)))");
        }
    }

    public void upsert(Connection c, ProductoComplejo p) throws SQLException {
        final String sql =
            "INSERT INTO productos(id, nombre, categoria_id, stock, precio_unit) " +
            "VALUES (?, ?, ?, ?, ?) " +
            "ON CONFLICT (id) DO UPDATE SET " +
            "  nombre       = EXCLUDED.nombre, " +
            "  categoria_id = EXCLUDED.categoria_id, " +
            "  stock        = EXCLUDED.stock, " +
            "  precio_unit  = EXCLUDED.precio_unit";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p.getId());
            ps.setString(2, p.getNombre());
            ps.setString(3, p.getCategoriaId());
            ps.setInt(4, p.getStock());
            BigDecimal precio = (p.getPrecio() == null) ? BigDecimal.ZERO : p.getPrecio();
            ps.setBigDecimal(5, precio);
            ps.executeUpdate();
        }
    }

    public void delete(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM productos WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    public ProductoComplejo get(Connection c, String id) throws SQLException {
        final String sql = "SELECT id, nombre, categoria_id, stock, precio_unit FROM productos WHERE id=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new ProductoComplejo(
                    rs.getString("id"),
                    rs.getString("nombre"),
                    rs.getBigDecimal("precio_unit"),
                    rs.getInt("stock"),
                    rs.getString("categoria_id")
                );
            }
        }
    }

    public List<ProductoComplejo> listAll(Connection c) throws SQLException {
        List<ProductoComplejo> out = new ArrayList<>();
        final String sql =
            "SELECT id, nombre, categoria_id, stock, precio_unit " +
            "FROM productos ORDER BY lower(nombre) ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ProductoComplejo p = new ProductoComplejo(
                    rs.getString("id"),
                    rs.getString("nombre"),
                    rs.getBigDecimal("precio_unit"),
                    rs.getInt("stock"),
                    rs.getString("categoria_id")
                );
                out.add(p);
            }
        }
        return out;
    }

    public void updateStockDelta(Connection c, String id, int delta) throws SQLException {
        final String sql =
            "UPDATE productos " +
            "   SET stock = stock + ? " +
            " WHERE id = ? " +
            "   AND stock + ? >= 0";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, delta);
            ps.setString(2, id);
            ps.setInt(3, delta);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new SQLException("Stock insuficiente o producto inexistente: " + id);
        }
    }
}
