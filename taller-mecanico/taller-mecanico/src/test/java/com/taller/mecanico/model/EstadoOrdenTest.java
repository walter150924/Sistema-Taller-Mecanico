package com.taller.mecanico.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoOrdenTest {
    @Test
    void sigueElFlujoDelTaller() {
        assertThat(EstadoOrden.RECIBIDO.siguientes())
                .containsExactly(EstadoOrden.EN_DIAGNOSTICO, EstadoOrden.EN_REPARACION, EstadoOrden.CANCELADO);
        assertThat(EstadoOrden.EN_REPARACION.siguientes())
                .containsExactly(EstadoOrden.LISTO, EstadoOrden.CANCELADO);
        assertThat(EstadoOrden.LISTO.siguientes())
                .containsExactly(EstadoOrden.ENTREGADO, EstadoOrden.CANCELADO);
    }

    @Test
    void lasOrdenesCerradasNoCambianDeEstado() {
        assertThat(EstadoOrden.ENTREGADO.siguientes()).isEmpty();
        assertThat(EstadoOrden.CANCELADO.siguientes()).isEmpty();
        assertThat(EstadoOrden.LISTO.esEditable()).isFalse();
        assertThat(EstadoOrden.EN_DIAGNOSTICO.esEditable()).isTrue();
    }

    @Test
    void elMecanicoNoPuedeEntregarNiCancelar() {
        assertThat(EstadoOrden.LISTO.getPermisoRequerido().permiteA(Rol.MECANICO)).isTrue();
        assertThat(EstadoOrden.ENTREGADO.getPermisoRequerido().permiteA(Rol.MECANICO)).isFalse();
        assertThat(EstadoOrden.CANCELADO.getPermisoRequerido().permiteA(Rol.MECANICO)).isFalse();
    }
}
