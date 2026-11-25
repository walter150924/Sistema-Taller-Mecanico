package com.taller.mecanico.controller;

import atlantafx.base.theme.Styles;
import com.taller.mecanico.model.Repuesto;
import com.taller.mecanico.model.Venta;
import com.taller.mecanico.service.ComprobantePdfService;
import com.taller.mecanico.service.DashboardService;
import com.taller.mecanico.service.OrdenServicioService;
import com.taller.mecanico.service.OrdenServicioService.ResumenTaller;
import com.taller.mecanico.service.Periodo;
import com.taller.mecanico.service.ReporteExcelService;
import com.taller.mecanico.service.ReporteVentasService;
import com.taller.mecanico.service.ReporteVentasService.ClienteFrecuente;
import com.taller.mecanico.service.ReporteVentasService.RepuestoVendido;
import com.taller.mecanico.service.ReporteVentasService.PuntoSerie;
import com.taller.mecanico.service.ReporteVentasService.ResumenVentas;
import com.taller.mecanico.service.DashboardService.RangoPrecio;
import com.taller.mecanico.service.DashboardService.Resumen;
import com.taller.mecanico.service.DashboardService.SerieCategoria;
import com.taller.mecanico.ui.Exportador;
import com.taller.mecanico.ui.Refrescable;
import com.taller.mecanico.ui.Tablas;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class DashboardController implements Refrescable {
    private static final int MAX_CATEGORIAS = 6;

    private static final int MAX_BARRAS = 8;
    private static final Duration DURACION = Duration.millis(900);
    private static final Interpolator SUAVE = Interpolator.SPLINE(0.25, 0.1, 0.25, 1);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd MMM", Tablas.LOCALE);
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMM yy", Tablas.LOCALE);
    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final int MAX_TOP = 5;

    private final DashboardService service;
    private final OrdenServicioService ordenService;
    private final ReporteVentasService reporteVentas;
    private final ReporteExcelService excelService;
    private final ComprobantePdfService pdfService;
    private final Exportador exportador;
    private final MainController mainController;

    @FXML private ComboBox<Periodo> cmbPeriodo;
    @FXML private Label kpiEnTaller;
    @FXML private Label kpiListos;
    @FXML private Label kpiOrdenesMes;
    @FXML private Label kpiFacturadoTaller;
    @FXML private Label kpiVentas;
    @FXML private Label kpiVentasVariacion;
    @FXML private Label kpiVentasDetalle;
    @FXML private Label kpiComprobantes;
    @FXML private Label kpiComprobantesDetalle;
    @FXML private Label kpiTicket;
    @FXML private Label kpiTicketDetalle;
    @FXML private Label kpiIgv;
    @FXML private Label kpiIgvDetalle;
    @FXML private Label lblSerieSubtitulo;
    @FXML private AreaChart<Number, Number> graficoVentas;
    @FXML private NumberAxis ejeFechas;
    @FXML private NumberAxis ejeVentas;
    @FXML private VBox listaTopVendidos;
    @FXML private VBox listaUltimas;
    @FXML private VBox listaClientes;

    private List<String> etiquetasSerie = List.of();

    @FXML private Label lblActualizado;
    @FXML private FontIcon iconoActualizar;

    @FXML private Label kpiClientes;
    @FXML private Label kpiClientesDetalle;
    @FXML private Label kpiCategorias;
    @FXML private Label kpiCategoriasDetalle;
    @FXML private Label kpiRepuestos;
    @FXML private Label kpiRepuestosDetalle;
    @FXML private Label kpiValor;
    @FXML private Label kpiValorDetalle;

    @FXML private StackPane contenedorDona;
    @FXML private PieChart graficoCategorias;
    @FXML private Circle huecoDona;
    @FXML private Label lblTotalDona;
    @FXML private VBox leyendaDona;

    @FXML private BarChart<Number, String> graficoPromedio;
    @FXML private NumberAxis ejePromedio;
    @FXML private BarChart<String, Number> graficoRangos;
    @FXML private NumberAxis ejeRangos;

    @FXML private VBox listaTop;
    @FXML private HBox medidoresContacto;

    @FXML
    public void initialize() {
        huecoDona.radiusProperty().bind(Bindings.min(
                graficoCategorias.widthProperty(), graficoCategorias.heightProperty()).multiply(0.5 * 0.62));

        for (NumberAxis eje : List.of(ejePromedio, ejeRangos)) {
            eje.setAutoRanging(false);
            eje.setLowerBound(0);
            eje.setAnimated(false);
        }
        graficoPromedio.getYAxis().setAnimated(false);
        graficoRangos.getXAxis().setAnimated(false);
        NumberFormat enteros = NumberFormat.getIntegerInstance(Tablas.LOCALE);
        ejePromedio.setTickLabelFormatter(formato(v -> "S/ " + enteros.format(v)));

        cmbPeriodo.getItems().setAll(Periodo.values());
        cmbPeriodo.setValue(Periodo.ULTIMOS_30);
        cmbPeriodo.valueProperty().addListener((obs, a, b) -> mostrarVentas());

        for (NumberAxis eje : List.of(ejeFechas, ejeVentas)) {
            eje.setAutoRanging(false);
            eje.setAnimated(false);
        }
        ejeVentas.setLowerBound(0);
        ejeVentas.setTickLabelFormatter(formato(v -> "S/ " + enteros.format(v)));

        ejeFechas.setTickLabelFormatter(formato(v -> {
            int i = (int) Math.round(v.doubleValue());
            return Math.abs(v.doubleValue() - i) < 0.01 && i >= 0 && i < etiquetasSerie.size()
                    ? etiquetasSerie.get(i) : "";
        }));
    }

    @Override
    public void refrescar() {
        mostrarTaller(ordenService.resumen());
        Resumen r = service.obtenerResumen();
        mostrarIndicadores(r);
        mostrarDona(r);
        mostrarPromedios(r.porCategoria());
        mostrarRangos(r.rangosPrecio());
        mostrarTop(r.masCaros());
        mostrarContacto(r);
        mostrarVentas();
        lblActualizado.setText("Resumen general · actualizado a las " + LocalTime.now().format(HORA));
    }

    @FXML
    private void onActualizar() {
        RotateTransition giro = new RotateTransition(Duration.millis(600), iconoActualizar);
        giro.setByAngle(360);
        giro.setInterpolator(SUAVE);
        giro.play();
        refrescar();
    }

    @FXML
    private void onExportarExcel() {
        LocalDate hasta = LocalDate.now();
        LocalDate desde = cmbPeriodo.getValue().desde(hasta);
        exportador.guardar(cmbPeriodo.getScene().getWindow(), "Exportar reporte de ventas",
                "reporte-ventas_" + desde + "_" + hasta, Exportador.Formato.EXCEL,
                destino -> excelService.generar(desde, hasta, destino));
    }

    @FXML
    private void onVerVentas() {
        mainController.irA("ventas-view");
    }

    private void mostrarTaller(ResumenTaller r) {
        animarNumero(kpiEnTaller, r.enTaller(), v -> String.format(Tablas.LOCALE, "%,.0f", v));
        animarNumero(kpiListos, r.listasParaEntrega(), v -> String.format(Tablas.LOCALE, "%,.0f", v));
        animarNumero(kpiOrdenesMes, r.ingresadasEnElMes(), v -> String.format(Tablas.LOCALE, "%,.0f", v));
        animarNumero(kpiFacturadoTaller, r.facturadoEnElMes().doubleValue(), v -> Tablas.MONEDA.format(v));
    }

    private void mostrarVentas() {
        LocalDate hoy = LocalDate.now();
        ResumenVentas r = reporteVentas.resumen(cmbPeriodo.getValue().desde(hoy), hoy);

        animarNumero(kpiVentas, r.total().doubleValue(), v -> Tablas.MONEDA.format(v));
        animarNumero(kpiComprobantes, r.comprobantes(), v -> String.format(Tablas.LOCALE, "%,.0f", v));
        animarNumero(kpiTicket, r.ticketPromedio().doubleValue(), v -> Tablas.MONEDA.format(v));
        animarNumero(kpiIgv, r.igv().doubleValue(), v -> Tablas.MONEDA.format(v));

        if (r.variacionTotal() == null) {
            kpiVentasVariacion.setVisible(false);
            kpiVentasVariacion.setManaged(false);
            kpiVentasDetalle.setText("Sin ventas en el periodo anterior");
        } else {
            double var = r.variacionTotal();
            kpiVentasVariacion.setVisible(true);
            kpiVentasVariacion.setManaged(true);
            kpiVentasVariacion.setText(String.format(Tablas.LOCALE, "%s %.1f%%", var >= 0 ? "▲" : "▼", Math.abs(var)));
            kpiVentasVariacion.getStyleClass().setAll("label", "variacion", var >= 0 ? "positiva" : "negativa");
            kpiVentasDetalle.setText("vs. periodo anterior");
        }
        kpiComprobantesDetalle.setText(r.anuladas() == 0
                ? "Ningun comprobante anulado"
                : r.anuladas() + (r.anuladas() == 1 ? " anulado" : " anulados"));
        kpiTicketDetalle.setText("Promedio por comprobante emitido");
        kpiIgvDetalle.setText("Base imponible " + Tablas.MONEDA.format(r.baseImponible()));

        mostrarSerie(r);
        mostrarTopVendidos(r.topRepuestos());
        mostrarUltimas(r.ultimas());
        mostrarClientes(r.topClientes());
    }

    private void mostrarSerie(ResumenVentas r) {
        List<PuntoSerie> puntos = r.serie();
        DateTimeFormatter formato = r.seriePorMes() ? MES : DIA;
        etiquetasSerie = puntos.stream().map(p -> p.fecha().format(formato)).toList();
        lblSerieSubtitulo.setText(r.seriePorMes() ? "Total vendido por mes (S/)" : "Total vendido por dia (S/)");

        XYChart.Series<Number, Number> serie = new XYChart.Series<>();
        for (int i = 0; i < puntos.size(); i++) {
            serie.getData().add(new XYChart.Data<>(i, 0.0));
        }
        graficoVentas.getData().setAll(List.of(serie));

        int n = puntos.size();
        ejeFechas.setLowerBound(0);
        ejeFechas.setUpperBound(Math.max(1, n - 1));
        ejeFechas.setTickUnit(Math.max(1, Math.ceil((n - 1) / 7.0)));
        double maximo = puntos.stream().mapToDouble(p -> p.total().doubleValue()).max().orElse(0);
        ajustarEje(ejeVentas, maximo, false);

        Timeline crecimiento = new Timeline();
        for (int i = 0; i < n; i++) {
            XYChart.Data<Number, Number> d = serie.getData().get(i);
            PuntoSerie p = puntos.get(i);
            crecimiento.getKeyFrames().add(new KeyFrame(DURACION,
                    new KeyValue(d.YValueProperty(), p.total().doubleValue(), SUAVE)));
            instalarTooltip(d.getNode(), p.fecha().format(r.seriePorMes() ? MES : DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + "\n" + Tablas.MONEDA.format(p.total()) + " · " + p.comprobantes()
                    + (p.comprobantes() == 1 ? " comprobante" : " comprobantes"));
        }
        crecimiento.play();
    }

    private void mostrarTopVendidos(List<RepuestoVendido> repuestos) {
        listaTopVendidos.getChildren().clear();
        if (repuestos.isEmpty()) {
            listaTopVendidos.getChildren().add(textoVacio("Sin ventas en el periodo."));
            return;
        }
        double maximo = repuestos.get(0).importe().doubleValue();
        int posicion = 1;
        for (RepuestoVendido p : repuestos.stream().limit(MAX_TOP).toList()) {
            listaTopVendidos.getChildren().add(filaRanking(posicion++, p.nombre(),
                    p.unidades() + (p.unidades() == 1 ? " unidad" : " unidades"),
                    Tablas.MONEDA.format(p.importe()), maximo == 0 ? 0 : p.importe().doubleValue() / maximo));
        }
    }

    private void mostrarClientes(List<ClienteFrecuente> clientes) {
        listaClientes.getChildren().clear();
        if (clientes.isEmpty()) {
            listaClientes.getChildren().add(textoVacio("Sin ventas en el periodo."));
            return;
        }
        double maximo = clientes.get(0).importe().doubleValue();
        int posicion = 1;
        for (ClienteFrecuente c : clientes.stream().limit(MAX_TOP).toList()) {
            listaClientes.getChildren().add(filaRanking(posicion++, c.nombre(),
                    "DNI " + c.dni() + " · " + c.compras() + (c.compras() == 1 ? " compra" : " compras"),
                    Tablas.MONEDA.format(c.importe()), maximo == 0 ? 0 : c.importe().doubleValue() / maximo));
        }
    }

    private void mostrarUltimas(List<Venta> ventas) {
        listaUltimas.getChildren().clear();
        if (ventas.isEmpty()) {
            listaUltimas.getChildren().add(textoVacio("Aun no hay ventas registradas."));
            return;
        }
        for (Venta v : ventas) {
            StackPane icono = new StackPane(new FontIcon(Feather.FILE_TEXT));
            icono.getStyleClass().add("venta-icono");

            Label comprobante = new Label(v.getComprobante());
            comprobante.getStyleClass().add("ranking-nombre");
            Label cliente = new Label(v.getCliente().getNombreCompleto() + " · " + cuando(v.getFecha()));
            cliente.getStyleClass().add("ranking-detalle");
            VBox textos = new VBox(1, comprobante, cliente);
            HBox.setHgrow(textos, Priority.ALWAYS);

            Label total = new Label(Tablas.MONEDA.format(v.getTotal()));
            total.getStyleClass().add("ranking-precio");
            Label estado = new Label(v.getEstado().name());
            estado.getStyleClass().addAll("chip", "chip-mini", v.isAnulada() ? "peligro" : "exito");
            VBox derecha = new VBox(3, total, estado);
            derecha.setAlignment(Pos.CENTER_RIGHT);

            Button imprimir = new Button(null, new FontIcon(Feather.PRINTER));
            imprimir.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
            instalarTooltip(imprimir, "Imprimir comprobante (PDF)");
            Long id = v.getId();
            imprimir.setOnAction(e -> exportador.abrirTemporal(v.getComprobante(), Exportador.Formato.PDF,
                    destino -> pdfService.generar(id, destino)));

            HBox fila = new HBox(12, icono, textos, derecha, imprimir);
            fila.setAlignment(Pos.CENTER_LEFT);
            fila.getStyleClass().add("venta-fila");
            if (v.isAnulada()) {
                fila.getStyleClass().add("anulada");
            }
            listaUltimas.getChildren().add(fila);
        }
    }

    private Node filaRanking(int posicion, String titulo, String detalle, String valor, double fraccion) {
        Label pos = new Label(String.valueOf(posicion));
        pos.getStyleClass().add("ranking-posicion");
        Label nombre = new Label(titulo);
        nombre.getStyleClass().add("ranking-nombre");
        Label sub = new Label(detalle);
        sub.getStyleClass().add("ranking-detalle");
        VBox textos = new VBox(1, nombre, sub);
        HBox.setHgrow(textos, Priority.ALWAYS);
        Label importe = new Label(valor);
        importe.getStyleClass().add("ranking-precio");
        importe.setMinWidth(Region.USE_PREF_SIZE);

        HBox cabecera = new HBox(12, pos, textos, importe);
        cabecera.setAlignment(Pos.CENTER_LEFT);
        ProgressBar barra = new ProgressBar(0);
        barra.getStyleClass().add("barra-fina");
        barra.setMaxWidth(Double.MAX_VALUE);
        animarProgreso(barra, fraccion);

        VBox fila = new VBox(8, cabecera, barra);
        fila.getStyleClass().add("ranking-fila");
        return fila;
    }

    private static Label textoVacio(String texto) {
        Label vacio = new Label(texto);
        vacio.getStyleClass().add("texto-vacio");
        return vacio;
    }

    private static String cuando(LocalDateTime fecha) {
        LocalDateTime ahora = LocalDateTime.now();
        long minutos = java.time.Duration.between(fecha, ahora).toMinutes();
        if (minutos >= 0 && minutos < 60) {
            return minutos <= 1 ? "hace un momento" : "hace " + minutos + " min";
        }
        if (fecha.toLocalDate().equals(ahora.toLocalDate())) {
            return "hoy " + fecha.format(HORA);
        }
        if (fecha.toLocalDate().equals(ahora.toLocalDate().minusDays(1))) {
            return "ayer " + fecha.format(HORA);
        }
        return fecha.format(FECHA_CORTA);
    }

    private void mostrarIndicadores(Resumen r) {
        animarNumero(kpiClientes, r.clientes(), v -> String.format(Tablas.LOCALE, "%,.0f", v));
        animarNumero(kpiCategorias, r.categorias(), v -> String.format(Tablas.LOCALE, "%,.0f", v));
        animarNumero(kpiRepuestos, r.repuestos(), v -> String.format(Tablas.LOCALE, "%,.0f", v));
        animarNumero(kpiValor, r.valorCatalogo().doubleValue(), v -> Tablas.MONEDA.format(v));

        kpiClientesDetalle.setText(r.clientesContactoCompleto() + " con contacto completo");
        kpiCategoriasDetalle.setText(r.categorias() == 0
                ? "Sin categorias registradas"
                : String.format(Tablas.LOCALE, "%.1f repuestos por categoria", (double) r.repuestos() / r.categorias()));
        long sinCategoria = r.porCategoria().stream()
                .filter(s -> s.nombre().equals("Sin categoria"))
                .mapToLong(SerieCategoria::repuestos).sum();
        kpiRepuestosDetalle.setText(sinCategoria == 0
                ? "Todos tienen categoria"
                : sinCategoria + " sin categoria asignada");
        kpiValorDetalle.setText("Precio promedio " + Tablas.MONEDA.format(r.precioPromedio()));
    }

    private void mostrarDona(Resumen r) {
        List<SerieCategoria> principales = new ArrayList<>(
                r.porCategoria().subList(0, Math.min(MAX_CATEGORIAS, r.porCategoria().size())));
        long otras = r.porCategoria().stream().skip(MAX_CATEGORIAS).mapToLong(SerieCategoria::repuestos).sum();

        principales.sort(Comparator.comparing((SerieCategoria s) -> s.nombre().equals("Sin categoria"))
                .thenComparing(SerieCategoria::nombre, String.CASE_INSENSITIVE_ORDER));

        List<PieChart.Data> datos = new ArrayList<>();
        List<String> colores = new ArrayList<>();
        for (int i = 0; i < principales.size(); i++) {
            datos.add(new PieChart.Data(principales.get(i).nombre(), principales.get(i).repuestos()));
            colores.add("-serie-" + (i + 1));
        }
        if (otras > 0) {
            datos.add(new PieChart.Data("Otras", otras));
            colores.add("-serie-otros");
        }

        graficoCategorias.setData(FXCollections.observableArrayList(datos));
        leyendaDona.getChildren().clear();
        lblTotalDona.setText(String.valueOf(r.repuestos()));

        if (datos.isEmpty()) {
            Label vacio = new Label("Aun no hay repuestos registrados.");
            vacio.getStyleClass().add("texto-vacio");
            leyendaDona.getChildren().add(vacio);
            return;
        }

        for (int i = 0; i < datos.size(); i++) {
            PieChart.Data d = datos.get(i);
            String color = colores.get(i);
            double porcentaje = 100.0 * d.getPieValue() / r.repuestos();
            d.getNode().setStyle("-fx-pie-color: " + color + ";");
            instalarTooltip(d.getNode(), d.getName() + "\n"
                    + (long) d.getPieValue() + " repuestos · " + String.format(Tablas.LOCALE, "%.0f%%", porcentaje));

            Region punto = new Region();
            punto.getStyleClass().add("leyenda-punto");
            punto.setStyle("-fx-background-color: " + color + ";");
            Label nombre = new Label(d.getName());
            nombre.getStyleClass().add("leyenda-nombre");
            nombre.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(nombre, Priority.ALWAYS);
            Label cantidad = new Label(String.valueOf((long) d.getPieValue()));
            cantidad.getStyleClass().add("leyenda-valor");
            cantidad.setMinWidth(Region.USE_PREF_SIZE);
            Label pct = new Label(String.format(Tablas.LOCALE, "%.0f%%", porcentaje));
            pct.getStyleClass().add("leyenda-porcentaje");
            pct.setMinWidth(Region.USE_PREF_SIZE);

            HBox fila = new HBox(10, punto, nombre, cantidad, pct);
            fila.setAlignment(Pos.CENTER_LEFT);
            fila.getStyleClass().add("leyenda-fila");
            leyendaDona.getChildren().add(fila);
        }
    }

    private void mostrarPromedios(List<SerieCategoria> categorias) {
        List<SerieCategoria> visibles = categorias.stream()
                .limit(MAX_BARRAS)
                .sorted(Comparator.comparingDouble(SerieCategoria::precioPromedio))
                .toList();

        XYChart.Series<Number, String> serie = new XYChart.Series<>();
        for (SerieCategoria c : visibles) {
            serie.getData().add(new XYChart.Data<>(0.0, c.nombre()));
        }
        graficoPromedio.getData().setAll(List.of(serie));

        double maximo = visibles.stream().mapToDouble(SerieCategoria::precioPromedio).max().orElse(0);
        ajustarEje(ejePromedio, maximo, false);

        Timeline crecimiento = new Timeline();
        for (int i = 0; i < visibles.size(); i++) {
            XYChart.Data<Number, String> d = serie.getData().get(i);
            SerieCategoria c = visibles.get(i);
            crecimiento.getKeyFrames().add(new KeyFrame(DURACION,
                    new KeyValue(d.XValueProperty(), c.precioPromedio(), SUAVE)));
            instalarTooltip(d.getNode(), c.nombre() + "\nPromedio " + Tablas.MONEDA.format(c.precioPromedio())
                    + " · " + c.repuestos() + " repuestos");
        }
        crecimiento.play();
    }

    private void mostrarRangos(List<RangoPrecio> rangos) {
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        for (RangoPrecio r : rangos) {
            serie.getData().add(new XYChart.Data<>(r.etiqueta(), 0.0));
        }
        graficoRangos.getData().setAll(List.of(serie));

        double maximo = rangos.stream().mapToLong(RangoPrecio::cantidad).max().orElse(0);
        ajustarEje(ejeRangos, maximo, true);

        Timeline crecimiento = new Timeline();
        for (int i = 0; i < rangos.size(); i++) {
            XYChart.Data<String, Number> d = serie.getData().get(i);
            RangoPrecio r = rangos.get(i);
            crecimiento.getKeyFrames().add(new KeyFrame(DURACION,
                    new KeyValue(d.YValueProperty(), (double) r.cantidad(), SUAVE)));
            instalarTooltip(d.getNode(), "S/ " + r.etiqueta() + "\n" + r.cantidad() + " repuestos");
        }
        crecimiento.play();
    }

    private void mostrarTop(List<Repuesto> repuestos) {
        listaTop.getChildren().clear();
        if (repuestos.isEmpty()) {
            Label vacio = new Label("Aun no hay repuestos registrados.");
            vacio.getStyleClass().add("texto-vacio");
            listaTop.getChildren().add(vacio);
            return;
        }

        double maximo = repuestos.get(0).getPrecio().doubleValue();
        int posicion = 1;
        for (Repuesto p : repuestos) {
            Label pos = new Label(String.valueOf(posicion++));
            pos.getStyleClass().add("ranking-posicion");

            Label nombre = new Label(p.getNombre());
            nombre.getStyleClass().add("ranking-nombre");
            Label categoria = new Label(p.getCategoria() != null ? p.getCategoria().getNombre() : "Sin categoria");
            categoria.getStyleClass().add("ranking-detalle");
            VBox textos = new VBox(1, nombre, categoria);
            HBox.setHgrow(textos, Priority.ALWAYS);

            Label precio = new Label(Tablas.MONEDA.format(p.getPrecio()));
            precio.getStyleClass().add("ranking-precio");

            HBox cabecera = new HBox(12, pos, textos, precio);
            cabecera.setAlignment(Pos.CENTER_LEFT);

            ProgressBar barra = new ProgressBar(0);
            barra.getStyleClass().add("barra-fina");
            barra.setMaxWidth(Double.MAX_VALUE);
            animarProgreso(barra, maximo == 0 ? 0 : p.getPrecio().doubleValue() / maximo);

            VBox fila = new VBox(8, cabecera, barra);
            fila.getStyleClass().add("ranking-fila");
            listaTop.getChildren().add(fila);
        }
    }

    private void mostrarContacto(Resumen r) {
        medidoresContacto.getChildren().setAll(
                medidor("Con correo electronico", r.clientesConEmail(), r.clientes()),
                medidor("Con telefono", r.clientesConTelefono(), r.clientes()),
                medidor("Contacto completo", r.clientesContactoCompleto(), r.clientes()));
    }

    private Node medidor(String titulo, long cantidad, long total) {
        double fraccion = total == 0 ? 0 : (double) cantidad / total;

        Label lblTitulo = new Label(titulo);
        lblTitulo.getStyleClass().add("medidor-titulo");
        Label lblPct = new Label("0%");
        lblPct.getStyleClass().add("medidor-valor");
        animarNumero(lblPct, fraccion * 100, v -> String.format(Tablas.LOCALE, "%.0f%%", v));

        ProgressBar barra = new ProgressBar(0);
        barra.getStyleClass().add("barra-fina");
        barra.setMaxWidth(Double.MAX_VALUE);
        animarProgreso(barra, fraccion);

        Label detalle = new Label(cantidad + " de " + total + " clientes");
        detalle.getStyleClass().add("medidor-detalle");

        VBox caja = new VBox(6, lblTitulo, lblPct, barra, detalle);
        caja.getStyleClass().add("medidor");
        HBox.setHgrow(caja, Priority.ALWAYS);
        caja.setMaxWidth(Double.MAX_VALUE);
        return caja;
    }

    private void animarNumero(Label etiqueta, double destino, DoubleFunction<String> formato) {
        DoubleProperty valor = new SimpleDoubleProperty(0);
        valor.addListener((obs, a, b) -> etiqueta.setText(formato.apply(b.doubleValue())));
        etiqueta.setText(formato.apply(0));
        new Timeline(new KeyFrame(DURACION, new KeyValue(valor, destino, SUAVE))).play();
    }

    private void animarProgreso(ProgressBar barra, double destino) {
        new Timeline(new KeyFrame(DURACION, new KeyValue(barra.progressProperty(), destino, SUAVE))).play();
    }

    private static StringConverter<Number> formato(Function<Number, String> f) {
        return new StringConverter<>() {
            @Override
            public String toString(Number valor) {
                return f.apply(valor);
            }

            @Override
            public Number fromString(String texto) {
                return null;
            }
        };
    }

    private void instalarTooltip(Node nodo, String texto) {
        Tooltip t = new Tooltip(texto);
        t.setShowDelay(Duration.millis(80));
        t.setHideDelay(Duration.millis(80));
        Tooltip.install(nodo, t);
    }

    private static void ajustarEje(NumberAxis eje, double maximo, boolean enteros) {
        double superior;
        if (maximo <= 0) {
            superior = enteros ? 5 : 100;
        } else {
            double escala = Math.pow(10, Math.floor(Math.log10(maximo)));
            double f = maximo / escala;
            double redondo = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10;
            superior = redondo * escala;
        }
        if (enteros) {
            superior = Math.max(5, Math.ceil(superior));
            if (superior % 5 != 0) {
                superior = Math.ceil(superior / 5) * 5;
            }
        }
        eje.setUpperBound(superior);
        eje.setTickUnit(superior / 5);
    }
}
