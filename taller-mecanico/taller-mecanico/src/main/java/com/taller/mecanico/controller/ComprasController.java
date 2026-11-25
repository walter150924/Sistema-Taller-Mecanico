package com.taller.mecanico.controller;

import atlantafx.base.util.Animations;
import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.Compra;
import com.taller.mecanico.model.CompraDetalle;
import com.taller.mecanico.service.CompraService;
import com.taller.mecanico.service.CompraService.Totalizado;
import com.taller.mecanico.service.NumeroALetras;
import com.taller.mecanico.service.ReporteInventarioExcelService;
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
public class ComprasController implements Refrescable {
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final PseudoClass ANULADA = PseudoClass.getPseudoClass("anulada");

    private final CompraService service;
    private final ReporteInventarioExcelService excelService;
    private final Exportador exportador;
    private final Notificador notificador;
    private final MainController mainController;

    @FXML private TextField txtBuscar;
    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private Button btnVer;
    @FXML private Button btnAnular;
    @FXML private Paginador paginador;

    @FXML private Label lblResCompras;
    @FXML private Label lblResBase;
    @FXML private Label lblResIgv;
    @FXML private Label lblResTotal;

    @FXML private TableView<Compra> tabla;
    @FXML private TableColumn<Compra, String> colCodigo;
    @FXML private TableColumn<Compra, String> colFecha;
    @FXML private TableColumn<Compra, String> colProveedor;
    @FXML private TableColumn<Compra, String> colDocumento;
    @FXML private TableColumn<Compra, BigDecimal> colBase;
    @FXML private TableColumn<Compra, BigDecimal> colIgv;
    @FXML private TableColumn<Compra, BigDecimal> colTotal;
    @FXML private TableColumn<Compra, String> colEstado;

    @FXML private VBox panelDetalle;
    @FXML private Label lblDetCodigo;
    @FXML private Label lblDetEstado;
    @FXML private Label lblDetSubtitulo;
    @FXML private Label lblDetProveedor;
    @FXML private Label lblDetProveedorDatos;
    @FXML private TableView<CompraDetalle> tablaDetalle;
    @FXML private TableColumn<CompraDetalle, String> colDetItem;
    @FXML private TableColumn<CompraDetalle, String> colDetRepuesto;
    @FXML private TableColumn<CompraDetalle, String> colDetCantidad;
    @FXML private TableColumn<CompraDetalle, BigDecimal> colDetCosto;
    @FXML private TableColumn<CompraDetalle, BigDecimal> colDetImporte;
    @FXML private Label lblDetEtiquetaBase;
    @FXML private Label lblDetBase;
    @FXML private Label lblDetEtiquetaIgv;
    @FXML private Label lblDetIgv;
    @FXML private Label lblDetTotal;
    @FXML private Label lblDetLetras;
    @FXML private Label lblDetObservacion;

