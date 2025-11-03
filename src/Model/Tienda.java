package Model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.sql.Connection;
import java.sql.SQLException;

import DAO.CategoriaPostgresDAO;
import DAO.ProductoPostgresDAO;
import DAO.TransaccionPostgresDAO;
import Postgres.DbPostgres;
import Invoice.LineaVenta;


public class Tienda {

    
    private static final int MONEY_SCALE = 2;
    private static BigDecimal money(BigDecimal x) {
        return (x == null ? BigDecimal.ZERO : x).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
    private static String nz(String s) { return (s == null ? "" : s); }

    
    private String id;
    private String nombre;
    private String direccion;
    private String telefono;
    private Date fechaCreacion;

    private final List<Categoria> categorias;
    private final Map<String, ProductoComplejo> productosMap;

    
    private final List<TransaccionCompleja> transaccionesTodas;

    
    public Tienda(String nombre) {
        this("T" + System.currentTimeMillis(), nombre, "", "");
    }

    public Tienda(String id, String nombre, String direccion, String telefono) {
        this.id = id;
        this.nombre = nombre;
        this.direccion = direccion;
        this.telefono = telefono;
        this.fechaCreacion = new Date();
        this.categorias = new ArrayList<>();
        this.productosMap = new LinkedHashMap<>();
        this.transaccionesTodas = new ArrayList<>();
    }

    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public Date getFechaCreacion() { return fechaCreacion; }

    public List<Categoria> getCategorias() { return new ArrayList<>(categorias); }
    public Map<String, ProductoComplejo> getProductos() { return new LinkedHashMap<>(productosMap); }
    public Map<String, ProductoComplejo> getProductosMap() { return new LinkedHashMap<>(productosMap); }
    public List<TransaccionCompleja> getTransacciones() { return new ArrayList<>(transaccionesTodas); }
    public List<TransaccionCompleja> getTransaccionesTodas() { return getTransacciones(); }

    
    public void agregarCategoria(Categoria categoria) {
        if (categoria != null && !categorias.contains(categoria)) categorias.add(categoria);
    }
    public void addCategoria(Categoria categoria) { agregarCategoria(categoria); }

    public Categoria buscarCategoria(String id) {
        for (Categoria cat : categorias) if (cat.getId().equals(id)) return cat;
        return null;
    }
    public Categoria buscarCategoriaPorNombre(String nombre) {
        for (Categoria cat : categorias) if (cat.getNombre().equals(nombre)) return cat;
        return null;
    }
    public void eliminarCategoria(String id) {
        categorias.removeIf(cat -> cat.getId().equals(id));
    }

    
    public void agregarProducto(ProductoComplejo producto) {
        if (producto == null) return;
        productosMap.put(producto.getId(), producto);
        Categoria categoria = buscarCategoria(producto.getCategoriaId());
        if (categoria != null) categoria.agregarProducto(producto);
    }
    public void addProducto(ProductoComplejo producto) { agregarProducto(producto); }

    public ProductoComplejo getProducto(String id) { return productosMap.get(id); }
    public ProductoComplejo buscarProducto(String id) { return productosMap.get(id); }
    public ProductoComplejo buscarProductoPorNombre(String nombre) {
        for (ProductoComplejo p : productosMap.values())
            if (p.getNombre().equalsIgnoreCase(nombre)) return p;
        return null;
    }
    public void eliminarProducto(String id) {
        ProductoComplejo producto = productosMap.remove(id);
        if (producto != null) for (Categoria cat : categorias) cat.eliminarProductoPorId(id);
    }

    

    
    public TransaccionCompleja registrarTransaccion(
            String productoId, TipoTx tipo, int cantidad, BigDecimal precioUnit, String usuario) {

        ProductoComplejo p = getProducto(productoId);
        if (p == null) throw new IllegalArgumentException("Producto no existe: " + productoId);
        if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser > 0");
        if ((tipo == TipoTx.SALIDA || tipo == TipoTx.VENTA) && cantidad > p.getStock())
            throw new IllegalArgumentException("Stock insuficiente. Stock actual: " + p.getStock());

        BigDecimal precio = money(precioUnit != null ? precioUnit : p.getPrecio());

        TransaccionCompleja tx = new TransaccionCompleja(
                "TX-" + UUID.randomUUID().toString().substring(0, 8),
                productoId, tipo, cantidad, precio, nz(usuario));

        
        runInTransaction(conn -> {
            new ProductoPostgresDAO().createTableIfNotExists(conn);
            new TransaccionPostgresDAO().createTableIfNotExists(conn);

            int delta = computeDelta(tipo, cantidad);
            new ProductoPostgresDAO().updateStockDelta(conn, productoId, delta);
            new TransaccionPostgresDAO().insert(conn, tx, null, null);
        });

        
        p.aplicarMovimientoStock(tipo, cantidad);
        p.appendTransaccion(tx); 
        transaccionesTodas.add(tx);
        return tx;
    }

    
    public List<TransaccionCompleja> registrarVentaAgrupada(
            String facturaNumero, String usuario, List<LineaVenta> items) throws Exception {

        
        class Agr { String id; int cant; BigDecimal precio; Agr(String id){this.id=id;} }
        Map<String, Agr> grupo = new LinkedHashMap<>();
        for (LineaVenta it : items) {
            Agr a = grupo.computeIfAbsent(it.productoId, Agr::new);
            a.cant += it.cantidad;
            a.precio = (it.precioUnit != null ? it.precioUnit : a.precio);
        }

        
        for (Agr a : grupo.values()) {
            ProductoComplejo p = getProducto(a.id);
            if (p == null) throw new IllegalArgumentException("Producto no existe: " + a.id);
            if (a.cant <= 0) throw new IllegalArgumentException("Cantidad inválida para " + a.id);
            if (a.cant > p.getStock())
                throw new IllegalArgumentException("Stock insuficiente para " + p.getNombre() + ". Disponible: " + p.getStock());
        }

        String loteId = "LOT-" + UUID.randomUUID().toString().substring(0,8);
        List<TransaccionCompleja> txs = new ArrayList<>();

        
        runInTransaction(conn -> {
            ProductoPostgresDAO prodDao = new ProductoPostgresDAO();
            TransaccionPostgresDAO txDao = new TransaccionPostgresDAO();
            prodDao.createTableIfNotExists(conn);
            txDao.createTableIfNotExists(conn);

            for (Agr a : grupo.values()) {
                ProductoComplejo p = getProducto(a.id);
                BigDecimal precio = money(a.precio != null ? a.precio : p.getPrecio());

                
                prodDao.updateStockDelta(conn, a.id, -a.cant);

                
                TransaccionCompleja tx = new TransaccionCompleja(
                        "TX-" + UUID.randomUUID().toString().substring(0, 8),
                        a.id, TipoTx.VENTA, a.cant, precio,
                        "Venta Factura " + nz(facturaNumero), nz(usuario), new Date()
                );
                txDao.insert(conn, tx, facturaNumero, loteId);
                txs.add(tx);
            }
        });

        
        for (Agr a : grupo.values()) {
            ProductoComplejo p = getProducto(a.id);
            p.aplicarMovimientoStock(TipoTx.SALIDA, a.cant);
        }
        for (TransaccionCompleja tx : txs) {
            ProductoComplejo p = getProducto(tx.getProductoId());
            p.appendTransaccion(tx); 
        }
        transaccionesTodas.addAll(txs);
        return txs;
    }

    private static int computeDelta(TipoTx tipo, int cantidad) {
        return (tipo == TipoTx.ENTRADA || tipo == TipoTx.DEVOLUCION) ? cantidad : -cantidad;
    }

    public void agregarTransaccion(TransaccionCompleja t) {
        if (t != null) transaccionesTodas.add(t);
    }

    
    public double calcularValorTotalTienda() {
        double total = 0;
        for (Categoria c : categorias) total += c.calcularValorCategoria();
        return total;
    }
    public int contarProductosTotal() { return productosMap.size(); }
    public int contarTransaccionesTotal() { return transaccionesTodas.size(); }
    public int contarProductosConBajoStock() {
        int count = 0; for (ProductoComplejo p : productosMap.values()) if (p.requiereReposicion()) count++; return count;
    }
    public List<ProductoComplejo> getProductosBajoStock() {
        List<ProductoComplejo> out = new ArrayList<>();
        for (ProductoComplejo p : productosMap.values()) if (p.requiereReposicion()) out.add(p);
        return out;
    }
    public double calcularVentasTotales() {
        double total = 0;
        for (TransaccionCompleja tx : transaccionesTodas)
            if (tx.getTipo() == TipoTx.VENTA) total += tx.getTotal().doubleValue();
        return total;
    }

    

    
    public void loadCatalogFromPostgres() throws SQLException {
        try (Connection c = DbPostgres.open()) {
            runInTransaction(c, conn -> {
                CategoriaPostgresDAO catDao = new CategoriaPostgresDAO();
                ProductoPostgresDAO  prodDao = new ProductoPostgresDAO();
                TransaccionPostgresDAO txDao = new TransaccionPostgresDAO();

                catDao.createTableIfNotExists(conn);
                prodDao.createTableIfNotExists(conn);
                txDao.createTableIfNotExists(conn);

                categorias.clear();
                productosMap.clear();
                transaccionesTodas.clear();

                
                for (Categoria cat : catDao.listAll(conn)) categorias.add(cat);
                for (ProductoComplejo p : prodDao.listAll(conn)) {
                    productosMap.put(p.getId(), p);
                    Categoria cat = buscarCategoria(p.getCategoriaId());
                    if (cat != null) cat.agregarProducto(p);
                }

                
                for (ProductoComplejo p : productosMap.values()) {
                    List<TransaccionCompleja> txs = txDao.listByProducto(conn, p.getId());
                    p.replaceTransacciones(txs); 
                    transaccionesTodas.addAll(txs != null ? txs : Collections.emptyList());
                }

                
                try {
                    transaccionesTodas.sort(java.util.Comparator.comparing(
                        TransaccionCompleja::getFechaHora,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())
                    ));
                } catch (Throwable ignored) {}
            });
        }
    }

    
    public void syncCatalogToPostgres() throws SQLException {
        runInTransaction(conn -> {
            CategoriaPostgresDAO catDao = new CategoriaPostgresDAO();
            ProductoPostgresDAO  prodDao = new ProductoPostgresDAO();
            catDao.createTableIfNotExists(conn);
            prodDao.createTableIfNotExists(conn);
            for (Categoria cat : categorias) catDao.upsert(conn, cat);
            for (ProductoComplejo p : productosMap.values()) prodDao.upsert(conn, p);
        });
    }

