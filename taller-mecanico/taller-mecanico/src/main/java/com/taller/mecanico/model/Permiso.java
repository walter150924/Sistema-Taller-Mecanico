package com.taller.mecanico.model;

import java.util.EnumSet;
import java.util.Set;

import static com.taller.mecanico.model.Rol.ADMINISTRADOR;
import static com.taller.mecanico.model.Rol.MECANICO;
import static com.taller.mecanico.model.Rol.RECEPCIONISTA;

public enum Permiso {
    VER_DASHBOARD(ADMINISTRADOR),
    GESTIONAR_VENTAS(ADMINISTRADOR, RECEPCIONISTA),
    GESTIONAR_COMPRAS(ADMINISTRADOR),
    VER_INVENTARIO(ADMINISTRADOR, RECEPCIONISTA),
    GESTIONAR_CLIENTES(ADMINISTRADOR, RECEPCIONISTA),
    GESTIONAR_VEHICULOS(ADMINISTRADOR, RECEPCIONISTA),
    GESTIONAR_CATALOGO(ADMINISTRADOR),
    GESTIONAR_USUARIOS(ADMINISTRADOR),
    CREAR_ORDENES(ADMINISTRADOR, RECEPCIONISTA),
    TRABAJAR_ORDENES(ADMINISTRADOR, RECEPCIONISTA, MECANICO),
    ENTREGAR_ORDENES(ADMINISTRADOR, RECEPCIONISTA),
    CANCELAR_ORDENES(ADMINISTRADOR, RECEPCIONISTA),
    VER_TODAS_LAS_ORDENES(ADMINISTRADOR, RECEPCIONISTA);

    private final Set<Rol> rolesPermitidos;

    Permiso(Rol primero, Rol... otros) {
        this.rolesPermitidos = EnumSet.of(primero, otros);
    }

    public boolean permiteA(Rol rol) {
        return rolesPermitidos.contains(rol);
    }
}
