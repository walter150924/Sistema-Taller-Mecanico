package com.taller.mecanico.ui;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.controls.Notification;
import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

@Component
public class Notificador {
    private static final Duration DURACION_TOAST = Duration.seconds(3.5);

    private ModalPane modalPanel;
    private ModalPane modalDialogo;
    private VBox zonaToasts;

    public void registrar(ModalPane modalPanel, ModalPane modalDialogo, VBox zonaToasts) {
        this.modalPanel = modalPanel;
        this.modalDialogo = modalDialogo;
        this.zonaToasts = zonaToasts;
    }

    public void exito(String mensaje) {
        toast(mensaje, Feather.CHECK_CIRCLE, Styles.SUCCESS);
    }

    public void advertencia(String mensaje) {
        toast(mensaje, Feather.ALERT_TRIANGLE, Styles.WARNING);
    }

    public void error(String mensaje) {
        toast(mensaje, Feather.X_CIRCLE, Styles.DANGER);
    }

    public void info(String mensaje) {
        toast(mensaje, Feather.INFO, Styles.ACCENT);
    }

    private void toast(String mensaje, Ikon icono, String estilo) {
        Notification n = new Notification(mensaje, new FontIcon(icono));
        n.getStyleClass().addAll(estilo, Styles.ELEVATED_2, "toast");
        n.setPrefWidth(360);
        n.setMaxWidth(360);
        n.setOnClose(e -> cerrarToast(n));

        zonaToasts.getChildren().add(n);
        Animations.fadeInUp(n, Duration.millis(280)).play();

        PauseTransition espera = new PauseTransition(DURACION_TOAST);
        espera.setOnFinished(e -> cerrarToast(n));
        espera.play();
    }

    private void cerrarToast(Notification n) {
        if (!zonaToasts.getChildren().contains(n) || n.getProperties().containsKey("cerrando")) {
            return;
        }
        n.getProperties().put("cerrando", true);
        Timeline salida = Animations.fadeOutRight(n, Duration.millis(250));
        salida.setOnFinished(e -> zonaToasts.getChildren().remove(n));
        salida.play();
    }

    public void mostrarDialogo(Node contenido) {
        modalDialogo.usePredefinedTransitionFactories(null);
        modalDialogo.show(contenido);
    }

    public void cerrarDialogo() {
        modalDialogo.hide();
    }

    public void confirmar(String titulo, String mensaje, String textoAccion, Runnable alConfirmar) {
        dialogo(Feather.TRASH_2, "peligro", titulo, mensaje,
                "Cancelar", null, textoAccion, Styles.DANGER, alConfirmar);
    }

    public void exitoConAcciones(String titulo, String mensaje,
                                 String textoSecundario, Runnable secundaria,
                                 String textoPrincipal, Runnable principal) {
        dialogo(Feather.CHECK_CIRCLE, "exito", titulo, mensaje,
                textoSecundario, secundaria, textoPrincipal, Styles.ACCENT, principal);
    }

    private void dialogo(Ikon iconoDialogo, String claseIcono, String titulo, String mensaje,
                         String textoSecundario, Runnable secundaria,
                         String textoPrincipal, String estiloPrincipal, Runnable principal) {
        FontIcon icono = new FontIcon(iconoDialogo);
        StackPane circulo = new StackPane(icono);
        circulo.getStyleClass().addAll("dialogo-icono", claseIcono);

        Label lblTitulo = new Label(titulo);
        lblTitulo.getStyleClass().add("dialogo-titulo");
        Label lblMensaje = new Label(mensaje);
        lblMensaje.getStyleClass().add("dialogo-mensaje");
        lblMensaje.setWrapText(true);

        Button cancelar = new Button(textoSecundario);
        cancelar.getStyleClass().add(Styles.BUTTON_OUTLINED);
        cancelar.setCancelButton(true);
        cancelar.setOnAction(e -> {
            modalDialogo.hide();
            if (secundaria != null) {
                secundaria.run();
            }
        });

        Button aceptar = new Button(textoPrincipal);
        aceptar.getStyleClass().add(estiloPrincipal);
        aceptar.setOnAction(e -> {
            modalDialogo.hide();
            principal.run();
        });

        Region espacio = new Region();
        HBox.setHgrow(espacio, javafx.scene.layout.Priority.ALWAYS);
        HBox acciones = new HBox(10, espacio, cancelar, aceptar);
        acciones.setAlignment(Pos.CENTER_RIGHT);
        acciones.getStyleClass().add("dialogo-acciones");

        VBox dialogo = new VBox(14, circulo, lblTitulo, lblMensaje, acciones);
        dialogo.getStyleClass().add("dialogo");
        dialogo.setMaxSize(440, Region.USE_PREF_SIZE);

        modalDialogo.usePredefinedTransitionFactories(null);
        modalDialogo.show(dialogo);
        aceptar.requestFocus();
    }

    public void abrirPanel(Node contenido) {
        modalPanel.usePredefinedTransitionFactories(Side.RIGHT,
                Duration.millis(320), Duration.millis(220));
        modalPanel.show(contenido);
    }

    public void cerrarPanel() {
        modalPanel.hide();
    }
}
