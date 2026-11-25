package com.taller.mecanico.controller;

import atlantafx.base.util.Animations;
import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.Compra;
import com.taller.mecanico.model.Proveedor;
import com.taller.mecanico.service.ProveedorService;
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
import org.kordamp.ikonli.feather.Feather;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProveedorController implements Refrescable {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int DIGITOS_RUC = 11;
    private static final int DIGITOS_TELEFONO = 9;

    private final ProveedorService service;
    private final Notificador notificador;

    @FXML private VBox panelFormulario;
    @FXML private Label lblTituloFormulario;
    @FXML private Label lblSubtituloFormulario;
    @FXML private TextField txtRuc;
    @FXML private TextField txtRazonSocial;
    @FXML private TextField txtDireccion;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtBuscar;

    @FXML private TableView<Proveedor> tabla;
    @FXML private TableColumn<Proveedor, String> colRuc;
    @FXML private TableColumn<Proveedor, String> colRazonSocial;
    @FXML private TableColumn<Proveedor, String> colDireccion;
    @FXML private TableColumn<Proveedor, String> colTelefono;

    @FXML private VBox panelCompras;
    @FXML private Label lblTituloCompras;
    @FXML private Label lblSubtituloCompras;
    @FXML private TableView<Compra> tablaCompras;
    @FXML private TableColumn<Compra, String> colCompraCodigo;
    @FXML private TableColumn<Compra, String> colCompraFecha;
    @FXML private TableColumn<Compra, String> colCompraDocumento;
    @FXML private TableColumn<Compra, BigDecimal> colCompraTotal;
    @FXML private TableColumn<Compra, String> colCompraEstado;

    @FXML private Button btnCompras;
    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;
    @FXML private Paginador paginador;

    private final ObservableList<Proveedor> datos = FXCollections.observableArrayList();
    private Long idEditando;

    @FXML
    public void initialize() {
        configurarTabla();
        configurarTablaCompras();
        paginador.setOnCambio(this::cargarTabla);
        txtBuscar.textProperty().addListener((obs, anterior, actual) -> paginador.irAPrimera());
        Formularios.soloDigitos(txtRuc, DIGITOS_RUC);
        Formularios.soloDigitos(txtTelefono, DIGITOS_TELEFONO);
        Formularios.limpiarAlEditar(txtRuc, txtRazonSocial, txtDireccion, txtTelefono);
    }

    @Override
    public void refrescar() {
        cargarTabla();
    }

    private void configurarTabla() {
        colRuc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRuc()));
        colRuc.setCellFactory(Tablas.celdaChip("—"));
        colRazonSocial.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRazonSocial()));
        colDireccion.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDireccion()));
        colDireccion.setCellFactory(Tablas.celdaOpcional());
        colTelefono.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTelefono()));
        colTelefono.setCellFactory(Tablas.celdaOpcional());

        Tablas.configurar(tabla, "Registra un proveedor o cambia el texto de busqueda.", this::editar);
        tabla.setItems(datos);

        var seleccion = tabla.getSelectionModel().selectedItemProperty();
        btnCompras.disableProperty().bind(seleccion.isNull());
        btnEditar.disableProperty().bind(seleccion.isNull());
        btnEliminar.disableProperty().bind(seleccion.isNull());
    }

    private void configurarTablaCompras() {
        colCompraCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        colCompraFecha.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFecha().format(FECHA)));
        colCompraDocumento.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTipoDocumento().getDescripcion() + " " + c.getValue().getNumeroDocumento()));
        colCompraTotal.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getTotal()));
        colCompraTotal.setCellFactory(Tablas.celdaMoneda());
        colCompraEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstado().name()));
        colCompraEstado.setCellFactory(Tablas.celdaEstado());
        Tablas.configurar(tablaCompras, Feather.ARCHIVE, "Sin compras",
                "Aun no se registraron compras a este proveedor.", compra -> { });
    }

    @FXML
    private void onNuevo() {
        tabla.getSelectionModel().clearSelection();
        limpiarFormulario();
        idEditando = null;
        lblTituloFormulario.setText("Nuevo proveedor");
        lblSubtituloFormulario.setText("Empresa que vende repuestos al taller");
        abrirFormulario();
    }

    @FXML
    private void onEditar() {
        editar(tabla.getSelectionModel().getSelectedItem());
    }

    private void editar(Proveedor proveedor) {
        if (proveedor == null) {
            return;
        }
        limpiarFormulario();
        idEditando = proveedor.getId();
        txtRuc.setText(proveedor.getRuc());
        txtRazonSocial.setText(proveedor.getRazonSocial());
        txtDireccion.setText(proveedor.getDireccion() == null ? "" : proveedor.getDireccion());
        txtTelefono.setText(proveedor.getTelefono() == null ? "" : proveedor.getTelefono());
        lblTituloFormulario.setText("Editar proveedor");
        lblSubtituloFormulario.setText("RUC " + proveedor.getRuc());
        abrirFormulario();
    }

    @FXML
    private void onCompras() {
        Proveedor proveedor = tabla.getSelectionModel().getSelectedItem();
        if (proveedor == null) {
            return;
        }
        List<Compra> compras = service.compras(proveedor.getId());
        lblTituloCompras.setText("Compras a " + proveedor.getRazonSocial());
        lblSubtituloCompras.setText("RUC " + proveedor.getRuc() + " · " + compras.size() + " compras registradas");
        tablaCompras.getItems().setAll(compras);
        notificador.abrirPanel(panelCompras);
    }

    @FXML
    private void onCerrarPanel() {
        notificador.cerrarPanel();
    }

    @FXML
    private void onGuardar() {
        Formularios.limpiarErrores(txtRuc, txtRazonSocial, txtDireccion, txtTelefono);
        Proveedor proveedor = Proveedor.builder()
                .id(idEditando)
                .ruc(txtRuc.getText().trim())
                .razonSocial(txtRazonSocial.getText().trim().toUpperCase())
                .direccion(textoONulo(txtDireccion))
                .telefono(textoONulo(txtTelefono))
                .build();
        try {
            Proveedor guardado = service.guardar(proveedor);
            notificador.cerrarPanel();
            notificador.exito(guardado.getRazonSocial() + " se guardo correctamente.");
            cargarTabla();
        } catch (ConstraintViolationException e) {
            List<String> otros = Formularios.mostrarViolaciones(e, Map.of(
                    "ruc", txtRuc, "razonSocial", txtRazonSocial, "direccion", txtDireccion, "telefono", txtTelefono));
            otros.forEach(notificador::advertencia);
        } catch (NegocioException e) {
            Formularios.marcarError(txtRuc, e.getMessage());
        }
    }

    @FXML
    private void onEliminar() {
        Proveedor seleccionado = tabla.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            return;
        }
        notificador.confirmar("¿Eliminar a " + seleccionado.getRazonSocial() + "?",
                "Esta accion no se puede deshacer.",
                "Eliminar",
                () -> {
                    try {
                        service.eliminar(seleccionado.getId());
                        notificador.exito("Proveedor eliminado.");
                        cargarTabla();
                    } catch (NegocioException e) {
                        notificador.error(e.getMessage());
                    }
                });
    }

    private void cargarTabla() {
        Page<Proveedor> pagina = service.listar(txtBuscar.getText(), paginador.getPagina(), paginador.getTamanio());
        datos.setAll(pagina.getContent());
        paginador.actualizar(pagina);
        Animations.fadeIn(tabla, Duration.millis(260)).play();
    }

    private void abrirFormulario() {
        notificador.abrirPanel(panelFormulario);
        Platform.runLater(txtRuc::requestFocus);
    }

    private void limpiarFormulario() {
        Formularios.limpiarErrores(txtRuc, txtRazonSocial, txtDireccion, txtTelefono);
        txtRuc.clear();
        txtRazonSocial.clear();
        txtDireccion.clear();
        txtTelefono.clear();
    }

    private static String textoONulo(TextField campo) {
        String texto = campo.getText().trim();
        return texto.isEmpty() ? null : texto;
    }
}
