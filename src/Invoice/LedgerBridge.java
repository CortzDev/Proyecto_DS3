package Invoice;

import Model.ProductoComplejo;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import Blockchain.Block;


public final class LedgerBridge {

    private LedgerBridge() {}

    public static Factura facturaDesdeCarrito(
            String numeroFactura,
            String clienteNombre,
            String clienteId,
            String direccion,
            String formaPago,
            String vendedor,
            String tienda,
            BigDecimal tasaImpuesto,
            List<LineaVenta> carrito,
            Map<String, ProductoComplejo> productos
    ) {
        if (numeroFactura == null) numeroFactura = "F-AUTO";

        Factura f = new Factura(numeroFactura)
                .setCliente(ns(clienteNombre), ns(clienteId))
                .setDireccion(ns(direccion))
                .setFormaPago(ns(formaPago))
                .setVendedor(ns(vendedor))
                .setTienda(ns(tienda))
                .setTasaImpuesto(tasaImpuesto == null ? BigDecimal.ZERO : tasaImpuesto);

        if (carrito == null || carrito.isEmpty()) return f;

        for (LineaVenta lv : carrito) {
            if (lv == null) continue;
            if (lv.cantidad <= 0) continue; 

            String id = lv.productoId;
            ProductoComplejo p = (productos != null && id != null) ? productos.get(id) : null;

            String nombre = (p != null && p.getNombre() != null && !p.getNombre().isBlank())
                    ? p.getNombre()
                    : (id != null ? id : "Producto");

            BigDecimal precioUnit = lv.precioUnit != null ? lv.precioUnit : BigDecimal.ZERO;

            f.addItem(new FacturaItem(id, nombre, lv.cantidad, precioUnit));
        }
        return f;
    }

    public static Block buildBlock(int index, String previousHash, Factura factura) {
        return new Block(index, previousHash == null ? "0" : previousHash, factura);
    }

    private static String ns(String s) { return s == null ? "" : s; }
}
