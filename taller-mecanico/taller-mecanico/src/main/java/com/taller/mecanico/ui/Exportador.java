package com.taller.mecanico.ui;

import javafx.application.HostServices;
import javafx.concurrent.Task;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Component
@RequiredArgsConstructor
@Slf4j
public class Exportador {
    @FunctionalInterface
    public interface Generador {
        void generar(File destino) throws Exception;
    }

    public enum Formato {
        PDF("Documento PDF", "*.pdf", ".pdf"),
        EXCEL("Libro de Excel", "*.xlsx", ".xlsx");

        final String descripcion;
        final String patron;
        final String extension;

        Formato(String descripcion, String patron, String extension) {
            this.descripcion = descripcion;
            this.patron = patron;
            this.extension = extension;
        }
    }

    private final Notificador notificador;
    private HostServices hostServices;
    private File ultimaCarpeta = new File(System.getProperty("user.home"));

    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    public void guardar(Window ventana, String titulo, String nombreSugerido, Formato formato, Generador generador) {
        FileChooser selector = new FileChooser();
        selector.setTitle(titulo);
        selector.setInitialFileName(nombreSugerido + formato.extension);
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter(formato.descripcion, formato.patron));
        if (ultimaCarpeta.isDirectory()) {
            selector.setInitialDirectory(ultimaCarpeta);
        }
        File elegido = selector.showSaveDialog(ventana);
        if (elegido == null) {
            return;
        }
        File destino = elegido.getName().toLowerCase().endsWith(formato.extension)
                ? elegido
                : new File(elegido.getParentFile(), elegido.getName() + formato.extension);
        ultimaCarpeta = destino.getParentFile();

        ejecutar(destino, generador, () -> {
            notificador.exito("Archivo guardado: " + destino.getName());
            abrir(destino);
        });
    }

    public void abrirTemporal(String nombre, Formato formato, Generador generador) {
        File destino;
        try {
            File carpeta = Files.createTempDirectory("comprobantes").toFile();
            carpeta.deleteOnExit();
            destino = new File(carpeta, nombre + formato.extension);
            destino.deleteOnExit();
        } catch (IOException e) {
            notificador.error("No se pudo crear el archivo temporal: " + e.getMessage());
            return;
        }
        ejecutar(destino, generador, () -> {
            notificador.info("Abriendo " + destino.getName() + "…");
            abrir(destino);
        });
    }

    private void ejecutar(File destino, Generador generador, Runnable alTerminar) {
        Task<Void> tarea = new Task<>() {
            @Override
            protected Void call() throws Exception {
                generador.generar(destino);
                return null;
            }
        };
        tarea.setOnSucceeded(e -> alTerminar.run());
        tarea.setOnFailed(e -> {
            Throwable causa = tarea.getException();
            log.error("No se pudo generar {}", destino, causa);
            notificador.error("No se pudo generar el archivo: " + causa.getMessage());
        });
        Thread hilo = new Thread(tarea, "exportador");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void abrir(File archivo) {
        if (hostServices != null) {
            hostServices.showDocument(archivo.toURI().toString());
        }
    }
}
