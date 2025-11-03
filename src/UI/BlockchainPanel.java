package UI;

import App.AppContext;
import Blockchain.Block;
import Blockchain.BlockChain;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;

public class BlockchainPanel extends JPanel implements RefreshableView {

    private final AppContext ctx;
    private JTextArea area;
    private JLabel lblEstado;

    public BlockchainPanel(AppContext ctx) {
        this.ctx = ctx;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(10,10));
        setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton btnValidar = new JButton("Validar Blockchain");
        btnValidar.addActionListener(e -> { validar(); pintar(); });

        JButton btnVer = new JButton("Ver Blockchain");
        btnVer.addActionListener(e -> mostrarDialog());

        JButton btnGuardarPem = new JButton("Guardar (PEM)");
        btnGuardarPem.addActionListener(e -> guardarPEM());

        JButton btnCargarPem = new JButton("Cargar (PEM)");
        btnCargarPem.addActionListener(e -> cargarPEM());

        JButton btnExportCsv = new JButton("Exportar CSV");
        btnExportCsv.addActionListener(e -> exportCSV());

        JButton btnValidarReparar = new JButton("Validar/Reparar");
        btnValidarReparar.addActionListener(e -> validarReparar());

        JButton btnMigrar = new JButton("Migrar a PoW");
        btnMigrar.addActionListener(e -> migrar());

        
        top.add(btnValidar);
        top.add(btnVer);
        top.add(btnGuardarPem);
        top.add(btnCargarPem);
        top.add(btnExportCsv);
        top.add(btnValidarReparar);
        top.add(btnMigrar);

