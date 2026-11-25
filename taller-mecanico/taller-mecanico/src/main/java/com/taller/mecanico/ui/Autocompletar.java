package com.taller.mecanico.ui;

import javafx.animation.PauseTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Popup;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class Autocompletar<T> {
    private static final Duration PAUSA = Duration.millis(180);
    private static final int FILAS_VISIBLES = 7;
    private static final double ALTO_FILA = 54;

    private final TextField campo;
    private final Function<String, List<T>> buscador;
    private final Consumer<T> alSeleccionar;

    private Function<T, String> titulo = String::valueOf;
    private Function<T, String> detalle = t -> null;
    private Function<T, String> extra = t -> null;
    private Function<T, Node> icono = t -> new FontIcon(Feather.SEARCH);
    private int minimoCaracteres = 1;

    private final Popup popup = new Popup();
    private final ListView<T> lista = new ListView<>();
    private final PauseTransition espera = new PauseTransition(PAUSA);
    private boolean suspendido;
    private String consulta = "";

    public Autocompletar(TextField campo, Function<String, List<T>> buscador, Consumer<T> alSeleccionar) {
        this.campo = campo;
        this.buscador = buscador;
        this.alSeleccionar = alSeleccionar;
        construirPopup();
        instalarEventos();
    }

    public Autocompletar<T> presentacion(Function<T, String> titulo, Function<T, String> detalle,
                                         Function<T, String> extra) {
        this.titulo = titulo;
        this.detalle = detalle;
        this.extra = extra;
        return this;
    }

    public Autocompletar<T> icono(Function<T, Node> icono) {
        this.icono = icono;
        return this;
    }

    public Autocompletar<T> minimoCaracteres(int minimo) {
        this.minimoCaracteres = minimo;
        return this;
    }

    public void setTexto(String texto) {
        suspendido = true;
        campo.setText(texto);
        campo.positionCaret(campo.getLength());
        suspendido = false;
        ocultar();
    }

    public void ocultar() {
        espera.stop();
        popup.hide();
    }

    private void construirPopup() {
        lista.getStyleClass().add("autocompletar-lista");
        lista.setCellFactory(lv -> new Celda());
        lista.setFocusTraversable(false);
        Label sinResultados = new Label("Sin coincidencias");
        sinResultados.getStyleClass().add("autocompletar-vacio");
        lista.setPlaceholder(sinResultados);
        lista.setOnMouseClicked(e -> elegir(lista.getSelectionModel().getSelectedItem()));

        VBox contenedor = new VBox(lista);
        contenedor.getStyleClass().add("autocompletar");

        contenedor.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());
        popup.getContent().add(contenedor);
        popup.setAutoHide(true);
        popup.setAutoFix(true);
    }

    private void instalarEventos() {
        espera.setOnFinished(e -> buscar());
        campo.textProperty().addListener((obs, a, b) -> {
            if (!suspendido) {
                espera.playFromStart();
            }
        });
        campo.focusedProperty().addListener((obs, a, enfocado) -> {
            if (!enfocado) {
                ocultar();
            }
        });
        campo.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            switch (e.getCode()) {
                case DOWN -> {
                    if (!popup.isShowing()) {
                        buscar();
                    } else {
                        mover(1);
                    }
                    e.consume();
                }
                case UP -> {
                    mover(-1);
                    e.consume();
                }
                case ENTER -> {
                    if (popup.isShowing() && lista.getSelectionModel().getSelectedItem() != null) {
                        elegir(lista.getSelectionModel().getSelectedItem());
                        e.consume();
                    }
                }
                case ESCAPE -> {
                    if (popup.isShowing()) {
                        ocultar();
                        e.consume();
                    }
                }
                case TAB -> ocultar();
                default -> { }
            }
        });
    }

    private void buscar() {
        String texto = campo.getText() == null ? "" : campo.getText().trim();
        if (texto.length() < minimoCaracteres || campo.getScene() == null) {
            popup.hide();
            return;
        }
        consulta = texto;
        List<T> resultados = buscador.apply(texto);
        lista.getItems().setAll(resultados);
        lista.getSelectionModel().clearSelection();
        if (!resultados.isEmpty()) {
            lista.getSelectionModel().selectFirst();
        }
        lista.setPrefHeight(resultados.isEmpty()
                ? ALTO_FILA
                : Math.min(resultados.size(), FILAS_VISIBLES) * ALTO_FILA + 6);
        mostrar();
    }

    private void mostrar() {
        Bounds b = campo.localToScreen(campo.getBoundsInLocal());
        if (b == null) {
            return;
        }
        lista.setPrefWidth(Math.max(campo.getWidth(), 360));
        if (!popup.isShowing()) {
            popup.show(campo, b.getMinX(), b.getMaxY() + 4);
        } else {
            popup.setX(b.getMinX());
            popup.setY(b.getMaxY() + 4);
        }
    }

    private void mover(int delta) {
        if (!popup.isShowing() || lista.getItems().isEmpty()) {
            return;
        }
        int actual = lista.getSelectionModel().getSelectedIndex();
        int nuevo = Math.max(0, Math.min(lista.getItems().size() - 1, actual + delta));
        lista.getSelectionModel().select(nuevo);
        lista.scrollTo(Math.max(0, nuevo - FILAS_VISIBLES + 1));
    }

    private void elegir(T item) {
        if (item == null) {
            return;
        }
        ocultar();
        alSeleccionar.accept(item);
    }

    private TextFlow resaltar(String texto) {
        String minusculas = texto.toLowerCase(Locale.ROOT);
        boolean[] marcado = new boolean[texto.length()];
        for (String palabra : consulta.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (palabra.isEmpty()) {
                continue;
            }
            int i = minusculas.indexOf(palabra);
            while (i >= 0) {
                for (int k = i; k < i + palabra.length(); k++) {
                    marcado[k] = true;
                }
                i = minusculas.indexOf(palabra, i + palabra.length());
            }
        }

        List<Text> tramos = new ArrayList<>();
        int inicio = 0;
        for (int i = 1; i <= texto.length(); i++) {
            if (i == texto.length() || marcado[i] != marcado[inicio]) {
                Text t = new Text(texto.substring(inicio, i));
                t.getStyleClass().add(marcado[inicio] ? "autocompletar-coincidencia" : "autocompletar-texto");
                tramos.add(t);
                inicio = i;
            }
        }
        TextFlow flujo = new TextFlow(tramos.toArray(Text[]::new));
        flujo.getStyleClass().add("autocompletar-titulo");
        return flujo;
    }

    private class Celda extends ListCell<T> {
        @Override
        protected void updateItem(T item, boolean vacio) {
            super.updateItem(item, vacio);
            if (vacio || item == null) {
                setGraphic(null);
                return;
            }
            StackPane caja = new StackPane(icono.apply(item));
            caja.getStyleClass().add("autocompletar-icono");

            VBox textos = new VBox(2, resaltar(titulo.apply(item)));
            String d = detalle.apply(item);
            if (d != null && !d.isBlank()) {
                TextFlow detalleFlujo = resaltar(d);
                detalleFlujo.getStyleClass().setAll("autocompletar-detalle");
                textos.getChildren().add(detalleFlujo);
            }
            HBox.setHgrow(textos, Priority.ALWAYS);

            HBox fila = new HBox(12, caja, textos);
            fila.setAlignment(Pos.CENTER_LEFT);
            String valor = extra.apply(item);
            if (valor != null) {
                Label lblExtra = new Label(valor);
                lblExtra.getStyleClass().add("autocompletar-extra");
                lblExtra.setMinWidth(Label.USE_PREF_SIZE);
                fila.getChildren().add(lblExtra);
            }
            setGraphic(fila);
        }
    }
}
