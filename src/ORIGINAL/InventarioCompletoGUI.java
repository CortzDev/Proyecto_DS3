package ORIGINAL;

import javax.crypto.SecretKey;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.PrintWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import Blockchain.Block;
import Blockchain.Block;
import Blockchain.BlockChain;
import Blockchain.BlockChain;
import Blockchain.BlockChainStore;
import Blockchain.BlockChainStore;
import DAO.BlockPostgresDAO;
import DAO.BlockPostgresDAO;
import Model.Categoria;
import Model.Categoria;
import Crypto.Cifrado;
import Crypto.Cifrado;
import Postgres.DbPostgres;
import Postgres.DbPostgres;
import Crypto.EncriptacionUtil;
import Crypto.EncriptacionUtil;
import Invoice.Factura;
import Invoice.Factura;
import Invoice.FacturaItem;
import Invoice.FacturaItem;
import Invoice.LedgerBridge;
import Invoice.LedgerBridge;
import Invoice.LineaVenta;
import Invoice.LineaVenta;
import Model.ProductoComplejo;
import Model.ProductoComplejo;
import Model.Tienda;
import Model.Tienda;
import Model.TipoTx;
import Model.TipoTx;
import Model.TransaccionCompleja;
import Model.TransaccionCompleja;

public class InventarioCompletoGUI extends JFrame {

    // ===== Estado principal =====
    private final Tienda tienda;
    private final BlockChainStore blockchainStore = new BlockChainStore();

    // ===== Modelos de tabla =====
    private DefaultTableModel modeloCategorias;
    private DefaultTableModel modeloProductos;
    private DefaultTableModel modeloTransacciones;
    private DefaultTableModel modeloCarrito;

    // ===== Selecciones =====
    private Categoria categoriaSeleccionada;
    private ProductoComplejo productoSeleccionado;

    // ===== Componentes UI =====
    private JTabbedPane jTabbedPane1;
    private JTable tablaCategorias, tablaProductos, tablaTransacciones, tablaCarrito;

    // Categorías (sin descripción)
    private JTextField txtIdCategoria, txtNombreCategoria;

    // Productos
    private JTextField txtCodigoProducto, txtNombreProducto, txtPrecioProducto, txtStockProducto;
    private JComboBox<String> cmbCategorias;

    // Ventas / Factura (Carrito)
    private JTextField txtClienteNombre, txtClienteId, txtClienteDireccion, txtNumeroFactura;
    private JComboBox<String> cmbFormaPago;
    private JComboBox<String> cmbProductosVenta;
    private JTextField txtCantidadVenta, txtPrecioVenta;
    private JButton btnAgregarCarrito, btnEmitirFactura, btnVaciarCarrito, btnQuitarItem;
    private JLabel lblSubtotal, lblImpuesto, lblTotal;

    // Transacciones: filtros
    private JTextField txtFiltroProducto, txtFechaDesde, txtFechaHasta;
    private JComboBox<String> cmbFiltroTipo;

    // (Compat)
    private JTextField txtCantidadTransaccion, txtDescripcionTransaccion, txtUsuarioTransaccion;
    private JComboBox<String> cmbTipoTransaccion;

    private JButton btnAgregarCategoria, btnAgregarProducto;
    private JButton btnExportarJSON, btnDesencriptarJSON, btnGenerarHTML, btnGenerarLedger;

    private JTextArea areaResumen;

    // === Área persistente del tab “Blockchain”
    private JTextArea areaBlockchain;
    private JLabel lblEstadoCadena; // estado/dificultad/largo

    public InventarioCompletoGUI() {
        this.tienda = new Tienda("T503", "SuperTech Store", "Av. Principal 503", "2437-5678");
        setTitle("Sistema de Inventario - " + tienda.getNombre());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1180, 780);

        inicializarComponentes();

        // ======= Cargar catálogo desde PostgreSQL =======
        try {
            tienda.loadCatalogFromPostgres();  // PG -> Memoria
        } catch (Exception ex) {
            mostrarError("No se pudo conectar con PostgreSQL: " + ex.getMessage());
        }
        // =================================================

        actualizarTodo();
        cargarBlockchainSiExiste();
        pintarBlockchainEnArea();