        area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));

        lblEstado = new JLabel(" ");
        lblEstado.setFont(lblEstado.getFont().deriveFont(Font.BOLD));

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(area), BorderLayout.CENTER);
        add(lblEstado, BorderLayout.SOUTH);
    }

    private void validar() {
        try {
            boolean ok = ctx.blockchainStore.getChain().isValid();
            JOptionPane.showMessageDialog(this, ok ? "✓ Cadena válida" : "⚠ Cadena inválida",
                    ok ? "OK" : "Atención", ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al validar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

private void mostrarDialog() {
    try {
        var chain = ctx.blockchainStore.getChain();
        StringBuilder sb = new StringBuilder("=== CADENA DE FACTURAS ===\n");
        int i = 0;
        for (Block b : chain.getChain()) {
            if (b == null) continue;

            // nonce (seguro)
            String nonceStr;
            try { nonceStr = String.valueOf(b.getNonce()); } catch (Throwable t) { nonceStr = "(s/d)"; }

            if (i == 0 || b.getFactura() == null) {
                sb.append("\n[GÉNESIS] #").append(i)
                  .append("  hash=").append(ns(b.getHash()))
                  .append("\n   prev=").append(ns(b.getPreviousHash()))
                  .append("\n   nonce=").append(nonceStr)
                  .append("\n");
            } else {
                boolean enc = false;
                try { enc = b.isEncrypted(); } catch (Throwable ignored) {}

                if (enc) {
                    sb.append("\n#").append(b.index)
                      .append("  [CIFRADO]")
                      .append("\n   prev=").append(ns(b.getPreviousHash()))
                      .append("\n   hash=").append(ns(b.getHash()))
                      .append("\n   nonce=").append(nonceStr)
                      .append("\n");
                } else {
                    var f = b.getFactura();
                    sb.append("\n#").append(b.index)
                      .append("  Factura ").append(ns(safe(() -> f.getNumero())))
                      .append("  Total $").append(ns(safe(() -> f.getTotal())))
                      .append("\n   prev=").append(ns(b.getPreviousHash()))
                      .append("\n   hash=").append(ns(b.getHash()))
                      .append("\n   nonce=").append(nonceStr)
                      .append("\n");
                }
            }
            i++;
        }
        JTextArea t = new JTextArea(sb.toString(), 30, 90);
        t.setEditable(false);
        t.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JOptionPane.showMessageDialog(this, new JScrollPane(t), "Blockchain", JOptionPane.INFORMATION_MESSAGE);
    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this, "Error al mostrar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
}


    private void guardarPEM() {
        try {
            JFileChooser fcPub = new JFileChooser();
            fcPub.setDialogTitle("Seleccione la clave pública (PEM)");
            if (fcPub.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path pub = fcPub.getSelectedFile().toPath();

            JFileChooser fcOut = new JFileChooser();
            fcOut.setDialogTitle("Guardar blockchain.enc");
            fcOut.setSelectedFile(new File("blockchain.enc"));
            if (fcOut.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

            ctx.blockchainStore.saveEncryptedAsymmetric(pub, fcOut.getSelectedFile().toPath());
            JOptionPane.showMessageDialog(this, "Blockchain guardada cifrada.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar (PEM): " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargarPEM() {
        try {
            JFileChooser fcPriv = new JFileChooser();
            fcPriv.setDialogTitle("Seleccione la clave privada (PEM PKCS#8)");
            if (fcPriv.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path priv = fcPriv.getSelectedFile().toPath();

            JFileChooser fcIn = new JFileChooser();
            fcIn.setDialogTitle("Seleccione el archivo .enc de blockchain");
            if (fcIn.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

            ctx.blockchainStore.loadEncryptedAsymmetric(priv, fcIn.getSelectedFile().toPath());
            JOptionPane.showMessageDialog(this, "Blockchain cargada/descifrada.");
            refreshAll();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo cargar (PEM): " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportCSV() {
        try {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Guardar blockchain.csv");
            fc.setSelectedFile(new File("blockchain.csv"));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            ctx.blockchainStore.exportCSV(fc.getSelectedFile().toPath());
            JOptionPane.showMessageDialog(this, "CSV exportado:\n" + fc.getSelectedFile().getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo exportar CSV: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void validarReparar() {
        try {
            int fixed = ctx.blockchainStore.repairInMemoryAndSaveToPostgres();
            boolean ok = ctx.blockchainStore.validateFromPostgres();
            if (ok && fixed > 0) {
                JOptionPane.showMessageDialog(this, "✓ Cadena reparada y válida (persistida en PG).");
            } else if (ok) {
                JOptionPane.showMessageDialog(this, "✓ La cadena ya era válida. Sin cambios.");
            } else {
                JOptionPane.showMessageDialog(this, "⚠ La cadena sigue con inconsistencias.", "Atención", JOptionPane.WARNING_MESSAGE);
            }
            refreshAll();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al validar/reparar en PG: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void migrar() {
        try {
            ctx.blockchainStore.migrateIfLegacy();
            JOptionPane.showMessageDialog(this, "Migración completada (si era necesaria).");
            refreshAll();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al migrar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String ns(Object o) { return (o==null) ? "" : o.toString(); }

    private void pintar() {
        try {
            if (ctx.blockchainStore == null || ctx.blockchainStore.getChain() == null) {
                area.setText("Blockchain no inicializada.");
                lblEstado.setText(" ");
                return;
            }
            var chain = ctx.blockchainStore.getChain();
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (Block b : chain.getChain()) {
                if (b == null) { i++; continue; }
                sb.append("============================================================================\n\n");
                sb.append("Bloque #").append(i).append("\n");
                String prev = (i == 0) ? "0" : ns(b.getPreviousHash());
                if (prev.isBlank()) prev = "0";
                sb.append("prevHash : ").append(prev).append("\n");
                sb.append("hash     : ").append(ns(b.getHash())).append("\n");
                try { sb.append("nonce    : ").append(b.getNonce()).append("\n"); } catch (Throwable ignored) {}

                boolean enc = false;
                try { enc = b.isEncrypted(); } catch (Throwable ignored) {}

                if (i == 0 || b.getFactura() == null) { 
                    sb.append("GÉNESIS\n\n");
                    i++; continue;
                }

                if (enc) {
                    sb.append("[CIFRADO]\n\n");
                    i++; continue;
                }

                var f = b.getFactura();
                sb.append("Factura  : ").append(ns(safe(() -> f.getNumero()))).append("\n");
                try {
                    sb.append("Cliente  : ").append(ns(safe(() -> f.getCliente()))).append(" (").append(ns(safe(() -> f.getClienteNit()))).append(")\n");
                    sb.append("Dirección: ").append(ns(safe(() -> f.getDireccion()))).append("\n");
                    sb.append("Pago     : ").append(ns(safe(() -> f.getFormaPago())))
                            .append(" | Vendedor: ").append(ns(safe(() -> f.getVendedor())))
                            .append(" | Tienda: ").append(ns(safe(() -> f.getSucursal()))).append("\n");
                } catch (Throwable ignored) {}

                sb.append("Items:\n");
                try {
                    f.getItems().forEach(it -> sb.append(String.format(
                            "  - %s x%d @ $%.2f => $%.2f\n",
                            ns(it.getDescripcion()), it.getCantidad(),
                            it.getPrecioUnitario().doubleValue(),
                            it.getSubtotal().doubleValue()
                    )));
                } catch (Throwable ignored) {}

                try {
                    sb.append(String.format(
                            "Subtotal: $%.2f   IVA: $%.2f   TOTAL: $%.2f\n\n",
                            safeD(() -> f.getSubtotal().doubleValue()),
                            safeD(() -> f.getImpuesto().doubleValue()),
                            safeD(() -> f.getTotal().doubleValue())
                    ));
                } catch (Throwable e) {
                    sb.append(String.format(
                            "Subtotal: $%.2f   IVA: $%.2f   TOTAL: $%.2f\n\n",
                            safeD(() -> f.getSubTotal().doubleValue()),
                            safeD(() -> f.getImpuesto().doubleValue()),
                            safeD(() -> f.getTotal().doubleValue())
                    ));
                }
                i++;
            }
            area.setText(sb.toString());
            area.setCaretPosition(0);

            try {
                boolean ok = ctx.blockchainStore.getChain().isValid();
                lblEstado.setText("Cadena: " + (ok ? "VÁLIDA" : "INVÁLIDA")
                        + " | Dificultad: " + BlockChain.DIFFICULTY
                        + " | Bloques: " + ctx.blockchainStore.getChain().getChainLength());
            } catch (Throwable ignored) { lblEstado.setText(" "); }
        } catch (Exception ex) {
            area.setText("Error al renderizar: " + ex.getMessage());
            lblEstado.setText(" ");
        }
    }

    @Override public void refreshAll() { pintar(); }

    
    private interface ThrowingSupplier<T> { T get() throws Exception; }
    private static <T> T safe(ThrowingSupplier<T> s) {
        try { return s.get(); } catch (Throwable t) { return null; }
    }
    private static double safeD(ThrowingSupplier<Double> s) {
        try { return s.get(); } catch (Throwable t) { return 0.0; }
    }
}
