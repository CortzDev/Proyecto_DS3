package Model;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;

public class Transaction {
    public final String id;
    public final String tipo;
    public final String descripcion;
    public final int cantidad;
    public final double precioUnitario;
    public final double total;
    public final String usuario;
    public final Date fechaHora;
    public final String fechaISO;

    
    public Transaction(String id, String tipo, String descripcion, int cantidad, 
                      double precioUnitario, String usuario, Date fechaHora) {
        this.id = id;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.total = cantidad * precioUnitario;
        this.usuario = usuario != null ? usuario : "Sistema";
        this.fechaHora = fechaHora != null ? fechaHora : new Date();
        this.fechaISO = dateToISO(this.fechaHora);
    }

    
    public Transaction(String id, String descripcion, int cantidad, String precioUnit, 
                      String total, String usuario, String fechaISO) {
        this.id = id;
        this.tipo = "TRANSACCION"; 
        this.descripcion = descripcion;
        this.cantidad = cantidad;
        this.precioUnitario = parseDouble(precioUnit);
        this.total = parseDouble(total);
        this.usuario = usuario;
        this.fechaISO = fechaISO;
        this.fechaHora = isoToDate(fechaISO);
    }

    
    public Transaction(String id, String tipo, int cantidad, double precioUnitario, 
                      String descripcion, Date fechaHora) {
        this(id, tipo, descripcion, cantidad, precioUnitario, "Sistema", fechaHora);
    }


    
    public Transaction(String id, String descripcion, int cantidad, double precioUnitario, 
                      String usuario) {
        this(id, "TRANSACCION", descripcion, cantidad, precioUnitario, usuario, new Date());
    }

    
    public Transaction() {
        this("", "TRANSACCION", "", 0, 0.0, "Sistema", new Date());
    }

    

    public String getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public int getCantidad() {
        return cantidad;
    }

    public double getPrecioUnitario() {
        return precioUnitario;
    }

    public String getPrecioUnit() {
        return String.format("%.2f", precioUnitario);
    }

    public double getTotal() {
        return total;
    }

    public String getTotalString() {
        return String.format("%.2f", total);
    }

    public String getUsuario() {
        return usuario;
    }

    public Date getFechaHora() {
        return fechaHora;
    }

    public String getFechaISO() {
        return fechaISO;
    }

    public Instant getFechaInstant() {
        return fechaHora != null ? fechaHora.toInstant() : Instant.now();
    }

    

    public boolean esEntrada() {
        return "ENTRADA".equalsIgnoreCase(tipo);
    }

    public boolean esSalida() {
        return "SALIDA".equalsIgnoreCase(tipo);
    }

    public boolean esVenta() {
        return "VENTA".equalsIgnoreCase(tipo);
    }

    public boolean esDevolucion() {
        return "DEVOLUCIÓN".equalsIgnoreCase(tipo) || "DEVOLUCION".equalsIgnoreCase(tipo);
    }

    public boolean esAjuste() {
        return "AJUSTE".equalsIgnoreCase(tipo);
    }

    public boolean disminuyeStock() {
        return esVenta() || esSalida();
    }

    public boolean aumentaStock() {
        return esEntrada() || esDevolucion();
    }

    