    public void agregarCategoriaPersistente(Categoria categoria) throws SQLException {
        if (categoria == null) return;
        runInTransaction(conn -> {
            CategoriaPostgresDAO dao = new CategoriaPostgresDAO();
            dao.createTableIfNotExists(conn);
            dao.upsert(conn, categoria);
        });
        Categoria existente = buscarCategoria(categoria.getId());
        if (existente != null) eliminarCategoria(categoria.getId());
        categorias.add(categoria);
    }

    public void agregarProductoPersistente(ProductoComplejo producto) throws SQLException {
        if (producto == null) return;
        runInTransaction(conn -> {
            ProductoPostgresDAO dao = new ProductoPostgresDAO();
            dao.createTableIfNotExists(conn);
            dao.upsert(conn, producto);
        });
        productosMap.put(producto.getId(), producto);
        Categoria cat = buscarCategoria(producto.getCategoriaId());
        if (cat != null) { cat.eliminarProductoPorId(producto.getId()); cat.agregarProducto(producto); }
    }

    
    public void actualizarStockPersistente(String productoId, int nuevoStock) throws SQLException {
        ProductoComplejo p = productosMap.get(productoId);
        if (p == null) return;
        try (Connection c = DbPostgres.open()) {
            String sql = "UPDATE productos SET stock = ? WHERE id = ?";
            try (var ps = c.prepareStatement(sql)) {
                ps.setInt(1, nuevoStock);
                ps.setString(2, productoId);
                ps.executeUpdate();
            }
        }
    }

    

