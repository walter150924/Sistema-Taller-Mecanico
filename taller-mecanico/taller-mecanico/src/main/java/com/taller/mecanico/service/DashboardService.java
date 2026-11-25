package com.taller.mecanico.service;

import com.taller.mecanico.model.Repuesto;
import com.taller.mecanico.repository.CategoriaRepository;
import com.taller.mecanico.repository.ClienteRepository;
import com.taller.mecanico.repository.RepuestoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {
    private static final int[] LIMITES_PRECIO = {10, 50, 100, 500, 1000};

    private final ClienteRepository clienteRepository;
    private final CategoriaRepository categoriaRepository;
    private final RepuestoRepository repuestoRepository;

    public record SerieCategoria(String nombre, long repuestos, double precioPromedio) {}

    public record RangoPrecio(String etiqueta, long cantidad) {}

    public record Resumen(
            long clientes,
            long categorias,
            long repuestos,
            BigDecimal valorCatalogo,
            BigDecimal precioPromedio,
            long clientesConEmail,
            long clientesConTelefono,
            long clientesContactoCompleto,
            List<SerieCategoria> porCategoria,
            List<RangoPrecio> rangosPrecio,
            List<Repuesto> masCaros) {}

    public Resumen obtenerResumen() {
        List<BigDecimal> precios = repuestoRepository.listarPrecios();
        BigDecimal total = precios.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal promedio = precios.isEmpty()
                ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(precios.size()), 2, RoundingMode.HALF_UP);

        List<SerieCategoria> porCategoria = repuestoRepository.resumenPorCategoria().stream()
                .map(fila -> new SerieCategoria(
                        (String) fila[0],
                        ((Number) fila[1]).longValue(),
                        fila[2] == null ? 0 : ((Number) fila[2]).doubleValue()))
                .toList();

        return new Resumen(
                clienteRepository.count(),
                categoriaRepository.count(),
                precios.size(),
                total,
                promedio,
                clienteRepository.contarConEmail(),
                clienteRepository.contarConTelefono(),
                clienteRepository.contarContactoCompleto(),
                porCategoria,
                agruparPorRango(precios),
                repuestoRepository.top5MasCaros());
    }

    private List<RangoPrecio> agruparPorRango(List<BigDecimal> precios) {
        long[] conteo = new long[LIMITES_PRECIO.length + 1];
        for (BigDecimal precio : precios) {
            int i = 0;
            while (i < LIMITES_PRECIO.length && precio.doubleValue() >= LIMITES_PRECIO[i]) {
                i++;
            }
            conteo[i]++;
        }

        List<RangoPrecio> rangos = new ArrayList<>();
        int desde = 0;
        for (int i = 0; i < LIMITES_PRECIO.length; i++) {
            rangos.add(new RangoPrecio(desde + "–" + LIMITES_PRECIO[i], conteo[i]));
            desde = LIMITES_PRECIO[i];
        }
        rangos.add(new RangoPrecio("≥ " + desde, conteo[LIMITES_PRECIO.length]));
        return rangos;
    }
}
