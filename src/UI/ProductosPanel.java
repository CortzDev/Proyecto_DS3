package UI;

import App.AppContext;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import Model.Categoria;
import Model.ProductoComplejo;
import Model.TipoTx;

public class ProductosPanel extends JPanel implements RefreshableView {

    private final AppContext ctx;
    private JTable tabla;
    private DefaultTableModel modelo;
    private JTextField txtCodigo, txtNombre, txtPrecio, txtStock;
    private JComboBox<String> cmbCategorias;

    public ProductosPanel(AppContext ctx) {
        this.ctx = ctx;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(10,10));
        setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        JPanel form = new JPanel(new GridLayout(6,2,8,8));
        txtCodigo = new JTextField(); txtNombre = new JTextField();
        txtPrecio = new JTextField(); txtStock = new JTextField();
        cmbCategorias = new JComboBox<>();

        form.add(new JLabel("Código:")); form.add(txtCodigo);
        form.add(new JLabel("Nombre:")); form.add(txtNombre);
        form.add(new JLabel("Precio:")); form.add(txtPrecio);
        form.add(new JLabel("Stock Inicial:")); form.add(txtStock);
        form.add(new JLabel("Categoría:")); form.add(cmbCategorias);

        JButton btnAgregar = new JButton("Agregar Producto");
        btnAgregar.addActionListener(e -> onAgregarProducto());
        form.add(new JLabel()); form.add(btnAgregar);

        modelo = new DefaultTableModel(new Object[]{"Código","Nombre","Precio","Stock","Categoría","Valor"},0) {
            public boolean isCellEditable(int r,int c){ return false; }
        };
        tabla = new JTable(modelo);

        add(form, BorderLayout.NORTH);
        add(new JScrollPane(tabla), BorderLayout.CENTER);
    }

    private void onAgregarProducto() {
        try {
            if (cmbCategorias.getSelectedIndex() == -1) {
                JOptionPane.showMessageDialog(this, "Seleccione una categoría", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String codigo = txtCodigo.getText().trim();
            String nombre = txtNombre.getText().trim();
            double precioD = Double.parseDouble(txtPrecio.getText().trim());
            int stockInicial = Integer.parseInt(txtStock.getText().trim());
            if (codigo.isEmpty() || nombre.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Código y nombre obligatorios", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String catNombre = (String) cmbCategorias.getSelectedItem();
            Categoria cat = buscarCategoriaPorNombre(catNombre);
            if (cat == null) { JOptionPane.showMessageDialog(this, "Categoría no encontrada", "Error", JOptionPane.ERROR_MESSAGE); return; }

            String proveedor = JOptionPane.showInputDialog(this, "Proveedor:", "Sin especificar");

            ProductoComplejo p = new ProductoComplejo(codigo, nombre, BigDecimal.valueOf(precioD), 0, cat.getId());
            if (proveedor != null && !proveedor.isBlank()) p.setProveedor(proveedor);

            ctx.tienda.agregarProductoPersistente(p);

            if (stockInicial > 0) {
                ctx.tienda.registrarTransaccion(
                        p.getId(), TipoTx.ENTRADA, stockInicial, BigDecimal.valueOf(precioD), "Admin"
                );
            }

            limpiar();
            refreshAll();
            JOptionPane.showMessageDialog(this, "Producto agregado y guardado en PostgreSQL");
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Error en formato numérico", "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al agregar producto: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Categoria buscarCategoriaPorNombre(String n) {
        for (Categoria c : ctx.tienda.getCategorias()) if (c.getNombre().equals(n)) return c;
        return null;
    }

    private void limpiar() {
        txtCodigo.setText(""); txtNombre.setText("");
        txtPrecio.setText(""); txtStock.setText("");
    }

    @Override public void refreshAll() {
        
        cmbCategorias.removeAllItems();
        for (Categoria c : ctx.tienda.getCategorias()) cmbCategorias.addItem(c.getNombre());
        
        modelo.setRowCount(0);
        for (Categoria cat : ctx.tienda.getCategorias()) {
            for (ProductoComplejo p : cat.getProductos()) {
                modelo.addRow(new Object[]{
                        p.getId(), p.getNombre(),
                        String.format("$%.2f", p.getPrecio().doubleValue()),
                        p.getStock(), cat.getNombre(),
                        String.format("$%.2f", p.getValorInventario().doubleValue())
                });
            }
        }
    }
}
