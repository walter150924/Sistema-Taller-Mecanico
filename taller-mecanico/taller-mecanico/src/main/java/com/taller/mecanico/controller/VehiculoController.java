package com.taller.mecanico.controller;

import atlantafx.base.util.Animations;
import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.Cliente;
import com.taller.mecanico.model.EstadoOrden;
import com.taller.mecanico.model.OrdenServicio;
import com.taller.mecanico.model.Vehiculo;
import com.taller.mecanico.service.ClienteService;
import com.taller.mecanico.service.VehiculoService;
import com.taller.mecanico.ui.Autocompletar;
import com.taller.mecanico.ui.Formularios;
import com.taller.mecanico.ui.Notificador;
import com.taller.mecanico.ui.Paginador;
import com.taller.mecanico.ui.Refrescable;
import com.taller.mecanico.ui.Tablas;
import jakarta.validation.ConstraintViolationException;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class VehiculoController implements Refrescable {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String FORMATO_PLACA_PARCIAL = "[A-Z0-9]{0,3}(-[0-9]{0,3})?";
    private static final int DIGITOS_ANIO = 4;
    private static final int DIGITOS_KILOMETRAJE = 7;

    private final VehiculoService service;
    private final ClienteService clienteService;
    private final Notificador notificador;

    @FXML private VBox panelFormulario;
    @FXML private Label lblTituloFormulario;
    @FXML private Label lblSubtituloFormulario;
    @FXML private TextField txtCliente;
    @FXML private TextField txtPlaca;
    @FXML private TextField txtAnio;
    @FXML private TextField txtMarca;
    @FXML private TextField txtModelo;
    @FXML private TextField txtColor;
    @FXML private TextField txtKilometraje;
    @FXML private TextField txtBuscar;

    @FXML private TableView<Vehiculo> tabla;
    @FXML private TableColumn<Vehiculo, String> colPlaca;
    @FXML private TableColumn<Vehiculo, String> colVehiculo;
    @FXML private TableColumn<Vehiculo, String> colPropietario;
    @FXML private TableColumn<Vehiculo, String> colColor;
    @FXML private TableColumn<Vehiculo, String> colKilometraje;

    @FXML private VBox panelHistorial;
    @FXML private Label lblTituloHistorial;
    @FXML private Label lblSubtituloHistorial;
    @FXML private TableView<OrdenServicio> tablaHistorial;
    @FXML private TableColumn<OrdenServicio, String> colHistCodigo;
    @FXML private TableColumn<OrdenServicio, String> colHistFecha;
    @FXML private TableColumn<OrdenServicio, String> colHistKilometraje;
    @FXML private TableColumn<OrdenServicio, String> colHistDiagnostico;
    @FXML private TableColumn<OrdenServicio, EstadoOrden> colHistEstado;
    @FXML private TableColumn<OrdenServicio, BigDecimal> colHistTotal;

    @FXML private Button btnHistorial;
    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;
    @FXML private Paginador paginador;

    private final ObservableList<Vehiculo> datos = FXCollections.observableArrayList();
    private Autocompletar<Cliente> autoCliente;
    private Cliente propietario;
    private Long idEditando;

    @FXML
    public void initialize() {
        configurarTabla();
        configurarHistorial();
        configurarFormulario();
        paginador.setOnCambio(this::cargarTabla);
        txtBuscar.textProperty().addListener((obs, anterior, actual) -> paginador.irAPrimera());
    }

    @Override
    public void refrescar() {
        cargarTabla();
    }

    private void configurarTabla() {
        colPlaca.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPlaca()));
        colPlaca.setCellFactory(Tablas.celdaChip("—"));
        colVehiculo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescripcion()));
        colPropietario.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCliente().getNombreCompleto()));
        colPropietario.setCellFactory(Tablas.celdaAvatar());
        colColor.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getColor()));
        colColor.setCellFactory(Tablas.celdaOpcional());
        colKilometraje.setCellValueFactory(c -> new SimpleStringProperty(kilometros(c.getValue().getKilometraje())));

        Tablas.configurar(tabla, "Registra un vehiculo o cambia el texto de busqueda.", this::editar);
        tabla.setItems(datos);

        var seleccion = tabla.getSelectionModel().selectedItemProperty();
        btnHistorial.disableProperty().bind(seleccion.isNull());
        btnEditar.disableProperty().bind(seleccion.isNull());
        btnEliminar.disableProperty().bind(seleccion.isNull());
    }

    private void configurarHistorial() {
        colHistCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        colHistFecha.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFechaIngreso().format(FECHA)));
        colHistKilometraje.setCellValueFactory(c -> new SimpleStringProperty(kilometros(c.getValue().getKilometrajeIngreso())));
        colHistDiagnostico.setCellValueFactory(c -> new SimpleStringProperty(resumenTrabajo(c.getValue())));
        colHistEstado.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getEstado()));
        colHistEstado.setCellFactory(Tablas.celdaEstadoOrden());
        colHistTotal.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getTotal()));
        colHistTotal.setCellFactory(Tablas.celdaMoneda());
        Tablas.configurar(tablaHistorial, Feather.CLOCK, "Sin historial",
                "Este vehiculo aun no tiene ordenes de servicio.", orden -> { });
    }

    private void configurarFormulario() {
        txtPlaca.setTextFormatter(new TextFormatter<String>(cambio -> {
            cambio.setText(cambio.getText().toUpperCase());
            return cambio.getControlNewText().matches(FORMATO_PLACA_PARCIAL) ? cambio : null;
        }));
        Formularios.soloDigitos(txtAnio, DIGITOS_ANIO);
        Formularios.soloDigitos(txtKilometraje, DIGITOS_KILOMETRAJE);
        Formularios.limpiarAlEditar(txtCliente, txtPlaca, txtAnio, txtMarca, txtModelo, txtColor, txtKilometraje);

        autoCliente = new Autocompletar<>(txtCliente, clienteService::autocompletar, this::seleccionarPropietario)
                .presentacion(Cliente::getNombreCompleto, c -> "DNI " + c.getDni(), c -> null)
                .icono(this::avatar)
                .minimoCaracteres(2);
        txtCliente.textProperty().addListener((obs, anterior, actual) -> {
            if (propietario != null && !actual.equals(propietario.getNombreCompleto())) {
                propietario = null;
            }
        });
    }

    private void seleccionarPropietario(Cliente cliente) {
        propietario = cliente;
        autoCliente.setTexto(cliente.getNombreCompleto());
        Formularios.limpiarError(txtCliente);
        txtPlaca.requestFocus();
    }

    @FXML
    private void onNuevo() {
        tabla.getSelectionModel().clearSelection();
        limpiarFormulario();
        idEditando = null;
        lblTituloFormulario.setText("Nuevo vehiculo");
        lblSubtituloFormulario.setText("Registra el vehiculo y su propietario");
        abrirFormulario(txtCliente);
    }

    @FXML
    private void onEditar() {
        editar(tabla.getSelectionModel().getSelectedItem());
    }

    private void editar(Vehiculo vehiculo) {
        if (vehiculo == null) {
            return;
        }
        limpiarFormulario();
        idEditando = vehiculo.getId();
        seleccionarPropietario(vehiculo.getCliente());
        txtPlaca.setText(vehiculo.getPlaca());
        txtAnio.setText(String.valueOf(vehiculo.getAnio()));
        txtMarca.setText(vehiculo.getMarca());
        txtModelo.setText(vehiculo.getModelo());
        txtColor.setText(vehiculo.getColor() == null ? "" : vehiculo.getColor());
        txtKilometraje.setText(String.valueOf(vehiculo.getKilometraje()));
        lblTituloFormulario.setText("Editar vehiculo");
        lblSubtituloFormulario.setText(vehiculo.getPlaca() + " · " + vehiculo.getDescripcion());
        abrirFormulario(txtPlaca);
    }

    @FXML
    private void onHistorial() {
        Vehiculo vehiculo = tabla.getSelectionModel().getSelectedItem();
        if (vehiculo == null) {
            return;
        }
        List<OrdenServicio> ordenes = service.historial(vehiculo.getId());
        lblTituloHistorial.setText("Historial de " + vehiculo.getPlaca());
        lblSubtituloHistorial.setText(vehiculo.getDescripcion() + " · " + ordenes.size() + " ordenes registradas");
        tablaHistorial.getItems().setAll(ordenes);
        notificador.abrirPanel(panelHistorial);
    }

    @FXML
    private void onCerrarFormulario() {
        notificador.cerrarPanel();
    }

    @FXML
    private void onGuardar() {
        Formularios.limpiarErrores(txtCliente, txtPlaca, txtAnio, txtMarca, txtModelo, txtColor, txtKilometraje);
        if (propietario == null) {
            Formularios.marcarError(txtCliente, "Selecciona el propietario de la lista");
            return;
        }
        String color = txtColor.getText().trim();
        Vehiculo vehiculo = Vehiculo.builder()
                .id(idEditando)
                .cliente(propietario)
                .placa(txtPlaca.getText().trim())
                .anio(enteroONulo(txtAnio))
                .marca(txtMarca.getText().trim())
                .modelo(txtModelo.getText().trim())
                .color(color.isEmpty() ? null : color)
                .kilometraje(enteroONulo(txtKilometraje))
                .build();
        try {
            Vehiculo guardado = service.guardar(vehiculo);
            notificador.cerrarPanel();
            notificador.exito("El vehiculo " + guardado.getPlaca() + " se guardo correctamente.");
            cargarTabla();
        } catch (ConstraintViolationException e) {
            List<String> otros = Formularios.mostrarViolaciones(e, Map.of(
                    "cliente", txtCliente, "placa", txtPlaca, "anio", txtAnio, "marca", txtMarca,
                    "modelo", txtModelo, "color", txtColor, "kilometraje", txtKilometraje));
            otros.forEach(notificador::advertencia);
        } catch (NegocioException e) {
            Formularios.marcarError(txtPlaca, e.getMessage());
        }
    }

    @FXML
    private void onEliminar() {
        Vehiculo seleccionado = tabla.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            return;
        }
        notificador.confirmar("¿Eliminar el vehiculo " + seleccionado.getPlaca() + "?",
                "Esta accion no se puede deshacer.",
                "Eliminar",
                () -> {
                    try {
                        service.eliminar(seleccionado.getId());
                        notificador.exito("Vehiculo eliminado.");
                        cargarTabla();
                    } catch (NegocioException e) {
                        notificador.error(e.getMessage());
                    }
                });
    }

    private void cargarTabla() {
        Page<Vehiculo> pagina = service.listar(txtBuscar.getText(), paginador.getPagina(), paginador.getTamanio());
        datos.setAll(pagina.getContent());
        paginador.actualizar(pagina);
        Animations.fadeIn(tabla, Duration.millis(260)).play();
    }

    private void abrirFormulario(TextField campoInicial) {
        notificador.abrirPanel(panelFormulario);
        Platform.runLater(campoInicial::requestFocus);
    }

    private void limpiarFormulario() {
        Formularios.limpiarErrores(txtCliente, txtPlaca, txtAnio, txtMarca, txtModelo, txtColor, txtKilometraje);
        propietario = null;
        autoCliente.setTexto("");
        txtPlaca.clear();
        txtAnio.clear();
        txtMarca.clear();
        txtModelo.clear();
        txtColor.clear();
        txtKilometraje.clear();
    }

    private StackPane avatar(Cliente cliente) {
        StackPane avatar = new StackPane(new Label(Tablas.iniciales(cliente.getNombreCompleto())));
        avatar.getStyleClass().add("avatar");
        return avatar;
    }

    private static Integer enteroONulo(TextField campo) {
        String texto = campo.getText().trim();
        return texto.isEmpty() ? null : Integer.valueOf(texto);
    }

    private static String kilometros(int kilometraje) {
        return String.format(Tablas.LOCALE, "%,d km", kilometraje);
    }

    private static String resumenTrabajo(OrdenServicio orden) {
        return orden.getDiagnostico() == null ? orden.getProblemaReportado() : orden.getDiagnostico();
    }
}