    public String toJSON() {
        StringBuilder json = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        json.append("{\n  \"tienda\": {\n");
        json.append("    \"id\": \"").append(escapeJSON(id)).append("\",\n");
        json.append("    \"nombre\": \"").append(escapeJSON(nombre)).append("\",\n");
        json.append("    \"direccion\": \"").append(escapeJSON(direccion)).append("\",\n");
        json.append("    \"telefono\": \"").append(escapeJSON(telefono)).append("\",\n");
        json.append("    \"fechaCreacion\": \"").append(sdf.format(fechaCreacion)).append("\",\n");
        json.append("    \"valorTotal\": ").append(calcularValorTotalTienda()).append(",\n");
        json.append("    \"categorias\": [\n");
        for (int i = 0; i < categorias.size(); i++) {
            String catJson = categorias.get(i).toJSON();
            String[] lines = catJson.split("\n");
            for (int j = 0; j < lines.length; j++) {
                json.append("      ").append(lines[j]);
                if (j < lines.length - 1) json.append("\n");
            }
            if (i < categorias.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("    ]\n  },\n  \"metadata\": {\n");
        json.append("    \"totalCategorias\": ").append(categorias.size()).append(",\n");
        json.append("    \"totalProductos\": ").append(contarProductosTotal()).append(",\n");
        json.append("    \"totalTransacciones\": ").append(contarTransaccionesTotal()).append(",\n");
        json.append("    \"productosBajoStock\": ").append(contarProductosConBajoStock()).append(",\n");
        json.append("    \"valorInventarioTotal\": ").append(calcularValorTotalTienda()).append(",\n");
        json.append("    \"fechaReporte\": \"").append(sdf.format(new Date())).append("\"\n");
        json.append("  }\n}");
        return json.toString();
    }

    private String escapeJSON(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    public String generarResumen() {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        sb.append("=== RESUMEN DE TIENDA ===\n");
        sb.append("Nombre: ").append(nombre).append("\n");
        sb.append("ID: ").append(id).append("\n");
        sb.append("Dirección: ").append(direccion).append("\n");
        sb.append("Teléfono: ").append(telefono).append("\n");
        sb.append("Fecha de creación: ").append(sdf.format(fechaCreacion)).append("\n\n");
        sb.append("ESTADÍSTICAS:\n");
        sb.append("- Categorías: ").append(categorias.size()).append("\n");
        sb.append("- Productos: ").append(contarProductosTotal()).append("\n");
        sb.append("- Transacciones: ").append(contarTransaccionesTotal()).append("\n");
        sb.append("- Productos con bajo stock: ").append(contarProductosConBajoStock()).append("\n");
        sb.append("- Valor total del inventario: $").append(String.format("%.2f", calcularValorTotalTienda())).append("\n");
        sb.append("- Ventas totales: $").append(String.format("%.2f", calcularVentasTotales())).append("\n");
        return sb.toString();
    }

    public void limpiar() {
        categorias.clear();
        productosMap.clear();
        transaccionesTodas.clear();
    }

    @Override public String toString() {
        return "Tienda{" +
                "id='" + id + '\'' +
                ", nombre='" + nombre + '\'' +
                ", categorias=" + categorias.size() +
                ", productos=" + productosMap.size() +
                ", valorTotal=" + calcularValorTotalTienda() +
                '}';
    }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tienda t = (Tienda) o; return id != null && id.equals(t.id);
    }
    @Override public int hashCode() { return id != null ? id.hashCode() : 0; }

    
    private interface SQLBlock { void run(Connection c) throws Exception; }

    private void runInTransaction(SQLBlock work) {
        try (Connection c = DbPostgres.open()) { runInTransaction(c, work); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private void runInTransaction(Connection c, SQLBlock work) {
        try {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try { work.run(c); c.commit(); }
            catch (Exception ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(prevAuto); }
        } catch (RuntimeException re) { throw re; }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
