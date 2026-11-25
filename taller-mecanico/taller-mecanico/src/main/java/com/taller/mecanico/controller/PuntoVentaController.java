package com.taller.mecanico.controller;

import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.util.Animations;
import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.Cliente;
import com.taller.mecanico.model.MetodoPago;
import com.taller.mecanico.model.Repuesto;
import com.taller.mecanico.model.TipoComprobante;
import com.taller.mecanico.model.Venta;
import com.taller.mecanico.service.CalculadoraIgv;
import com.taller.mecanico.service.CalculadoraIgv.Totales;
import com.taller.mecanico.service.ComprobantePdfService;
import com.taller.mecanico.service.NumeroALetras;
import com.taller.mecanico.service.ClienteService;
import com.taller.mecanico.service.RepuestoService;
import com.taller.mecanico.service.VentaService;
import com.taller.mecanico.service.VentaService.LineaCarrito;
import com.taller.mecanico.service.VentaService.NuevaVenta;
import com.taller.mecanico.ui.Autocompletar;
import com.taller.mecanico.ui.CeldasCarrito;
import com.taller.mecanico.ui.Exportador;
import com.taller.mecanico.ui.Formularios;
import com.taller.mecanico.ui.Notificador;
import com.taller.mecanico.ui.Refrescable;
import com.taller.mecanico.ui.Tablas;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class PuntoVentaController implements Refrescable {
    private static final DateTimeFormatter FECHA_LARGA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Tablas.LOCALE);

    private final VentaService ventaService;
    private final ClienteService clienteService;
    private final RepuestoService repuestoService;
    private final ComprobantePdfService pdfService;
    private final Exportador exportador;
    private final Notificador notificador;

    @FXML private VBox raiz;

    @FXML private TextField txtDni;
    @FXML private TextField txtNombreCliente;
    @FXML private HBox panelSinCliente;
    @FXML private HBox panelCliente;
    @FXML private Label lblIniciales;
    @FXML private Label lblClienteNombre;
    @FXML private Label lblClienteDni;
    @FXML private Label lblClienteEmail;
    @FXML private Label lblClienteTelefono;

    @FXML private TextField txtRepuesto;
    @FXML private Spinner<Integer> spnCantidad;
    @FXML private Label lblContadorItems;
    @FXML private TableView<ItemCarrito> tablaCarrito;
    @FXML private TableColumn<ItemCarrito, ItemCarrito> colItem;
    @FXML private TableColumn<ItemCarrito, String> colCodigo;
    @FXML private TableColumn<ItemCarrito, String> colRepuesto;
    @FXML private TableColumn<ItemCarrito, ItemCarrito> colCantidad;
    @FXML private TableColumn<ItemCarrito, ItemCarrito> colPrecio;
    @FXML private TableColumn<ItemCarrito, BigDecimal> colImporte;
    @FXML private TableColumn<ItemCarrito, ItemCarrito> colQuitar;

    @FXML private Label lblFecha;
    @FXML private ComboBox<TipoComprobante> cmbTipo;
    @FXML private Label lblNumero;
    @FXML private ToggleSwitch swIgv;
    @FXML private ToggleSwitch swIncluyeIgv;
    @FXML private Label lblModoIgv;
    @FXML private TextField txtObservacion;
    @FXML private Label lblArticulos;
    @FXML private Label lblSubtotal;
    @FXML private Label lblEtiquetaBase;
    @FXML private Label lblBase;
    @FXML private Label lblEtiquetaIgv;
    @FXML private Label lblIgv;
    @FXML private Label lblTotal;
    @FXML private Label lblEnLetras;
    @FXML private Button btnRegistrar;
    @FXML private ComboBox<MetodoPago> cmbMetodoPago;

    private final ObservableList<ItemCarrito> carrito =
            FXCollections.observableArrayList(i -> new Observable[]{i.cantidad, i.precio});
    private final ObjectProperty<Cliente> cliente = new SimpleObjectProperty<>();

    private Autocompletar<Cliente> autoDni;
    private Autocompletar<Cliente> autoNombre;
    private Autocompletar<Repuesto> autoRepuesto;

    @FXML
    public void initialize() {
        configurarCliente();
        configurarCarrito();
        configurarResumen();
        configurarPago();

        raiz.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F9 && !btnRegistrar.isDisabled()) {
                e.consume();
                onRegistrar();
            }
        });
    }

    @Override
    public void refrescar() {
        lblFecha.setText(capitalizar(LocalDateTime.now().format(FECHA_LARGA)));
        actualizarNumero();
        Platform.runLater(() -> (cliente.get() == null ? txtDni : txtRepuesto).requestFocus());
    }

    private void configurarPago() {
        cmbMetodoPago.getItems().setAll(MetodoPago.values());
        cmbMetodoPago.setValue(MetodoPago.EFECTIVO);
    }

    private void configurarCliente() {
        Formularios.soloDigitos(txtDni, 8);
        Formularios.limpiarAlEditar(txtDni, txtNombreCliente);

        autoDni = new Autocompletar<>(txtDni, clienteService::autocompletar, this::seleccionarCliente)
                .presentacion(Cliente::getDni, Cliente::getNombreCompleto, t -> null)
                .icono(this::avatar)
                .minimoCaracteres(2);
        autoNombre = new Autocompletar<>(txtNombreCliente, clienteService::autocompletar, this::seleccionarCliente)
                .presentacion(Cliente::getNombreCompleto, p -> "DNI " + p.getDni(), t -> null)
                .icono(this::avatar)
                .minimoCaracteres(2);

        txtDni.setOnAction(e -> onBuscarDni());
    }

    @FXML
    private void onBuscarDni() {
        String dni = txtDni.getText().trim();
        autoDni.ocultar();
        if (dni.length() != 8) {
            Formularios.marcarError(txtDni, "Ingresa los 8 digitos del DNI");
            return;
        }
        clienteService.buscarPorDni(dni).ifPresentOrElse(this::seleccionarCliente,
                () -> Formularios.marcarError(txtDni, "No hay ningun cliente con DNI " + dni));
    }

    private void seleccionarCliente(Cliente p) {
        cliente.set(p);
        Formularios.limpiarErrores(txtDni, txtNombreCliente);
        autoDni.setTexto(p.getDni());
        autoNombre.setTexto(p.getNombreCompleto());

        lblIniciales.setText(Tablas.iniciales(p.getNombreCompleto()));
        lblClienteNombre.setText(p.getNombreCompleto());
        lblClienteDni.setText("DNI " + p.getDni());
        mostrarDato(lblClienteEmail, p.getEmail());
        mostrarDato(lblClienteTelefono, p.getTelefono());

        alternar(panelCliente, panelSinCliente);
        Animations.fadeIn(panelCliente, Duration.millis(250)).play();
        Platform.runLater(txtRepuesto::requestFocus);
    }

    @FXML
    private void onQuitarCliente() {
        cliente.set(null);
        autoDni.setTexto("");
        autoNombre.setTexto("");
        alternar(panelSinCliente, panelCliente);
        txtDni.requestFocus();
    }

    private void configurarCarrito() {
        spnCantidad.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 9999, 1));
        Formularios.soloDigitos(spnCantidad.getEditor(), 4);

        autoRepuesto = new Autocompletar<>(txtRepuesto, repuestoService::autocompletar, this::agregarRepuesto)
                .presentacion(Repuesto::getNombre,
                        p -> p.getCodigoRepuesto() + "  ·  " + (p.isSinStock() ? "Sin stock" : "Stock " + p.getStock()),
                        p -> Tablas.MONEDA.format(p.getPrecio()))
                .icono(p -> new FontIcon(Feather.PACKAGE))
                .minimoCaracteres(1);

        colItem.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colItem.setCellFactory(CeldasCarrito.numeroFila());
        colCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().repuesto.getCodigoRepuesto()));
        colCodigo.setCellFactory(Tablas.celdaChip("—"));
        colRepuesto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().repuesto.getNombre()));
        colCantidad.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colCantidad.setCellFactory(CeldasCarrito.cantidad(i -> i.cantidad, i -> i.repuesto.getStock(),
                i -> "Solo hay " + i.repuesto.getStock() + " unidades de " + i.repuesto.getNombre() + " en stock.",
                notificador::advertencia));
        colPrecio.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colPrecio.setCellFactory(CeldasCarrito.importe(i -> i.precio, notificador::advertencia));
        colImporte.setCellValueFactory(c -> c.getValue().importe);
        colImporte.setCellFactory(Tablas.celdaMoneda());
        colQuitar.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colQuitar.setCellFactory(CeldasCarrito.quitar(carrito::remove));

        Tablas.configurar(tablaCarrito, Feather.SHOPPING_CART, "Carrito vacio",
                "Busca un repuesto arriba para agregarlo al carrito.", i -> { });
        tablaCarrito.setItems(carrito);
        tablaCarrito.getStyleClass().add("tabla-carrito");

        carrito.addListener((ListChangeListener<ItemCarrito>) c -> recalcular());
    }

    private void agregarRepuesto(Repuesto p) {
        if (p.isSinStock()) {
            notificador.advertencia(p.getNombre() + " no tiene stock disponible. Registra una compra para reponerlo.");
            autoRepuesto.setTexto("");
            return;
        }
        int cantidad = spnCantidad.getValue() == null ? 1 : spnCantidad.getValue();
        ItemCarrito existente = carrito.stream()
                .filter(i -> i.repuesto.getId().equals(p.getId()))
                .findFirst().orElse(null);
        int enCarrito = existente == null ? 0 : existente.cantidad.get();
        if (enCarrito + cantidad > p.getStock()) {
            cantidad = p.getStock() - enCarrito;
            notificador.advertencia("Solo hay " + p.getStock() + " unidades de " + p.getNombre() + " en stock.");
            if (cantidad <= 0) {
                autoRepuesto.setTexto("");
                return;
            }
        }
        if (existente != null) {
            existente.cantidad.set(enCarrito + cantidad);
            tablaCarrito.getSelectionModel().select(existente);
            tablaCarrito.scrollTo(existente);
            notificador.info(p.getNombre() + ": cantidad actualizada a " + existente.cantidad.get());
        } else {
            ItemCarrito nuevo = new ItemCarrito(p, cantidad, p.getPrecio().setScale(2, RoundingMode.HALF_UP));
            carrito.add(nuevo);
            tablaCarrito.getSelectionModel().select(nuevo);
            tablaCarrito.scrollTo(nuevo);
        }
        autoRepuesto.setTexto("");
        spnCantidad.getValueFactory().setValue(1);
        Platform.runLater(txtRepuesto::requestFocus);
    }

    private void configurarResumen() {
        cmbTipo.getItems().setAll(TipoComprobante.values());
        cmbTipo.setValue(TipoComprobante.BOLETA);
        cmbTipo.valueProperty().addListener((obs, a, b) -> actualizarNumero());

        swIncluyeIgv.disableProperty().bind(swIgv.selectedProperty().not());
        swIgv.selectedProperty().addListener((obs, a, b) -> recalcular());
        swIncluyeIgv.selectedProperty().addListener((obs, a, b) -> recalcular());

        btnRegistrar.disableProperty().bind(Bindings.isEmpty(carrito).or(cliente.isNull()));
        recalcular();
    }

    private void recalcular() {
        boolean aplica = swIgv.isSelected();
        boolean incluye = swIncluyeIgv.isSelected();
        Totales t = CalculadoraIgv.calcular(carrito.stream().map(i -> i.importe.get()).toList(), aplica, incluye);
        BigDecimal suma = carrito.stream().map(i -> i.importe.get()).reduce(BigDecimal.ZERO, BigDecimal::add);
        int articulos = carrito.stream().mapToInt(i -> i.cantidad.get()).sum();

        lblArticulos.setText(String.valueOf(articulos));
        lblContadorItems.setText(carrito.size() == 1 ? "1 item" : carrito.size() + " items");
        lblSubtotal.setText(Tablas.MONEDA.format(suma));
        lblEtiquetaBase.setText(aplica ? "Base imponible (Op. gravada)" : "Op. exonerada / inafecta");
        lblBase.setText(Tablas.MONEDA.format(t.baseImponible()));
        lblEtiquetaIgv.setText(aplica ? "IGV (18%)" : "IGV (no aplica)");
        lblIgv.setText(Tablas.MONEDA.format(t.igv()));
        lblTotal.setText(Tablas.MONEDA.format(t.total()));
        lblEnLetras.setText("SON: " + NumeroALetras.importe(t.total()));
        lblModoIgv.setText(!aplica ? "Sin IGV: el total es la suma de importes"
                : incluye ? "El IGV se extrae del precio (total ÷ 1.18)"
                : "El IGV (18%) se suma al precio");
    }

    private void actualizarNumero() {
        lblNumero.setText(ventaService.siguienteComprobante(cmbTipo.getValue()));
    }

    @FXML
    private void onRegistrar() {
        if (cliente.get() == null) {
            Formularios.marcarError(txtDni, "Selecciona un cliente");
            return;
        }
        if (carrito.isEmpty()) {
            notificador.advertencia("Agrega al menos un repuesto al carrito.");
            return;
        }
        List<LineaCarrito> lineas = carrito.stream()
                .map(i -> new LineaCarrito(i.repuesto.getId(), i.cantidad.get(), i.precio.get()))
                .toList();
        try {
            Venta venta = ventaService.registrar(new NuevaVenta(
                    cmbTipo.getValue(), cliente.get().getId(),
                    swIgv.isSelected(), swIncluyeIgv.isSelected(),
                    txtObservacion.getText(), lineas,
                    cmbMetodoPago.getValue()));

            Long id = venta.getId();
            String comprobante = venta.getComprobante();
            notificador.exitoConAcciones("Venta registrada",
                    "Se emitio el comprobante " + comprobante + " por " + Tablas.MONEDA.format(venta.getTotal())
                            + " a nombre de " + cliente.get().getNombreCompleto() + ".",
                    "Nueva venta", this::limpiar,
                    "Imprimir comprobante", () -> {
                        exportador.abrirTemporal(comprobante, Exportador.Formato.PDF,
                                destino -> pdfService.generar(id, destino));
                        limpiar();
                    });
        } catch (NegocioException e) {
            notificador.advertencia(e.getMessage());
        } catch (Exception e) {
            notificador.error("No se pudo registrar la venta: " + e.getMessage());
        }
    }

    @FXML
    private void onLimpiar() {
        if (carrito.isEmpty() && cliente.get() == null) {
            limpiar();
            return;
        }
        notificador.confirmar("¿Vaciar la venta en curso?",
                "Se quitaran el cliente y los " + carrito.size() + " repuestos del carrito.",
                "Vaciar", this::limpiar);
    }

    private void limpiar() {
        carrito.clear();
        onQuitarCliente();
        txtObservacion.clear();
        cmbMetodoPago.setValue(MetodoPago.EFECTIVO);
        autoRepuesto.setTexto("");
        spnCantidad.getValueFactory().setValue(1);
        swIgv.setSelected(true);
        swIncluyeIgv.setSelected(true);
        actualizarNumero();
    }

    private StackPane avatar(Cliente p) {
        StackPane a = new StackPane(new Label(Tablas.iniciales(p.getNombreCompleto())));
        a.getStyleClass().add("avatar");
        return a;
    }

    private static void mostrarDato(Label etiqueta, String valor) {
        boolean hay = valor != null && !valor.isBlank();
        etiqueta.setText(hay ? valor : "");
        etiqueta.setVisible(hay);
        etiqueta.setManaged(hay);
    }

    private static void alternar(Node mostrar, Node ocultar) {
        mostrar.setVisible(true);
        mostrar.setManaged(true);
        ocultar.setVisible(false);
        ocultar.setManaged(false);
    }

    private static String capitalizar(String texto) {
        return texto.isEmpty() ? texto : texto.substring(0, 1).toUpperCase(Locale.ROOT) + texto.substring(1);
    }

    static final class ItemCarrito {
        final Repuesto repuesto;
        final IntegerProperty cantidad;
        final ObjectProperty<BigDecimal> precio;
        final ObjectBinding<BigDecimal> importe;

        ItemCarrito(Repuesto repuesto, int cantidad, BigDecimal precio) {
            this.repuesto = repuesto;
            this.cantidad = new SimpleIntegerProperty(cantidad);
            this.precio = new SimpleObjectProperty<>(precio);
            this.importe = Bindings.createObjectBinding(
                    () -> CalculadoraIgv.importe(this.cantidad.get(), this.precio.get()),
                    this.cantidad, this.precio);
        }
    }
}
