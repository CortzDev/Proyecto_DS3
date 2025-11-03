package Invoice;

import java.math.BigDecimal;
import java.util.Objects;

public class FacturaItem {
    private String productoId;
    private String descripcion;       
    private int cantidad;
    private BigDecimal precioUnitario; 
    private BigDecimal subtotal;       

    public FacturaItem(String productoId, String descripcion, int cantidad, BigDecimal precioUnitario) {
        this.productoId = productoId;
        this.descripcion = descripcion;
        this.cantidad = cantidad;
        this.precioUnitario = (precioUnitario == null) ? BigDecimal.ZERO : precioUnitario;
        recalc();
    }

    public void recalc() {
        this.subtotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    
    public String getProductoId() { return productoId; }
    public String getDescripcion() { return descripcion; }
    public int getCantidad() { return cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public BigDecimal getSubtotal() { return subtotal; }

    
    public String getNombre() {                 
        return getDescripcion();
    }
    public BigDecimal getPrecioUnit() {         
        return getPrecioUnitario();
    }

    public String toJSON() {
        return "{"
            + "\"productoId\":\"" + esc(productoId) + "\","
            + "\"descripcion\":\"" + esc(descripcion) + "\","
            + "\"cantidad\":" + cantidad + ","
            + "\"precioUnitario\":" + precioUnitario + ","
            + "\"subtotal\":" + subtotal
            + "}";
    }

    private static String esc(String s){
        return (s == null) ? "" : s.replace("\\","\\\\").replace("\"","\\\"");
    }

    @Override public boolean equals(Object o){
        if (this == o) return true;
        if (!(o instanceof FacturaItem)) return false;
        FacturaItem that = (FacturaItem) o;
        return cantidad == that.cantidad &&
               Objects.equals(productoId, that.productoId) &&
               Objects.equals(descripcion, that.descripcion) &&
               Objects.equals(precioUnitario, that.precioUnitario) &&
               Objects.equals(subtotal, that.subtotal);
    }

    @Override public int hashCode() {
        return Objects.hash(productoId, descripcion, cantidad, precioUnitario, subtotal);
    }
}
