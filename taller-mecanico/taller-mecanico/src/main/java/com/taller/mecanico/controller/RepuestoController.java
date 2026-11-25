package com.taller.mecanico.controller;

import atlantafx.base.util.Animations;
import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.Categoria;
import com.taller.mecanico.model.Repuesto;
import com.taller.mecanico.service.RepuestoService;
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
public class RepuestoController implements Refrescable {
    private final RepuestoService service;
    private final Notificador notificador;

    @FXML private VBox panelFormulario;
    @FXML private Label lblTituloFormulario;
    @FXML private Label lblSubtituloFormulario;
    @FXML private TextField txtNombre;
    @FXML private TextField txtCodigoRepuesto;
    @FXML private TextArea txtDescripcion;
    @FXML private TextField txtPrecio;
    @FXML private ComboBox<Categoria> cmbCategoria;
    @FXML private TextField txtStockMinimo;
    @FXML private Label lblStockActual;
    @FXML private TextField txtBuscar;

    @FXML private TableView<Repuesto> tabla;
    @FXML private TableColumn<Repuesto, String> colId;
    @FXML private TableColumn<Repuesto, String> colNombre;
    @FXML private TableColumn<Repuesto, String> colCodigo;
    @FXML private TableColumn<Repuesto, BigDecimal> colPrecio;
    @FXML private TableColumn<Repuesto, String> colCategoria;
    @FXML private TableColumn<Repuesto, Repuesto> colStock;

    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;
    @FXML private Paginador paginador;

    private final ObservableList<Repuesto> datos = FXCollections.observableArrayList();

