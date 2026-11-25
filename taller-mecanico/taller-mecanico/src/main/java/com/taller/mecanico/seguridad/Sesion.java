package com.taller.mecanico.seguridad;

import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.Permiso;
import com.taller.mecanico.model.Usuario;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class Sesion {
    private Usuario usuarioActual;

    public void iniciar(Usuario usuario) {
        this.usuarioActual = usuario;
    }

    public void cerrar() {
        this.usuarioActual = null;
    }

    public Optional<Usuario> usuario() {
        return Optional.ofNullable(usuarioActual);
    }

    public Usuario usuarioActual() {
        return usuario().orElseThrow(() -> new NegocioException("No hay una sesion iniciada."));
    }

    public boolean tiene(Permiso permiso) {
        return usuario().map(u -> u.tiene(permiso)).orElse(false);
    }

    public void exigir(Permiso permiso) {
        if (!tiene(permiso)) {
            throw new NegocioException("No tienes permiso para realizar esta accion.");
        }
    }

    public boolean esMecanico() {
        return usuario().map(Usuario::esMecanico).orElse(false);
    }
}
