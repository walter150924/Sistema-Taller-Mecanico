package com.taller.mecanico.seeder;

import com.taller.mecanico.model.Rol;
import com.taller.mecanico.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class UsuarioInicialSeeder implements ApplicationRunner {
    static final String USUARIO_ADMINISTRADOR = "admin";
    static final String CLAVE_ADMINISTRADOR = "admin123";

    private final UsuarioService usuarioService;

    @Override
    public void run(ApplicationArguments args) {
        if (!usuarioService.hayAdministradorActivo()) {
            usuarioService.registrarSiNoExiste(USUARIO_ADMINISTRADOR, "Administrador del taller",
                    Rol.ADMINISTRADOR, CLAVE_ADMINISTRADOR);
            log.info("Usuario inicial creado: {} / {}", USUARIO_ADMINISTRADOR, CLAVE_ADMINISTRADOR);
        }
    }
}
