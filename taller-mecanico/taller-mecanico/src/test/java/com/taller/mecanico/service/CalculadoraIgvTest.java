package com.taller.mecanico.service;

import com.taller.mecanico.service.CalculadoraIgv.Totales;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalculadoraIgvTest {
    private static final List<BigDecimal> IMPORTES = List.of(new BigDecimal("100.00"), new BigDecimal("18.00"));

    @Test
    void preciosConIgvExtraeLaBase() {
        Totales t = CalculadoraIgv.calcular(IMPORTES, true, true);
        assertThat(t.total()).isEqualByComparingTo("118.00");
        assertThat(t.baseImponible()).isEqualByComparingTo("100.00");
        assertThat(t.igv()).isEqualByComparingTo("18.00");
    }

    @Test
    void preciosSinIgvLoAgregaEncima() {
        Totales t = CalculadoraIgv.calcular(IMPORTES, true, false);
        assertThat(t.baseImponible()).isEqualByComparingTo("118.00");
        assertThat(t.igv()).isEqualByComparingTo("21.24");
        assertThat(t.total()).isEqualByComparingTo("139.24");
    }

    @Test
    void sinIgvElTotalEsLaSuma() {
        Totales t = CalculadoraIgv.calcular(IMPORTES, false, true);
        assertThat(t.baseImponible()).isEqualByComparingTo("118.00");
        assertThat(t.igv()).isEqualByComparingTo("0");
        assertThat(t.total()).isEqualByComparingTo("118.00");
        assertThat(t.tasa()).isEqualByComparingTo("0");
    }

    @Test
    void baseMasIgvSiempreCuadraConElTotal() {
        for (int centimos = 1; centimos < 5000; centimos += 7) {
            BigDecimal importe = BigDecimal.valueOf(centimos, 2);
            Totales t = CalculadoraIgv.calcular(List.of(importe), true, true);
            assertThat(t.baseImponible().add(t.igv())).isEqualByComparingTo(t.total());
        }
    }

    @Test
    void importeRedondeaADosDecimales() {
        assertThat(CalculadoraIgv.importe(3, new BigDecimal("3.335"))).isEqualByComparingTo("10.01");
    }
}
