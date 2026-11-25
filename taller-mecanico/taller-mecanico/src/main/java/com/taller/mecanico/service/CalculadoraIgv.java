package com.taller.mecanico.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

public final class CalculadoraIgv {
    public static final BigDecimal TASA_IGV = new BigDecimal("0.18");

    private CalculadoraIgv() {
    }

    public record Totales(BigDecimal baseImponible, BigDecimal igv, BigDecimal total, BigDecimal tasa) {
        public static final Totales CERO = new Totales(cero(), cero(), cero(), TASA_IGV);
    }

    public static BigDecimal importe(int cantidad, BigDecimal precioUnitario) {
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
    }

    public static Totales calcular(Collection<BigDecimal> importes, boolean aplicaIgv, boolean preciosIncluyenIgv) {
        BigDecimal suma = importes.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        if (!aplicaIgv) {
            return new Totales(suma, cero(), suma, BigDecimal.ZERO);
        }
        if (preciosIncluyenIgv) {
            BigDecimal base = suma.divide(BigDecimal.ONE.add(TASA_IGV), 2, RoundingMode.HALF_UP);
            return new Totales(base, suma.subtract(base), suma, TASA_IGV);
        }
        BigDecimal igv = suma.multiply(TASA_IGV).setScale(2, RoundingMode.HALF_UP);
        return new Totales(suma, igv, suma.add(igv), TASA_IGV);
    }

    private static BigDecimal cero() {
        return BigDecimal.ZERO.setScale(2);
    }
}
