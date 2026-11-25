package com.taller.mecanico.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Rol {
    ADMINISTRADOR("Administrador"),
    RECEPCIONISTA("Recepcionista"),
    MECANICO("Mecanico");

    private final String descripcion;

    @Override
    public String toString() {
        return descripcion;
    }
}
