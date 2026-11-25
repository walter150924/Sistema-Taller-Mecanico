package com.taller.mecanico.controller;

import atlantafx.base.util.Animations;
import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.Venta;
import com.taller.mecanico.model.VentaDetalle;
import com.taller.mecanico.service.ComprobantePdfService;
import com.taller.mecanico.service.NumeroALetras;
import com.taller.mecanico.service.ReporteExcelService;
import com.taller.mecanico.service.VentaService;
import com.taller.mecanico.service.VentaService.Totalizado;
import com.taller.mecanico.ui.Exportador;
import com.taller.mecanico.ui.Notificador;
import com.taller.mecanico.ui.Paginador;
import com.taller.mecanico.ui.Refrescable;
import com.taller.mecanico.ui.Tablas;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class VentasController implements Refrescable {
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final PseudoClass ANULADA = PseudoClass.getPseudoClass("anulada");

    private final VentaService service;
    private final ComprobantePdfService pdfService;
    private final ReporteExcelService excelService;
    private final Exportador exportador;
    private final Notificador notificador;
    private final MainController mainController;

    @FXML private TextField txtBuscar;
    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private Button btnVer;
    @FXML private Button btnImprimir;
    @FXML private Button btnAnular;
    @FXML private Paginador paginador;

    @FXML private Label lblResComprobantes;
    @FXML private Label lblResBase;
    @FXML private Label lblResIgv;
    @FXML private Label lblResTotal;

    @FXML private TableView<Venta> tabla;
    @FXML private TableColumn<Venta, String> colComprobante;
    @FXML private TableColumn<Venta, String> colFecha;
    @FXML private TableColumn<Venta, String> colCliente;
    @FXML private TableColumn<Venta, String> colDni;
    @FXML private TableColumn<Venta, BigDecimal> colBase;
    @FXML private TableColumn<Venta, BigDecimal> colIgv;
    @FXML private TableColumn<Venta, BigDecimal> colTotal;
    @FXML private TableColumn<Venta, String> colEstado;

    @FXML private VBox panelDetalle;
    @FXML private Label lblDetComprobante;
    @FXML private Label lblDetEstado;
    @FXML private Label lblDetSubtitulo;
    @FXML private Label lblDetIniciales;
    @FXML private Label lblDetCliente;
    @FXML private Label lblDetClienteDatos;
    @FXML private TableView<VentaDetalle> tablaDetalle;
    @FXML private TableColumn<VentaDetalle, String> colDetItem;
    @FXML private TableColumn<VentaDetalle, String> colDetRepuesto;
    @FXML private TableColumn<VentaDetalle, String> colDetCantidad;
    @FXML private TableColumn<VentaDetalle, BigDecimal> colDetPrecio;
    @FXML private TableColumn<VentaDetalle, BigDecimal> colDetImporte;
    @FXML private Label lblDetEtiquetaBase;
    @FXML private Label lblDetBase;
    @FXML private Label lblDetEtiquetaIgv;
    @FXML private Label lblDetIgv;
    @FXML private Label lblDetTotal;
    @FXML private Label lblDetLetras;
    @FXML private Label lblDetObservacion;

    private final ObservableList<Venta> datos = FXCollections.observableArrayList();
    private Venta enDetalle;

    @FXML
    public void initialize() {
        colComprobante.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getComprobante()));
        colComprobante.setCellFactory(Tablas.celdaChip("—"));
        colFecha.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFecha().format(FECHA_HORA)));
        colCliente.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCliente().getNombreCompleto()));
        colCliente.setCellFactory(Tablas.celdaAvatar());
        colDni.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCliente().getDni()));
        colBase.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getBaseImponible()));
        colBase.setCellFactory(Tablas.celdaMoneda());
        colIgv.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getIgv()));
        colIgv.setCellFactory(Tablas.celdaMoneda());
        colTotal.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getTotal()));
        colTotal.setCellFactory(Tablas.celdaMoneda());
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstado().name()));
        colEstado.setCellFactory(Tablas.celdaEstado());

        Tablas.configurar(tabla, Feather.FILE_TEXT, "Sin ventas",
                "No hay comprobantes para el filtro seleccionado.", this::verDetalle);
        tabla.setItems(datos);

        tabla.setRowFactory(tv -> {
            TableRow<Venta> fila = new TableRow<>() {
                @Override
                protected void updateItem(Venta v, boolean vacio) {
                    super.updateItem(v, vacio);
                    pseudoClassStateChanged(ANULADA,
                            !vacio && v != null && v.isAnulada());
                }
            };
            fila.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !fila.isEmpty()) {
                    verDetalle(fila.getItem());
                }
            });
            return fila;
        });

        var seleccion = tabla.getSelectionModel().selectedItemProperty();
        btnVer.disableProperty().bind(seleccion.isNull());
        btnImprimir.disableProperty().bind(seleccion.isNull());
        btnAnular.disableProperty().bind(seleccion.isNull());

        paginador.setOnCambio(this::cargarTabla);
        txtBuscar.textProperty().addListener((obs, a, b) -> paginador.irAPrimera());
        dpDesde.valueProperty().addListener((obs, a, b) -> paginador.irAPrimera());
        dpHasta.valueProperty().addListener((obs, a, b) -> paginador.irAPrimera());

        colDetItem.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getItem())));
        colDetItem.getStyleClass().add("celda-id");
        colDetRepuesto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreRepuesto()));
        colDetCantidad.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getCantidad())));
        colDetPrecio.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPrecioUnitario()));
        colDetPrecio.setCellFactory(Tablas.celdaMoneda());
        colDetImporte.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getImporte()));
        colDetImporte.setCellFactory(Tablas.celdaMoneda());
        tablaDetalle.getStyleClass().add("tabla");
        tablaDetalle.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    @Override
    public void refrescar() {
        cargarTabla();
    }

    @FXML
    private void onNuevaVenta() {
        mainController.irA("punto-venta-view");
    }

    @FXML
    private void onVer() {
        verDetalle(tabla.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void onImprimir() {
        imprimir(tabla.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void onAnular() {
        Venta v = tabla.getSelectionModel().getSelectedItem();
        if (v == null) {
            return;
        }
        if (v.isAnulada()) {
            notificador.advertencia("El comprobante " + v.getComprobante() + " ya esta anulado.");
            return;
        }
        notificador.confirmar("¿Anular el comprobante " + v.getComprobante() + "?",
                "La venta de " + Tablas.MONEDA.format(v.getTotal()) + " a " + v.getCliente().getNombreCompleto()
                        + " dejara de sumar en los reportes. El comprobante se conserva marcado como ANULADO.",
                "Anular",
                () -> {
                    try {
                        service.anular(v.getId());
                        notificador.exito("Comprobante " + v.getComprobante() + " anulado.");
                        cargarTabla();
                    } catch (NegocioException e) {
                        notificador.error(e.getMessage());
                    }
                });
    }

    @FXML
    private void onExportarExcel() {
        LocalDate hasta = dpHasta.getValue() != null ? dpHasta.getValue() : LocalDate.now();
        LocalDate desde = dpDesde.getValue() != null ? dpDesde.getValue() : service.primeraFecha();
        if (desde.isAfter(hasta)) {
            notificador.advertencia("La fecha inicial no puede ser posterior a la final.");
            return;
        }
        exportador.guardar(tabla.getScene().getWindow(), "Exportar reporte de ventas",
                "reporte-ventas_" + desde + "_" + hasta, Exportador.Formato.EXCEL,
                destino -> excelService.generar(desde, hasta, destino));
    }

    @FXML
    private void onCerrarDetalle() {
        notificador.cerrarPanel();
    }

    @FXML
    private void onImprimirDetalle() {
        imprimir(enDetalle);
    }

    @FXML
    private void onDescargarDetalle() {
        if (enDetalle == null) {
            return;
        }
        Long id = enDetalle.getId();
        exportador.guardar(panelDetalle.getScene().getWindow(), "Guardar comprobante",
                enDetalle.getComprobante(), Exportador.Formato.PDF,
                destino -> pdfService.generar(id, destino));
    }

    private void imprimir(Venta v) {
        if (v == null) {
            return;
        }
        Long id = v.getId();
        exportador.abrirTemporal(v.getComprobante(), Exportador.Formato.PDF,
                destino -> pdfService.generar(id, destino));
    }

    private void verDetalle(Venta seleccionada) {
        if (seleccionada == null) {
            return;
        }
        Venta v = service.obtener(seleccionada.getId());
        enDetalle = v;

        lblDetComprobante.setText(v.getComprobante());
        lblDetEstado.setText(v.getEstado().name());
        lblDetEstado.getStyleClass().setAll("chip", v.isAnulada() ? "peligro" : "exito");
        lblDetSubtitulo.setText(v.getTipoComprobante().getDescripcion() + " · " + v.getFecha().format(FECHA_HORA));

        lblDetIniciales.setText(Tablas.iniciales(v.getCliente().getNombreCompleto()));
        lblDetCliente.setText(v.getCliente().getNombreCompleto());
        StringBuilder datosCliente = new StringBuilder("DNI " + v.getCliente().getDni());
        if (v.getCliente().getEmail() != null) {
            datosCliente.append("  ·  ").append(v.getCliente().getEmail());
        }
        if (v.getCliente().getTelefono() != null) {
            datosCliente.append("  ·  ").append(v.getCliente().getTelefono());
        }
        lblDetClienteDatos.setText(datosCliente.toString());

        tablaDetalle.getItems().setAll(v.getDetalles());

        lblDetEtiquetaBase.setText(v.isAplicaIgv() ? "Base imponible (Op. gravada)" : "Op. exonerada / inafecta");
        lblDetBase.setText(Tablas.MONEDA.format(v.getBaseImponible()));
        lblDetEtiquetaIgv.setText(v.isAplicaIgv()
                ? "IGV (18%) · " + (v.isPreciosIncluyenIgv() ? "incluido en precios" : "agregado al precio")
                : "IGV (no aplica)");
        lblDetIgv.setText(Tablas.MONEDA.format(v.getIgv()));
        lblDetTotal.setText(Tablas.MONEDA.format(v.getTotal()));
        lblDetLetras.setText("SON: " + NumeroALetras.importe(v.getTotal()));
        boolean hayObs = v.getObservacion() != null;
        lblDetObservacion.setText(hayObs ? "Observacion: " + v.getObservacion() : "");
        lblDetObservacion.setVisible(hayObs);
        lblDetObservacion.setManaged(hayObs);

        notificador.abrirPanel(panelDetalle);
    }

    private void cargarTabla() {
        Page<Venta> pagina = service.listar(txtBuscar.getText(), dpDesde.getValue(), dpHasta.getValue(),
                paginador.getPagina(), paginador.getTamanio());
        datos.setAll(pagina.getContent());
        paginador.actualizar(pagina);

        Totalizado t = service.totalizar(txtBuscar.getText(), dpDesde.getValue(), dpHasta.getValue());
        lblResComprobantes.setText(String.format(Tablas.LOCALE, "%,d", t.comprobantes()));
        lblResBase.setText(Tablas.MONEDA.format(t.baseImponible()));
        lblResIgv.setText(Tablas.MONEDA.format(t.igv()));
        lblResTotal.setText(Tablas.MONEDA.format(t.total()));
        Animations.fadeIn(tabla, Duration.millis(260)).play();
    }
}