        setLocationRelativeTo(null);
    }

    // ==================== Inicialización ====================

    private void inicializarComponentes() {
        jTabbedPane1 = new JTabbedPane();

        jTabbedPane1.addTab("Categorías", crearPanelCategorias());
        jTabbedPane1.addTab("Productos", crearPanelProductos());
        jTabbedPane1.addTab("Ventas / Factura", crearPanelVentasFactura());
        jTabbedPane1.addTab("Transacciones", crearPanelTransacciones());
        jTabbedPane1.addTab("Reportes", crearPanelReportes());
        jTabbedPane1.addTab("Blockchain", crearPanelBlockchain());

        add(jTabbedPane1, BorderLayout.CENTER);
        setJMenuBar(crearMenuBar());
    }

    private JMenuBar crearMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu menuArchivo = new JMenu("Archivo");
        JMenuItem itemGuardar = new JMenuItem("Guardar (Cifrado)");
        JMenuItem itemCargar = new JMenuItem("Cargar");
        JMenuItem itemSalir = new JMenuItem("Salir");

        itemGuardar.addActionListener(e -> guardarCifrado());
        itemCargar.addActionListener(e -> cargarCifrado());
        itemSalir.addActionListener(e -> System.exit(0));

        menuArchivo.add(itemGuardar);
        menuArchivo.add(itemCargar);
        menuArchivo.addSeparator();
        menuArchivo.add(itemSalir);

        JMenu menuHerramientas = new JMenu("Herramientas");
        JMenuItem itemEmitir = new JMenuItem("Emitir Factura y Encadenar");
        JMenuItem itemValidar = new JMenuItem("Validar Blockchain");
        JMenuItem itemVer = new JMenuItem("Ver Blockchain");

        itemEmitir.addActionListener(e -> emitirFacturaYEncadenar());
        itemValidar.addActionListener(e -> { validarBlockchain(); pintarBlockchainEnArea(); });
        itemVer.addActionListener(e -> mostrarBlockchain());

        menuHerramientas.add(itemEmitir);
        menuHerramientas.add(itemValidar);
        menuHerramientas.add(itemVer);

        // ===== Menú PostgreSQL =====
        JMenu menuPG = new JMenu("PostgreSQL");

        JMenuItem itemSubir = new JMenuItem("Subir cadena a PostgreSQL");
        itemSubir.addActionListener(e -> {
            try (var conn = DbPostgres.open()) {
                BlockPostgresDAO dao = new BlockPostgresDAO();
                dao.createTableIfNotExists(conn);
                dao.upsertChain(conn, blockchainStore.getChain());
                mostrarInfo("✓ Cadena subida/actualizada en PostgreSQL.");
            } catch (Exception ex) {
                mostrarError("Error al subir a PG: " + ex.getMessage());
            }
        });

        JMenuItem itemValidarPG = new JMenuItem("Validar en PostgreSQL");
        itemValidarPG.addActionListener(e -> {
            try {
                boolean ok = blockchainStore.validateFromPostgres();
                if (ok) mostrarInfo("✓ Cadena VÁLIDA en PostgreSQL.");
                else     mostrarAdvertencia("⚠ Cadena INVÁLIDA en PostgreSQL.");
            } catch (Exception ex) {
                mostrarError("Error al validar en PG: " + ex.getMessage());
            }
        });

        JMenuItem itemCargarPG = new JMenuItem("Cargar desde PostgreSQL");
        itemCargarPG.addActionListener(e -> {
            try {
                blockchainStore.loadFromPostgresIfAny();
                pintarBlockchainEnArea();
                mostrarInfo("✓ Cadena cargada desde PostgreSQL.");
            } catch (Exception ex) {
                mostrarError("Error al cargar desde PG: " + ex.getMessage());
            }
        });

        // Validar “re-minando una vez con el mismo nonce”
        JMenuItem itemValidarRemine = new JMenuItem("Validar cadena (re-minar con nonce)");
        itemValidarRemine.addActionListener(e -> {
            try (var conn = DbPostgres.open()) {
                BlockPostgresDAO dao = new BlockPostgresDAO();
                boolean ok = dao.validateFromDB_RemineOnce(conn);
                JOptionPane.showMessageDialog(
                        this,
                        ok ? "Cadena VÁLIDA en PostgreSQL" : "Cadena INVÁLIDA en PostgreSQL",
                        "Validación PostgreSQL (re-minar con nonce)",
                        ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE
                );
            } catch (Exception ex) {
                ex.printStackTrace();
                mostrarError("Error validando en PostgreSQL (re-minar): " + ex.getMessage());
            }
        });

        // Validación rápida (sin recalcular hashes)
        JMenuItem itemValidarRapida = new JMenuItem("Validar cadena (rápida, sin recalcular)");
        itemValidarRapida.addActionListener(e -> {
            try (var conn = DbPostgres.open()) {
                BlockPostgresDAO dao = new BlockPostgresDAO();
                boolean ok = dao.validateFromDB(conn);
                JOptionPane.showMessageDialog(
                        this,
                        ok ? "Cadena VÁLIDA en PostgreSQL" : "Cadena INVÁLIDA en PostgreSQL",
                        "Validación PostgreSQL (rápida)",
                        ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE
                );
            } catch (Exception ex) {
                ex.printStackTrace();
                mostrarError("Error validando en PostgreSQL (rápida): " + ex.getMessage());
            }
        });

        // ======= Catálogo (PG) =======
        JMenuItem itemCatCargar = new JMenuItem("Cargar Catálogo (PG → Memoria)");
        itemCatCargar.addActionListener(e -> {
            try {
                tienda.loadCatalogFromPostgres();
                actualizarTodo();
                mostrarInfo("✓ Catálogo cargado desde PostgreSQL.");
            } catch (Exception ex) {
                mostrarError("Error al cargar catálogo de PG: " + ex.getMessage());
            }
        });

        JMenuItem itemCatSubir = new JMenuItem("Subir Catálogo (Memoria → PG)");
        itemCatSubir.addActionListener(e -> {
            try {
                tienda.syncCatalogToPostgres();
                mostrarInfo("✓ Catálogo sincronizado a PostgreSQL.");
            } catch (Exception ex) {
                mostrarError("Error al subir catálogo a PG: " + ex.getMessage());
            }
        });

        menuPG.add(itemSubir);
        menuPG.add(itemCargarPG);
        menuPG.addSeparator();
        menuPG.add(itemValidarPG);
        menuPG.add(itemValidarRemine);
        menuPG.add(itemValidarRapida);
        menuPG.addSeparator();
        menuPG.add(itemCatCargar);
        menuPG.add(itemCatSubir);

        menuBar.add(menuArchivo);
        menuBar.add(menuHerramientas);
        menuBar.add(menuPG);
        return menuBar;
    }

    private JPanel crearPanelCategorias() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Formulario sin descripción (3 filas)
        JPanel formPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        formPanel.add(new JLabel("ID:"));
        txtIdCategoria = new JTextField();
        formPanel.add(txtIdCategoria);

        formPanel.add(new JLabel("Nombre:"));
        txtNombreCategoria = new JTextField();
        formPanel.add(txtNombreCategoria);

        btnAgregarCategoria = new JButton("Agregar Categoría");
        btnAgregarCategoria.addActionListener(e -> agregarCategoria());
        formPanel.add(new JLabel());
        formPanel.add(btnAgregarCategoria);

        tablaCategorias = new JTable();
        // Columnas: ID, Nombre, Productos, Valor
        modeloCategorias = new DefaultTableModel(
                new Object[]{"ID", "Nombre", "Productos", "Valor"}, 0
        ) { @Override public boolean isCellEditable(int r, int c) { return false; } };
        tablaCategorias.setModel(modeloCategorias);
        tablaCategorias.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) { seleccionarCategoria(); }
        });

        panel.add(formPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(tablaCategorias), BorderLayout.CENTER);
        return panel;
    }

    private JPanel crearPanelProductos() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel formPanel = new JPanel(new GridLayout(6, 2, 5, 5));
        formPanel.add(new JLabel("Código:"));
        txtCodigoProducto = new JTextField();
        formPanel.add(txtCodigoProducto);

        formPanel.add(new JLabel("Nombre:"));
        txtNombreProducto = new JTextField();
        formPanel.add(txtNombreProducto);

        formPanel.add(new JLabel("Precio:"));
        txtPrecioProducto = new JTextField();
        formPanel.add(txtPrecioProducto);

        formPanel.add(new JLabel("Stock Inicial:"));
        txtStockProducto = new JTextField();
        formPanel.add(txtStockProducto);

        formPanel.add(new JLabel("Categoría:"));
        cmbCategorias = new JComboBox<>();
        formPanel.add(cmbCategorias);

        btnAgregarProducto = new JButton("Agregar Producto");
        btnAgregarProducto.addActionListener(e -> agregarProducto());
        formPanel.add(new JLabel());
        formPanel.add(btnAgregarProducto);

        tablaProductos = new JTable();
        modeloProductos = new DefaultTableModel(
                new Object[]{"Código", "Nombre", "Precio", "Stock", "Categoría", "Valor"}, 0
        ) { @Override public boolean isCellEditable(int r, int c) { return false; } };
        tablaProductos.setModel(modeloProductos);
        tablaProductos.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) { seleccionarProducto(); }
        });

        panel.add(formPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(tablaProductos), BorderLayout.CENTER);
        return panel;
    }

    // ----------------- PESTAÑA: Ventas / Factura (Carrito) -----------------
    private JPanel crearPanelVentasFactura() {
        JPanel root = new JPanel(new BorderLayout(10,10));
        root.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        // ===== Encabezado (cliente/factura) =====
        JPanel header = new JPanel(new GridLayout(2,1,8,8));

        JPanel fila1 = new JPanel(new GridLayout(1,6,8,8));
        fila1.add(new JLabel("N° Factura:"));
        txtNumeroFactura = new JTextField(generarConsecutivoFactura());
        fila1.add(txtNumeroFactura);

        fila1.add(new JLabel("Cliente:"));
        txtClienteNombre = new JTextField();
        fila1.add(txtClienteNombre);

        fila1.add(new JLabel("DUI/NIT:"));
        txtClienteId = new JTextField();
        fila1.add(txtClienteId);

        JPanel fila2 = new JPanel(new GridLayout(1,6,8,8));
        fila2.add(new JLabel("Dirección:"));
        txtClienteDireccion = new JTextField();
        fila2.add(txtClienteDireccion);

        fila2.add(new JLabel("Forma de pago:"));
        cmbFormaPago = new JComboBox<>(new String[]{"EFECTIVO","TARJETA","TRANSFERENCIA"});
        fila2.add(cmbFormaPago);

        fila2.add(new JLabel("Vendedor:"));
        txtUsuarioTransaccion = new JTextField(System.getProperty("user.name","Vendedor"));
        fila2.add(txtUsuarioTransaccion);

        header.add(fila1); header.add(fila2);
        root.add(header, BorderLayout.NORTH);

        // ===== Centro: JSplitPane (izquierda selector | derecha carrito) =====
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.35);
        split.setDividerLocation(0.35);

        // ---- Izquierda: selector producto ----
        JPanel left = new JPanel(new BorderLayout(8,8));

        JPanel form = new JPanel(new GridLayout(4,2,6,6));
        form.add(new JLabel("Producto:"));
        cmbProductosVenta = new JComboBox<>();
        form.add(cmbProductosVenta);

        form.add(new JLabel("Cantidad:"));
        txtCantidadVenta = new JTextField("1");
        form.add(txtCantidadVenta);

        form.add(new JLabel("Precio unit.:"));
        txtPrecioVenta = new JTextField();
        form.add(txtPrecioVenta);

        form.add(new JLabel(""));
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btnAgregarCarrito = new JButton("Agregar");
        btnAgregarCarrito.addActionListener(e -> agregarLineaAlCarrito());
        btnQuitarItem = new JButton("Quitar selección");
        btnQuitarItem.addActionListener(e -> quitarSeleccionCarrito());
        btnVaciarCarrito = new JButton("Vaciar");
        btnVaciarCarrito.addActionListener(e -> { modeloCarrito.setRowCount(0); actualizarTotalesCarrito(); });
        botones.add(btnAgregarCarrito); botones.add(btnQuitarItem); botones.add(btnVaciarCarrito);

        left.add(form, BorderLayout.NORTH);
        left.add(botones, BorderLayout.CENTER);

        // Autorellena SIEMPRE el precio al cambiar el producto
        cmbProductosVenta.addActionListener(e -> {
            String sel = (String) cmbProductosVenta.getSelectedItem();
            if (sel != null && sel.contains(" - ")) {
                String codigo = sel.split(" - ")[0];
                ProductoComplejo p = buscarProductoPorCodigo(codigo);
                if (p != null) txtPrecioVenta.setText(p.getPrecio().toPlainString());
            }
        });

        // ---- Derecha: carrito y totales ----
        JPanel right = new JPanel(new BorderLayout(8,8));
        modeloCarrito = new DefaultTableModel(new Object[]{"Código","Producto","Cant.","Precio","Subtotal"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return switch (c) { case 2 -> Integer.class; case 3,4 -> BigDecimal.class; default -> String.class; };
            }
        };
        tablaCarrito = new JTable(modeloCarrito);
        tablaCarrito.setRowHeight(22);
        tablaCarrito.getColumnModel().getColumn(0).setPreferredWidth(90);
        tablaCarrito.getColumnModel().getColumn(1).setPreferredWidth(240);
        tablaCarrito.getColumnModel().getColumn(2).setPreferredWidth(60);
        tablaCarrito.getColumnModel().getColumn(3).setPreferredWidth(90);
        tablaCarrito.getColumnModel().getColumn(4).setPreferredWidth(100);
        right.add(new JScrollPane(tablaCarrito), BorderLayout.CENTER);

        JPanel totals = new JPanel(new GridLayout(3,2,6,6));
        totals.setBorder(BorderFactory.createTitledBorder("Totales"));
        totals.add(new JLabel("Subtotal:"));
        lblSubtotal = new JLabel("$0.00", SwingConstants.RIGHT); totals.add(lblSubtotal);
        totals.add(new JLabel("Impuesto (IVA):"));
        lblImpuesto = new JLabel("$0.00", SwingConstants.RIGHT); totals.add(lblImpuesto);
        totals.add(new JLabel("TOTAL:"));
        lblTotal = new JLabel("$0.00", SwingConstants.RIGHT);
        lblTotal.setFont(lblTotal.getFont().deriveFont(Font.BOLD, 16f));
        totals.add(lblTotal);

        btnEmitirFactura = new JButton("Emitir Factura y Encadenar");
        btnEmitirFactura.setFont(btnEmitirFactura.getFont().deriveFont(Font.BOLD));
        btnEmitirFactura.addActionListener(e -> emitirFacturaYEncadenar());

        JPanel bottomRight = new JPanel(new BorderLayout());
        bottomRight.add(totals, BorderLayout.CENTER);
        bottomRight.add(btnEmitirFactura, BorderLayout.SOUTH);

        right.add(bottomRight, BorderLayout.SOUTH);

        // Recalcular totales cuando cambie el carrito
        modeloCarrito.addTableModelListener(e -> actualizarTotalesCarrito());

        split.setLeftComponent(left);
        split.setRightComponent(right);
        root.add(split, BorderLayout.CENTER);

        return root;
    }

    // ----------------- PESTAÑA: Transacciones (todas + filtros) -----------------
    private JPanel crearPanelTransacciones() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Filtros
        JPanel filtros = new JPanel(new GridLayout(2, 6, 8, 6));
        filtros.setBorder(BorderFactory.createTitledBorder("Filtros"));

        filtros.add(new JLabel("Producto/Código:"));
        txtFiltroProducto = new JTextField();
        filtros.add(txtFiltroProducto);

        filtros.add(new JLabel("Tipo:"));
        cmbFiltroTipo = new JComboBox<>(new String[]{"TODOS","ENTRADA","SALIDA","VENTA","AJUSTE","DEVOLUCION"});
        filtros.add(cmbFiltroTipo);

        filtros.add(new JLabel("Desde (yyyy-MM-dd):"));
        txtFechaDesde = new JTextField(); filtros.add(txtFechaDesde);

        filtros.add(new JLabel("Hasta (yyyy-MM-dd):"));
        txtFechaHasta = new JTextField(); filtros.add(txtFechaHasta);

        JButton btnAplicar = new JButton("Aplicar");
        btnAplicar.addActionListener(e -> actualizarTablaTransacciones());
        JButton btnLimpiar = new JButton("Limpiar");
        btnLimpiar.addActionListener(e -> {
            txtFiltroProducto.setText(""); cmbFiltroTipo.setSelectedIndex(0);
            txtFechaDesde.setText(""); txtFechaHasta.setText("");
            actualizarTablaTransacciones();
        });
        JButton btnRefrescar = new JButton("Refrescar");
        btnRefrescar.addActionListener(e -> actualizarTablaTransacciones());

        JPanel barraBotones = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        barraBotones.add(btnAplicar);
        barraBotones.add(btnLimpiar);
        barraBotones.add(btnRefrescar);

        JPanel north = new JPanel(new BorderLayout());
        north.add(filtros, BorderLayout.CENTER);
        north.add(barraBotones, BorderLayout.SOUTH);

        tablaTransacciones = new JTable();
        modeloTransacciones = new DefaultTableModel(
                new Object[]{"ID", "Producto", "Tipo", "Cantidad", "Total", "Usuario", "Fecha"}, 0
        ) { @Override public boolean isCellEditable(int r, int c) { return false; } };
        tablaTransacciones.setModel(modeloTransacciones);
        tablaTransacciones.setRowHeight(22);

        panel.add(north, BorderLayout.NORTH);
        panel.add(new JScrollPane(tablaTransacciones), BorderLayout.CENTER);
        return panel;
    }

    private JPanel crearPanelReportes() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel botonesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnExportarJSON = new JButton("Exportar JSON Cifrado");
        btnExportarJSON.addActionListener(e -> exportarJSONCifrado());
        botonesPanel.add(btnExportarJSON);

        btnDesencriptarJSON = new JButton("Desencriptar JSON");
        btnDesencriptarJSON.addActionListener(e -> desencriptarJSON());
        botonesPanel.add(btnDesencriptarJSON);

        btnGenerarHTML = new JButton("Generar Reporte HTML");
        btnGenerarHTML.addActionListener(e -> generarReporteHTML());
        botonesPanel.add(btnGenerarHTML);

        areaResumen = new JTextArea();
        areaResumen.setEditable(false);
        areaResumen.setFont(new Font("Monospaced", Font.PLAIN, 12));

        panel.add(botonesPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(areaResumen), BorderLayout.CENTER);
        return panel;
    }

    private JPanel crearPanelBlockchain() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel botonesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnGenerarLedger = new JButton("Generar Ledger (desde carrito)");
        btnGenerarLedger.addActionListener(e -> { emitirFacturaYEncadenar(); pintarBlockchainEnArea(); });
        botonesPanel.add(btnGenerarLedger);

        JButton btnValidar = new JButton("Validar Blockchain");
        btnValidar.addActionListener(e -> { validarBlockchain(); pintarBlockchainEnArea(); });
        botonesPanel.add(btnValidar);

        JButton btnVerBlockchain = new JButton("Ver Blockchain");
        btnVerBlockchain.addActionListener(e -> mostrarBlockchain());
        botonesPanel.add(btnVerBlockchain);

        // Guardar/Cargar con PEM (backups locales cifrados)
        JButton btnGuardarPem = new JButton("Guardar (PEM)");
        btnGuardarPem.addActionListener(e -> { guardarBlockchainAsim(); pintarBlockchainEnArea(); });
        botonesPanel.add(btnGuardarPem);

        JButton btnCargarPem = new JButton("Cargar (PEM)");
        btnCargarPem.addActionListener(e -> { cargarBlockchainAsim(); pintarBlockchainEnArea(); });
        botonesPanel.add(btnCargarPem);

        // Exportar CSV
        JButton btnExportCsv = new JButton("Exportar CSV");
        btnExportCsv.addActionListener(e -> {
            try {
                JFileChooser fc = new JFileChooser();
                fc.setDialogTitle("Guardar blockchain.csv");
                fc.setSelectedFile(new File("blockchain.csv"));
                if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
                blockchainStore.exportCSV(fc.getSelectedFile().toPath());
                mostrarInfo("CSV exportado en:\n" + fc.getSelectedFile().getAbsolutePath());
            } catch (Exception ex) {
                mostrarError("No se pudo exportar CSV: " + ex.getMessage());
            }
        });
        botonesPanel.add(btnExportCsv);

        // Validar/Reparar desde PG (A DEMANDA)
        JButton btnValidarReparar = new JButton("Validar/Reparar");
        btnValidarReparar.addActionListener(e -> {
            try {
                int fixed = blockchainStore.repairInMemoryAndSaveToPostgres();
                boolean ok = blockchainStore.validateFromPostgres();
                if (ok && fixed > 0) {
                    mostrarInfo("✓ Cadena reparada y válida (persistida en PostgreSQL).");
                } else if (ok) {
                    mostrarInfo("✓ La cadena ya era válida. No se realizaron cambios.");
                } else {
                    mostrarAdvertencia("⚠ La cadena sigue presentando inconsistencias en PostgreSQL.");
                }
                pintarBlockchainEnArea();
            } catch (Exception ex) {
                mostrarError("Error al validar/reparar en PG: " + ex.getMessage());
            }
        });
        botonesPanel.add(btnValidarReparar);

        // (Opcional) Migrar JSON antiguo a PoW (solo afecta backups locales)
        JButton btnMigrar = new JButton("Migrar a PoW");
        btnMigrar.addActionListener(e -> {
            try {
                blockchainStore.migrateIfLegacy();
                mostrarInfo("Migración completada (si era necesaria).");
                pintarBlockchainEnArea();
            } catch (Exception ex) {
                mostrarError("Error al migrar: " + ex.getMessage());
            }
        });
        botonesPanel.add(btnMigrar);

        // === Área persistente del tab
        areaBlockchain = new JTextArea();
        areaBlockchain.setEditable(false);
        areaBlockchain.setFont(new Font("Monospaced", Font.PLAIN, 12));

        // Estado abajo
        lblEstadoCadena = new JLabel(" ");
        lblEstadoCadena.setFont(lblEstadoCadena.getFont().deriveFont(Font.BOLD));

        panel.add(botonesPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(areaBlockchain), BorderLayout.CENTER);
        panel.add(lblEstadoCadena, BorderLayout.SOUTH);
        return panel;
    }

    // ==================== Acciones (Categorías/Productos) ====================

    private void agregarCategoria() {
        try {
            String id = txtIdCategoria.getText().trim();
            String nombre = txtNombreCategoria.getText().trim();

            if (id.isEmpty() || nombre.isEmpty()) {
                mostrarError("ID y nombre son obligatorios");
                return;
            }

            Categoria categoria = new Categoria(id, nombre);
            tienda.agregarCategoriaPersistente(categoria);

            limpiarCamposCategoria();
            actualizarTodo();
            mostrarInfo("Categoría agregada y guardada en PostgreSQL");
        } catch (Exception e) {
            mostrarError("Error al agregar categoría: " + e.getMessage());
        }
    }

    private void agregarProducto() {
        try {
            if (cmbCategorias.getSelectedIndex() == -1) {
                mostrarError("Seleccione una categoría");
                return;
            }

            String codigo = txtCodigoProducto.getText().trim();
            String nombre = txtNombreProducto.getText().trim();
            double precioD = Double.parseDouble(txtPrecioProducto.getText().trim());
            int stockInicial = Integer.parseInt(txtStockProducto.getText().trim());

            if (codigo.isEmpty() || nombre.isEmpty()) {
                mostrarError("Código y nombre son obligatorios");
                return;
            }

            String nombreCat = (String) cmbCategorias.getSelectedItem();
            Categoria categoria = buscarCategoriaPorNombre(nombreCat);
            if (categoria == null) {
                mostrarError("Categoría no encontrada");
                return;
            }

            String proveedor = JOptionPane.showInputDialog(this, "Proveedor:", "Sin especificar");

            ProductoComplejo producto = new ProductoComplejo(
                    codigo, nombre, BigDecimal.valueOf(precioD), 0, categoria.getId()
            );
            if (proveedor != null && !proveedor.isBlank()) {
                producto.setProveedor(proveedor);
            }

            // Guardar producto (UPSERT) en PG
            tienda.agregarProductoPersistente(producto);

            // Stock inicial como ENTRADA persistente (evita “doble descuento”)
            if (stockInicial > 0) {
                tienda.registrarTransaccion(
                        producto.getId(),
                        TipoTx.ENTRADA,
                        stockInicial,
                        BigDecimal.valueOf(precioD),
                        "Admin"
                );
            }

            limpiarCamposProducto();
            actualizarTodo();
            mostrarInfo("Producto agregado y guardado en PostgreSQL");
        } catch (NumberFormatException e) {
            mostrarError("Error en formato numérico");
        } catch (Exception e) {
            mostrarError("Error al agregar producto: " + e.getMessage());
        }
    }

    // ==================== Ventas / Factura (lógica) ====================

    private void agregarLineaAlCarrito() {
        try {
            String sel = (String) cmbProductosVenta.getSelectedItem();
            if (sel == null || !sel.contains(" - ")) { mostrarAdvertencia("Seleccione un producto"); return; }
            String codigo = sel.split(" - ")[0];
            ProductoComplejo p = buscarProductoPorCodigo(codigo);
            if (p == null) { mostrarError("Producto no encontrado"); return; }

            int cant = Integer.parseInt(txtCantidadVenta.getText().trim());
            if (cant <= 0) { mostrarAdvertencia("Cantidad inválida"); return; }

            BigDecimal precio = (txtPrecioVenta.getText().isBlank())
                    ? p.getPrecio()
                    : new BigDecimal(txtPrecioVenta.getText().trim());

            BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(cant)).setScale(2, RoundingMode.HALF_UP);
            modeloCarrito.addRow(new Object[]{ p.getId(), p.getNombre(), cant, precio, subtotal });

            txtCantidadVenta.setText("1");
            txtPrecioVenta.setText(p.getPrecio().toPlainString());
            actualizarTotalesCarrito();
        } catch (NumberFormatException ex) {
            mostrarError("Formato numérico inválido en cantidad/precio");
        }
    }

    private void quitarSeleccionCarrito() {
        int row = tablaCarrito.getSelectedRow();
        if (row != -1) {
            modeloCarrito.removeRow(row);
            actualizarTotalesCarrito();
        } else {
            mostrarAdvertencia("Selecciona una fila del carrito para quitar.");
        }
    }

    private List<LineaVenta> extraerCarritoDesdeTabla() {
        List<LineaVenta> carrito = new ArrayList<>();
        for (int i = 0; i < modeloCarrito.getRowCount(); i++) {
            String id = modeloCarrito.getValueAt(i, 0).toString();
            int cant = Integer.parseInt(modeloCarrito.getValueAt(i, 2).toString());
            BigDecimal pUnit = (BigDecimal) modeloCarrito.getValueAt(i, 3);

            ProductoComplejo p = buscarProductoPorCodigo(id);
            if (p == null) throw new IllegalArgumentException("Producto no existe: " + id);
            if (p.getStock() < cant) throw new IllegalStateException(
                    "Stock insuficiente para " + p.getNombre() + ". Disponible: " + p.getStock());

            carrito.add(new LineaVenta(id, cant, pUnit));
        }
        return carrito;
    }

    private String generarConsecutivoFactura() {
        return "F-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date())
                + "-" + String.format("%03d", (int)(Math.random()*999));
    }

    private void emitirFacturaYEncadenar() {
        try {
            // (Opcional) validar PG antes de emitir
            try {
                if (!blockchainStore.validateFromPostgres()) {
                    mostrarAdvertencia("La cadena en PostgreSQL NO es válida. Usa 'Validar/Reparar' antes de emitir.");
                    return;
                }
            } catch (Exception ex) {
                mostrarAdvertencia("No se pudo validar en PostgreSQL. Continuar bajo tu propio riesgo.");
            }

            List<LineaVenta> carrito = extraerCarritoDesdeTabla();
            if (carrito.isEmpty()) { mostrarAdvertencia("El carrito está vacío"); return; }

            String numero = txtNumeroFactura.getText().trim().isBlank() ? generarConsecutivoFactura() : txtNumeroFactura.getText().trim();
            String cliente = txtClienteNombre.getText().trim();
            String clienteId = txtClienteId.getText().trim();
            String direccion = txtClienteDireccion.getText().trim();
            String formaPago = (String) cmbFormaPago.getSelectedItem();
            String vendedor = txtUsuarioTransaccion.getText().trim();
            String sucursal = tienda.getNombre();

            // Crear factura
            Factura f = LedgerBridge.facturaDesdeCarrito(
                    numero, cliente, clienteId, direccion, formaPago, vendedor, sucursal,
                    new BigDecimal("0.13"), carrito, tienda.getProductosMap()
            );

            // --- Registrar la venta AGRUPADA (PG + memoria) ---
            List<TransaccionCompleja> txsVenta = tienda.registrarVentaAgrupada(
                    numero,    // facturaNumero
                    vendedor,  // usuario
                    carrito
            );

            // Encadenar bloque con Factura (minado + persistencia)
            int newIndex = 0;
            try {
                BlockChain chain = blockchainStore.getChain();
                newIndex = (chain != null) ? chain.getChainLength() : 0;
            } catch (Throwable ignored) {}

            String prev = blockchainStore.getLastHash();

            long t0 = System.currentTimeMillis();
            Block bloque = new Block(newIndex, prev, f);
            bloque.mineBlock(BlockChain.DIFFICULTY);
            long ms = System.currentTimeMillis() - t0;

            blockchainStore.appendBlock(bloque);
            blockchainStore.appendBlockAndPersist(true); // backup JSON local

            // Limpieza UI
            BigDecimal totalVenta = txsVenta.stream().map(TransaccionCompleja::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            mostrarInfo("✅ Factura emitida"
                    + "\nN°: " + f.getNumero()
                    + "\nTotal: $" + f.getTotal()
                    + "\n(Transacciones registradas: " + txsVenta.size() + ", total venta $" + totalVenta + ")"
                    + "\nHash: " + bloque.getHash()
                    + "\nNonce: " + bloque.getNonce()
                    + "\nMinado en: " + ms + " ms (dificultad=" + BlockChain.DIFFICULTY + ")"
            );
            modeloCarrito.setRowCount(0);
            actualizarTotalesCarrito();
            txtNumeroFactura.setText(generarConsecutivoFactura());
            actualizarTodo();

            pintarBlockchainEnArea();
        } catch (Exception e) {
            mostrarError("No se pudo emitir la factura: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void actualizarTotalesCarrito() {
        BigDecimal sub = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (int i = 0; i < modeloCarrito.getRowCount(); i++) {
            BigDecimal s = (BigDecimal) modeloCarrito.getValueAt(i, 4);
            if (s != null) sub = sub.add(s);
        }
        BigDecimal iva = sub.multiply(new BigDecimal("0.13")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = sub.add(iva).setScale(2, RoundingMode.HALF_UP);

        lblSubtotal.setText(String.format("$%.2f", sub));
        lblImpuesto.setText(String.format("$%.2f", iva));
        lblTotal.setText(String.format("$%.2f", total));
    }

    // ==================== Blockchain ====================

    private void cargarBlockchainSiExiste() {
        try {
            blockchainStore.loadFromPostgresIfAny();
            if (!blockchainStore.getChain().isValid()) {
                System.err.println("La cadena cargó desde PG pero NO es válida localmente. Usa 'Validar Blockchain' o 'Validar en PostgreSQL'.");
            }
        } catch (Exception e) {
            System.err.println("No se pudo cargar blockchain desde PostgreSQL: " + e.getMessage());
        }
    }

    private void validarBlockchain() {
        try {
            BlockChain chain = blockchainStore.getChain();
            boolean valida = chain.isValid();

            if (valida) {
                mostrarInfo("✓ La blockchain (en memoria) es válida\nLongitud: " + chain.getChainLength() + " bloques");
            } else {
                mostrarAdvertencia("⚠ La blockchain en memoria ha sido alterada o es inválida");
            }
        } catch (Exception e) {
            mostrarError("Error al validar blockchain: " + e.getMessage());
        }
    }

    private void mostrarBlockchain() {
        try {
            BlockChain chain = blockchainStore.getChain();
            StringBuilder sb = new StringBuilder("=== CADENA DE FACTURAS ===\n");
            int idx = 0;
            for (Block b : chain.getChain()) {
                Factura f = b.getFactura();
                if (f == null) {
                    sb.append("\n[GÉNESIS] #").append(idx).append(" hash=").append(b.getHash()).append("\n");
                } else {
                    sb.append("\n#").append(b.index)
                            .append("  Factura ").append(f.getNumero())
                            .append("  Total $").append(f.getTotal())
                            .append("\n   prev=").append(b.previousHash)
                            .append("\n   hash=").append(b.getHash())
                            .append("\n");
                }
                idx++;
            }

            JTextArea textArea = new JTextArea(sb.toString(), 30, 90);
            textArea.setEditable(false);
            textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            JOptionPane.showMessageDialog(this, new JScrollPane(textArea), "Blockchain", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            mostrarError("Error al mostrar blockchain: " + e.getMessage());
        }
    }

    // === Guardar/Cargar asimétrico (PEM) [backups locales cifrados] ===
    private void guardarBlockchainAsim() {
        try {
            JFileChooser fcPub = new JFileChooser();
            fcPub.setDialogTitle("Seleccione la clave pública (PEM)");
            if (fcPub.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path publicPem = fcPub.getSelectedFile().toPath();

            JFileChooser fcOut = new JFileChooser();
            fcOut.setDialogTitle("Guardar blockchain cifrada");
            fcOut.setSelectedFile(new File("blockchain.enc"));
            if (fcOut.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path outFile = fcOut.getSelectedFile().toPath();

            blockchainStore.saveEncryptedAsymmetric(publicPem, outFile);
            mostrarInfo("Blockchain guardada cifrada en:\n" + outFile.toAbsolutePath());
        } catch (Exception ex) {
            ex.printStackTrace();
            mostrarError("No se pudo guardar (PEM): " + ex.getMessage());
        }
    }

    private void cargarBlockchainAsim() {
        try {
            JFileChooser fcPriv = new JFileChooser();
            fcPriv.setDialogTitle("Seleccione la clave privada (PEM PKCS#8)");
            if (fcPriv.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path privatePem = fcPriv.getSelectedFile().toPath();

            JFileChooser fcIn = new JFileChooser();
            fcIn.setDialogTitle("Seleccione el archivo .enc de blockchain");
            if (fcIn.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path inFile = fcIn.getSelectedFile().toPath();

            blockchainStore.loadEncryptedAsymmetric(privatePem, inFile);
            actualizarTodo();
            mostrarInfo("Blockchain cargada y descifrada correctamente (en memoria).");
        } catch (Exception ex) {
            ex.printStackTrace();
            mostrarError("No se pudo cargar (PEM): " + ex.getMessage());
        }
    }

    // ==================== Archivo (inventario) ====================

    private void guardarCifrado() {
        try {
            String password = JOptionPane.showInputDialog(this, "Ingrese contraseña para cifrar:", "supertech-pass");
            if (password == null || password.isEmpty()) return;

            SecretKey key = Cifrado.deriveKey(password);
            String json = tienda.toJSON();
            String cifrado = Cifrado.cifrar(json, key);

            Files.writeString(Path.of("tienda.enc"), cifrado);
            mostrarInfo("Inventario guardado y cifrado en tienda.enc");
        } catch (Exception e) {
            mostrarError("Error al guardar: " + e.getMessage());
        }
    }

    private void cargarCifrado() {
        try {
            String password = JOptionPane.showInputDialog(this, "Ingrese contraseña para descifrar:", "supertech-pass");
            if (password == null || password.isEmpty()) return;

            SecretKey key = Cifrado.deriveKey(password);
            String cifrado = Files.readString(Path.of("tienda.enc"));
            String json = Cifrado.descifrar(cifrado, key);

            JTextArea textArea = new JTextArea(json, 30, 80);
            textArea.setEditable(false);
            textArea.setLineWrap(true);

            JScrollPane scrollPane = new JScrollPane(textArea);
            JOptionPane.showMessageDialog(this, scrollPane, "JSON Descifrado", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            mostrarAdvertencia("No existe el archivo tienda.enc");
        } catch (Exception e) {
            mostrarError("Error al cargar: " + e.getMessage());
        }
    }

    private void exportarJSONCifrado() {
        try {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Guardar JSON Cifrado");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            fileChooser.setSelectedFile(new File("inventario_" + sdf.format(new Date()) + ".json"));

            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();

                String password = JOptionPane.showInputDialog(this, "Contraseña:", "MySecretKey12345");
                if (password == null) return;

                String json = tienda.toJSON();
                String cifrado = EncriptacionUtil.encriptar(json);

                try (PrintWriter writer = new PrintWriter(file)) {
                    writer.write("# INVENTARIO JSON CIFRADO - " + new Date() + "\n");
                    writer.write("# Clave: MySecretKey12345\n");
                    writer.write("ENCRYPTED_DATA:\n");
                    writer.write(cifrado);
                }

                mostrarInfo("Archivo guardado exitosamente en:\n" + file.getAbsolutePath());
            }
        } catch (Exception e) {
            mostrarError("Error al exportar: " + e.getMessage());
        }
    }

    private String generarHTMLCompleto() {
        StringBuilder html = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

        int totalProductos = 0;
        int totalTransacciones = 0;
        double valorTotal = 0;

        for (Categoria cat : tienda.getCategorias()) {
            totalProductos += cat.contarProductos();
            valorTotal += cat.calcularValorCategoria();
            for (ProductoComplejo p : cat.getProductos()) {
                totalTransacciones += p.getTransacciones().size();
            }
        }

        html.append("<!DOCTYPE html>\n<html lang='es'>\n<head>\n")
                .append("<meta charset='UTF-8'>\n")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1'>\n")
                .append("<title>Reporte - ").append(tienda.getNombre()).append("</title>\n")
                .append("<style>\n")
                .append("body{font:14px/1.6 system-ui;background:#f5f5f5;margin:0;padding:20px}\n")
                .append(".container{max-width:1200px;margin:0 auto;background:#fff;padding:30px;border-radius:8px;box-shadow:0 2px 10px rgba(0,0,0,.1)}\n")
                .append("h1{color:#2563eb;margin:0 0 10px}\n")
                .append(".header{border-bottom:2px solid #2563eb;padding-bottom:15px;margin-bottom:25px}\n")
                .append(".stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:15px;margin:20px 0}\n")
                .append(".stat{background:#f8fafc;padding:15px;border-radius:6px;border-left:4px solid #2563eb}\n")
                .append(".stat-label{font-size:12px;color:#64748b;margin-bottom:5px}\n")
                .append(".stat-value{font-size:24px;font-weight:700;color:#0f172a}\n")
                .append("table{width:100%;border-collapse:collapse;margin:20px 0}\n")
                .append("th{background:#2563eb;color:#fff;padding:12px;text-align:left;font-weight:600}\n")
                .append("td{padding:10px;border-bottom:1px solid #e2e8f0}\n")
                .append("tr:hover{background:#f8fafc}\n")
                .append(".category{margin:30px 0;padding:20px;background:#f8fafc;border-radius:8px}\n")
                .append(".category h2{color:#1e40af;margin:0 0 15px}\n")
                .append(".badge{display:inline-block;padding:4px 10px;border-radius:12px;font-size:11px;font-weight:600}\n")
                .append(".badge-ok{background:#dcfce7;color:#166534}\n")
                .append(".badge-warn{background:#fef3c7;color:#92400e}\n")
                .append("</style>\n</head>\n<body>\n<div class='container'>\n");

        html.append("<div class='header'>")
                .append("<h1>Reporte de Inventario</h1>")
                .append("<p>").append(tienda.getNombre()).append(" | ")
                .append(tienda.getDireccion()).append(" | Tel: ").append(tienda.getTelefono())
                .append("<br>Generado: ").append(sdf.format(new Date())).append("</p>")
                .append("</div>");

        html.append("<div class='stats'>")
                .append("<div class='stat'><div class='stat-label'>Categorías</div><div class='stat-value'>")
                .append(tienda.getCategorias().size()).append("</div></div>")
                .append("<div class='stat'><div class='stat-label'>Productos</div><div class='stat-value'>")
                .append(totalProductos).append("</div></div>")
                .append("<div class='stat'><div class='stat-label'>Transacciones</div><div class='stat-value'>")
                .append(totalTransacciones).append("</div></div>")
                .append("<div class='stat'><div class='stat-label'>Valor Total</div><div class='stat-value'>$")
                .append(String.format("%.2f", valorTotal)).append("</div></div>")
                .append("</div>");

        for (Categoria cat : tienda.getCategorias()) {
            html.append("<div class='category'>")
                    .append("<h2>").append(cat.getNombre()).append("</h2>")
                    .append("<p><strong>Valor de categoría:</strong> $")
                    .append(String.format("%.2f", cat.calcularValorCategoria())).append("</p>");

            if (!cat.getProductos().isEmpty()) {
                html.append("<table>")
                        .append("<thead><tr>")
                        .append("<th>Código</th><th>Producto</th><th>Precio</th><th>Stock</th>")
                        .append("<th>Valor</th><th>Estado</th>")
                        .append("</tr></thead><tbody>");

                for (ProductoComplejo p : cat.getProductos()) {
                    boolean bajoStock = p.requiereReposicion();
                    html.append("<tr>")
                            .append("<td>").append(p.getId()).append("</td>")
                            .append("<td>").append(p.getNombre()).append("</td>")
                            .append("<td>$").append(String.format("%.2f", p.getPrecio().doubleValue())).append("</td>")
                            .append("<td>").append(p.getStock()).append("</td>")
                            .append("<td>$").append(String.format("%.2f", p.getValorInventario().doubleValue())).append("</td>")
                            .append("<td><span class='badge ").append(bajoStock ? "badge-warn" : "badge-ok").append("'>")
                            .append(bajoStock ? "BAJO STOCK" : "OK").append("</span></td>")
                            .append("</tr>");
                }

                html.append("</tbody></table>");
            }
            html.append("</div>");
        }

        html.append("<div style='margin-top:30px;text-align:center;color:#64748b;font-size:12px'>")
                .append("Generado automáticamente por Sistema de Inventario | ")
                .append(sdf.format(new Date()))
                .append("</div>");

        html.append("</div></body></html>");
        return html.toString();
    }

    // ==================== Actualizaciones UI ====================

    private void actualizarTodo() {
        actualizarTablaCategorias();
        actualizarTablaProductos();
        actualizarTablaTransacciones();
        actualizarCombosCategorias();
        actualizarCombosProductos(); // combo de Ventas
        actualizarResumen();
    }

    private void actualizarTablaCategorias() {
        modeloCategorias.setRowCount(0);
        for (Categoria cat : tienda.getCategorias()) {
            modeloCategorias.addRow(new Object[]{
                    cat.getId(),
                    cat.getNombre(),
                    cat.contarProductos(),
                    String.format("$%.2f", cat.calcularValorCategoria())
            });
        }
    }

    private void actualizarTablaProductos() {
        modeloProductos.setRowCount(0);
        for (Categoria cat : tienda.getCategorias()) {
            for (ProductoComplejo prod : cat.getProductos()) {
                modeloProductos.addRow(new Object[]{
                        prod.getId(),
                        prod.getNombre(),
                        String.format("$%.2f", prod.getPrecio().doubleValue()),
                        prod.getStock(),
                        cat.getNombre(),
                        String.format("$%.2f", prod.getValorInventario().doubleValue())
                });
            }
        }
    }

    // Junta transacciones recorriendo productos si la lista central está vacía
    private List<TransaccionCompleja> snapshotTransaccionesDesdeProductos() {
        List<TransaccionCompleja> out = new ArrayList<>();
        for (Categoria cat : tienda.getCategorias()) {
            for (ProductoComplejo p : cat.getProductos()) {
                List<TransaccionCompleja> tp = p.getTransacciones();
                if (tp != null && !tp.isEmpty()) out.addAll(tp);
            }
        }
        try {
            out.sort(Comparator.comparing(TransaccionCompleja::getFechaHora));
        } catch (Throwable ignored) {}
        return out;
    }

    // Filtros aplicados a la lista de transacciones
    private boolean pasaFiltros(TransaccionCompleja t, String q, String tipo, Date dDesde, Date dHasta) {
        // filtro por producto/código
        if (q != null && !q.isBlank()) {
            String ql = q.trim().toLowerCase(Locale.ROOT);
            String codigo = String.valueOf(t.getProductoId());
            ProductoComplejo p = buscarProductoPorCodigo(codigo);
            String nombre = (p != null ? p.getNombre() : "");
            String comp = (codigo + " " + nombre).toLowerCase(Locale.ROOT);
            if (!comp.contains(ql)) return false;
        }
        // filtro por tipo
        if (!"TODOS".equalsIgnoreCase(tipo)) {
            String tt = t.getTipoString();
            if (tt == null || !tt.equalsIgnoreCase(tipo)) return false;
        }
        // filtro por fechas
        Date f = null;
        try { f = t.getFechaHora(); } catch (Throwable ignored) {}
        if (dDesde != null && f != null && f.before(dDesde)) return false;
        if (dHasta != null && f != null && f.after(dHasta)) return false;

        return true;
    }

    private Date parseFechaYmd(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(s.trim());
        } catch (ParseException e) {
            return null;
        }
    }

    // Carga SIEMPRE TODAS las transacciones, aplicando filtros si están llenos
    private void actualizarTablaTransacciones() {
        modeloTransacciones.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        // 1) fuente
        List<TransaccionCompleja> txs = tienda.getTransacciones();
        if (txs == null || txs.isEmpty()) txs = snapshotTransaccionesDesdeProductos();

        // 2) filtros
        String q = (txtFiltroProducto != null) ? txtFiltroProducto.getText() : "";
        String tipo = (cmbFiltroTipo != null && cmbFiltroTipo.getSelectedItem() != null)
                ? cmbFiltroTipo.getSelectedItem().toString()
                : "TODOS";
        Date dDesde = parseFechaYmd(txtFechaDesde != null ? txtFechaDesde.getText() : null);
        Date dHasta = parseFechaYmd(txtFechaHasta != null ? txtFechaHasta.getText() : null);

        for (TransaccionCompleja t : txs) {
            if (t == null) continue;
            if (!pasaFiltros(t, q, tipo, dDesde, dHasta)) continue;

            ProductoComplejo p = buscarProductoPorCodigo(t.getProductoId());
            String nombreProd = (p != null ? p.getNombre() : t.getProductoId());

            BigDecimal total;
            try {
                total = (t.getTotal() != null)
                        ? t.getTotal()
                        : t.getPrecioUnit().multiply(BigDecimal.valueOf(t.getCantidad())).setScale(2, RoundingMode.HALF_UP);
            } catch (Throwable ex) {
                total = BigDecimal.ZERO;
            }

            String fechaTxt;
            try {
                fechaTxt = sdf.format(t.getFechaHora());
            } catch (Throwable ex) {
                fechaTxt = "";
            }

            modeloTransacciones.addRow(new Object[]{
                    t.getId(),
                    nombreProd,
                    t.getTipoString(),
                    t.getCantidad(),
                    String.format("$%.2f", total.doubleValue()),
                    t.getUsuario(),
                    fechaTxt
            });
        }
    }

    private void actualizarCombosCategorias() {
        if (cmbCategorias != null) {
            cmbCategorias.removeAllItems();
            for (Categoria cat : tienda.getCategorias()) {
                cmbCategorias.addItem(cat.getNombre());
            }
        }
    }

    // Solo carga el combo de productos para Ventas
    private void actualizarCombosProductos() {
        if (cmbProductosVenta != null) {
            cmbProductosVenta.removeAllItems();
            for (Categoria cat : tienda.getCategorias()) {
                for (ProductoComplejo prod : cat.getProductos()) {
                    cmbProductosVenta.addItem(prod.getId() + " - " + prod.getNombre());
                }
            }
        }
    }

    private void actualizarResumen() {
        StringBuilder resumen = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

        resumen.append("=== SISTEMA DE INVENTARIO ===\n");
        resumen.append("Tienda: ").append(tienda.getNombre()).append("\n");
        resumen.append("ID: ").append(tienda.getId()).append("\n");
        resumen.append("Dirección: ").append(tienda.getDireccion()).append("\n");
        resumen.append("Teléfono: ").append(tienda.getTelefono()).append("\n");
        resumen.append("Última actualización: ").append(sdf.format(new Date())).append("\n\n");

        resumen.append("ESTADÍSTICAS GENERALES:\n");
        resumen.append("- Categorías: ").append(tienda.getCategorias().size()).append("\n");

        int totalProductos = 0;
        int totalTransacciones = 0;
        double valorTotal = 0;
        int productosConBajoStock = 0;

        for (Categoria cat : tienda.getCategorias()) {
            totalProductos += cat.contarProductos();
            valorTotal += cat.calcularValorCategoria();

            for (ProductoComplejo prod : cat.getProductos()) {
                totalTransacciones += prod.getTransacciones().size();
                if (prod.requiereReposicion()) productosConBajoStock++;
            }
        }

        resumen.append("- Total de productos: ").append(totalProductos).append("\n");
        resumen.append("- Total de transacciones: ").append(totalTransacciones).append("\n");
        resumen.append("- Valor total del inventario: $").append(String.format("%.2f", valorTotal)).append("\n");
        resumen.append("- Productos que requieren reposición: ").append(productosConBajoStock).append("\n\n");

        resumen.append("RESUMEN POR CATEGORÍAS:\n");
        for (Categoria cat : tienda.getCategorias()) {
            resumen.append("• ").append(cat.getNombre()).append(": ")
                    .append(cat.contarProductos()).append(" productos, ")
                    .append("Valor: $").append(String.format("%.2f", cat.calcularValorCategoria())).append("\n");
        }

        if (productosConBajoStock > 0) {
            resumen.append("\n⚠️ ALERTAS DE STOCK BAJO:\n");
            for (Categoria cat : tienda.getCategorias()) {
                for (ProductoComplejo prod : cat.getProductos()) {
                    if (prod.requiereReposicion()) {
                        resumen.append("- ").append(prod.getNombre())
                                .append(" (").append(prod.getId()).append("): ")
                                .append("Stock actual: ").append(prod.getStock()).append("\n");
                    }
                }
            }
        }

        areaResumen.setText(resumen.toString());
        areaResumen.setCaretPosition(0);
    }

    private void generarReporteHTML() {
        try {
            String html = generarHTMLCompleto();

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Guardar Reporte HTML");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            fileChooser.setSelectedFile(new File("reporte_" + sdf.format(new Date()) + ".html"));

            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                if (!file.getName().endsWith(".html")) {
                    file = new File(file.getAbsolutePath() + ".html");
                }

                try (PrintWriter writer = new PrintWriter(file, "UTF-8")) {
                    writer.write(html);
                }

                mostrarInfo("Reporte HTML generado en:\n" + file.getAbsolutePath());
            }
        } catch (Exception e) {
            mostrarError("Error al generar HTML: " + e.getMessage());
        }
    }

    // ==================== Selección ====================

    private void seleccionarCategoria() {
        int fila = tablaCategorias.getSelectedRow();
        if (fila != -1) {
            String id = (String) modeloCategorias.getValueAt(fila, 0);
            categoriaSeleccionada = buscarCategoriaPorId(id);
        }
    }

    private void seleccionarProducto() {
        int fila = tablaProductos.getSelectedRow();
        if (fila != -1) {
            String codigo = (String) modeloProductos.getValueAt(fila, 0);
            productoSeleccionado = buscarProductoPorCodigo(codigo);
        }
    }

    // ==================== Búsquedas ====================

    private Categoria buscarCategoriaPorNombre(String nombre) {
        for (Categoria cat : tienda.getCategorias()) {
            if (cat.getNombre().equals(nombre)) return cat;
        }
        return null;
    }

    private Categoria buscarCategoriaPorId(String id) {
        for (Categoria cat : tienda.getCategorias()) {
            if (cat.getId().equals(id)) return cat;
        }
        return null;
    }

    private ProductoComplejo buscarProductoPorCodigo(String codigo) {
        for (Categoria cat : tienda.getCategorias()) {
            for (ProductoComplejo prod : cat.getProductos()) {
                if (prod.getId().equals(codigo)) return prod;
            }
        }
        return null;
    }
    
        private void desencriptarJSON() {
        try {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Abrir JSON Cifrado");
            fileChooser.setFileFilter(new FileNameExtensionFilter("JSON", "json"));

            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                StringBuilder contenido = new StringBuilder();

                try (java.util.Scanner scanner = new java.util.Scanner(file)) {
                    boolean encontrado = false;
                    while (scanner.hasNextLine()) {
                        String linea = scanner.nextLine();
                        if (linea.equals("ENCRYPTED_DATA:")) {
                            encontrado = true;
                            continue;
                        }
                        if (encontrado) contenido.append(linea);
                    }
                }

                String descifrado = EncriptacionUtil.desencriptar(contenido.toString());
                areaResumen.setText("=== JSON DESCIFRADO ===\n\n" + descifrado);
                mostrarInfo("JSON descifrado correctamente");
            }
        } catch (Exception e) {
            mostrarError("Error al desencriptar: " + e.getMessage());
        }
    }

    // ==================== Limpieza ====================

    private void limpiarCamposCategoria() {
        txtIdCategoria.setText("");
        txtNombreCategoria.setText("");
    }

    private void limpiarCamposProducto() {
        txtCodigoProducto.setText("");
        txtNombreProducto.setText("");
        txtPrecioProducto.setText("");
        txtStockProducto.setText("");
    }

    private void limpiarCamposTransaccion() {
        if (txtCantidadTransaccion != null) txtCantidadTransaccion.setText("");
        if (txtDescripcionTransaccion != null) txtDescripcionTransaccion.setText("");
    }

    // ==================== Mensajes ====================

    private void mostrarInfo(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Información", JOptionPane.INFORMATION_MESSAGE);
    }

    private void mostrarAdvertencia(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Advertencia", JOptionPane.WARNING_MESSAGE);
    }

    private void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // ==================== PINTAR BLOCKCHAIN EN EL TAB ====================

    private void pintarBlockchainEnArea() {
        if (areaBlockchain == null) return;

        StringBuilder sb = new StringBuilder();
        int i = 0;

        for (Block b : blockchainStore.getChain().getChain()) {
            sb.append("============================================================================\n\n");
            sb.append("Bloque #").append(i).append("\n");

            String prev = (i == 0) ? "0" : nullSafe(b.previousHash);
            if (prev == null || prev.isBlank()) prev = "0";
            sb.append("prevHash : ").append(prev).append("\n");

            sb.append("hash     : ").append(nullSafe(b.getHash())).append("\n");

            try { sb.append("nonce    : ").append(b.getNonce()).append("\n"); } catch (Throwable ignored) {}

            Factura f = b.getFactura();
            if (f == null) {
                sb.append("GÉNESIS\n\n");
                i++;
                continue;
            }

            sb.append("Factura  : ").append(nullSafe(f.getNumero())).append("\n");
            try {
                sb.append("Cliente  : ").append(nullSafe(f.getCliente()))
                        .append(" (").append(nullSafe(f.getClienteNit())).append(")\n");
                sb.append("Dirección: ").append(nullSafe(f.getDireccion())).append("\n");
                sb.append("Pago     : ").append(nullSafe(f.getFormaPago()))
                        .append(" | Vendedor: ").append(nullSafe(f.getVendedor()))
                        .append(" | Tienda: ").append(nullSafe(f.getSucursal())).append("\n");
            } catch (Throwable ignored) {}

            sb.append("Items:\n");
            for (FacturaItem it : f.getItems()) {
                sb.append(String.format(
                        "  - %s x%d @ $%.2f => $%.2f\n",
                        nullSafe(it.getDescripcion()),
                        it.getCantidad(),
                        it.getPrecioUnitario().doubleValue(),
                        it.getSubtotal().doubleValue()
                ));
            }

            try {
                sb.append(String.format(
                        "Subtotal: $%.2f   IVA: $%.2f   TOTAL: $%.2f\n\n",
                        f.getSubtotal().doubleValue(),
                        f.getImpuesto().doubleValue(),
                        f.getTotal().doubleValue()
                ));
            } catch (Throwable e) {
                sb.append(String.format(
                        "Subtotal: $%.2f   IVA: $%.2f   TOTAL: $%.2f\n\n",
                        f.getSubTotal().doubleValue(),
                        f.getImpuesto().doubleValue(),
                        f.getTotal().doubleValue()
                ));
            }

            i++;
        }

        areaBlockchain.setText(sb.toString());
        areaBlockchain.setCaretPosition(0);

        // Estado al pie
        try {
            boolean ok = blockchainStore.getChain().isValid();
            String estado = (ok ? "VÁLIDA" : "INVÁLIDA");
            lblEstadoCadena.setText("Cadena: " + estado
                    + " | Dificultad: " + BlockChain.DIFFICULTY
                    + " | Bloques: " + blockchainStore.getChain().getChainLength());
        } catch (Throwable ignored) {
            lblEstadoCadena.setText(" ");
        }
    }

    private static String nullSafe(Object o) { return (o == null) ? "" : o.toString(); }

    // ==================== MAIN ====================

    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> new InventarioCompletoGUI().setVisible(true));
    }
}
