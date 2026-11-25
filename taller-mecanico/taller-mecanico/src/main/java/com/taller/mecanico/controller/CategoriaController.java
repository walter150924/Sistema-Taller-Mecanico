package com.taller.mecanico.controller;

import atlantafx.base.util.Animations;
import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.Categoria;
import com.taller.mecanico.service.CategoriaService;
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
public class CategoriaController implements Refrescable {
    private final CategoriaService service;
    private final Notificador notificador;

    @FXML private VBox panelFormulario;
    @FXML private Label lblTituloFormulario;
    @FXML private Label lblSubtituloFormulario;
    @FXML private TextField txtNombre;
    @FXML private TextField txtCodigoCategoria;
    @FXML private TextArea txtDescripcion;
    @FXML private TextField txtBuscar;

    @FXML private TableView<Categoria> tabla;
    @FXML private TableColumn<Categoria, String> colId;
    @FXML private TableColumn<Categoria, String> colNombre;
    @FXML private TableColumn<Categoria, String> colCodigo;
    @FXML private TableColumn<Categoria, String> colDescripcion;

    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;
    @FXML private Paginador paginador;

    private final ObservableList<Categoria> datos = FXCollections.observableArrayList();

    private Long idEditando;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        colId.setCellFactory(Tablas.celdaId());
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigoCategoria()));
        colCodigo.setCellFactory(Tablas.celdaChip("—"));
        colDescripcion.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescripcion()));
        colDescripcion.setCellFactory(Tablas.celdaOpcional());

        Tablas.configurar(tabla, "Registra una categoria o cambia el texto de busqueda.", this::editar);
        tabla.setItems(datos);

        var seleccion = tabla.getSelectionModel().selectedItemProperty();
        btnEditar.disableProperty().bind(seleccion.isNull());
        btnEliminar.disableProperty().bind(seleccion.isNull());

        paginador.setOnCambio(this::cargarTabla);
        txtBuscar.textProperty().addListener((obs, a, b) -> paginador.irAPrimera());

        Formularios.limpiarAlEditar(txtNombre, txtCodigoCategoria, txtDescripcion);
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
        lblTituloFormulario.setText("Nueva categoria");
        lblSubtituloFormulario.setText("Agrupa tus repuestos por categoria");
        abrirFormulario();
    }

    @FXML
    private void onEditar() {
        editar(tabla.getSelectionModel().getSelectedItem());
    }

    private void editar(Categoria c) {
        if (c == null) {
            return;
        }
        limpiarFormulario();
        idEditando = c.getId();
        txtNombre.setText(c.getNombre());
        txtCodigoCategoria.setText(c.getCodigoCategoria());
        txtDescripcion.setText(textoODefecto(c.getDescripcion()));
        lblTituloFormulario.setText("Editar categoria");
        lblSubtituloFormulario.setText("Registro #" + c.getId() + " · " + c.getNombre());
        abrirFormulario();
    }

    @FXML
    private void onCerrarFormulario() {
        notificador.cerrarPanel();
    }

    @FXML
    private void onGuardar() {
        Formularios.limpiarErrores(txtNombre, txtCodigoCategoria, txtDescripcion);
        Categoria categoria = Categoria.builder()
                .id(idEditando)
                .nombre(txtNombre.getText().trim())
                .codigoCategoria(txtCodigoCategoria.getText().trim())
                .descripcion(txtDescripcion.getText().trim())
                .build();

        try {
            boolean esNueva = categoria.getId() == null;
            Categoria guardada = service.guardar(categoria);
            notificador.cerrarPanel();
            notificador.exito(esNueva
                    ? "Categoria \"" + guardada.getNombre() + "\" registrada."
                    : "Categoria \"" + guardada.getNombre() + "\" actualizada.");
            cargarTabla();
        } catch (ConstraintViolationException e) {
            List<String> otros = Formularios.mostrarViolaciones(e, Map.of(
                    "nombre", txtNombre, "codigoCategoria", txtCodigoCategoria,
                    "descripcion", txtDescripcion));
            otros.forEach(notificador::advertencia);
        } catch (NegocioException e) {
            Formularios.marcarError(txtCodigoCategoria, e.getMessage());
        } catch (Exception e) {
            notificador.error("Error inesperado: " + e.getMessage());
        }
    }

    @FXML
    private void onEliminar() {
        Categoria seleccionada = tabla.getSelectionModel().getSelectedItem();
        if (seleccionada == null) {
            return;
        }
        notificador.confirmar("¿Eliminar la categoria " + seleccionada.getNombre() + "?",
                "Esta accion no se puede deshacer. La categoria se eliminara permanentemente.",
                "Eliminar",
                () -> {
                    try {
                        service.eliminar(seleccionada.getId());
                        notificador.exito("Categoria eliminada.");
                        cargarTabla();
                    } catch (NegocioException e) {
                        notificador.error(e.getMessage());
                    } catch (Exception e) {
                        notificador.error("No se pudo eliminar: " + e.getMessage());
                    }
                });
    }

    private void cargarTabla() {
        Page<Categoria> pagina = service.listar(txtBuscar.getText(), paginador.getPagina(), paginador.getTamanio());
        datos.setAll(pagina.getContent());
        paginador.actualizar(pagina);
        Animations.fadeIn(tabla, Duration.millis(260)).play();
    }

    private void abrirFormulario() {
        notificador.abrirPanel(panelFormulario);
        Platform.runLater(txtCodigoCategoria::requestFocus);
    }

    private void limpiarFormulario() {
        Formularios.limpiarErrores(txtNombre, txtCodigoCategoria, txtDescripcion);
        txtNombre.clear();
        txtCodigoCategoria.clear();
        txtDescripcion.clear();
    }

    private String textoODefecto(String valor) {
        return valor == null ? "" : valor;
    }
}
