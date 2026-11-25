package com.taller.mecanico.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TipoMovimiento {
    COMPRA("Compra", true),
    ANULACION_VENTA("Anulacion de venta", true),
    VENTA("Venta", false),
    ANULACION_COMPRA("Anulacion de compra", false),
    CONSUMO_ORDEN("Consumo en orden de servicio", false),
    DEVOLUCION_ORDEN("Devolucion de orden de servicio", true);

    private final String descripcion;
    private final boolean entrada;

    @Override
    public String toString() {
        return descripcion;
    }
}
