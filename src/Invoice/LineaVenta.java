
package Invoice;


public class LineaVenta {
    public final String productoId;
    public final int cantidad;
    public final java.math.BigDecimal precioUnit;

    public LineaVenta(String productoId, int cantidad, java.math.BigDecimal precioUnit) {
        this.productoId = productoId;
        this.cantidad = cantidad;
        this.precioUnit = precioUnit;
    }
}
