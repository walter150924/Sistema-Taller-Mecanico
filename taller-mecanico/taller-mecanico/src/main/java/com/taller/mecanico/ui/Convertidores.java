package com.taller.mecanico.ui;

import javafx.util.StringConverter;

import java.util.function.Function;

public final class Convertidores {
    private Convertidores() {
    }

    public static <T> StringConverter<T> mostrando(Function<T, String> texto, String textoSinValor) {
        return new StringConverter<>() {
            @Override
            public String toString(T valor) {
                return valor == null ? textoSinValor : texto.apply(valor);
            }

            @Override
            public T fromString(String texto) {
                throw new UnsupportedOperationException("Seleccion de solo lectura");
            }
        };
    }
}
