package Model;

import java.util.ArrayList;
import java.util.List;

public class Categoria {
    private String id;
    private String nombre;
    

    private final List<ProductoComplejo> productos;

    
    public Categoria(String id, String nombre) {
        this.id = id;
        this.nombre = nombre;
        this.productos = new ArrayList<>();
    }

    
    public Categoria(String id, String nombre, String descripcionIgnorada) {
        this(id, nombre);
        
    }

    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    
    public String getDescripcion() { return ""; }
    public void setDescripcion(String descripcionIgnorada) {  }

    public List<ProductoComplejo> getProductos() { return new ArrayList<>(productos); }

    
    public void agregarProducto(ProductoComplejo producto) {
        if (producto != null && !productos.contains(producto)) {
            productos.add(producto);
        }
    }

    public void eliminarProducto(ProductoComplejo producto) { productos.remove(producto); }

    public void eliminarProductoPorId(String productoId) {
        productos.removeIf(p -> p.getId().equals(productoId));
    }

    public ProductoComplejo buscarProducto(String productoId) {
        for (ProductoComplejo p : productos) {
            if (p.getId().equals(productoId)) return p;
        }
        return null;
    }

    
    public double calcularValorCategoria() {
        double total = 0;
        for (ProductoComplejo p : productos) {
            total += p.getValorInventario().doubleValue();
        }
        return total;
    }

    public int contarProductosConStock() {
        int contador = 0;
        for (ProductoComplejo p : productos) if (p.getStock() > 0) contador++;
        return contador;
    }

    public int contarProductos() { return productos.size(); }

    public int contarStockTotal() {
        int total = 0;
        for (ProductoComplejo p : productos) total += p.getStock();
        return total;
    }

    public List<ProductoComplejo> getProductosBajoStock(int umbral) {
        List<ProductoComplejo> bajos = new ArrayList<>();
        for (ProductoComplejo p : productos) if (p.getStock() < umbral) bajos.add(p);
        return bajos;
    }

    
    public String toJSON() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"id\": \"").append(escapeJSON(id)).append("\",\n");
        sb.append("  \"nombre\": \"").append(escapeJSON(nombre)).append("\",\n");
        sb.append("  \"descripcion\": \"\", \n"); 
        sb.append("  \"cantidadProductos\": ").append(productos.size()).append(",\n");
        sb.append("  \"productosConStock\": ").append(contarProductosConStock()).append(",\n");
        sb.append("  \"stockTotal\": ").append(contarStockTotal()).append(",\n");
        sb.append("  \"valorTotal\": ").append(calcularValorCategoria()).append(",\n");
        sb.append("  \"productos\": [\n");

        for (int i = 0; i < productos.size(); i++) {
            String productoJson = productos.get(i).toJSON();
            String[] lines = productoJson.split("\n");
            for (int j = 0; j < lines.length; j++) {
                sb.append("    ").append(lines[j]);
                if (j < lines.length - 1) sb.append("\n");
            }
            if (i < productos.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }

    private String escapeJSON(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    @Override
    public String toString() {
        return "Categoria{" +
                "id='" + id + '\'' +
                ", nombre='" + nombre + '\'' +
                ", productos=" + productos.size() +
                ", valor=" + calcularValorCategoria() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Categoria)) return false;
        Categoria that = (Categoria) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() { return id != null ? id.hashCode() : 0; }
}
