package UI;

import App.AppContext;
import Model.ProductoComplejo;
import Model.TransaccionCompleja;
import Model.TipoTx;
import Postgres.DbPostgres;
import DAO.TransaccionPostgresDAO;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class TransaccionesPanel extends JPanel implements RefreshableView {

    private final AppContext ctx;

    
    private JList<String> listProductos; 
    private DefaultListModel<String> modelProductos;

    private JTable tabla;
    private DefaultTableModel modelo;
    private JTextField txtFiltro, txtDesde, txtHasta;
    private JComboBox<String> cmbTipo;

    private JComboBox<TipoTx> cmbTipoMov;
    private JSpinner spCantidad;
    private JTextField txtPrecioUnit;   
    private JTextField txtUsuario;
    private JTextField txtMotivo;
    private JButton btnRegistrar;

    public TransaccionesPanel(AppContext ctx) {
        this.ctx = ctx;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        
        JPanel left = new JPanel(new BorderLayout(6,6));
        left.setBorder(BorderFactory.createTitledBorder("Productos"));
        modelProductos = new DefaultListModel<>();
        listProductos = new JList<>(modelProductos);
        listProductos.setVisibleRowCount(20);
        listProductos.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        left.add(new JScrollPane(listProductos), BorderLayout.CENTER);

        JButton btnReload = new JButton("Recargar catálogo (PG → Memoria)");
        btnReload.addActionListener(e -> {
            try { ctx.tienda.loadCatalogFromPostgres(); } catch (Exception ignored) {}
            cargarListaProductos();
        });
        left.add(btnReload, BorderLayout.SOUTH);

        
        
        JPanel cap = new JPanel(new GridBagLayout());
        cap.setBorder(BorderFactory.createTitledBorder("Nuevo movimiento"));
        cap.setPreferredSize(new Dimension(400, 180)); 
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6,6,6,6);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.WEST;

        int r = 0;
        
        g.gridx=0; g.gridy=r; g.weightx=0; cap.add(new JLabel("Tipo:"), g);
        g.gridx=1; g.gridy=r++; g.weightx=1; cmbTipoMov = new JComboBox<>(TipoTx.values()); cap.add(cmbTipoMov, g);
        
        g.gridx=0; g.gridy=r; g.weightx=0; cap.add(new JLabel("Cantidad:"), g);
        g.gridx=1; g.gridy=r++; g.weightx=1; spCantidad = new JSpinner(new SpinnerNumberModel(1,1,1_000_000,1)); cap.add(spCantidad, g);
        
        g.gridx=0; g.gridy=r; g.weightx=0; cap.add(new JLabel("Precio unit.:"), g);
        g.gridx=1; g.gridy=r++; g.weightx=1;
        txtPrecioUnit = new JTextField();
        txtPrecioUnit.setEditable(false);
        txtPrecioUnit.setBackground(new Color(245,245,245));
        cap.add(txtPrecioUnit, g);
        
        g.gridx=0; g.gridy=r; g.weightx=0; cap.add(new JLabel("Usuario:"), g);
        g.gridx=1; g.gridy=r++; g.weightx=1; txtUsuario = new JTextField(); cap.add(txtUsuario, g);
        
        g.gridx=0; g.gridy=r; g.weightx=0; cap.add(new JLabel("Motivo/Notas:"), g);
        g.gridx=1; g.gridy=r++; g.weightx=1; txtMotivo = new JTextField(); cap.add(txtMotivo, g);
        
        g.gridx=1; g.gridy=r; g.weightx=0; g.anchor = GridBagConstraints.EAST; g.fill = GridBagConstraints.NONE;
        btnRegistrar = new JButton("Registrar (PG)");
        btnRegistrar.addActionListener(e -> registrarEnPostgres());
        cap.add(btnRegistrar, g);

        
        JPanel filtros = new JPanel(new GridBagLayout());
        filtros.setBorder(BorderFactory.createTitledBorder("Filtros"));
        GridBagConstraints f = new GridBagConstraints();
        f.insets = new Insets(4,4,4,4);
        f.anchor = GridBagConstraints.WEST;

        f.gridx=0; f.gridy=0; filtros.add(new JLabel("Producto / Código:"), f);
        f.gridx=1; f.gridy=0; f.weightx=1; f.fill = GridBagConstraints.HORIZONTAL;
        txtFiltro = new JTextField(); filtros.add(txtFiltro, f);

        f.gridx=0; f.gridy=1; f.weightx=0; f.fill = GridBagConstraints.NONE;
        filtros.add(new JLabel("Tipo:"), f);
        f.gridx=1; f.gridy=1; f.weightx=1; f.fill = GridBagConstraints.HORIZONTAL;
        cmbTipo = new JComboBox<>(new String[]{"TODOS","ENTRADA","SALIDA","VENTA","AJUSTE","DEVOLUCION"});
        filtros.add(cmbTipo, f);

        f.gridx=0; f.gridy=2; filtros.add(new JLabel("Desde (yyyy-MM-dd):"), f);
        f.gridx=1; f.gridy=2; f.weightx=1; f.fill = GridBagConstraints.HORIZONTAL;
        txtDesde = new JTextField(); filtros.add(txtDesde, f);

        f.gridx=0; f.gridy=3; f.weightx=0; f.fill = GridBagConstraints.NONE;
        filtros.add(new JLabel("Hasta (yyyy-MM-dd):"), f);
        f.gridx=1; f.gridy=3; f.weightx=1; f.fill = GridBagConstraints.HORIZONTAL;
        txtHasta = new JTextField(); filtros.add(txtHasta, f);

        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        JButton aplicar = new JButton("Aplicar");   aplicar.addActionListener(e -> refreshAll());
        JButton limpiar = new JButton("Limpiar");   limpiar.addActionListener(e -> { txtFiltro.setText(""); cmbTipo.setSelectedIndex(0); txtDesde.setText(""); txtHasta.setText(""); refreshAll(); });
        JButton refrescar = new JButton("Refrescar"); refrescar.addActionListener(e -> refreshAll());
        barra.add(aplicar); barra.add(limpiar); barra.add(refrescar);

        JPanel filtrosWrap = new JPanel(new BorderLayout(6,6));
        filtrosWrap.add(filtros, BorderLayout.CENTER);
        filtrosWrap.add(barra, BorderLayout.SOUTH);

        
        modelo = new DefaultTableModel(new Object[]{"ID","Producto","Tipo","Cantidad","Total","Usuario","Fecha"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        tabla = new JTable(modelo);
        tabla.setRowHeight(22);

        JPanel abajo = new JPanel(new BorderLayout(6,6));
        abajo.add(filtrosWrap, BorderLayout.NORTH);
        abajo.add(new JScrollPane(tabla), BorderLayout.CENTER);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, cap, abajo);
        rightSplit.setResizeWeight(0.35); 
        rightSplit.setContinuousLayout(true);

        
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, rightSplit);
        mainSplit.setResizeWeight(0.28);
        mainSplit.setContinuousLayout(true);
        add(mainSplit, BorderLayout.CENTER);

        
        listProductos.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String pid = productoSeleccionadoId();
                ProductoComplejo pSel = (pid != null) ? buscarProducto(pid) : null;
                if (pSel != null && txtPrecioUnit != null) {
                    try {
                        txtPrecioUnit.setText(pSel.getPrecio().setScale(2, RoundingMode.HALF_UP).toPlainString());
                    } catch (Throwable ignored) {
                        txtPrecioUnit.setText("");
                    }
                } else if (txtPrecioUnit != null) {
                    txtPrecioUnit.setText("");
                }
            }
        });

        
        cargarListaProductos();
        if (!modelProductos.isEmpty()) {
            String pid0 = productoSeleccionadoId();
            ProductoComplejo p0 = (pid0 != null) ? buscarProducto(pid0) : null;
            if (p0 != null) txtPrecioUnit.setText(p0.getPrecio().setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
    }

    private void cargarListaProductos() {
        modelProductos.clear();
        for (var c : ctx.tienda.getCategorias()) {
            for (var p : c.getProductos()) {
                modelProductos.addElement(p.getId() + " - " + p.getNombre());
            }
        }
        if (!modelProductos.isEmpty()) listProductos.setSelectedIndex(0);
        refreshAll();
    }

    private String productoSeleccionadoId() {
        String sel = listProductos.getSelectedValue();
        if (sel == null || !sel.contains(" - ")) return null;
        return sel.substring(0, sel.indexOf(" - ")).trim();
    }

    
    private void registrarEnPostgres() {
        try {
            String prodId = productoSeleccionadoId();
            if (prodId == null) {
                JOptionPane.showMessageDialog(this, "Selecciona un producto de la lista.", "Atención", JOptionPane.WARNING_MESSAGE);
                return;
            }

            TipoTx tipo = (TipoTx) cmbTipoMov.getSelectedItem();
            int cantidad = ((Number) spCantidad.getValue()).intValue();

            
            ProductoComplejo p = buscarProducto(prodId);
            if (p == null) {
                JOptionPane.showMessageDialog(this, "Producto no encontrado.", "Atención", JOptionPane.WARNING_MESSAGE);
                return;
            }
            BigDecimal precioUnit = p.getPrecio();

            String usuario = txtUsuario.getText().trim();
            String motivo  = txtMotivo.getText().trim();
            Date   ahora   = new Date();

            
            p.agregarTransaccion(tipo, cantidad, precioUnit, motivo, usuario);

            
            try (var conn = DbPostgres.open()) {
                TransaccionPostgresDAO dao = new TransaccionPostgresDAO();
                dao.createTableIfNotExists(conn);

                List<TransaccionCompleja> hist = p.getTransacciones();
                TransaccionCompleja txToPersist = (hist != null && !hist.isEmpty())
                        ? hist.get(hist.size() - 1)
                        : new TransaccionCompleja(
                            null, prodId, tipo, cantidad, precioUnit,
                            (usuario.isBlank()? "Sistema" : usuario),
                            (motivo == null ? "" : motivo),
                            new java.util.Date(ahora.getTime())
                          );

                dao.insert(conn, txToPersist, null, null);
            }

            
            try { ctx.tienda.syncCatalogToPostgres(); } catch (Exception ignore) {}

            
            try {
                if (ctx.getRefresher() != null) {
                    SwingUtilities.invokeLater(ctx.getRefresher());
                }
            } catch (Throwable ignored) {}

            
            JOptionPane.showMessageDialog(this, "✓ Movimiento guardado en PostgreSQL.", "OK", JOptionPane.INFORMATION_MESSAGE);
            spCantidad.setValue(1);
            txtMotivo.setText("");
            txtPrecioUnit.setText(p.getPrecio().setScale(2, RoundingMode.HALF_UP).toPlainString());
            refreshAll();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al registrar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    

    private Date parseYMD(String s) {
        if (s == null || s.isBlank()) return null;
        try { return new SimpleDateFormat("yyyy-MM-dd").parse(s.trim()); }
        catch (ParseException e) { return null; }
    }

    private ProductoComplejo buscarProducto(String id) {
        for (var c : ctx.tienda.getCategorias())
            for (var p : c.getProductos())
                if (p.getId().equals(id)) return p;
        return null;
    }

    private boolean pasaFiltros(TransaccionCompleja t, String q, String tipo, Date dDesde, Date dHasta) {
        if (q != null && !q.isBlank()) {
            String ql = q.trim().toLowerCase(Locale.ROOT);
            String codigo = String.valueOf(t.getProductoId());
            ProductoComplejo p = buscarProducto(codigo);
            String nombre = (p != null ? p.getNombre() : "");
            String comp = (codigo + " " + nombre).toLowerCase(Locale.ROOT);
            if (!comp.contains(ql)) return false;
        }
        if (!"TODOS".equalsIgnoreCase(tipo)) {
            String tt = t.getTipoString();
            if (tt == null || !tt.equalsIgnoreCase(tipo)) return false;
        }
        Date f = null;
        try { f = t.getFechaHora(); } catch (Throwable ignored) {}
        if (dDesde != null && f != null && f.before(dDesde)) return false;
        if (dHasta != null && f != null && f.after(dHasta)) return false;
        return true;
    }

    private List<TransaccionCompleja> snapshotTxs() {
        List<TransaccionCompleja> out = new ArrayList<>();
        for (var c : ctx.tienda.getCategorias()) {
            for (var p : c.getProductos()) {
                List<TransaccionCompleja> txs = p.getTransacciones();
                if (txs != null) out.addAll(txs);
            }
        }
        try {
            out.sort(Comparator.comparing(TransaccionCompleja::getFechaHora,
                    Comparator.nullsLast(Comparator.naturalOrder())));
        } catch (Throwable ignored) {}
        return out;
    }

    @Override
    public void refreshAll() {
        if (modelo == null) return;
        modelo.setRowCount(0);
        List<TransaccionCompleja> txs = ctx.tienda.getTransacciones();
        if (txs == null || txs.isEmpty()) txs = snapshotTxs();

        String q = (txtFiltro != null) ? txtFiltro.getText() : "";
        String tipo = (cmbTipo != null && cmbTipo.getSelectedItem() != null) ? cmbTipo.getSelectedItem().toString() : "TODOS";
        Date d1 = parseYMD(txtDesde != null ? txtDesde.getText() : null);
        Date d2 = parseYMD(txtHasta != null ? txtHasta.getText() : null);
        var sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        for (var t : txs) {
            if (t == null) continue;
            if (!pasaFiltros(t, q, tipo, d1, d2)) continue;

            String codigo = String.valueOf(t.getProductoId());
            ProductoComplejo p = buscarProducto(codigo);
            String nombre = (p != null ? p.getNombre() : codigo);

            BigDecimal total;
            try {
                if (t.getTotal() != null) {
                    total = t.getTotal();
                } else {
                    BigDecimal unit = (t.getPrecioUnit() != null) ? t.getPrecioUnit() : BigDecimal.ZERO;
                    int cant; try { cant = t.getCantidad(); } catch (Throwable ignored) { cant = 0; }
                    total = unit.multiply(BigDecimal.valueOf(cant)).setScale(2, RoundingMode.HALF_UP);
                }
            } catch (Throwable ex) {
                total = BigDecimal.ZERO;
            }

            String fechaTxt;
            try { Date f = t.getFechaHora(); fechaTxt = (f != null) ? sdf.format(f) : ""; }
            catch (Throwable ex) { fechaTxt = ""; }

            int cantidad = 0; try { cantidad = t.getCantidad(); } catch (Throwable ignored) {}

            modelo.addRow(new Object[]{
                    t.getId(), nombre, t.getTipoString(), cantidad,
                    String.format("$%.2f", total.doubleValue()),
                    t.getUsuario(), fechaTxt
            });
        }
    }
}
