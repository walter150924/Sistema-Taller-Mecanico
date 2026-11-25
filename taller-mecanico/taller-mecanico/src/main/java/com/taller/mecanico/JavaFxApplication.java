package com.taller.mecanico;

import com.taller.mecanico.ui.Exportador;
import com.taller.mecanico.ui.Ventana;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class JavaFxApplication extends Application {
    private ConfigurableApplicationContext context;

    @Override
    public void init() {
        this.context = new SpringApplicationBuilder(TallerMecanicoApplication.class)
                .web(WebApplicationType.NONE)
                .run();
    }

    @Override
    public void start(Stage stage) {
        context.getBean(Exportador.class).setHostServices(getHostServices());
        context.getBean(Ventana.class).iniciar(stage);
    }

    @Override
    public void stop() {
        context.close();
        Platform.exit();
    }
}
