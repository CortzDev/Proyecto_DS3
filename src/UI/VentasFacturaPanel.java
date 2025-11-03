package UI;

import App.AppContext;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import Invoice.Factura;
import Invoice.FacturaItem;
import Invoice.LedgerBridge;
import Invoice.LineaVenta;
import Model.ProductoComplejo;
import Model.TransaccionCompleja;

public class VentasFacturaPanel extends JPanel implements RefreshableView {

    private final AppContext ctx;

    private JComboBox<String> cmbProductos;
    private JTextField txtCant, txtPrecio;
    private JTable tablaCarrito;
    private DefaultTableModel modeloCarrito;
    private JLabel lblSub, lblIva, lblTotal;

    private JTextField txtNFactura, txtCliente, txtDUI, txtDireccion, txtVendedor;
    private JComboBox<String> cmbPago;

    
    private JButton btnQuitarItem;
    private JButton btnLimpiar;

    public VentasFacturaPanel(AppContext ctx) {
        this.ctx = ctx; initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(10,10));
        setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        
        JPanel header = new JPanel(new GridLayout(2,1,8,8));
        JPanel f1 = new JPanel(new GridLayout(1,6,8,8));
        f1.add(new JLabel("N° Factura:"));
        txtNFactura = new JTextField(genNum()); f1.add(txtNFactura);
        f1.add(new JLabel("Cliente:"));
        txtCliente = new JTextField(); f1.add(txtCliente);
        f1.add(new JLabel("DUI/NIT:"));
        txtDUI = new JTextField(); f1.add(txtDUI);

        JPanel f2 = new JPanel(new GridLayout(1,6,8,8));
        f2.add(new JLabel("Dirección:"));
        txtDireccion = new JTextField(); f2.add(txtDireccion);
        f2.add(new JLabel("Forma de pago:"));
        cmbPago = new JComboBox<>(new String[]{"EFECTIVO","TARJETA","TRANSFERENCIA"}); f2.add(cmbPago);
        f2.add(new JLabel("Vendedor:"));
        txtVendedor = new JTextField(System.getProperty("user.name","Vendedor")); f2.add(txtVendedor);

