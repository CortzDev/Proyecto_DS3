package Invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Factura {
    
    private String numero;             
    private String clienteNombre;
    private String clienteId;          
    private String direccion;
    private String formaPago;          
    private String vendedor;           
    private String tienda;             
    private final String fechaISO;     

    
    private final List<FacturaItem> items = new ArrayList<>();

    
    private BigDecimal subTotal   = BigDecimal.ZERO;
    private BigDecimal impuesto   = BigDecimal.ZERO;  
    private BigDecimal total      = BigDecimal.ZERO;

    private BigDecimal tasaImpuesto = new BigDecimal("0.13");

    
    public Factura(String numero) {
        this.numero = numero;
        this.fechaISO = java.time.Instant.now().toString();
    }

    public Factura(String numero, String fechaISO) {
        this.numero = numero;
        this.fechaISO = (fechaISO == null || fechaISO.isBlank())
                ? java.time.Instant.now().toString()
                : fechaISO;
    }

    
    public void addItem(FacturaItem it){
        if (it == null) return;
        it.recalc();
        items.add(it);
        recalcTotales();
    }

    public void recalcTotales(){
        subTotal = BigDecimal.ZERO;
        for (FacturaItem it : items) subTotal = subTotal.add(it.getSubtotal());
        impuesto = subTotal.multiply(tasaImpuesto);

        
        subTotal = subTotal.setScale(2, RoundingMode.HALF_UP);
        impuesto = impuesto.setScale(2, RoundingMode.HALF_UP);
        total    = subTotal.add(impuesto).setScale(2, RoundingMode.HALF_UP);
    }

    
    public Factura setCliente(String nombre, String id){ this.clienteNombre=nombre; this.clienteId=id; return this; }
    public Factura setDireccion(String dir){ this.direccion=dir; return this; }
    public Factura setFormaPago(String fp){ this.formaPago=fp; return this; }
    public Factura setVendedor(String vend){ this.vendedor=vend; return this; }
    public Factura setTienda(String tienda){ this.tienda=tienda; return this; }
    public Factura setTasaImpuesto(BigDecimal tasa){
        this.tasaImpuesto = (tasa == null) ? BigDecimal.ZERO : tasa;
        recalcTotales();
        return this;
    }

    
    public String getNumero(){ return numero; }
    public String getFechaISO(){ return fechaISO; }
    public List<FacturaItem> getItems(){ return Collections.unmodifiableList(items); }

    
    public BigDecimal getSubTotal(){ return subTotal; }
    public BigDecimal getSubtotal(){ return subTotal; } 

    public BigDecimal getImpuesto(){ return impuesto; }
    public BigDecimal getTotal(){ return total; }

    
    public BigDecimal getTasaImpuesto(){ return tasaImpuesto; }

    
    public String getCliente(){ return clienteNombre; }   
    public String getClienteNit(){ return clienteId; }    
    public String getClienteId(){ return clienteId; }     

    public String getDireccion(){ return direccion; }
    public String getFormaPago(){ return formaPago; }
    public String getVendedor(){ return vendedor; }

    public String getSucursal(){ return tienda; }         
    public String getTienda(){ return tienda; }           

    
    public String toJSON(){
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"numero\":\"").append(esc(numero)).append("\",");
        sb.append("\"fecha\":\"").append(esc(fechaISO)).append("\",");
        sb.append("\"clienteNombre\":\"").append(esc(clienteNombre)).append("\",");
        sb.append("\"clienteId\":\"").append(esc(clienteId)).append("\",");
        sb.append("\"direccion\":\"").append(esc(direccion)).append("\",");
        sb.append("\"formaPago\":\"").append(esc(formaPago)).append("\",");
        sb.append("\"vendedor\":\"").append(esc(vendedor)).append("\",");
        sb.append("\"tienda\":\"").append(esc(tienda)).append("\",");

        sb.append("\"items\":[");
        for (int i=0;i<items.size();i++){
            sb.append(items.get(i).toJSON());
            if(i<items.size()-1) sb.append(",");
        }
        sb.append("],");

        
        sb.append("\"tasaImpuesto\":").append(toNum(tasaImpuesto)).append(",");
        sb.append("\"subTotal\":").append(toNum(subTotal)).append(",");
        sb.append("\"impuesto\":").append(toNum(impuesto)).append(",");
        sb.append("\"total\":").append(toNum(total));
        sb.append("}");
        return sb.toString();
    }

    private static String esc(String s){
        return (s == null) ? "" : s.replace("\\","\\\\").replace("\"","\\\"");
    }

    private static String toNum(BigDecimal n){
        if (n == null) return "0";
        return n.stripTrailingZeros().toPlainString();
    }
    
    
    public String getFecha() {
        return fechaISO;
    }

}
