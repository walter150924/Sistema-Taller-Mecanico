package com.taller.mecanico.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Getter
@RequiredArgsConstructor
public enum Periodo {
    HOY("Hoy"),
    ULTIMOS_7("Ultimos 7 dias"),
    ESTE_MES("Este mes"),
    ULTIMOS_30("Ultimos 30 dias"),
    ULTIMOS_90("Ultimos 90 dias"),
    ESTE_ANIO("Este año");

    private final String etiqueta;

    public LocalDate desde(LocalDate hoy) {
        return switch (this) {
            case HOY -> hoy;
            case ULTIMOS_7 -> hoy.minusDays(6);
            case ESTE_MES -> hoy.withDayOfMonth(1);
            case ULTIMOS_30 -> hoy.minusDays(29);
            case ULTIMOS_90 -> hoy.minusDays(89);
            case ESTE_ANIO -> hoy.withDayOfYear(1);
        };
    }

    public long dias(LocalDate hoy) {
        return ChronoUnit.DAYS.between(desde(hoy), hoy) + 1;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
