package Model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ProductoComplejo {

    
    private final String id;
    private String nombre;
    private String descripcion;
    private String proveedor;
    private String categoriaId;

    
    private BigDecimal precio;
    private int stock;
    private int stockMinimo = 5;

    
    private final Instant fechaCreacion = Instant.now();
    private LocalDate fechaVencimiento;

    
    private final List<TransaccionCompleja> transacciones = new ArrayList<>();

    
    public ProductoComplejo(String id, String nombre, BigDecimal precio, int stock, String categoriaId) {
        this(id, nombre, null, precio, stock, null, categoriaId, null, 5);
    }

    public ProductoComplejo(
            String id,
            String nombre,
            String descripcion,
            BigDecimal precio,
            int stock,
            String proveedor,
            String categoriaId,
            LocalDate fechaVencimiento,
            int stockMinimo
    ) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id requerido");
        if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("nombre requerido");
        if (precio == null) throw new IllegalArgumentException("precio requerido");
        if (stock < 0) throw new IllegalArgumentException("stock no puede ser negativo");
        if (stockMinimo < 0) throw new IllegalArgumentException("stockMinimo no puede ser negativo");

        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precio = scale2(precio);
        this.stock = stock;
        this.proveedor = proveedor;
        this.categoriaId = categoriaId;
        this.fechaVencimiento = fechaVencimiento;
        this.stockMinimo = stockMinimo;
    }

    
    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getProveedor() { return proveedor; }
    public void setProveedor(String proveedor) { this.proveedor = proveedor; }
    public String getCategoriaId() { return categoriaId; }
    public void setCategoriaId(String categoriaId) { this.categoriaId = categoriaId; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = scale2(Objects.requireNonNull(precio)); }
    public int getStock() { return stock; }
    public int getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(int stockMinimo) {
        if (stockMinimo < 0) throw new IllegalArgumentException("stockMinimo no puede ser negativo");
        this.stockMinimo = stockMinimo;
    }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(LocalDate fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }

    
    public List<TransaccionCompleja> getTransacciones() { return List.copyOf(transacciones); }

    
    public void setStock(int nuevoStock) { this.stock = Math.max(0, nuevoStock); }

    
    
    public void replaceTransacciones(List<TransaccionCompleja> txs) {
        this.transacciones.clear();
        if (txs != null) this.transacciones.addAll(txs);
    }

    
    public void appendTransaccion(TransaccionCompleja tx) {
        if (tx != null) this.transacciones.add(tx);
    }

    
    public void aplicarMovimientoStock(TipoTx tipo, int cantidad) {
        validarCantidad(cantidad);
        switch (tipo) {
            case ENTRADA, AJUSTE, DEVOLUCION -> this.stock += cantidad;
            case SALIDA, VENTA -> {
                if (cantidad > this.stock) {
                    throw new IllegalArgumentException("Stock insuficiente para " + nombre +
                            ". Stock=" + stock + ", req=" + cantidad);
                }
                this.stock -= cantidad;
            }
            default -> {}
        }
    }

    
    public void agregarTransaccion(TipoTx tipo, int cantidad, BigDecimal precioUnit,
                                   String descripcion, String usuario) {
        validarCantidad(cantidad);
        BigDecimal pUnit = scale2(precioUnit != null ? precioUnit : this.precio);

        
        aplicarMovimientoStock(tipo, cantidad);

        
        TransaccionCompleja tx = new TransaccionCompleja(
                null,                
                this.id,             
                tipo.name(),         
                cantidad,
                pUnit.doubleValue(), 
                (descripcion != null ? descripcion : ""),
                (usuario != null ? usuario : "Sistema")
        );

        
        this.transacciones.add(tx);
    }

    
    public BigDecimal getValorInventario() {
        return scale2(this.precio.multiply(BigDecimal.valueOf(this.stock)));
    }

    public boolean requiereReposicion() {
        return this.stock <= this.stockMinimo;
    }

    public BigDecimal calcularTotalVentas() {
        BigDecimal total = BigDecimal.ZERO;
        for (TransaccionCompleja tx : transacciones) {
            if (tx.esVenta() || tx.esSalida()) {
                total = total.add(tx.getTotal());
            }
        }
        return scale2(total);
    }

    
    private static void validarCantidad(int cantidad) {
        if (cantidad <= 0) throw new IllegalArgumentException("cantidad debe ser > 0");
    }

    private static BigDecimal scale2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    
    public String toJSON() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"id\": \"").append(escapeJSON(id)).append("\",\n");
        sb.append("  \"nombre\": \"").append(escapeJSON(nombre)).append("\",\n");
        sb.append("  \"descripcion\": \"").append(escapeJSON(descripcion)).append("\",\n");
        sb.append("  \"proveedor\": \"").append(escapeJSON(proveedor)).append("\",\n");
        sb.append("  \"categoriaId\": \"").append(escapeJSON(categoriaId)).append("\",\n");
        sb.append("  \"precio\": ").append(precio.setScale(2, RoundingMode.HALF_UP)).append(",\n");
        sb.append("  \"stock\": ").append(stock).append(",\n");
        sb.append("  \"stockMinimo\": ").append(stockMinimo).append(",\n");
        sb.append("  \"valorInventario\": ").append(getValorInventario().setScale(2, RoundingMode.HALF_UP)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJSON(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    @Override
    public String toString() {
        return "ProductoComplejo{" +
                "id='" + id + '\'' +
                ", nombre='" + nombre + '\'' +
                ", precio=" + precio +
                ", stock=" + stock +
                ", stockMinimo=" + stockMinimo +
                ", categoriaId='" + categoriaId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductoComplejo)) return false;
        ProductoComplejo that = (ProductoComplejo) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
