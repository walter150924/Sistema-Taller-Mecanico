package com.taller.mecanico.ui;

import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class Formularios {
    private static final Pattern DECIMAL = Pattern.compile("\\d{0,8}([.,]\\d{0,2})?");

    private Formularios() {
    }

    public static void marcarError(Control campo, String mensaje) {
        campo.pseudoClassStateChanged(Styles.STATE_DANGER, true);
        Label error = etiquetaError(campo);
        if (error != null) {
            error.setText(mensaje);
            if (!error.isVisible()) {
                error.setVisible(true);
                error.setManaged(true);
                Animations.fadeInDown(error, Duration.millis(220)).play();
            }
        }
        Animations.shakeX(campo, 4).play();
    }

    public static void limpiarError(Control campo) {
        campo.pseudoClassStateChanged(Styles.STATE_DANGER, false);
        Label error = etiquetaError(campo);
        if (error != null) {
            error.setVisible(false);
            error.setManaged(false);
        }
    }

    public static void limpiarErrores(Control... campos) {
        for (Control campo : campos) {
            limpiarError(campo);
        }
    }

    public static void limpiarAlEditar(TextInputControl... campos) {
        for (TextInputControl campo : campos) {
            campo.textProperty().addListener((obs, a, b) -> limpiarError(campo));
        }
    }

    public static List<String> mostrarViolaciones(ConstraintViolationException e,
                                                  Map<String, ? extends Control> campos) {
        List<String> sinCampo = new ArrayList<>();
        for (ConstraintViolation<?> v : e.getConstraintViolations()) {
            Control campo = campos.get(ultimoNodo(v.getPropertyPath()));
            if (campo != null) {
                marcarError(campo, v.getMessage());
            } else {
                sinCampo.add(v.getMessage());
            }
        }
        return sinCampo;
    }

    public static void soloDigitos(TextInputControl campo, int maximo) {
        campo.setTextFormatter(new TextFormatter<>(cambio ->
                cambio.getControlNewText().matches("\\d{0," + maximo + "}") ? cambio : null));
    }

    public static void soloDecimal(TextInputControl campo) {
        campo.setTextFormatter(new TextFormatter<>(cambio ->
                DECIMAL.matcher(cambio.getControlNewText()).matches() ? cambio : null));
    }

    private static Label etiquetaError(Control campo) {
        Parent contenedor = campo.getParent();
        while (contenedor != null && !contenedor.getStyleClass().contains("campo")) {
            contenedor = contenedor.getParent();
        }
        if (contenedor == null) {
            return null;
        }
        Node nodo = contenedor.lookup(".campo-error");
        return nodo instanceof Label l ? l : null;
    }

    private static String ultimoNodo(Path ruta) {
        String nombre = null;
        for (Path.Node nodo : ruta) {
            nombre = nodo.getName();
        }
        return nombre;
    }
}
