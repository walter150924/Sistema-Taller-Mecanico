package com.taller.mecanico.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Getter
@RequiredArgsConstructor
public enum EstadoOrden {
    RECIBIDO("Recibido", "Recibir", Permiso.CREAR_ORDENES),
    EN_DIAGNOSTICO("En diagnostico", "Iniciar diagnostico", Permiso.TRABAJAR_ORDENES),
    EN_REPARACION("En reparacion", "Iniciar reparacion", Permiso.TRABAJAR_ORDENES),
    LISTO("Listo para entrega", "Marcar como listo", Permiso.TRABAJAR_ORDENES),
    ENTREGADO("Entregado", "Entregar al cliente", Permiso.ENTREGAR_ORDENES),
    CANCELADO("Cancelado", "Cancelar orden", Permiso.CANCELAR_ORDENES);

    private final String descripcion;
    private final String accion;
    private final Permiso permisoRequerido;

    public boolean esEditable() {
        return this == RECIBIDO || this == EN_DIAGNOSTICO || this == EN_REPARACION;
    }

    public boolean estaEnTaller() {
        return this != ENTREGADO && this != CANCELADO;
    }

    public boolean puedePasarA(EstadoOrden destino) {
        return switch (destino) {
            case EN_DIAGNOSTICO -> this == RECIBIDO;
            case EN_REPARACION -> this == RECIBIDO || this == EN_DIAGNOSTICO;
            case LISTO -> this == EN_REPARACION;
            case ENTREGADO -> this == LISTO;
            case CANCELADO -> estaEnTaller();
            case RECIBIDO -> false;
        };
    }

    public List<EstadoOrden> siguientes() {
        return Arrays.stream(values()).filter(this::puedePasarA).toList();
    }

    public static List<EstadoOrden> enTaller() {
        return Arrays.stream(values()).filter(EstadoOrden::estaEnTaller).toList();
    }

    @Override
    public String toString() {
        return descripcion;
    }
}
