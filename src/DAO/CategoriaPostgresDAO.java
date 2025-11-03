package DAO;

import Model.Categoria;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CategoriaPostgresDAO {

    public void createTableIfNotExists(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS categorias (
                  id      VARCHAR(64) PRIMARY KEY,
                  nombre  VARCHAR(200) NOT NULL UNIQUE
                )
            """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_categorias_nombre ON categorias((lower(nombre)))");
        }
    }

    public void upsert(Connection c, Categoria cat) throws SQLException {
        String sql = """
            INSERT INTO categorias(id, nombre)
            VALUES (?, ?)
            ON CONFLICT (id) DO UPDATE SET
              nombre = EXCLUDED.nombre
        """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, cat.getId());
            ps.setString(2, cat.getNombre());
            ps.executeUpdate();
        }
    }

    public void delete(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM categorias WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    public List<Categoria> listAll(Connection c) throws SQLException {
        List<Categoria> out = new ArrayList<>();
        String sql = "SELECT id, nombre FROM categorias ORDER BY lower(nombre)";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Categoria cat = new Categoria(
                        rs.getString("id"),
                        rs.getString("nombre"),
                        "" 
                );
                out.add(cat);
            }
        }
        return out;
    }
}