        header.add(f1); header.add(f2);
        add(header, BorderLayout.NORTH);

        
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.35);
        add(split, BorderLayout.CENTER);

        
        JPanel left = new JPanel(new BorderLayout(8,8));
        JPanel form = new JPanel(new GridLayout(4,2,6,6));
        form.add(new JLabel("Producto:"));
        cmbProductos = new JComboBox<>(); form.add(cmbProductos);
        form.add(new JLabel("Cantidad:"));
        txtCant = new JTextField("1"); form.add(txtCant);
        form.add(new JLabel("Precio unit.:"));
        txtPrecio = new JTextField(); form.add(txtPrecio);
        JButton btnAdd = new JButton("Agregar al carrito");
        btnAdd.addActionListener(e -> onAdd());
        form.add(new JLabel()); form.add(btnAdd);
        left.add(form, BorderLayout.NORTH);
        split.setLeftComponent(left);

        
        JPanel right = new JPanel(new BorderLayout(8,8));
        modeloCarrito = new DefaultTableModel(new Object[]{"Código","Producto","Cant.","Precio","Subtotal"}, 0){
            public boolean isCellEditable(int r,int c){ return false; }
            public Class<?> getColumnClass(int c) {
                return switch(c){ case 2->Integer.class; case 3,4->BigDecimal.class; default->String.class; };
            }
        };
        tablaCarrito = new JTable(modeloCarrito);
        right.add(new JScrollPane(tablaCarrito), BorderLayout.CENTER);

        
        JPanel barraCarrito = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnQuitarItem = new JButton("Quitar ítem");
        btnQuitarItem.addActionListener(e -> quitarItemSeleccionado());
        btnLimpiar = new JButton("Limpiar carrito");
        btnLimpiar.addActionListener(e -> limpiarCarrito());
        barraCarrito.add(btnQuitarItem);
        barraCarrito.add(btnLimpiar);
        right.add(barraCarrito, BorderLayout.NORTH);

        
        tablaCarrito.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("DELETE"), "delRow");
        tablaCarrito.getActionMap().put("delRow", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { quitarItemSeleccionado(); }
        });

        
        JPanel totals = new JPanel(new GridLayout(3,2,6,6));
        lblSub = new JLabel("$0.00", SwingConstants.RIGHT);
        lblIva = new JLabel("$0.00", SwingConstants.RIGHT);
        lblTotal = new JLabel("$0.00", SwingConstants.RIGHT);
        lblTotal.setFont(lblTotal.getFont().deriveFont(Font.BOLD, 16f));
        totals.setBorder(BorderFactory.createTitledBorder("Totales"));
        totals.add(new JLabel("Subtotal:")); totals.add(lblSub);
        totals.add(new JLabel("Impuesto (IVA):")); totals.add(lblIva);
        totals.add(new JLabel("TOTAL:")); totals.add(lblTotal);

        JButton btnEmitir = new JButton("Emitir Factura y Encadenar");
        btnEmitir.addActionListener(e -> onEmitir());

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(totals, BorderLayout.CENTER);
        bottom.add(btnEmitir, BorderLayout.SOUTH);
        right.add(bottom, BorderLayout.SOUTH);

        split.setRightComponent(right);

        
        cmbProductos.addActionListener(e -> {
            String s = (String) cmbProductos.getSelectedItem();
            if (s != null && s.contains(" - ")) {
                String codigo = s.split(" - ")[0];
                ProductoComplejo p = buscarProducto(codigo);
                if (p != null) txtPrecio.setText(p.getPrecio().toPlainString());
            }
        });

        
        modeloCarrito.addTableModelListener(e -> recalc());
    }

    private String genNum() {
        return "F-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date())
                + "-" + String.format("%03d", (int)(Math.random()*999));
    }

    private ProductoComplejo buscarProducto(String codigo) {
        for (var c : ctx.tienda.getCategorias())
            for (var p : c.getProductos())
                if (p.getId().equals(codigo)) return p;
        return null;
    }

    private void onAdd() {
        try {
            String sel = (String) cmbProductos.getSelectedItem();
            if (sel == null || !sel.contains(" - ")) {
                JOptionPane.showMessageDialog(this, "Seleccione un producto"); return;
            }
            String codigo = sel.split(" - ")[0];
            ProductoComplejo p = buscarProducto(codigo);
            if (p == null) { JOptionPane.showMessageDialog(this, "Producto no encontrado"); return; }

            int cant = Integer.parseInt(txtCant.getText().trim());
            if (cant <= 0) { JOptionPane.showMessageDialog(this, "Cantidad inválida"); return; }

            BigDecimal precio = txtPrecio.getText().isBlank() ? p.getPrecio() : new BigDecimal(txtPrecio.getText().trim());
            BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(cant)).setScale(2, RoundingMode.HALF_UP);
            modeloCarrito.addRow(new Object[]{ p.getId(), p.getNombre(), cant, precio, subtotal });

            txtCant.setText("1");
            txtPrecio.setText(p.getPrecio().toPlainString());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Formato numérico inválido", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    
    private void quitarItemSeleccionado() {
        int row = tablaCarrito.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona un ítem en el carrito.", "Atención",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = tablaCarrito.convertRowIndexToModel(row);
        modeloCarrito.removeRow(modelRow);
        recalc();
    }

    
    private void limpiarCarrito() {
        if (modeloCarrito.getRowCount() == 0) return;
        int opt = JOptionPane.showConfirmDialog(this,
                "¿Vaciar todo el carrito?", "Confirmar",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (opt != JOptionPane.YES_OPTION) return;
        modeloCarrito.setRowCount(0);
        recalc();
    }

    private List<LineaVenta> carrito() {
        List<LineaVenta> out = new ArrayList<>();
        for (int i=0;i<modeloCarrito.getRowCount();i++) {
            String id = modeloCarrito.getValueAt(i,0).toString();
            int cant = Integer.parseInt(modeloCarrito.getValueAt(i,2).toString());
            BigDecimal pUnit = (BigDecimal) modeloCarrito.getValueAt(i,3);
            ProductoComplejo p = buscarProducto(id);
            if (p == null) throw new IllegalArgumentException("Producto no existe: " + id);
            if (p.getStock() < cant) throw new IllegalStateException("Stock insuficiente para "+p.getNombre()+"; disponible: "+p.getStock());
            out.add(new LineaVenta(id, cant, pUnit));
        }
        return out;
    }

    private void onEmitir() {
        try {
            var items = carrito();
            if (items.isEmpty()) { JOptionPane.showMessageDialog(this, "El carrito está vacío"); return; }

            String numero   = txtNFactura.getText().trim().isBlank() ? genNum() : txtNFactura.getText().trim();
            String cliente  = txtCliente.getText().trim();
            String clienteId= txtDUI.getText().trim();
            String dir      = txtDireccion.getText().trim();
            String pago     = (String) cmbPago.getSelectedItem();
            String vendedor = txtVendedor.getText().trim();
            String sucursal = ctx.tienda.getNombre();

            
            Factura f = LedgerBridge.facturaDesdeCarrito(
                    numero, cliente, clienteId, dir, pago, vendedor, sucursal,
                    new BigDecimal("0.13"), items, ctx.tienda.getProductosMap()
            );

            
            java.util.List<TransaccionCompleja> txs = ctx.tienda.registrarVentaAgrupada(numero, vendedor, items);

            
            var chain = ctx.blockchainStore.getChain();
            int newIndex = (chain != null) ? chain.getChainLength() : 0;
            String prev = ctx.blockchainStore.getLastHash();

            Blockchain.Block bloque = new Blockchain.Block(newIndex, prev, f);
            long t0 = System.currentTimeMillis();
            bloque.mineBlock(Blockchain.BlockChain.DIFFICULTY);
            long ms = System.currentTimeMillis() - t0;

            ctx.blockchainStore.appendBlock(bloque);
            ctx.blockchainStore.appendBlockAndPersist(true);

            JOptionPane.showMessageDialog(this,
                    "✅ Factura emitida"
                            + "\nN°: " + f.getNumero()
                            + "\nTotal: $" + f.getTotal()
                            + "\nHash: " + bloque.getHash()
                            + "\nNonce: " + bloque.getNonce()
                            + "\nMinado en: " + ms + " ms",
                    "OK", JOptionPane.INFORMATION_MESSAGE);

            
            modeloCarrito.setRowCount(0);
            txtNFactura.setText(genNum());
            recalc();

            
            ctx.refreshAllViews();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo emitir: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void recalc() {
        BigDecimal sub = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (int i=0;i<modeloCarrito.getRowCount();i++) {
            BigDecimal s = (BigDecimal) modeloCarrito.getValueAt(i,4);
            if (s != null) sub = sub.add(s);
        }
        BigDecimal iva = sub.multiply(new BigDecimal("0.13")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = sub.add(iva).setScale(2, RoundingMode.HALF_UP);

        if (lblSub!=null)   lblSub.setText(String.format("$%.2f", sub));
        if (lblIva!=null)   lblIva.setText(String.format("$%.2f", iva));
        if (lblTotal!=null) lblTotal.setText(String.format("$%.2f", total));
    }

    @Override public void refreshAll() {
        cmbProductos.removeAllItems();
        for (var c : ctx.tienda.getCategorias())
            for (var p : c.getProductos())
                cmbProductos.addItem(p.getId() + " - " + p.getNombre());
        
        if (cmbProductos.getItemCount() > 0) {
            cmbProductos.setSelectedIndex(0);
            String s = (String) cmbProductos.getSelectedItem();
            if (s != null && s.contains(" - ")) {
                String codigo = s.split(" - ")[0];
                var p = buscarProducto(codigo);
                if (p != null) txtPrecio.setText(p.getPrecio().toPlainString());
            }
        }
        recalc();
    }
}
