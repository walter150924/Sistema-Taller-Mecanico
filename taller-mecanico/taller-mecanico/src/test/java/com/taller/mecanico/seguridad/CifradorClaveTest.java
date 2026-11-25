package com.taller.mecanico.seguridad;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CifradorClaveTest {
    private final CifradorClave cifrador = new CifradorClave();

    @Test
    void reconoceLaClaveCorrecta() {
        String cifrada = cifrador.cifrar("secreta123");

        assertThat(cifrada).doesNotContain("secreta123");
        assertThat(cifrador.coincide("secreta123", cifrada)).isTrue();
        assertThat(cifrador.coincide("otra-clave", cifrada)).isFalse();
    }

    @Test
    void usaUnaSalDistintaEnCadaCifrado() {
        assertThat(cifrador.cifrar("misma")).isNotEqualTo(cifrador.cifrar("misma"));
    }
}