    public String toJSON() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"id\": \"").append(escapeJSON(id)).append("\",\n");
        json.append("  \"tipo\": \"").append(escapeJSON(tipo)).append("\",\n");
        json.append("  \"descripcion\": \"").append(escapeJSON(descripcion)).append("\",\n");
        json.append("  \"cantidad\": ").append(cantidad).append(",\n");
        json.append("  \"precioUnit\": ").append(String.format("%.2f", precioUnitario)).append(",\n");
        json.append("  \"total\": ").append(String.format("%.2f", total)).append(",\n");
        json.append("  \"usuario\": \"").append(escapeJSON(usuario)).append("\",\n");
        json.append("  \"fecha\": \"").append(escapeJSON(fechaISO)).append("\"\n");
        json.append("}");
        return json.toString();
    }

    public String toJSONCompact() {
        return "{" +
            "\"id\":\"" + escapeJSON(id) + "\"," +
            "\"tipo\":\"" + escapeJSON(tipo) + "\"," +
            "\"descripcion\":\"" + escapeJSON(descripcion) + "\"," +
            "\"cantidad\":" + cantidad + "," +
            "\"precioUnit\":" + String.format("%.2f", precioUnitario) + "," +
            "\"total\":" + String.format("%.2f", total) + "," +
            "\"usuario\":\"" + escapeJSON(usuario) + "\"," +
            "\"fecha\":\"" + escapeJSON(fechaISO) + "\"" +
            "}";
    }

    private String escapeJSON(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    

    private static String dateToISO(Date date) {
        if (date == null) return Instant.now().toString();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }

    private static Date isoToDate(String iso) {
        if (iso == null || iso.isEmpty()) return new Date();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.parse(iso);
        } catch (Exception e) {
            return new Date();
        }
    }

    private static double parseDouble(String value) {
        if (value == null || value.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    

    public String getResumen() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        return String.format("[%s] %s - %s: %d x $%.2f = $%.2f (%s)",
            id, tipo, descripcion, cantidad, precioUnitario, total,
            sdf.format(fechaHora));
    }

    public String getResumenCorto() {
        return String.format("%s: %d x $%.2f = $%.2f", 
            tipo, cantidad, precioUnitario, total);
    }

    public boolean esMayorQue(double monto) {
        return total > monto;
    }

    public boolean esDelUsuario(String nombreUsuario) {
        return usuario != null && usuario.equalsIgnoreCase(nombreUsuario);
    }

    public boolean esDeFecha(Date fecha) {
        if (fechaHora == null || fecha == null) return false;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(fechaHora).equals(sdf.format(fecha));
    }

    

    public static Transaction crearEntrada(String id, String descripcion, int cantidad, 
                                          double precio, String usuario) {
        return new Transaction(id, "ENTRADA", descripcion, cantidad, precio, usuario, new Date());
    }

    public static Transaction crearSalida(String id, String descripcion, int cantidad, 
                                         double precio, String usuario) {
        return new Transaction(id, "SALIDA", descripcion, cantidad, precio, usuario, new Date());
    }

    public static Transaction crearVenta(String id, String descripcion, int cantidad, 
                                        double precio, String usuario) {
        return new Transaction(id, "VENTA", descripcion, cantidad, precio, usuario, new Date());
    }

    public static Transaction crearDevolucion(String id, String descripcion, int cantidad, 
                                             double precio, String usuario) {
        return new Transaction(id, "DEVOLUCIÓN", descripcion, cantidad, precio, usuario, new Date());
    }

    public static Transaction crearAjuste(String id, String descripcion, int cantidad, 
                                         double precio, String usuario) {
        return new Transaction(id, "AJUSTE", descripcion, cantidad, precio, usuario, new Date());
    }

    

    @Override
    public String toString() {
        return "Transaction{" +
                "id='" + id + '\'' +
                ", tipo='" + tipo + '\'' +
                ", descripcion='" + descripcion + '\'' +
                ", cantidad=" + cantidad +
                ", precioUnitario=" + precioUnitario +
                ", total=" + total +
                ", usuario='" + usuario + '\'' +
                ", fechaHora=" + (fechaHora != null ? fechaHora.getTime() : 0L) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    

    public static class Builder {
        private String id;
        private String tipo = "TRANSACCION";
        private String descripcion = "";
        private int cantidad;
        private double precioUnitario;
        private String usuario = "Sistema";
        private Date fechaHora = new Date();

        public Builder(String id) {
            this.id = id;
        }

        public Builder tipo(String tipo) {
            this.tipo = tipo;
            return this;
        }

        public Builder descripcion(String descripcion) {
            this.descripcion = descripcion;
            return this;
        }

        public Builder cantidad(int cantidad) {
            this.cantidad = cantidad;
            return this;
        }

        public Builder precioUnitario(double precioUnitario) {
            this.precioUnitario = precioUnitario;
            return this;
        }

        public Builder usuario(String usuario) {
            this.usuario = usuario;
            return this;
        }

        public Builder fechaHora(Date fechaHora) {
            this.fechaHora = fechaHora;
            return this;
        }

        public Transaction build() {
            return new Transaction(id, tipo, descripcion, cantidad, 
                                  precioUnitario, usuario, fechaHora);
        }
    }
}