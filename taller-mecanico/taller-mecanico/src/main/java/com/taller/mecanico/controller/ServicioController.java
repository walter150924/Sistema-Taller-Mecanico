package com.taller.mecanico.controller;

import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.util.Animations;
import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.Servicio;
import com.taller.mecanico.service.ServicioService;
import com.taller.mecanico.ui.Formularios;
import com.taller.mecanico.ui.Notificador;
import com.taller.mecanico.ui.Paginador;
import com.taller.mecanico.ui.Refrescable;
import com.taller.mecanico.ui.Tablas;
import jakarta.validation.ConstraintViolationException;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ServicioController implements Refrescable {
    private final ServicioService service;
    private final Notificador notificador;

    @FXML private VBox panelFormulario;
    @FXML private Label lblTituloFormulario;
    @FXML private Label lblSubtituloFormulario;
    @FXML private TextField txtNombre;
    @FXML private TextField txtDescripcion;
    @FXML private TextField txtPrecio;
    @FXML private ToggleSwitch swActivo;
    @FXML private TextField txtBuscar;

    @FXML private TableView<Servicio> tabla;
    @FXML private TableColumn<Servicio, String> colId;
    @FXML private TableColumn<Servicio, String> colNombre;
    @FXML private TableColumn<Servicio, String> colDescripcion;
    @FXML private TableColumn<Servicio, BigDecimal> colPrecio;
    @FXML private TableColumn<Servicio, Boolean> colActivo;

    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;
    @FXML private Paginador paginador;

    private final ObservableList<Servicio> datos = FXCollections.observableArrayList();
    private Long idEditando;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        colId.setCellFactory(Tablas.celdaId());
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colDescripcion.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescripcion()));
        colDescripcion.setCellFactory(Tablas.celdaOpcional());
        colPrecio.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPrecio()));
        colPrecio.setCellFactory(Tablas.celdaMoneda());
        colActivo.setCellValueFactory(c -> new SimpleBooleanProperty(c.getValue().isActivo()).asObject());
        colActivo.setCellFactory(Tablas.celdaActivo());

        Tablas.configurar(tabla, "Registra un servicio o cambia el texto de busqueda.", this::editar);
        tabla.setItems(datos);

        var seleccion = tabla.getSelectionModel().selectedItemProperty();
        btnEditar.disableProperty().bind(seleccion.isNull());
        btnEliminar.disableProperty().bind(seleccion.isNull());

        paginador.setOnCambio(this::cargarTabla);
        txtBuscar.textProperty().addListener((obs, anterior, actual) -> paginador.irAPrimera());
        Formularios.soloDecimal(txtPrecio);
        Formularios.limpiarAlEditar(txtNombre, txtDescripcion, txtPrecio);
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
        lblTituloFormulario.setText("Nuevo servicio");
        lblSubtituloFormulario.setText("Mano de obra que ofrece el taller");
        abrirFormulario();
    }

    @FXML
    private void onEditar() {
        editar(tabla.getSelectionModel().getSelectedItem());
    }

    private void editar(Servicio servicio) {
        if (servicio == null) {
            return;
        }
        limpiarFormulario();
        idEditando = servicio.getId();
        txtNombre.setText(servicio.getNombre());
        txtDescripcion.setText(servicio.getDescripcion() == null ? "" : servicio.getDescripcion());
        txtPrecio.setText(servicio.getPrecio().toPlainString());
        swActivo.setSelected(servicio.isActivo());
        lblTituloFormulario.setText("Editar servicio");
        lblSubtituloFormulario.setText("Servicio #" + servicio.getId() + " · " + servicio.getNombre());
        abrirFormulario();
    }

    @FXML
    private void onCerrarFormulario() {
        notificador.cerrarPanel();
    }

    @FXML
    private void onGuardar() {
        Formularios.limpiarErrores(txtNombre, txtDescripcion, txtPrecio);
        if (txtPrecio.getText().isBlank()) {
            Formularios.marcarError(txtPrecio, "Ingresa el precio de mano de obra");
            return;
        }
        String descripcion = txtDescripcion.getText().trim();
        Servicio servicio = Servicio.builder()
                .id(idEditando)
                .nombre(txtNombre.getText().trim())
                .descripcion(descripcion.isEmpty() ? null : descripcion)
                .precio(new BigDecimal(txtPrecio.getText().replace(",", ".")))
                .activo(swActivo.isSelected())
                .build();
        try {
            Servicio guardado = service.guardar(servicio);
            notificador.cerrarPanel();
            notificador.exito(guardado.getNombre() + " se guardo correctamente.");
            cargarTabla();
        } catch (ConstraintViolationException e) {
            List<String> otros = Formularios.mostrarViolaciones(e, Map.of(
                    "nombre", txtNombre, "descripcion", txtDescripcion, "precio", txtPrecio));
            otros.forEach(notificador::advertencia);
        } catch (NegocioException e) {
            Formularios.marcarError(txtNombre, e.getMessage());
        }
    }

    @FXML
    private void onEliminar() {
        Servicio seleccionado = tabla.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            return;
        }
        notificador.confirmar("¿Eliminar " + seleccionado.getNombre() + "?",
                "El servicio dejara de aparecer en el catalogo.",
                "Eliminar",
                () -> {
                    try {
                        service.eliminar(seleccionado.getId());
                        notificador.exito("Servicio eliminado.");
                        cargarTabla();
                    } catch (NegocioException e) {
                        notificador.error(e.getMessage());
                    }
                });
    }

    private void cargarTabla() {
        Page<Servicio> pagina = service.listar(txtBuscar.getText(), paginador.getPagina(), paginador.getTamanio());
        datos.setAll(pagina.getContent());
        paginador.actualizar(pagina);
        Animations.fadeIn(tabla, Duration.millis(260)).play();
    }

    private void abrirFormulario() {
        notificador.abrirPanel(panelFormulario);
        Platform.runLater(txtNombre::requestFocus);
    }

    private void limpiarFormulario() {
        Formularios.limpiarErrores(txtNombre, txtDescripcion, txtPrecio);
        txtNombre.clear();
        txtDescripcion.clear();
        txtPrecio.clear();
        swActivo.setSelected(true);
    }
}
