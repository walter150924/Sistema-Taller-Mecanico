package com.taller.mecanico.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TipoComprobante {
    BOLETA("B001", "Boleta de venta electrónica"),
    NOTA_VENTA("NV01", "Nota de venta");

    private final String serie;
    private final String descripcion;

    @Override
    public String toString() {
        return descripcion;
    }
}
