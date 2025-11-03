package Model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public class TransaccionCompleja {

    
    private String id;
    private String productoId;
    private TipoTx tipo;           
    private String tipoString;     
    private int cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal total;
    private String descripcion;
    private String usuario;
    private Instant fechaInstant;
    private Date fechaHora;
    private String estado;

    

    
    public TransaccionCompleja(String id, String productoId, TipoTx tipo,
                               int cantidad, BigDecimal precioUnit, String usuario) {
        this(id, productoId, tipo, cantidad, precioUnit,
             "Transacción " + (tipo != null ? tipo.name() : ""), usuario, new Date());
    }

    
    public TransaccionCompleja(String id, String productoId, TipoTx tipo,
                               int cantidad, BigDecimal precioUnit, String descripcion,
                               String usuario, Date fecha) {
        this.id = (id != null ? id : "TX-" + UUID.randomUUID().toString().substring(0, 8));
        this.productoId = productoId;
        this.tipo = tipo;
        this.tipoString = (tipo != null ? tipo.name() : null);
        this.cantidad = cantidad;
        this.precioUnitario = (precioUnit != null ? precioUnit : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        this.descripcion = (descripcion != null ? descripcion : "");
        this.usuario = (usuario != null ? usuario : "Sistema");
        setFechaHora(fecha != null ? fecha : new Date()); 
        recalcularTotal();
        this.estado = "COMPLETADO";
    }

    
    public TransaccionCompleja(String id, String productoId, String tipo,
                               int cantidad, double precioUnit, String descripcion, String usuario) {
        this(id, productoId, tipo, cantidad, BigDecimal.valueOf(precioUnit), descripcion, usuario, new Date());
    }

    
    public TransaccionCompleja(String id, String productoId, String tipo,
                               int cantidad, BigDecimal precioUnit, String descripcion,
                               String usuario, Date fecha) {
        this.id = (id != null ? id : "TX-" + UUID.randomUUID().toString().substring(0, 8));
        this.productoId = productoId;
        this.tipoString = tipo;
        try { this.tipo = (tipo != null ? TipoTx.valueOf(tipo) : null); }
        catch (IllegalArgumentException ignored) { this.tipo = null; }
        this.cantidad = cantidad;
        this.precioUnitario = (precioUnit != null ? precioUnit : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        this.descripcion = (descripcion != null ? descripcion : "");
        this.usuario = (usuario != null ? usuario : "Sistema");
        setFechaHora(fecha != null ? fecha : new Date());
        recalcularTotal();
        this.estado = "COMPLETADO";
    }

    
    public TransaccionCompleja(String productoId, String tipo, int cantidad,
                               double precioUnit, String descripcion, String usuario) {
        this(null, productoId, tipo, cantidad, BigDecimal.valueOf(precioUnit), descripcion, usuario, new Date());
    }

    
    public String getId() { return id; }
    public String getProductoId() { return productoId; }
    public TipoTx getTipo() { return tipo; }
    public String getTipoString() { return (tipo != null ? tipo.name() : tipoString); }
    public int getCantidad() { return cantidad; }
    public BigDecimal getPrecioUnit() { return precioUnitario; }       
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public double getPrecioUnitarioDouble() { return precioUnitario.doubleValue(); }
    public BigDecimal getTotal() { return total; }
    public double getTotalDouble() { return total.doubleValue(); }
    public String getDescripcion() { return descripcion; }
    public String getUsuario() { return usuario; }
    public Instant getFecha() { return fechaInstant; }
    public Date getFechaHora() { return fechaHora; }
    public String getEstado() { return estado; }

    
    public void setId(String id) { this.id = id; }
    public void setProductoId(String productoId) { this.productoId = productoId; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public void setEstado(String estado) { this.estado = estado; }

    
    public void setTotal(BigDecimal total) { this.total = (total != null ? total : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP); }

    
    public void setFechaHora(Date fecha) {
        this.fechaHora = (fecha != null ? fecha : new Date());
        try { this.fechaInstant = this.fechaHora.toInstant(); }
        catch (Throwable ignored) { this.fechaInstant = Instant.now(); }
    }

    public void setFecha(Instant inst) {
        this.fechaInstant = (inst != null ? inst : Instant.now());
        try { this.fechaHora = Date.from(this.fechaInstant); }
        catch (Throwable ignored) { this.fechaHora = new Date(); }
    }

    
    public void setCantidad(int cantidad) { this.cantidad = cantidad; recalcularTotal(); }
    public void setPrecioUnitario(BigDecimal precio) {
        this.precioUnitario = (precio != null ? precio : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        recalcularTotal();
    }

    
    public boolean esVenta()       { return tipo == TipoTx.VENTA      || "VENTA".equalsIgnoreCase(tipoString); }
    public boolean esSalida()      { return tipo == TipoTx.SALIDA     || "SALIDA".equalsIgnoreCase(tipoString); }
    public boolean esEntrada()     { return tipo == TipoTx.ENTRADA    || "ENTRADA".equalsIgnoreCase(tipoString); }
    public boolean esDevolucion()  { return tipo == TipoTx.DEVOLUCION || "DEVOLUCIÓN".equalsIgnoreCase(tipoString) || "DEVOLUCION".equalsIgnoreCase(tipoString); }
    public boolean esAjuste()      { return tipo == TipoTx.AJUSTE     || "AJUSTE".equalsIgnoreCase(tipoString); }
    public boolean disminuyeStock(){ return esVenta() || esSalida(); }
    public boolean aumentaStock()  { return esEntrada() || esDevolucion(); }
    public boolean esCompletado()  { return "COMPLETADO".equalsIgnoreCase(estado); }

    
    public void recalcularTotal() {
        this.total = (this.precioUnitario != null ? this.precioUnitario : BigDecimal.ZERO)
                .multiply(BigDecimal.valueOf(Math.max(0, this.cantidad)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public double calcularImpuesto(double tasaPorc) {
        return getTotal().multiply(BigDecimal.valueOf(tasaPorc / 100.0))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public double calcularTotalConImpuesto(double tasaPorc) {
        return getTotal().add(BigDecimal.valueOf(calcularImpuesto(tasaPorc)))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    
    public String toJSON() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"id\": \"").append(escapeJSON(id)).append("\",\n");
        json.append("  \"productoId\": \"").append(escapeJSON(productoId)).append("\",\n");
        json.append("  \"tipo\": \"").append(getTipoString()).append("\",\n");
        json.append("  \"cantidad\": ").append(cantidad).append(",\n");
        json.append("  \"precioUnitario\": ").append(precioUnitario).append(",\n");
        json.append("  \"total\": ").append(getTotal()).append(",\n");
        json.append("  \"descripcion\": \"").append(escapeJSON(descripcion)).append("\",\n");
        json.append("  \"usuario\": \"").append(escapeJSON(usuario)).append("\",\n");
        json.append("  \"estado\": \"").append(escapeJSON(estado)).append("\",\n");
        if (fechaInstant != null) json.append("  \"fecha\": \"").append(fechaInstant.toString()).append("\",\n");
        if (fechaHora != null)    json.append("  \"fechaHora\": \"").append(fechaHora.getTime()).append("\",\n");
        json.append("  \"esVenta\": ").append(esVenta()).append(",\n");
        json.append("  \"esEntrada\": ").append(esEntrada()).append(",\n");
        json.append("  \"disminuyeStock\": ").append(disminuyeStock()).append("\n");
        json.append("}");
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

    
    public String getResumen() {
        return String.format("%s - %s: %d unidades x $%.2f = $%.2f [%s] - %s",
                id, getTipoString(), cantidad, precioUnitario.doubleValue(),
                getTotal().doubleValue(), estado, usuario);
    }

    public TransaccionCompleja clonar() {
        TransaccionCompleja clon = new TransaccionCompleja(
                UUID.randomUUID().toString(),
                this.productoId,
                this.getTipoString(),
                this.cantidad,
                this.precioUnitario,
                this.descripcion,
                this.usuario,
                this.fechaHora
        );
        clon.estado = this.estado;
        return clon;
    }

    @Override
    public String toString() {
        return "TransaccionCompleja{" +
                "id='" + id + '\'' +
                ", productoId='" + productoId + '\'' +
                ", tipo=" + getTipoString() +
                ", cantidad=" + cantidad +
                ", total=" + total +
                ", usuario='" + usuario + '\'' +
                ", estado='" + estado + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransaccionCompleja that = (TransaccionCompleja) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return (id != null ? id.hashCode() : 0);
    }

    
    public static class Builder {
        private String id;
        private String productoId;
        private String tipo;
        private int cantidad;
        private BigDecimal precioUnit;
        private String descripcion = "";
        private String usuario = "Sistema";
        private Date fecha;

        public Builder(String productoId, String tipo, int cantidad, double precioUnit) {
            this.productoId = productoId;
            this.tipo = tipo;
            this.cantidad = cantidad;
            this.precioUnit = BigDecimal.valueOf(precioUnit);
        }

        public Builder precioUnit(BigDecimal p) { this.precioUnit = p; return this; }
        public Builder id(String id) { this.id = id; return this; }
        public Builder descripcion(String d) { this.descripcion = d; return this; }
        public Builder usuario(String u) { this.usuario = u; return this; }
        public Builder fecha(Date f) { this.fecha = f; return this; }

        public TransaccionCompleja build() {
            return new TransaccionCompleja(
                    id, productoId, tipo, cantidad, precioUnit, descripcion, usuario,
                    (fecha != null ? fecha : new Date())
            );
        }
    }
}
