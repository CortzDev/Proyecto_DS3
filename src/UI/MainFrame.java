package UI;

import App.AppContext;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import Model.Tienda;
import Blockchain.Block;
import Blockchain.BlockChainStore;
import Postgres.DbPostgres;
import DAO.BlockPostgresDAO;

public class MainFrame extends JFrame {

    private final AppContext ctx;
    private final List<RefreshableView> views = new ArrayList<>();

    public MainFrame() {
        
        Tienda tienda = new Tienda("T503", "SuperTech Store", "Av. Principal 503", "2437-5678");
        BlockChainStore store = new BlockChainStore();
        this.ctx = new AppContext(tienda, store);

        setTitle("Sistema de Inventario - " + tienda.getNombre());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1180, 780);
        setLocationRelativeTo(null);

        
        JTabbedPane tabs = new JTabbedPane();
        addTab(tabs, "Categorías",       new CategoriasPanel(ctx));
        addTab(tabs, "Productos",        new ProductosPanel(ctx));
        addTab(tabs, "Ventas / Factura", new VentasFacturaPanel(ctx));
        addTab(tabs, "Transacciones",    new TransaccionesPanel(ctx));
        addTab(tabs, "Reportes",         new ReportesPanel(ctx));
        addTab(tabs, "Blockchain",       new BlockchainPanel(ctx));
        add(tabs, BorderLayout.CENTER);

        
        setJMenuBar(buildMenuBar());

        
        try { ctx.tienda.loadCatalogFromPostgres(); } catch (Exception ignored) {}
        try { ctx.blockchainStore.loadFromPostgresIfAny(); } catch (Exception ignored) {}

        
        ctx.setRefresher(this::refreshAllViews);

        
        refreshAllViews();
    }

    private void addTab(JTabbedPane tabs, String title, JPanel panel) {
        tabs.addTab(title, panel);
        if (panel instanceof RefreshableView rv) views.add(rv);
    }

    private void refreshAllViews() {
        for (var v : views) {
            try { v.refreshAll(); } catch (Throwable ignored) {}
        }
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        
        JMenu mArchivo = new JMenu("Archivo");
        JMenuItem salir = new JMenuItem("Salir");
        salir.addActionListener(e -> System.exit(0));
        mArchivo.add(salir);

        
        JMenu mHerr = new JMenu("Herramientas");

        JMenuItem validarMem = new JMenuItem("Validar Blockchain (memoria)");
        validarMem.addActionListener(e -> {
            try {
                boolean ok = ctx.blockchainStore.getChain().isValid();
                JOptionPane.showMessageDialog(this, ok ? "✓ Cadena válida" : "⚠ Cadena inválida",
                        ok ? "OK" : "Atención", ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JMenuItem ver = new JMenuItem("Ver Blockchain");
        ver.addActionListener(e -> {
            try {
                var chain = ctx.blockchainStore.getChain();
                StringBuilder sb = new StringBuilder("=== CADENA DE FACTURAS ===\n");
                int i = 0;
                for (var b : chain.getChain()) {
                    if (b == null) { i++; continue; }
                    boolean enc = false;
                    try { enc = b.isEncrypted(); } catch (Throwable ignored) {}

                    if (i == 0 || b.getFactura() == null) {
                        sb.append("\n[GÉNESIS] #").append(i)
                          .append("  hash=").append(b.getHash())
                          .append("\n   prev=").append(b.getPreviousHash()).append("\n");
                    } else if (enc) {
                        sb.append("\n#").append(b.index)
                          .append("  [CIFRADO]  (use Seguridad → Ver JSON)")
                          .append("\n   prev=").append(b.getPreviousHash())
                          .append("\n   hash=").append(b.getHash()).append("\n");
                    } else {
                        var f = b.getFactura();
                        sb.append("\n#").append(b.index)
                          .append("  Factura ").append(s(nsSafe(() -> f.getNumero())))
                          .append("  Total $").append(s(nsSafe(() -> f.getTotal())))
                          .append("\n   prev=").append(b.getPreviousHash())
                          .append("\n   hash=").append(b.getHash()).append("\n");
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
        });

        mHerr.add(validarMem);
        mHerr.add(ver);

        
        JMenu mSec = new JMenu("Seguridad");

        JMenuItem cargarPub = new JMenuItem("Cargar pública (bloques)...");
        cargarPub.addActionListener(e -> {
            try {
                JFileChooser fcPub = new JFileChooser();
                fcPub.setDialogTitle("Seleccione la clave pública (PEM) para cifrado por bloque");
                if (fcPub.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
                Path pub = fcPub.getSelectedFile().toPath();
                ctx.blockchainStore.loadBlockEncryptionKey(pub);
                JOptionPane.showMessageDialog(this, "✓ Llave pública cargada.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error cargando pública: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JMenuItem cargarPriv = new JMenuItem("Cargar privada (bloques)...");
        cargarPriv.addActionListener(e -> {
            try {
                JFileChooser fcPriv = new JFileChooser();
                fcPriv.setDialogTitle("Seleccione la clave privada (PEM PKCS#8) para descifrado por bloque");
                if (fcPriv.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
                Path priv = fcPriv.getSelectedFile().toPath();
                ctx.blockchainStore.loadBlockDecryptionKey(priv);
                JOptionPane.showMessageDialog(this, "✓ Llave privada cargada.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error cargando privada: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        
        JMenuItem exportBlocksEncJsonHex = new JMenuItem("Exportar bloques (JSON cifrado HEX)...");
        exportBlocksEncJsonHex.addActionListener(e -> {
            try {
                
                JFileChooser fcDir = new JFileChooser();
                fcDir.setDialogTitle("Elige carpeta de salida");
                fcDir.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (fcDir.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
                Path outDir = fcDir.getSelectedFile().toPath();

                
                JFileChooser fcPub = new JFileChooser();
                fcPub.setDialogTitle("Seleccione la clave pública (PEM)");
                if (fcPub.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
                Path pub = fcPub.getSelectedFile().toPath();

                int r = JOptionPane.showConfirmDialog(this, "¿Incluir bloque génesis?", "Exportar JSON cifrado HEX", JOptionPane.YES_NO_OPTION);
                boolean includeGenesis = (r == JOptionPane.YES_OPTION);

                ctx.blockchainStore.exportBlocksAsEncryptedJsonHex(outDir, pub, includeGenesis);
                JOptionPane.showMessageDialog(this, "✓ Bloques exportados como JSON cifrado HEX en:\n" + outDir.toAbsolutePath());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error exportando: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        
JMenuItem verJson = new JMenuItem("Ver JSON (bloque)");
verJson.addActionListener(e -> {
    try {
        String input = JOptionPane.showInputDialog(this, "Índice del bloque:", "Ver JSON (bloque)", JOptionPane.QUESTION_MESSAGE);
        if (input == null) return;
        int idx = Integer.parseInt(input.trim());

        var chain = ctx.blockchainStore.getChain();
        if (idx < 0 || idx >= chain.getChainLength()) {
            JOptionPane.showMessageDialog(this, "Índice fuera de rango.", "Atención", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Block b = chain.getChain().get(idx);
        if (b == null) {
            JOptionPane.showMessageDialog(this, "Bloque no encontrado.", "Atención", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path blocksDir = Path.of("blocks_enc");
        Files.createDirectories(blocksDir); 
        Path encFile   = blocksDir.resolve(String.format("block_%d.enc.json", idx));

        
        if (!Files.exists(encFile)) {
            int opt = JOptionPane.showConfirmDialog(this,
                    "El archivo cifrado del bloque no existe.\nPrimero hay que guardarlo cifrado.\n¿Generarlo ahora?",
                    "Guardar JSON cifrado", JOptionPane.YES_NO_OPTION);
            if (opt != JOptionPane.YES_OPTION) {
                JOptionPane.showMessageDialog(this, "Acción cancelada. Primero guarda el JSON cifrado del bloque.", "Info", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            
            Path pubToUse = null;
            if (!ctx.blockchainStore.hasBlockEncryptionKey()) {
                JFileChooser fcPub = new JFileChooser();
                fcPub.setDialogTitle("Seleccione la clave pública (PEM) para cifrar el archivo del bloque");
                if (fcPub.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
                pubToUse = fcPub.getSelectedFile().toPath();
                ctx.blockchainStore.loadBlockEncryptionKey(pubToUse);
            }

            
            ctx.blockchainStore.saveEncryptedBlockFileByIndex(idx, blocksDir, pubToUse);
            JOptionPane.showMessageDialog(this, "✓ Archivo cifrado generado:\n" + encFile.toAbsolutePath(),
                    "Guardado", JOptionPane.INFORMATION_MESSAGE);
        }

        
        if (!ctx.blockchainStore.hasBlockDecryptionKey()) {
            int want = JOptionPane.showConfirmDialog(this,
                    "Para ver el contenido descifrado se necesita la clave privada PKCS#8.\n¿Cargar ahora?",
                    "Privada requerida", JOptionPane.YES_NO_OPTION);
            if (want != JOptionPane.YES_OPTION) return;
            JFileChooser fcPriv = new JFileChooser();
            fcPriv.setDialogTitle("Seleccione la clave privada (PEM PKCS#8)");
            if (fcPriv.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path priv = fcPriv.getSelectedFile().toPath();
            ctx.blockchainStore.loadBlockDecryptionKey(priv);
        }

        
        String clearJson = ctx.blockchainStore.decryptBlockFileToJson(idx, blocksDir, null);
        JTextArea t2 = new JTextArea(clearJson, 24, 80);
        t2.setEditable(false);
        t2.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JOptionPane.showMessageDialog(this, new JScrollPane(t2), "Contenido del bloque (DESCIFRADO)", JOptionPane.PLAIN_MESSAGE);

        
        
        

    } catch (NumberFormatException nfe) {
        JOptionPane.showMessageDialog(this, "Índice inválido.", "Atención", JOptionPane.WARNING_MESSAGE);
    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
});

        mSec.add(cargarPub);
        mSec.add(cargarPriv);
        mSec.addSeparator();
        mSec.add(exportBlocksEncJsonHex);
        mSec.addSeparator();
        mSec.add(verJson);

        
        JMenu mPG = new JMenu("PostgreSQL");

        JMenuItem catDown = new JMenuItem("Cargar Catálogo (PG → Memoria)");
        catDown.addActionListener(e -> {
            try {
                ctx.tienda.loadCatalogFromPostgres();
                refreshAllViews();
                JOptionPane.showMessageDialog(this, "✓ Catálogo cargado desde PostgreSQL");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al cargar catálogo: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JMenuItem catUp = new JMenuItem("Subir Catálogo (Memoria → PG)");
        catUp.addActionListener(e -> {
            try {
                ctx.tienda.syncCatalogToPostgres();
                JOptionPane.showMessageDialog(this, "✓ Catálogo sincronizado a PostgreSQL");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al subir catálogo: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JMenuItem chainUp = new JMenuItem("Subir cadena a PostgreSQL");
        chainUp.addActionListener(e -> {
            try (var conn = DbPostgres.open()) {
                BlockPostgresDAO dao = new BlockPostgresDAO();
                dao.createTableIfNotExists(conn);
                dao.upsertChain(conn, ctx.blockchainStore.getChain());
                JOptionPane.showMessageDialog(this, "✓ Cadena subida/actualizada en PostgreSQL");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al subir a PG: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JMenuItem chainDown = new JMenuItem("Cargar cadena desde PostgreSQL");
        chainDown.addActionListener(e -> {
            try {
                ctx.blockchainStore.loadFromPostgresIfAny();
                refreshAllViews();
                JOptionPane.showMessageDialog(this, "✓ Cadena cargada desde PostgreSQL");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al cargar de PG: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JMenuItem chainValidatePG = new JMenuItem("Validar en PostgreSQL");
        chainValidatePG.addActionListener(e -> {
            try {
                boolean ok = ctx.blockchainStore.validateFromPostgres();
                JOptionPane.showMessageDialog(this,
                        ok ? "✓ Cadena VÁLIDA en PostgreSQL." : "⚠ Cadena INVÁLIDA en PostgreSQL.",
                        "Validación PostgreSQL",
                        ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error validando en PG: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JMenuItem chainValidateRemine = new JMenuItem("Validar (re-minar con nonce)");
        chainValidateRemine.addActionListener(e -> {
            try (var conn = DbPostgres.open()) {
                BlockPostgresDAO dao = new BlockPostgresDAO();
                boolean ok = dao.validateFromDB_RemineOnce(conn);
                JOptionPane.showMessageDialog(this,
                        ok ? "Cadena VÁLIDA en PostgreSQL" : "Cadena INVÁLIDA en PostgreSQL",
                        "Validación PostgreSQL (re-minar con nonce)",
                        ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error (re-minar): " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JMenuItem chainValidateFast = new JMenuItem("Validar (rápida, sin recalcular)");
        chainValidateFast.addActionListener(e -> {
            try (var conn = DbPostgres.open()) {
                BlockPostgresDAO dao = new BlockPostgresDAO();
                boolean ok = dao.validateFromDB(conn);
                JOptionPane.showMessageDialog(this,
                        ok ? "Cadena VÁLIDA en PostgreSQL" : "Cadena INVÁLIDA en PostgreSQL",
                        "Validación PostgreSQL (rápida)",
                        ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error (rápida): " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        mPG.add(catDown);
        mPG.add(catUp);
        mPG.addSeparator();
        mPG.add(chainUp);
        mPG.add(chainDown);
        mPG.addSeparator();
        mPG.add(chainValidatePG);
        mPG.add(chainValidateRemine);
        mPG.add(chainValidateFast);

        menuBar.add(mArchivo);
        menuBar.add(mHerr);
        menuBar.add(mSec);
        menuBar.add(mPG);
        return menuBar;
    }

    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) { UIManager.setLookAndFeel(info.getClassName()); break; }
            }
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }

    
    private static String s(Object o) { return (o == null) ? "" : o.toString(); }
    private interface ThrowingSupplier<T> { T get() throws Exception; }
    private static Object nsSafe(ThrowingSupplier<?> s) {
        try { return s.get(); } catch (Throwable t) { return ""; }
    }
}
