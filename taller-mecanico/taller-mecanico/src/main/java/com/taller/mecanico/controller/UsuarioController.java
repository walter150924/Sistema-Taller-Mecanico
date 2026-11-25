package com.taller.mecanico.controller;

import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.util.Animations;
import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.Rol;
import com.taller.mecanico.model.Usuario;
import com.taller.mecanico.service.UsuarioService;
import com.taller.mecanico.ui.Formularios;
import com.taller.mecanico.ui.Notificador;
import com.taller.mecanico.ui.Paginador;
import com.taller.mecanico.ui.Refrescable;
import com.taller.mecanico.ui.Tablas;
import jakarta.validation.ConstraintViolationException;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
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
public class UsuarioController implements Refrescable {
    private final UsuarioService service;
    private final Notificador notificador;

    @FXML private VBox panelFormulario;
    @FXML private Label lblTituloFormulario;
    @FXML private Label lblSubtituloFormulario;
    @FXML private TextField txtNombreCompleto;
    @FXML private ComboBox<Rol> cmbRol;
    @FXML private TextField txtNombreUsuario;
    @FXML private Label lblClave;
    @FXML private PasswordField txtClave;
    @FXML private ToggleSwitch swActivo;
    @FXML private TextField txtBuscar;

    @FXML private TableView<Usuario> tabla;
    @FXML private TableColumn<Usuario, String> colId;
    @FXML private TableColumn<Usuario, String> colNombre;
    @FXML private TableColumn<Usuario, String> colUsuario;
    @FXML private TableColumn<Usuario, String> colRol;
    @FXML private TableColumn<Usuario, Boolean> colActivo;

    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;
    @FXML private Paginador paginador;

    private final ObservableList<Usuario> datos = FXCollections.observableArrayList();
    private Long idEditando;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        colId.setCellFactory(Tablas.celdaId());
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreCompleto()));
        colNombre.setCellFactory(Tablas.celdaAvatar());
        colUsuario.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreUsuario()));
        colRol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRol().getDescripcion()));
        colRol.setCellFactory(Tablas.celdaChip("—"));
        colActivo.setCellValueFactory(c -> new SimpleBooleanProperty(c.getValue().isActivo()).asObject());
        colActivo.setCellFactory(Tablas.celdaActivo());

        Tablas.configurar(tabla, "Registra un usuario o cambia el texto de busqueda.", this::editar);
        tabla.setItems(datos);

        var seleccion = tabla.getSelectionModel().selectedItemProperty();
        btnEditar.disableProperty().bind(seleccion.isNull());
        btnEliminar.disableProperty().bind(seleccion.isNull());

        cmbRol.getItems().setAll(Rol.values());
        cmbRol.valueProperty().addListener((obs, anterior, actual) -> Formularios.limpiarError(cmbRol));
        paginador.setOnCambio(this::cargarTabla);
        txtBuscar.textProperty().addListener((obs, anterior, actual) -> paginador.irAPrimera());
        txtNombreUsuario.setTextFormatter(new TextFormatter<String>(cambio -> {
            cambio.setText(cambio.getText().toLowerCase());
            return cambio;
        }));
        Formularios.limpiarAlEditar(txtNombreCompleto, txtNombreUsuario, txtClave);
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
        lblTituloFormulario.setText("Nuevo usuario");
        lblSubtituloFormulario.setText("Completa los datos de acceso");
        lblClave.setText("Contraseña *");
        abrirFormulario();
    }

    @FXML
    private void onEditar() {
        editar(tabla.getSelectionModel().getSelectedItem());
    }

    private void editar(Usuario usuario) {
        if (usuario == null) {
            return;
        }
        limpiarFormulario();
        idEditando = usuario.getId();
        txtNombreCompleto.setText(usuario.getNombreCompleto());
        cmbRol.setValue(usuario.getRol());
        txtNombreUsuario.setText(usuario.getNombreUsuario());
        swActivo.setSelected(usuario.isActivo());
        lblTituloFormulario.setText("Editar usuario");
        lblSubtituloFormulario.setText("Usuario #" + usuario.getId() + " · " + usuario.getNombreUsuario());
        lblClave.setText("Nueva contraseña (dejala vacia para mantener la actual)");
        abrirFormulario();
    }

    @FXML
    private void onCerrarFormulario() {
        notificador.cerrarPanel();
    }

    @FXML
    private void onGuardar() {
        Formularios.limpiarErrores(txtNombreCompleto, cmbRol, txtNombreUsuario, txtClave);
        Usuario usuario = Usuario.builder()
                .id(idEditando)
                .nombreCompleto(txtNombreCompleto.getText().trim())
                .rol(cmbRol.getValue())
                .nombreUsuario(txtNombreUsuario.getText().trim())
                .activo(swActivo.isSelected())
                .build();
        try {
            boolean esNuevo = idEditando == null;
            Usuario guardado = service.guardar(usuario, txtClave.getText());
            notificador.cerrarPanel();
            notificador.exito(esNuevo
                    ? guardado.getNombreCompleto() + " ya puede iniciar sesion."
                    : "Los cambios de " + guardado.getNombreCompleto() + " se guardaron.");
            cargarTabla();
        } catch (ConstraintViolationException e) {
            List<String> otros = Formularios.mostrarViolaciones(e, Map.of(
                    "nombreCompleto", txtNombreCompleto, "rol", cmbRol, "nombreUsuario", txtNombreUsuario));
            otros.forEach(notificador::advertencia);
        } catch (NegocioException e) {
            notificador.advertencia(e.getMessage());
        }
    }

    @FXML
    private void onEliminar() {
        Usuario seleccionado = tabla.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            return;
        }
        notificador.confirmar("¿Eliminar a " + seleccionado.getNombreCompleto() + "?",
                "El usuario " + seleccionado.getNombreUsuario() + " ya no podra ingresar al sistema.",
                "Eliminar",
                () -> {
                    try {
                        service.eliminar(seleccionado.getId());
                        notificador.exito("Usuario eliminado.");
                        cargarTabla();
                    } catch (NegocioException e) {
                        notificador.error(e.getMessage());
                    }
                });
    }

    private void cargarTabla() {
        Page<Usuario> pagina = service.listar(txtBuscar.getText(), paginador.getPagina(), paginador.getTamanio());
        datos.setAll(pagina.getContent());
        paginador.actualizar(pagina);
        Animations.fadeIn(tabla, Duration.millis(260)).play();
    }

    private void abrirFormulario() {
        notificador.abrirPanel(panelFormulario);
        Platform.runLater(txtNombreCompleto::requestFocus);
    }

    private void limpiarFormulario() {
        Formularios.limpiarErrores(txtNombreCompleto, cmbRol, txtNombreUsuario, txtClave);
        txtNombreCompleto.clear();
        cmbRol.setValue(null);
        txtNombreUsuario.clear();
        txtClave.clear();
        swActivo.setSelected(true);
    }
}
