package com.taller.mecanico.controller;

import atlantafx.base.util.Animations;
import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.EstadoOrden;
import com.taller.mecanico.model.OrdenServicio;
import com.taller.mecanico.model.Permiso;
import com.taller.mecanico.model.Usuario;
import com.taller.mecanico.model.Vehiculo;
import com.taller.mecanico.seguridad.Sesion;
import com.taller.mecanico.service.OrdenPdfService;
import com.taller.mecanico.service.OrdenServicioService;
import com.taller.mecanico.service.OrdenServicioService.NuevaOrden;
import com.taller.mecanico.service.UsuarioService;
import com.taller.mecanico.service.VehiculoService;
import com.taller.mecanico.ui.Autocompletar;
import com.taller.mecanico.ui.CargadorVistas;
import com.taller.mecanico.ui.Convertidores;
import com.taller.mecanico.ui.Exportador;
import com.taller.mecanico.ui.Formularios;
import com.taller.mecanico.ui.Notificador;
import com.taller.mecanico.ui.Paginador;
import com.taller.mecanico.ui.Refrescable;
import com.taller.mecanico.ui.Tablas;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrdenesController implements Refrescable {
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int DIGITOS_KILOMETRAJE = 7;

    private final OrdenServicioService ordenService;
    private final VehiculoService vehiculoService;
    private final UsuarioService usuarioService;
    private final OrdenPdfService pdfService;
    private final CargadorVistas cargador;
    private final Exportador exportador;
    private final Notificador notificador;
    private final Sesion sesion;

    @FXML private Label lblTitulo;
    @FXML private Label lblSubtitulo;
    @FXML private Button btnNuevaOrden;
    @FXML private TextField txtBuscar;
    @FXML private ComboBox<EstadoOrden> cmbEstado;
    @FXML private Button btnImprimir;
    @FXML private Button btnAbrir;

    @FXML private TableView<OrdenServicio> tabla;
    @FXML private TableColumn<OrdenServicio, String> colCodigo;
    @FXML private TableColumn<OrdenServicio, String> colIngreso;
    @FXML private TableColumn<OrdenServicio, String> colVehiculo;
    @FXML private TableColumn<OrdenServicio, String> colCliente;
    @FXML private TableColumn<OrdenServicio, String> colMecanico;
    @FXML private TableColumn<OrdenServicio, EstadoOrden> colEstado;
    @FXML private TableColumn<OrdenServicio, BigDecimal> colTotal;
    @FXML private Paginador paginador;

    @FXML private VBox panelNuevaOrden;
    @FXML private TextField txtVehiculo;
    @FXML private Label lblPropietario;
    @FXML private TextField txtKilometraje;
    @FXML private TextArea txtProblema;
    @FXML private ComboBox<Usuario> cmbMecanico;

    private final ObservableList<OrdenServicio> datos = FXCollections.observableArrayList();
    private Autocompletar<Vehiculo> autoVehiculo;
    private Vehiculo vehiculoElegido;
    private OrdenDetalleController detalle;

    @FXML
    public void initialize() {
        detalle = null;
        configurarEncabezado();
        configurarTabla();
        configurarFiltros();
        configurarNuevaOrden();
    }

    @Override
    public void refrescar() {
        cargarTabla();
    }

    private void configurarEncabezado() {
        boolean puedeCrear = sesion.tiene(Permiso.CREAR_ORDENES);
        btnNuevaOrden.setVisible(puedeCrear);
        btnNuevaOrden.setManaged(puedeCrear);
        if (!sesion.tiene(Permiso.VER_TODAS_LAS_ORDENES)) {
            lblTitulo.setText("Mis ordenes de servicio");
            lblSubtitulo.setText("Vehiculos que tienes asignados para diagnosticar y reparar");
        }
    }

    private void configurarTabla() {
        colCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        colCodigo.setCellFactory(Tablas.celdaChip("—"));
        colIngreso.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFechaIngreso().format(FECHA_HORA)));
        colVehiculo.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getVehiculo().getPlaca() + " · " + c.getValue().getVehiculo().getDescripcion()));
        colCliente.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCliente().getNombreCompleto()));
        colCliente.setCellFactory(Tablas.celdaAvatar());
        colMecanico.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreMecanico()));
        colEstado.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getEstado()));
        colEstado.setCellFactory(Tablas.celdaEstadoOrden());
        colTotal.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getTotal()));
        colTotal.setCellFactory(Tablas.celdaMoneda());

        Tablas.configurar(tabla, Feather.CLIPBOARD, "Sin ordenes",
                "No hay ordenes que coincidan con el filtro.", this::abrirDetalle);
        tabla.setItems(datos);

        var seleccion = tabla.getSelectionModel().selectedItemProperty();
        btnAbrir.disableProperty().bind(seleccion.isNull());
        btnImprimir.disableProperty().bind(seleccion.isNull());
        paginador.setOnCambio(this::cargarTabla);
    }

    private void configurarFiltros() {
        List<EstadoOrden> opciones = new ArrayList<>();
        opciones.add(null);
        opciones.addAll(Arrays.asList(EstadoOrden.values()));
        cmbEstado.getItems().setAll(opciones);
        cmbEstado.setConverter(Convertidores.mostrando(EstadoOrden::getDescripcion, "Todos los estados"));
        cmbEstado.valueProperty().addListener((obs, anterior, actual) -> paginador.irAPrimera());
        txtBuscar.textProperty().addListener((obs, anterior, actual) -> paginador.irAPrimera());
    }

    private void configurarNuevaOrden() {
        Formularios.soloDigitos(txtKilometraje, DIGITOS_KILOMETRAJE);
        Formularios.limpiarAlEditar(txtVehiculo, txtKilometraje, txtProblema);
        cmbMecanico.setConverter(Convertidores.mostrando(Usuario::getNombreCompleto, "Sin asignar"));

        autoVehiculo = new Autocompletar<>(txtVehiculo, vehiculoService::autocompletar, this::seleccionarVehiculo)
                .presentacion(Vehiculo::getPlaca,
                        v -> v.getDescripcion() + " · " + v.getCliente().getNombreCompleto(), v -> null)
                .icono(v -> new FontIcon(Feather.TRUCK))
                .minimoCaracteres(2);
        txtVehiculo.textProperty().addListener((obs, anterior, actual) -> {
            if (vehiculoElegido != null && !actual.equals(vehiculoElegido.getPlaca())) {
                elegirVehiculo(null);
            }
        });
    }

    private void seleccionarVehiculo(Vehiculo vehiculo) {
        elegirVehiculo(vehiculo);
        autoVehiculo.setTexto(vehiculo.getPlaca());
        txtKilometraje.setText(String.valueOf(vehiculo.getKilometraje()));
        Formularios.limpiarError(txtVehiculo);
        txtKilometraje.requestFocus();
    }

    private void elegirVehiculo(Vehiculo vehiculo) {
        vehiculoElegido = vehiculo;
        boolean hayVehiculo = vehiculo != null;
        lblPropietario.setVisible(hayVehiculo);
        lblPropietario.setManaged(hayVehiculo);
        lblPropietario.setText(hayVehiculo
                ? vehiculo.getDescripcion() + " · Propietario: " + vehiculo.getCliente().getNombreCompleto()
                : "");
    }

    @FXML
    private void onNuevaOrden() {
        limpiarNuevaOrden();
        cmbMecanico.getItems().setAll(usuarioService.mecanicosActivos());
        notificador.abrirPanel(panelNuevaOrden);
        Platform.runLater(txtVehiculo::requestFocus);
    }

    @FXML
    private void onCerrarFormulario() {
        notificador.cerrarPanel();
    }

    @FXML
    private void onRegistrar() {
        Formularios.limpiarErrores(txtVehiculo, txtKilometraje, txtProblema);
        if (!formularioCompleto()) {
            return;
        }
        Usuario mecanico = cmbMecanico.getValue();
        try {
            OrdenServicio orden = ordenService.registrar(new NuevaOrden(
                    vehiculoElegido.getId(),
                    mecanico == null ? null : mecanico.getId(),
                    Integer.valueOf(txtKilometraje.getText()),
                    txtProblema.getText()));
            notificador.cerrarPanel();
            cargarTabla();
            notificador.exitoConAcciones("Orden registrada",
                    "Se registro la orden " + orden.getCodigo() + " para el vehiculo "
                            + vehiculoElegido.getPlaca() + ".",
                    "Cerrar", null,
                    "Abrir orden", () -> abrirDetalle(orden));
        } catch (NegocioException e) {
            notificador.advertencia(e.getMessage());
        }
    }

    private boolean formularioCompleto() {
        boolean completo = true;
        if (vehiculoElegido == null) {
            Formularios.marcarError(txtVehiculo, "Selecciona el vehiculo de la lista");
            completo = false;
        }
        if (txtKilometraje.getText().isBlank()) {
            Formularios.marcarError(txtKilometraje, "Ingresa el kilometraje actual");
            completo = false;
        }
        if (txtProblema.getText().isBlank()) {
            Formularios.marcarError(txtProblema, "Describe el problema reportado");
            completo = false;
        }
        return completo;
    }

    @FXML
    private void onAbrir() {
        abrirDetalle(tabla.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void onImprimir() {
        OrdenServicio orden = tabla.getSelectionModel().getSelectedItem();
        if (orden != null) {
            exportador.abrirTemporal(orden.getCodigo(), Exportador.Formato.PDF,
                    destino -> pdfService.generar(orden.getId(), destino));
        }
    }

    private void abrirDetalle(OrdenServicio orden) {
        if (orden == null) {
            return;
        }
        try {
            detalle().mostrar(orden.getId(), this::cargarTabla);
        } catch (NegocioException e) {
            notificador.advertencia(e.getMessage());
        }
    }

    private OrdenDetalleController detalle() {
        if (detalle == null) {
            detalle = (OrdenDetalleController) cargador.cargar("orden-detalle-view").controlador();
        }
        return detalle;
    }

    private void cargarTabla() {
        Page<OrdenServicio> pagina = ordenService.listar(txtBuscar.getText(), cmbEstado.getValue(),
                paginador.getPagina(), paginador.getTamanio());
        datos.setAll(pagina.getContent());
        paginador.actualizar(pagina);
        Animations.fadeIn(tabla, Duration.millis(260)).play();
    }

    private void limpiarNuevaOrden() {
        Formularios.limpiarErrores(txtVehiculo, txtKilometraje, txtProblema);
        autoVehiculo.setTexto("");
        elegirVehiculo(null);
        txtKilometraje.clear();
        txtProblema.clear();
        cmbMecanico.setValue(null);
    }
}