    private Long idEditando;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        colId.setCellFactory(Tablas.celdaId());
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigoRepuesto()));
        colPrecio.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPrecio()));
        colPrecio.setCellFactory(Tablas.celdaMoneda());
        colCategoria.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCategoria() != null ? c.getValue().getCategoria().getNombre() : null));
        colCategoria.setCellFactory(Tablas.celdaChip("Sin categoria"));
        colStock.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colStock.setCellFactory(Tablas.celdaStock());

        Tablas.configurar(tabla, "Registra un repuesto o cambia el texto de busqueda.", this::editar);
        tabla.setItems(datos);

        var seleccion = tabla.getSelectionModel().selectedItemProperty();
        btnEditar.disableProperty().bind(seleccion.isNull());
        btnEliminar.disableProperty().bind(seleccion.isNull());

        paginador.setOnCambio(this::cargarTabla);
        txtBuscar.textProperty().addListener((obs, a, b) -> paginador.irAPrimera());

        cmbCategoria.setCellFactory(lv -> new CeldaCategoria());
        cmbCategoria.setButtonCell(new CeldaCategoria());

        Formularios.soloDecimal(txtPrecio);
        Formularios.soloDigitos(txtStockMinimo, 5);
        Formularios.limpiarAlEditar(txtNombre, txtCodigoRepuesto, txtDescripcion, txtPrecio, txtStockMinimo);
    }

    @Override
    public void refrescar() {
        cargarCategorias();
        cargarTabla();
    }

    @FXML
    private void onNuevo() {
        tabla.getSelectionModel().clearSelection();
        limpiarFormulario();
        idEditando = null;
        lblTituloFormulario.setText("Nuevo repuesto");
        lblSubtituloFormulario.setText("Agrega un repuesto al catalogo");
        abrirFormulario();
    }

    @FXML
    private void onEditar() {
        editar(tabla.getSelectionModel().getSelectedItem());
    }

    private void editar(Repuesto p) {
        if (p == null) {
            return;
        }
        limpiarFormulario();
        idEditando = p.getId();
        txtNombre.setText(p.getNombre());
        txtCodigoRepuesto.setText(p.getCodigoRepuesto());
        txtDescripcion.setText(textoODefecto(p.getDescripcion()));
        txtPrecio.setText(p.getPrecio() != null ? p.getPrecio().toPlainString() : "");
        cmbCategoria.setValue(p.getCategoria());
        txtStockMinimo.setText(String.valueOf(p.getStockMinimo()));
        lblStockActual.setText(p.getStock() + " unidades");
        lblTituloFormulario.setText("Editar repuesto");
        lblSubtituloFormulario.setText("Registro #" + p.getId() + " · " + p.getNombre());
        abrirFormulario();
    }

    @FXML
    private void onCerrarFormulario() {
        notificador.cerrarPanel();
    }

    @FXML
    private void onGuardar() {
        Formularios.limpiarErrores(txtNombre, txtCodigoRepuesto, txtDescripcion, txtPrecio, txtStockMinimo);

        BigDecimal precio = null;
        String textoPrecio = txtPrecio.getText().trim().replace(",", ".");
        if (!textoPrecio.isEmpty()) {
            try {
                precio = new BigDecimal(textoPrecio);
            } catch (NumberFormatException e) {
                Formularios.marcarError(txtPrecio, "El precio debe ser un numero valido");
                return;
            }
        }

        Repuesto repuesto = Repuesto.builder()
                .id(idEditando)
                .nombre(txtNombre.getText().trim())
                .codigoRepuesto(txtCodigoRepuesto.getText().trim())
                .descripcion(txtDescripcion.getText().trim())
                .precio(precio)
                .categoria(cmbCategoria.getValue())
                .stockMinimo(txtStockMinimo.getText().isBlank() ? 5 : Integer.parseInt(txtStockMinimo.getText()))
                .build();

        try {
            boolean esNuevo = repuesto.getId() == null;
            Repuesto guardado = service.guardar(repuesto);
            notificador.cerrarPanel();
            notificador.exito(esNuevo
                    ? "Repuesto \"" + guardado.getNombre() + "\" registrado."
                    : "Repuesto \"" + guardado.getNombre() + "\" actualizado.");
            cargarTabla();
        } catch (ConstraintViolationException e) {
            List<String> otros = Formularios.mostrarViolaciones(e, Map.of(
                    "nombre", txtNombre, "codigoRepuesto", txtCodigoRepuesto,
                    "descripcion", txtDescripcion, "precio", txtPrecio, "stockMinimo", txtStockMinimo));
            otros.forEach(notificador::advertencia);
        } catch (NegocioException e) {
            Formularios.marcarError(txtCodigoRepuesto, e.getMessage());
        } catch (Exception e) {
            notificador.error("Error inesperado: " + e.getMessage());
        }
    }

    @FXML
    private void onEliminar() {
        Repuesto seleccionado = tabla.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            return;
        }
        notificador.confirmar("¿Eliminar " + seleccionado.getNombre() + "?",
                "Esta accion no se puede deshacer. El repuesto con codigo "
                        + seleccionado.getCodigoRepuesto() + " se eliminara permanentemente.",
                "Eliminar",
                () -> {
                    try {
                        service.eliminar(seleccionado.getId());
                        notificador.exito("Repuesto eliminado.");
                        cargarTabla();
                    } catch (NegocioException e) {
                        notificador.error(e.getMessage());
                    }
                });
    }

    private void cargarCategorias() {
        ObservableList<Categoria> categorias = FXCollections.observableArrayList();
        categorias.add(null);
        categorias.addAll(service.listarCategorias());
        cmbCategoria.setItems(categorias);
    }

    private void cargarTabla() {
        Page<Repuesto> pagina = service.listar(txtBuscar.getText(), paginador.getPagina(), paginador.getTamanio());
        datos.setAll(pagina.getContent());
        paginador.actualizar(pagina);
        Animations.fadeIn(tabla, Duration.millis(260)).play();
    }

    private void abrirFormulario() {
        notificador.abrirPanel(panelFormulario);
        Platform.runLater(txtCodigoRepuesto::requestFocus);
    }

    private void limpiarFormulario() {
        Formularios.limpiarErrores(txtNombre, txtCodigoRepuesto, txtDescripcion, txtPrecio, txtStockMinimo);
        txtNombre.clear();
        txtCodigoRepuesto.clear();
        txtDescripcion.clear();
        txtPrecio.clear();
        cmbCategoria.setValue(null);
        txtStockMinimo.setText("5");
        lblStockActual.setText("0 unidades (nuevo)");
    }

    private String textoODefecto(String valor) {
        return valor == null ? "" : valor;
    }

    private static class CeldaCategoria extends ListCell<Categoria> {
        @Override
        protected void updateItem(Categoria categoria, boolean vacio) {
            super.updateItem(categoria, vacio);
            if (vacio) {
                setText(null);
            } else {
                setText(categoria == null ? "Sin categoria" : categoria.getNombre());
            }
        }
    }
}
