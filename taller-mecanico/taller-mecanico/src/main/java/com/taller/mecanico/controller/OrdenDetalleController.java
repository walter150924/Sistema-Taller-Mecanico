package com.taller.mecanico.controller;

import atlantafx.base.theme.Styles;
import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.EstadoOrden;
import com.taller.mecanico.model.LineaRepuesto;
import com.taller.mecanico.model.LineaServicio;
import com.taller.mecanico.model.MetodoPago;
import com.taller.mecanico.model.OrdenServicio;
import com.taller.mecanico.model.Permiso;
import com.taller.mecanico.model.Repuesto;
import com.taller.mecanico.model.Servicio;
import com.taller.mecanico.model.Usuario;
import com.taller.mecanico.seguridad.Sesion;
import com.taller.mecanico.service.OrdenPdfService;
import com.taller.mecanico.service.OrdenServicioService;
import com.taller.mecanico.service.RepuestoService;
import com.taller.mecanico.service.ServicioService;
import com.taller.mecanico.service.UsuarioService;
import com.taller.mecanico.ui.Autocompletar;
import com.taller.mecanico.ui.CeldasCarrito;
import com.taller.mecanico.ui.Convertidores;
import com.taller.mecanico.ui.Exportador;
import com.taller.mecanico.ui.Formularios;
import com.taller.mecanico.ui.Notificador;
import com.taller.mecanico.ui.Tablas;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class OrdenDetalleController {
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int CANTIDAD_MAXIMA = 999;
    private static final int DIGITOS_CANTIDAD = 3;

    private final OrdenServicioService ordenService;
    private final ServicioService servicioService;
    private final RepuestoService repuestoService;
    private final UsuarioService usuarioService;
    private final OrdenPdfService pdfService;
    private final Exportador exportador;
    private final Notificador notificador;
    private final Sesion sesion;

    @FXML private VBox raiz;
    @FXML private Label lblCodigo;
    @FXML private Label lblEstado;
    @FXML private Label lblSubtitulo;
    @FXML private Label lblCliente;
    @FXML private Label lblVehiculo;
    @FXML private Label lblKilometraje;
    @FXML private Label lblProblema;
    @FXML private Label lblMecanico;
    @FXML private Label lblPago;
    @FXML private HBox barraAsignacion;
    @FXML private ComboBox<Usuario> cmbMecanico;

    @FXML private TextArea txtDiagnostico;
    @FXML private Button btnGuardarDiagnostico;

    @FXML private HBox barraServicios;
    @FXML private ComboBox<Servicio> cmbServicio;
    @FXML private TableView<LineaServicio> tablaServicios;
    @FXML private TableColumn<LineaServicio, String> colServicio;
    @FXML private TableColumn<LineaServicio, BigDecimal> colPrecioServicio;
    @FXML private TableColumn<LineaServicio, LineaServicio> colQuitarServicio;

    @FXML private HBox barraRepuestos;
    @FXML private TextField txtRepuesto;
    @FXML private Spinner<Integer> spnCantidad;
    @FXML private TableView<LineaRepuesto> tablaRepuestos;
    @FXML private TableColumn<LineaRepuesto, String> colCodigoRepuesto;
    @FXML private TableColumn<LineaRepuesto, String> colRepuesto;
    @FXML private TableColumn<LineaRepuesto, String> colCantidad;
    @FXML private TableColumn<LineaRepuesto, BigDecimal> colPrecioRepuesto;
    @FXML private TableColumn<LineaRepuesto, BigDecimal> colImporteRepuesto;
    @FXML private TableColumn<LineaRepuesto, LineaRepuesto> colQuitarRepuesto;

    @FXML private Label lblTotalServicios;
    @FXML private Label lblTotalRepuestos;
    @FXML private Label lblBase;
    @FXML private Label lblIgv;
    @FXML private Label lblTotal;
    @FXML private HBox accionesEstado;

    @FXML private VBox dialogoCobro;
    @FXML private Label lblCobroTotal;
    @FXML private ComboBox<MetodoPago> cmbMetodoPago;

    private Autocompletar<Repuesto> autoRepuesto;
    private Repuesto repuestoElegido;
    private OrdenServicio orden;
    private Runnable alCambiar = () -> { };

    @FXML
    public void initialize() {
        cmbMecanico.setConverter(Convertidores.mostrando(Usuario::getNombreCompleto, "Selecciona un mecanico"));
        cmbServicio.setConverter(Convertidores.mostrando(
                s -> s.getNombre() + "  ·  " + Tablas.MONEDA.format(s.getPrecio()), "Selecciona un servicio"));
        configurarTablaServicios();
        configurarTablaRepuestos();
        configurarBuscadorRepuestos();
        configurarCobro();
    }

    public void mostrar(Long ordenId, Runnable alCambiar) {
        this.alCambiar = alCambiar;
        pintar(ordenService.obtener(ordenId));
        cmbServicio.getItems().setAll(servicioService.activos());
        cmbMecanico.getItems().setAll(usuarioService.mecanicosActivos());
        limpiarBuscadorRepuestos();
        notificador.abrirPanel(raiz);
    }

    private void configurarTablaServicios() {
        colServicio.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getServicio().getNombre()));
        colPrecioServicio.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPrecio()));
        colPrecioServicio.setCellFactory(Tablas.celdaMoneda());
        colQuitarServicio.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colQuitarServicio.setCellFactory(CeldasCarrito.quitar(linea ->
                ejecutar(() -> ordenService.quitarServicio(orden.getId(), linea.getId()), null)));
        Tablas.configurar(tablaServicios, Feather.TOOL, "Sin servicios",
                "Agrega la mano de obra realizada.", linea -> { });
    }

    private void configurarTablaRepuestos() {
        colCodigoRepuesto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRepuesto().getCodigoRepuesto()));
        colCodigoRepuesto.setCellFactory(Tablas.celdaChip("—"));
        colRepuesto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRepuesto().getNombre()));
        colCantidad.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getCantidad())));
        colPrecioRepuesto.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPrecioUnitario()));
        colPrecioRepuesto.setCellFactory(Tablas.celdaMoneda());
        colImporteRepuesto.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getImporte()));
        colImporteRepuesto.setCellFactory(Tablas.celdaMoneda());
        colQuitarRepuesto.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colQuitarRepuesto.setCellFactory(CeldasCarrito.quitar(linea ->
                ejecutar(() -> ordenService.quitarRepuesto(orden.getId(), linea.getId()),
                        linea.getRepuesto().getNombre() + " se devolvio al inventario.")));
        Tablas.configurar(tablaRepuestos, Feather.PACKAGE, "Sin repuestos",
                "Busca un repuesto arriba para registrarlo.", linea -> { });
    }

    private void configurarBuscadorRepuestos() {
        spnCantidad.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, CANTIDAD_MAXIMA, 1));
        Formularios.soloDigitos(spnCantidad.getEditor(), DIGITOS_CANTIDAD);
        autoRepuesto = new Autocompletar<>(txtRepuesto, repuestoService::autocompletar, this::elegirRepuesto)
                .presentacion(Repuesto::getNombre,
                        r -> r.getCodigoRepuesto() + "  ·  " + (r.isSinStock() ? "Sin stock" : "Stock " + r.getStock()),
                        r -> Tablas.MONEDA.format(r.getPrecio()))
                .icono(r -> new FontIcon(Feather.PACKAGE))
                .minimoCaracteres(1);
        txtRepuesto.textProperty().addListener((obs, anterior, actual) -> {
            if (repuestoElegido != null && !actual.equals(repuestoElegido.getNombre())) {
                repuestoElegido = null;
            }
        });
    }

    private void configurarCobro() {
        cmbMetodoPago.getItems().setAll(MetodoPago.values());
    }

    private void elegirRepuesto(Repuesto repuesto) {
        repuestoElegido = repuesto;
        autoRepuesto.setTexto(repuesto.getNombre());
        spnCantidad.requestFocus();
    }

    @FXML
    private void onAsignarMecanico() {
        Usuario mecanico = cmbMecanico.getValue();
        if (mecanico == null) {
            notificador.advertencia("Selecciona un mecanico.");
            return;
        }
        ejecutar(() -> ordenService.asignarMecanico(orden.getId(), mecanico.getId()),
                "Orden asignada a " + mecanico.getNombreCompleto() + ".");
    }

    @FXML
    private void onGuardarDiagnostico() {
        ejecutar(() -> ordenService.guardarDiagnostico(orden.getId(), txtDiagnostico.getText()),
                "Diagnostico guardado.");
    }

    @FXML
    private void onAgregarServicio() {
        Servicio servicio = cmbServicio.getValue();
        if (servicio == null) {
            notificador.advertencia("Selecciona un servicio del catalogo.");
            return;
        }
        ejecutar(() -> ordenService.agregarServicio(orden.getId(), servicio.getId()), null);
        cmbServicio.setValue(null);
    }

    @FXML
    private void onAgregarRepuesto() {
        if (repuestoElegido == null) {
            Formularios.marcarError(txtRepuesto, "Selecciona un repuesto de la lista");
            return;
        }
        Repuesto repuesto = repuestoElegido;
        int cantidad = spnCantidad.getValue() == null ? 1 : spnCantidad.getValue();
        boolean agregado = ejecutar(() -> ordenService.agregarRepuesto(orden.getId(), repuesto.getId(), cantidad),
                cantidad + " x " + repuesto.getNombre() + " registrado y descontado del inventario.");
        if (agregado) {
            limpiarBuscadorRepuestos();
        }
    }

    @FXML
    private void onImprimir() {
        Long ordenId = orden.getId();
        exportador.abrirTemporal(orden.getCodigo(), Exportador.Formato.PDF,
                destino -> pdfService.generar(ordenId, destino));
    }

    @FXML
    private void onCerrar() {
        notificador.cerrarPanel();
    }

    private void abrirCobro() {
        lblCobroTotal.setText("Total a cobrar por la orden " + orden.getCodigo() + ": "
                + Tablas.MONEDA.format(orden.getTotal()));
        cmbMetodoPago.setValue(MetodoPago.EFECTIVO);
        notificador.mostrarDialogo(dialogoCobro);
    }

    @FXML
    private void onConfirmarCobro() {
        MetodoPago metodo = cmbMetodoPago.getValue();
        boolean entregada = ejecutar(() -> ordenService.entregar(orden.getId(), metodo),
                "Orden " + orden.getCodigo() + " cobrada con " + metodo + " y entregada.");
        if (entregada) {
            notificador.cerrarDialogo();
        }
    }

    @FXML
    private void onCancelarCobro() {
        notificador.cerrarDialogo();
    }

    private void cambiarEstado(EstadoOrden destino) {
        if (destino == EstadoOrden.CANCELADO) {
            notificador.confirmar("¿Cancelar la orden " + orden.getCodigo() + "?",
                    "Los repuestos registrados se devolveran al inventario. Esta accion no se puede deshacer.",
                    "Cancelar orden",
                    () -> ejecutar(() -> ordenService.cambiarEstado(orden.getId(), destino), "Orden cancelada."));
            return;
        }
        ejecutar(() -> ordenService.cambiarEstado(orden.getId(), destino),
                "La orden " + orden.getCodigo() + " ahora esta: " + destino.getDescripcion() + ".");
    }

    private boolean ejecutar(Supplier<OrdenServicio> operacion, String mensajeExito) {
        try {
            pintar(operacion.get());
            alCambiar.run();
            if (mensajeExito != null) {
                notificador.exito(mensajeExito);
            }
            return true;
        } catch (NegocioException e) {
            notificador.advertencia(e.getMessage());
            return false;
        }
    }

    private void pintar(OrdenServicio actualizada) {
        orden = actualizada;
        lblCodigo.setText("Orden " + orden.getCodigo());
        Tablas.aplicarEstilo(lblEstado, orden.getEstado());
        lblSubtitulo.setText("Ingreso el " + orden.getFechaIngreso().format(FECHA_HORA)
                + (orden.getFechaEntrega() == null ? "" : " · Entregado el " + orden.getFechaEntrega().format(FECHA_HORA)));
        lblCliente.setText(orden.getCliente().getNombreCompleto() + " · DNI " + orden.getCliente().getDni());
        lblVehiculo.setText(orden.getVehiculo().getPlaca() + " · " + orden.getVehiculo().getDescripcion());
        lblKilometraje.setText(String.format(Tablas.LOCALE, "%,d km", orden.getKilometrajeIngreso()));
        lblProblema.setText(orden.getProblemaReportado());
        lblMecanico.setText(orden.getNombreMecanico());
        lblPago.setText(orden.getMetodoPago() == null ? "Pendiente" : orden.getMetodoPago().getDescripcion());
        txtDiagnostico.setText(orden.getDiagnostico() == null ? "" : orden.getDiagnostico());

        tablaServicios.getItems().setAll(orden.getServicios());
        tablaRepuestos.getItems().setAll(orden.getRepuestos());
        lblTotalServicios.setText(Tablas.MONEDA.format(orden.getTotalServicios()));
        lblTotalRepuestos.setText(Tablas.MONEDA.format(orden.getTotalRepuestos()));
        lblBase.setText(Tablas.MONEDA.format(orden.getBaseImponible()));
        lblIgv.setText(Tablas.MONEDA.format(orden.getIgv()));
        lblTotal.setText(Tablas.MONEDA.format(orden.getTotal()));

        aplicarPermisos();
        construirAccionesDeEstado();
    }

    private void aplicarPermisos() {
        boolean editable = orden.getEstado().esEditable() && sesion.tiene(Permiso.TRABAJAR_ORDENES);
        boolean puedeAsignar = orden.getEstado().estaEnTaller() && sesion.tiene(Permiso.CREAR_ORDENES);
        mostrarSi(barraAsignacion, puedeAsignar);
        mostrarSi(barraServicios, editable);
        mostrarSi(barraRepuestos, editable);
        mostrarSi(btnGuardarDiagnostico, editable);
        txtDiagnostico.setEditable(editable);
        colQuitarServicio.setVisible(editable);
        colQuitarRepuesto.setVisible(editable);
    }

    private void construirAccionesDeEstado() {
        accionesEstado.getChildren().clear();
        orden.getEstado().siguientes().stream()
                .filter(destino -> sesion.tiene(destino.getPermisoRequerido()))
                .map(this::botonDeEstado)
                .forEach(accionesEstado.getChildren()::add);
    }

    private Button botonDeEstado(EstadoOrden destino) {
        Button boton = new Button(destino.getAccion());
        boton.getStyleClass().add(destino == EstadoOrden.CANCELADO ? Styles.DANGER : Styles.ACCENT);
        if (destino == EstadoOrden.CANCELADO) {
            boton.getStyleClass().add(Styles.BUTTON_OUTLINED);
        }
        boton.setOnAction(e -> {
            if (destino == EstadoOrden.ENTREGADO) {
                abrirCobro();
            } else {
                cambiarEstado(destino);
            }
        });
        return boton;
    }

    private void limpiarBuscadorRepuestos() {
        repuestoElegido = null;
        autoRepuesto.setTexto("");
        spnCantidad.getValueFactory().setValue(1);
        Formularios.limpiarError(txtRepuesto);
    }

    private static void mostrarSi(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }
}
