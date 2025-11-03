package UI;

import App.AppContext;
import Model.Categoria;
import Model.ProductoComplejo;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ReportesPanel extends JPanel implements RefreshableView {

    private final AppContext ctx;
    private JTextArea area;
    private JButton btnGenerarHtml;

    public ReportesPanel(AppContext ctx) {
        this.ctx = ctx;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(10,10));
        setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnGenerarHtml = new JButton("Generar Reporte HTML");
        btnGenerarHtml.addActionListener(e -> onGenerarHTML());
        top.add(btnGenerarHtml);

        area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(area), BorderLayout.CENTER);
    }

    @Override
    public void refreshAll() {

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

        sb.append("=== SISTEMA DE INVENTARIO ===\n");
        sb.append("Tienda: ").append(ctx.tienda.getNombre()).append("\n");
        sb.append("ID: ").append(ctx.tienda.getId()).append("\n");
        sb.append("Dirección: ").append(ctx.tienda.getDireccion()).append("\n");
        sb.append("Teléfono: ").append(ctx.tienda.getTelefono()).append("\n");
        sb.append("Última actualización: ").append(sdf.format(new Date())).append("\n\n");

        sb.append("ESTADÍSTICAS GENERALES:\n");
        sb.append("- Categorías: ").append(ctx.tienda.getCategorias().size()).append("\n");

        int totalProductos = 0;
        int totalTransacciones = 0;
        double valorTotal = 0;
        int productosConBajoStock = 0;

        for (Categoria cat : ctx.tienda.getCategorias()) {
            totalProductos += cat.contarProductos();
            valorTotal += cat.calcularValorCategoria();
            for (ProductoComplejo prod : cat.getProductos()) {
                totalTransacciones += prod.getTransacciones().size();
                if (prod.requiereReposicion()) productosConBajoStock++;
            }
        }

        sb.append("- Total de productos: ").append(totalProductos).append("\n");
        sb.append("- Total de transacciones: ").append(totalTransacciones).append("\n");
        sb.append("- Valor total del inventario: $").append(String.format("%.2f", valorTotal)).append("\n");
        sb.append("- Productos que requieren reposición: ").append(productosConBajoStock).append("\n\n");

        sb.append("RESUMEN POR CATEGORÍAS:\n");
        for (Categoria cat : ctx.tienda.getCategorias()) {
            sb.append("• ").append(cat.getNombre()).append(": ")
              .append(cat.contarProductos()).append(" productos, ")
              .append("Valor: $").append(String.format("%.2f", cat.calcularValorCategoria())).append("\n");
        }

        if (productosConBajoStock > 0) {
            sb.append("\n⚠️ ALERTAS DE STOCK BAJO:\n");
            for (Categoria cat : ctx.tienda.getCategorias()) {
                for (ProductoComplejo prod : cat.getProductos()) {
                    if (prod.requiereReposicion()) {
                        sb.append("- ").append(prod.getNombre())
                          .append(" (").append(prod.getId()).append("): ")
                          .append("Stock actual: ").append(prod.getStock()).append("\n");
                    }
                }
            }
        }

        area.setText(sb.toString());
        area.setCaretPosition(0);
    }

    private void onGenerarHTML() {
        try {
            String html = buildHTMLProfesional();

            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Guardar Reporte HTML");
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            fc.setSelectedFile(new java.io.File("reporte_" + stamp + ".html"));

            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                var file = fc.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".html")) {
                    file = new java.io.File(file.getAbsolutePath() + ".html");
                }
                try (PrintWriter pw = new PrintWriter(file, "UTF-8")) {
                    pw.write(html);
                }
                JOptionPane.showMessageDialog(this, "Reporte HTML generado en:\n" + file.getAbsolutePath(),
                        "Reporte", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al generar HTML: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    private String buildHTMLProfesional() {
        var sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

        int totalProductos = 0;
        int totalTransacciones = 0;
        double valorTotal = 0;
        int bajoStockCount = 0;

        for (Categoria cat : ctx.tienda.getCategorias()) {
            totalProductos += cat.contarProductos();
            valorTotal += cat.calcularValorCategoria();
            for (ProductoComplejo p : cat.getProductos()) {
                totalTransacciones += p.getTransacciones().size();
                if (p.requiereReposicion()) bajoStockCount++;
            }
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang='es'>\n<head>\n")
            .append("<meta charset='UTF-8'>\n")
            .append("<meta name='viewport' content='width=device-width, initial-scale=1'>\n")
            .append("<title>Reporte de Inventario - ").append(escape(ctx.tienda.getNombre())).append("</title>\n")
            .append("<link rel='preconnect' href='https://fonts.googleapis.com'>\n")
            .append("<link rel='preconnect' href='https://fonts.gstatic.com' crossorigin>\n")
            .append("<link href='https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&display=swap' rel='stylesheet'>\n")
            .append("<style>\n")
            .append(":root{--bg:#f6f7fb;--card:#ffffff;--ink:#0f172a;--muted:#64748b;--brand:#2563eb;--ok:#16a34a;--warn:#b45309;--border:#e5e7eb}\n")
            .append("@media (prefers-color-scheme: dark){:root{--bg:#0b1020;--card:#121735;--ink:#e5e7eb;--muted:#94a3b8;--brand:#60a5fa;--ok:#22c55e;--warn:#f59e0b;--border:#1f2a44}}\n")
            .append("body{margin:0;background:var(--bg);color:var(--ink);font:14px/1.6 'Inter',system-ui,sans-serif}\n")
            .append(".container{max-width:1200px;margin:0 auto;padding:28px}\n")
            .append(".header{background:linear-gradient(135deg, rgba(37,99,235,.12), rgba(99,102,241,.12));border:1px solid var(--border);border-radius:16px;padding:24px 20px;margin-bottom:24px}\n")
            .append(".title{margin:0;color:var(--brand);font-weight:800;letter-spacing:.3px}\n")
            .append(".muted{color:var(--muted)}\n")
            .append(".grid{display:grid;gap:14px;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));margin:18px 0}\n")
            .append(".stat{background:var(--card);border:1px solid var(--border);border-radius:14px;padding:16px}\n")
            .append(".stat .label{font-size:12px;color:var(--muted)}\n")
            .append(".stat .value{font-size:26px;font-weight:700;margin-top:6px}\n")
            .append(".section{margin:26px 0}\n")
            .append(".card{background:var(--card);border:1px solid var(--border);border-radius:16px;padding:16px}\n")
            .append(".category{margin-top:16px}\n")
            .append("table{width:100%;border-collapse:collapse;margin-top:10px;font-size:13px}\n")
            .append("th,td{padding:10px;border-bottom:1px solid var(--border)}\n")
            .append("th{background:rgba(37,99,235,.10);text-align:left}\n")
            .append("tr:hover td{background:rgba(37,99,235,.04)}\n")
            .append(".badge{display:inline-block;padding:4px 10px;border-radius:999px;font-size:11px;font-weight:700}\n")
            .append(".ok{background:rgba(34,197,94,.12);color:var(--ok)}\n")
            .append(".warn{background:rgba(245,158,11,.12);color:var(--warn)}\n")
            .append(".footer{margin:30px 0;color:var(--muted);text-align:center;font-size:12px}\n")
            .append("</style>\n</head>\n<body>\n<div class='container'>\n");


        html.append("<div class='header'>")
            .append("<h1 class='title'>Reporte de Inventario</h1>")
            .append("<p class='muted'>")
            .append(escape(ctx.tienda.getNombre())).append(" &middot; ")
            .append(escape(ctx.tienda.getDireccion())).append(" &middot; Tel: ")
            .append(escape(ctx.tienda.getTelefono()))
            .append("<br>Generado: ").append(sdf.format(new Date())).append("</p>")
            .append("</div>");


        html.append("<div class='grid'>")
            .append(tile("Categorías", String.valueOf(ctx.tienda.getCategorias().size())))
            .append(tile("Productos", String.valueOf(totalProductos)))
            .append(tile("Transacciones", String.valueOf(totalTransacciones)))
            .append(tile("Valor total", "$" + String.format("%.2f", valorTotal)))
            .append(tile("Bajo stock", String.valueOf(bajoStockCount)))
            .append("</div>");


        html.append("<div class='section'>");
        for (Categoria cat : ctx.tienda.getCategorias()) {
            double valCat = cat.calcularValorCategoria();

            html.append("<div class='card category'>")
                .append("<h3 style='margin:0 0 6px 0'>").append(escape(cat.getNombre())).append("</h3>")
                .append("<div class='muted' style='margin-bottom:10px'>Valor de categoría: $")
                .append(String.format("%.2f", valCat)).append("</div>");

            if (!cat.getProductos().isEmpty()) {
                html.append("<table><thead><tr>")
                    .append("<th>Código</th><th>Producto</th><th>Precio</th><th>Stock</th><th>Valor</th><th>Estado</th>")
                    .append("</tr></thead><tbody>");
                for (ProductoComplejo p : cat.getProductos()) {
                    boolean bajo = p.requiereReposicion();
                    html.append("<tr>")
                        .append(td(escape(p.getId())))
                        .append(td(escape(p.getNombre())))
                        .append(td("$" + String.format("%.2f", p.getPrecio().doubleValue())))
                        .append(td(String.valueOf(p.getStock())))
                        .append(td("$" + String.format("%.2f", p.getValorInventario().doubleValue())))
                        .append("<td><span class='badge ").append(bajo ? "warn" : "ok").append("'>")
                        .append(bajo ? "BAJO STOCK" : "OK").append("</span></td>")
                        .append("</tr>");
                }
                html.append("</tbody></table>");
            } else {
                html.append("<div class='muted'>No hay productos en esta categoría.</div>");
            }
            html.append("</div>");
        }
        html.append("</div>");

        html.append("<div class='footer'>Generado automáticamente por Sistema de Inventario &middot; ")
            .append(sdf.format(new Date()))
            .append("</div>");

        html.append("</div></body></html>");
        return html.toString();
    }

    private static String tile(String label, String value) {
        return "<div class='stat'><div class='label'>" + escape(label) + "</div><div class='value'>" + escape(value) + "</div></div>";
    }
    private static String td(String s) { return "<td>" + s + "</td>"; }
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&#39;");
    }
}
