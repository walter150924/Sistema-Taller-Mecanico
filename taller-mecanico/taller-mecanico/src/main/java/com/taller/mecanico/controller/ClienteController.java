package com.taller.mecanico.controller;

import atlantafx.base.util.Animations;
import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.Cliente;
import com.taller.mecanico.service.ClienteService;
import com.taller.mecanico.ui.Formularios;
import com.taller.mecanico.ui.Notificador;
import com.taller.mecanico.ui.Paginador;
import com.taller.mecanico.ui.Refrescable;
import com.taller.mecanico.ui.Tablas;
import jakarta.validation.ConstraintViolationException;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ClienteController implements Refrescable {
    private final ClienteService service;
    private final Notificador notificador;

    @FXML private VBox panelFormulario;
    @FXML private Label lblTituloFormulario;
    @FXML private Label lblSubtituloFormulario;
    @FXML private TextField txtNombre;
    @FXML private TextField txtApellido;
    @FXML private TextField txtDni;
    @FXML private TextField txtEmail;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtBuscar;

    @FXML private TableView<Cliente> tabla;
    @FXML private TableColumn<Cliente, String> colId;
    @FXML private TableColumn<Cliente, String> colNombre;
    @FXML private TableColumn<Cliente, String> colDni;
    @FXML private TableColumn<Cliente, String> colEmail;
    @FXML private TableColumn<Cliente, String> colTelefono;

    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;
    @FXML private Paginador paginador;

    private final ObservableList<Cliente> datos = FXCollections.observableArrayList();

    private Long idEditando;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        colId.setCellFactory(Tablas.celdaId());
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreCompleto()));
        colNombre.setCellFactory(Tablas.celdaAvatar());
        colDni.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDni()));
        colEmail.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEmail()));
        colEmail.setCellFactory(Tablas.celdaOpcional());
        colTelefono.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTelefono()));
        colTelefono.setCellFactory(Tablas.celdaOpcional());

        Tablas.configurar(tabla, "Registra un cliente o cambia el texto de busqueda.", this::editar);
        tabla.setItems(datos);

        var seleccion = tabla.getSelectionModel().selectedItemProperty();
        btnEditar.disableProperty().bind(seleccion.isNull());
        btnEliminar.disableProperty().bind(seleccion.isNull());

        paginador.setOnCambio(this::cargarTabla);
        txtBuscar.textProperty().addListener((obs, a, b) -> paginador.irAPrimera());

        Formularios.soloDigitos(txtDni, 8);
        Formularios.soloDigitos(txtTelefono, 9);
        Formularios.limpiarAlEditar(txtNombre, txtApellido, txtDni, txtEmail, txtTelefono);
    }

    @Override
    public void refrescar() {
        cargarTabla();
    }

    @FXML
    private void onNuevo() {
        tabla.getSelectionModel().clearSelection();
        limpiarFormulario();
        idEditando = null;
        lblTituloFormulario.setText("Nuevo cliente");
        lblSubtituloFormulario.setText("Completa los datos para registrarla");
        abrirFormulario();
    }

    @FXML
    private void onEditar() {
        editar(tabla.getSelectionModel().getSelectedItem());
    }

    private void editar(Cliente p) {
        if (p == null) {
            return;
        }
        limpiarFormulario();
        idEditando = p.getId();
        txtNombre.setText(p.getNombre());
        txtApellido.setText(p.getApellido());
        txtDni.setText(p.getDni());
        txtEmail.setText(textoODefecto(p.getEmail()));
        txtTelefono.setText(textoODefecto(p.getTelefono()));
        lblTituloFormulario.setText("Editar cliente");
        lblSubtituloFormulario.setText("Registro #" + p.getId() + " · " + p.getNombreCompleto());
        abrirFormulario();
    }

    @FXML
    private void onCerrarFormulario() {
        notificador.cerrarPanel();
    }

    @FXML
    private void onGuardar() {
        Formularios.limpiarErrores(txtNombre, txtApellido, txtDni, txtEmail, txtTelefono);
        Cliente cliente = Cliente.builder()
                .id(idEditando)
                .nombre(txtNombre.getText().trim())
                .apellido(txtApellido.getText().trim())
                .dni(txtDni.getText().trim())
                .email(txtEmail.getText().trim())
                .telefono(txtTelefono.getText().trim())
                .build();

        try {
            boolean esNuevo = cliente.getId() == null;
            Cliente guardado = service.guardar(cliente);
            notificador.cerrarPanel();
            notificador.exito(esNuevo
                    ? guardado.getNombreCompleto() + " se registro correctamente."
                    : "Los cambios de " + guardado.getNombreCompleto() + " se guardaron.");
            cargarTabla();
        } catch (ConstraintViolationException e) {
            List<String> otros = Formularios.mostrarViolaciones(e, Map.of(
                    "nombre", txtNombre, "apellido", txtApellido, "dni", txtDni,
                    "email", txtEmail, "telefono", txtTelefono));
            otros.forEach(notificador::advertencia);
        } catch (NegocioException e) {
            Formularios.marcarError(txtDni, e.getMessage());
        } catch (Exception e) {
            notificador.error("Error inesperado: " + e.getMessage());
        }
    }

    @FXML
    private void onEliminar() {
        Cliente seleccionado = tabla.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            return;
        }
        notificador.confirmar("¿Eliminar a " + seleccionado.getNombreCompleto() + "?",
                "Esta accion no se puede deshacer. El registro con DNI "
                        + seleccionado.getDni() + " se eliminara permanentemente.",
                "Eliminar",
                () -> {
                    try {
                        service.eliminar(seleccionado.getId());
                        notificador.exito("Registro eliminado.");
                        cargarTabla();
                    } catch (NegocioException e) {
                        notificador.error(e.getMessage());
                    }
                });
    }

    private void cargarTabla() {
        Page<Cliente> pagina = service.listar(txtBuscar.getText(), paginador.getPagina(), paginador.getTamanio());
        datos.setAll(pagina.getContent());
        paginador.actualizar(pagina);
        Animations.fadeIn(tabla, Duration.millis(260)).play();
    }

    private void abrirFormulario() {
        notificador.abrirPanel(panelFormulario);
        Platform.runLater(txtDni::requestFocus);
    }

    private void limpiarFormulario() {
        Formularios.limpiarErrores(txtNombre, txtApellido, txtDni, txtEmail, txtTelefono);
        txtNombre.clear();
        txtApellido.clear();
        txtDni.clear();
        txtEmail.clear();
        txtTelefono.clear();
    }

    private String textoODefecto(String valor) {
        return valor == null ? "" : valor;
    }
}
