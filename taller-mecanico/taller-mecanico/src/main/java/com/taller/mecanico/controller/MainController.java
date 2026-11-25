package com.taller.mecanico.controller;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.util.Animations;
import com.taller.mecanico.model.Permiso;
import com.taller.mecanico.model.Usuario;
import com.taller.mecanico.seguridad.Sesion;
import com.taller.mecanico.ui.CargadorVistas;
import com.taller.mecanico.ui.CargadorVistas.Vista;
import com.taller.mecanico.ui.Notificador;
import com.taller.mecanico.ui.Refrescable;
import com.taller.mecanico.ui.Tablas;
import com.taller.mecanico.ui.Ventana;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MainController {
    private static final double ANCHO_EXPANDIDO = 248;
    private static final double ANCHO_CONTRAIDO = 76;
    private static final Duration DURACION_MENU = Duration.millis(260);
    private static final Interpolator SUAVE = Interpolator.SPLINE(0.4, 0, 0.2, 1);

    private final CargadorVistas cargador;
    private final Notificador notificador;
    private final Sesion sesion;
    private final Ventana ventana;

    @FXML private StackPane raiz;
    @FXML private VBox sidebar;
    @FXML private StackPane contenido;
    @FXML private ModalPane modalPanel;
    @FXML private ModalPane modalDialogo;
    @FXML private VBox zonaNotificaciones;

    @FXML private ToggleGroup menu;
    @FXML private Label secGeneral;
    @FXML private Label secTaller;
    @FXML private Label secVentas;
    @FXML private Label secCompras;
    @FXML private Label secCatalogo;
    @FXML private Label secAdministracion;
    @FXML private ToggleButton navDashboard;
    @FXML private ToggleButton navOrdenes;
    @FXML private ToggleButton navVehiculos;
    @FXML private ToggleButton navClientes;
    @FXML private ToggleButton navPuntoVenta;
    @FXML private ToggleButton navVentas;
    @FXML private ToggleButton navCompra;
    @FXML private ToggleButton navCompras;
    @FXML private ToggleButton navProveedores;
    @FXML private ToggleButton navInventario;
    @FXML private ToggleButton navServicios;
    @FXML private ToggleButton navRepuestos;
    @FXML private ToggleButton navCategorias;
    @FXML private ToggleButton navUsuarios;
    @FXML private Region indicador;

    @FXML private VBox marcaTextos;
    @FXML private VBox usuarioTextos;
    @FXML private Label lblIniciales;
    @FXML private Label lblUsuario;
    @FXML private Label lblRol;
    @FXML private Button btnCerrarSesion;
    @FXML private Button btnTema;
    @FXML private Button btnContraer;

    private final Map<String, Vista> vistas = new HashMap<>();
    private final List<OpcionMenu> opcionesPermitidas = new ArrayList<>();
    private final List<Label> seccionesVisibles = new ArrayList<>();
    private boolean oscuro;
    private boolean contraido;

    private record OpcionMenu(ToggleButton boton, String vista, Permiso permiso) {}

    private record Seccion(Label titulo, List<OpcionMenu> opciones) {}

    @FXML
    public void initialize() {
        vistas.clear();
        oscuro = false;
        contraido = false;
        notificador.registrar(modalPanel, modalDialogo, zonaNotificaciones);
        modalPanel.setAlignment(Pos.TOP_RIGHT);
        modalDialogo.setAlignment(Pos.CENTER);

        mostrarUsuario(sesion.usuarioActual());
        construirMenu();

        menu.selectedToggleProperty().addListener((obs, anterior, actual) -> {
            if (actual == null) {
                menu.selectToggle(anterior);
                return;
            }
            mostrar((String) actual.getUserData());
            moverIndicador(actual);
        });

        ToggleButton inicial = opcionesPermitidas.get(0).boton();
        menu.selectToggle(inicial);
        Platform.runLater(() -> moverIndicador(inicial));
    }

    public void irA(String vista) {
        opcionesPermitidas.stream()
                .filter(opcion -> opcion.vista().equals(vista))
                .findFirst()
                .ifPresentOrElse(opcion -> menu.selectToggle(opcion.boton()),
                        () -> notificador.advertencia("No tienes acceso a esa seccion."));
    }

    private void construirMenu() {
        List<Seccion> secciones = List.of(
                new Seccion(secGeneral, List.of(
                        new OpcionMenu(navDashboard, "dashboard-view", Permiso.VER_DASHBOARD))),
                new Seccion(secTaller, List.of(
                        new OpcionMenu(navOrdenes, "ordenes-view", Permiso.TRABAJAR_ORDENES),
                        new OpcionMenu(navVehiculos, "vehiculos-view", Permiso.GESTIONAR_VEHICULOS),
                        new OpcionMenu(navClientes, "cliente-view", Permiso.GESTIONAR_CLIENTES))),
                new Seccion(secVentas, List.of(
                        new OpcionMenu(navPuntoVenta, "punto-venta-view", Permiso.GESTIONAR_VENTAS),
                        new OpcionMenu(navVentas, "ventas-view", Permiso.GESTIONAR_VENTAS))),
                new Seccion(secCompras, List.of(
                        new OpcionMenu(navCompra, "compra-form-view", Permiso.GESTIONAR_COMPRAS),
                        new OpcionMenu(navCompras, "compras-view", Permiso.GESTIONAR_COMPRAS),
                        new OpcionMenu(navProveedores, "proveedores-view", Permiso.GESTIONAR_COMPRAS),
                        new OpcionMenu(navInventario, "inventario-view", Permiso.VER_INVENTARIO))),
                new Seccion(secCatalogo, List.of(
                        new OpcionMenu(navServicios, "servicios-view", Permiso.GESTIONAR_CATALOGO),
                        new OpcionMenu(navRepuestos, "repuesto-view", Permiso.GESTIONAR_CATALOGO),
                        new OpcionMenu(navCategorias, "categoria-view", Permiso.GESTIONAR_CATALOGO))),
                new Seccion(secAdministracion, List.of(
                        new OpcionMenu(navUsuarios, "usuarios-view", Permiso.GESTIONAR_USUARIOS))));

        opcionesPermitidas.clear();
        seccionesVisibles.clear();
        secciones.forEach(this::aplicarPermisos);
    }

    private void aplicarPermisos(Seccion seccion) {
        List<OpcionMenu> permitidas = seccion.opciones().stream()
                .filter(opcion -> sesion.tiene(opcion.permiso()))
                .toList();
        seccion.opciones().stream()
                .filter(opcion -> !permitidas.contains(opcion))
                .forEach(opcion -> quitarDelMenu(opcion.boton()));
        if (permitidas.isEmpty()) {
            quitarDelMenu(seccion.titulo());
            return;
        }
        seccionesVisibles.add(seccion.titulo());
        permitidas.forEach(this::prepararOpcion);
        opcionesPermitidas.addAll(permitidas);
    }

    private void prepararOpcion(OpcionMenu opcion) {
        opcion.boton().setUserData(opcion.vista());
        Tooltip ayuda = new Tooltip(opcion.boton().getText());
        ayuda.setShowDelay(Duration.millis(300));
        opcion.boton().setTooltip(ayuda);
    }

    private static void quitarDelMenu(Node nodo) {
        ((Pane) nodo.getParent()).getChildren().remove(nodo);
    }

    private void mostrarUsuario(Usuario usuario) {
        lblIniciales.setText(Tablas.iniciales(usuario.getNombreCompleto()));
        lblUsuario.setText(usuario.getNombreCompleto());
        lblRol.setText(usuario.getRol().getDescripcion());
    }

    private void mostrar(String nombre) {
        Vista vista = vistas.computeIfAbsent(nombre, cargador::cargar);
        contenido.getChildren().setAll(vista.nodo());
        if (vista.controlador() instanceof Refrescable refrescable) {
            refrescable.refrescar();
        }
        Animations.fadeInUp(vista.nodo(), Duration.millis(380)).play();
    }

    private void moverIndicador(Toggle toggle) {
        Node boton = (Node) toggle;
        double destino = boton.getBoundsInParent().getMinY();
        indicador.setPrefHeight(boton.getBoundsInParent().getHeight());
        new Timeline(new KeyFrame(Duration.millis(280),
                new KeyValue(indicador.translateYProperty(), destino, SUAVE)))
                .play();
    }

    @FXML
    private void onCerrarSesion() {
        notificador.confirmar("¿Cerrar sesion?",
                "Volveras a la pantalla de ingreso. Los cambios ya guardados se conservan.",
                "Cerrar sesion",
                () -> {
                    sesion.cerrar();
                    ventana.mostrarLogin();
                });
    }

    @FXML
    private void onCambiarTema() {
        oscuro = !oscuro;
        Application.setUserAgentStylesheet(oscuro
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
        if (oscuro) {
            raiz.getStyleClass().add("dark");
        } else {
            raiz.getStyleClass().remove("dark");
        }
        ((FontIcon) btnTema.getGraphic()).setIconCode(oscuro ? Feather.SUN : Feather.MOON);
        btnTema.setText(oscuro ? "Modo claro" : "Modo oscuro");
        Animations.fadeIn(contenido, Duration.millis(300)).play();
    }

    @FXML
    private void onContraerMenu() {
        contraido = !contraido;
        double ancho = contraido ? ANCHO_CONTRAIDO : ANCHO_EXPANDIDO;

        Timeline ajuste = new Timeline(new KeyFrame(DURACION_MENU,
                new KeyValue(sidebar.prefWidthProperty(), ancho, SUAVE),
                new KeyValue(sidebar.minWidthProperty(), ancho, SUAVE)));

        ContentDisplay modo = contraido ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT;
        List<Node> textos = new ArrayList<>(seccionesVisibles);
        textos.add(marcaTextos);
        textos.add(usuarioTextos);

        if (contraido) {
            textos.forEach(nodo -> alternarVisibilidad(nodo, false));
            todosLosBotones().forEach(boton -> boton.setContentDisplay(modo));
            ajuste.play();
        } else {
            ajuste.setOnFinished(e -> {
                textos.forEach(nodo -> alternarVisibilidad(nodo, true));
                todosLosBotones().forEach(boton -> boton.setContentDisplay(modo));
                ParallelTransition aparicion = new ParallelTransition();
                textos.forEach(nodo -> aparicion.getChildren().add(Animations.fadeIn(nodo, Duration.millis(200))));
                aparicion.play();
                Platform.runLater(() -> moverIndicador(menu.getSelectedToggle()));
            });
            ajuste.play();
        }
        ((FontIcon) btnContraer.getGraphic()).setIconCode(
                contraido ? Feather.CHEVRONS_RIGHT : Feather.CHEVRONS_LEFT);
        Platform.runLater(() -> moverIndicador(menu.getSelectedToggle()));
    }

    private static void alternarVisibilidad(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

    private List<ButtonBase> todosLosBotones() {
        List<ButtonBase> todos = new ArrayList<>(opcionesPermitidas.stream().map(OpcionMenu::boton).toList());
        todos.add(btnCerrarSesion);
        todos.add(btnTema);
        todos.add(btnContraer);
        return todos;
    }
}