    private final ObservableList<Compra> datos = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        colCodigo.setCellFactory(Tablas.celdaChip("—"));
        colFecha.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFecha().format(FECHA_HORA)));
        colProveedor.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProveedor().getRazonSocial()));
        colDocumento.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNumeroDocumento()));
        colBase.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getBaseImponible()));
        colBase.setCellFactory(Tablas.celdaMoneda());
        colIgv.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getIgv()));
        colIgv.setCellFactory(Tablas.celdaMoneda());
        colTotal.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getTotal()));
        colTotal.setCellFactory(Tablas.celdaMoneda());
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstado().name()));
        colEstado.setCellFactory(Tablas.celdaEstado());

        Tablas.configurar(tabla, Feather.TRUCK, "Sin compras",
                "No hay compras para el filtro seleccionado.", this::verDetalle);
        tabla.setItems(datos);
        tabla.setRowFactory(tv -> {
            TableRow<Compra> fila = new TableRow<>() {
                @Override
                protected void updateItem(Compra c, boolean vacio) {
                    super.updateItem(c, vacio);
                    pseudoClassStateChanged(ANULADA, !vacio && c != null && c.isAnulada());
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
        btnAnular.disableProperty().bind(seleccion.isNull());

        paginador.setOnCambio(this::cargarTabla);
        txtBuscar.textProperty().addListener((obs, a, b) -> paginador.irAPrimera());
        dpDesde.valueProperty().addListener((obs, a, b) -> paginador.irAPrimera());
        dpHasta.valueProperty().addListener((obs, a, b) -> paginador.irAPrimera());

        colDetItem.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getItem())));
        colDetItem.getStyleClass().add("celda-id");
        colDetRepuesto.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCodigoRepuesto() + "  " + c.getValue().getNombreRepuesto()));
        colDetCantidad.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getCantidad())));
        colDetCosto.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCostoUnitario()));
        colDetCosto.setCellFactory(Tablas.celdaMoneda());
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
    private void onNuevaCompra() {
        mainController.irA("compra-form-view");
    }

    @FXML
    private void onVer() {
        verDetalle(tabla.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void onAnular() {
        Compra c = tabla.getSelectionModel().getSelectedItem();
        if (c == null) {
            return;
        }
        if (c.isAnulada()) {
            notificador.advertencia("La compra " + c.getCodigo() + " ya esta anulada.");
            return;
        }
        notificador.confirmar("¿Anular la compra " + c.getCodigo() + "?",
                "Se retiraran del inventario las unidades que ingresaron con el documento "
                        + c.getNumeroDocumento() + " de " + c.getProveedor().getRazonSocial() + ".",
                "Anular",
                () -> {
                    try {
                        service.anular(c.getId());
                        notificador.exito("Compra " + c.getCodigo() + " anulada; el stock fue actualizado.");
                        cargarTabla();
                    } catch (NegocioException e) {
                        notificador.error(e.getMessage());
                    }
                });
    }

    @FXML
    private void onExportarExcel() {
        LocalDate hasta = dpHasta.getValue() != null ? dpHasta.getValue() : LocalDate.now();
        LocalDate desde = dpDesde.getValue() != null ? dpDesde.getValue() : hasta.minusYears(1);
        if (desde.isAfter(hasta)) {
            notificador.advertencia("La fecha inicial no puede ser posterior a la final.");
            return;
        }
        exportador.guardar(tabla.getScene().getWindow(), "Exportar compras",
                "compras_" + desde + "_" + hasta, Exportador.Formato.EXCEL,
                destino -> excelService.generarCompras(desde, hasta, destino));
    }

    @FXML
    private void onCerrarDetalle() {
        notificador.cerrarPanel();
    }

    private void verDetalle(Compra seleccionada) {
        if (seleccionada == null) {
            return;
        }
        Compra c = service.obtener(seleccionada.getId());

        lblDetCodigo.setText(c.getCodigo());
        lblDetEstado.setText(c.getEstado().name());
        lblDetEstado.getStyleClass().setAll("chip", c.isAnulada() ? "peligro" : "exito");
        lblDetSubtitulo.setText(c.getTipoDocumento().getDescripcion() + " " + c.getNumeroDocumento()
                + " del " + c.getFechaDocumento().format(FECHA) + " · registrada " + c.getFecha().format(FECHA_HORA));
        lblDetProveedor.setText(c.getProveedor().getRazonSocial());
        lblDetProveedorDatos.setText("RUC " + c.getProveedor().getRuc()
                + (c.getProveedor().getTelefono() != null ? "  ·  " + c.getProveedor().getTelefono() : ""));

        tablaDetalle.getItems().setAll(c.getDetalles());

        lblDetEtiquetaBase.setText(c.isAplicaIgv() ? "Base imponible (Op. gravada)" : "Op. no gravada");
        lblDetBase.setText(Tablas.MONEDA.format(c.getBaseImponible()));
        lblDetEtiquetaIgv.setText(c.isAplicaIgv()
                ? "IGV (18%) · " + (c.isPreciosIncluyenIgv() ? "incluido en costos" : "agregado a los costos")
                : "IGV (no aplica)");
        lblDetIgv.setText(Tablas.MONEDA.format(c.getIgv()));
        lblDetTotal.setText(Tablas.MONEDA.format(c.getTotal()));
        lblDetLetras.setText("SON: " + NumeroALetras.importe(c.getTotal()));
        boolean hayObs = c.getObservacion() != null;
        lblDetObservacion.setText(hayObs ? "Observacion: " + c.getObservacion() : "");
        lblDetObservacion.setVisible(hayObs);
        lblDetObservacion.setManaged(hayObs);

        notificador.abrirPanel(panelDetalle);
    }

    private void cargarTabla() {
        Page<Compra> pagina = service.listar(txtBuscar.getText(), dpDesde.getValue(), dpHasta.getValue(),
                paginador.getPagina(), paginador.getTamanio());
        datos.setAll(pagina.getContent());
        paginador.actualizar(pagina);

        Totalizado t = service.totalizar(txtBuscar.getText(), dpDesde.getValue(), dpHasta.getValue());
        lblResCompras.setText(String.format(Tablas.LOCALE, "%,d", t.compras()));
        lblResBase.setText(Tablas.MONEDA.format(t.baseImponible()));
        lblResIgv.setText(Tablas.MONEDA.format(t.igv()));
        lblResTotal.setText(Tablas.MONEDA.format(t.total()));
        Animations.fadeIn(tabla, Duration.millis(260)).play();
    }
}
