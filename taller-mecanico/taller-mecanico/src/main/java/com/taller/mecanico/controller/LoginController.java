package com.taller.mecanico.controller;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.util.Animations;
import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.service.UsuarioService;
import com.taller.mecanico.ui.Ventana;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginController {
    private final UsuarioService usuarioService;
    private final Ventana ventana;

    @FXML private VBox tarjeta;
    @FXML private CustomTextField txtUsuario;
    @FXML private PasswordField txtClave;
    @FXML private Label lblError;

    @FXML
    public void initialize() {
        txtUsuario.textProperty().addListener((obs, anterior, actual) -> ocultarError());
        txtClave.textProperty().addListener((obs, anterior, actual) -> ocultarError());
        Animations.fadeInUp(tarjeta, Duration.millis(420)).play();
        Platform.runLater(txtUsuario::requestFocus);
    }

    @FXML
    private void onIngresar() {
        if (txtUsuario.getText().isBlank() || txtClave.getText().isBlank()) {
            mostrarError("Ingresa tu usuario y contraseña.");
            return;
        }
        try {
            usuarioService.iniciarSesion(txtUsuario.getText(), txtClave.getText());
            ventana.mostrarPanel();
        } catch (NegocioException e) {
            txtClave.clear();
            mostrarError(e.getMessage());
        }
    }

    private void mostrarError(String mensaje) {
        lblError.setText(mensaje);
        lblError.setVisible(true);
        lblError.setManaged(true);
        Animations.shakeX(tarjeta, 6).play();
    }

    private void ocultarError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
    }
}
