package UI;

import App.AppContext;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import Model.Categoria;

public class CategoriasPanel extends JPanel implements RefreshableView {

    private final AppContext ctx;
    private JTable tabla;
    private DefaultTableModel modelo;
    private JTextField txtId, txtNombre;

    public CategoriasPanel(AppContext ctx) {
        this.ctx = ctx;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.add(new JLabel("ID:"));
        txtId = new JTextField();
        form.add(txtId);
        form.add(new JLabel("Nombre:"));
        txtNombre = new JTextField();
        form.add(txtNombre);

        JButton btnAgregar = new JButton("Agregar Categoría");
        btnAgregar.addActionListener(e -> onAgregar());
        form.add(new JLabel());
        form.add(btnAgregar);

        modelo = new DefaultTableModel(new Object[]{"ID","Nombre","Productos","Valor"},0) {
            public boolean isCellEditable(int r,int c){ return false; }
        };
        tabla = new JTable(modelo);

        add(form, BorderLayout.NORTH);
        add(new JScrollPane(tabla), BorderLayout.CENTER);
    }

    private void onAgregar() {
        try {
            String id = txtId.getText().trim();
            String nombre = txtNombre.getText().trim();
            if (id.isEmpty() || nombre.isEmpty()) {
                JOptionPane.showMessageDialog(this, "ID y nombre obligatorios", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            ctx.tienda.agregarCategoriaPersistente(new Categoria(id, nombre));
            txtId.setText(""); txtNombre.setText("");
            refreshAll();
            JOptionPane.showMessageDialog(this, "Categoría agregada y guardada en PostgreSQL", "OK", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override public void refreshAll() {
        modelo.setRowCount(0);
        for (Categoria cat : ctx.tienda.getCategorias()) {
            modelo.addRow(new Object[]{
                    cat.getId(), cat.getNombre(),
                    cat.contarProductos(),
                    String.format("$%.2f", cat.calcularValorCategoria())
            });
        }
    }
}
