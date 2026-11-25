package com.taller.mecanico.ui;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class Ventana {
    private static final String TITULO = "Taller Mecanico | Sistema de gestion";
    private static final String HOJA_DE_ESTILOS = "/css/app.css";
    private static final double ANCHO_MINIMO_PANEL = 1100;
    private static final double ALTO_MINIMO_PANEL = 700;

    private final CargadorVistas cargador;
    private Stage escenario;

    public void iniciar(Stage escenario) {
        this.escenario = escenario;
        escenario.setTitle(TITULO);
        mostrarLogin();
        escenario.show();
    }

    public void mostrarLogin() {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        escenario.setMinWidth(0);
        escenario.setMinHeight(0);
        mostrar(cargador.cargar("login-view").nodo());
        escenario.setMaximized(false);
        escenario.sizeToScene();
        escenario.centerOnScreen();
    }

    public void mostrarPanel() {
        mostrar(cargador.cargar("main-view").nodo());
        escenario.setMinWidth(ANCHO_MINIMO_PANEL);
        escenario.setMinHeight(ALTO_MINIMO_PANEL);
        escenario.setMaximized(true);
    }

    private void mostrar(Parent raiz) {
        Scene escena = new Scene(raiz);
        escena.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource(HOJA_DE_ESTILOS)).toExternalForm());
        escenario.setScene(escena);
    }
}
