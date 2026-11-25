package com.taller.mecanico.service;

import com.taller.mecanico.model.EstadoVenta;
import com.taller.mecanico.model.Venta;
import com.taller.mecanico.model.VentaDetalle;
import com.taller.mecanico.repository.VentaDetalleRepository;
import com.taller.mecanico.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteVentasService {
    private static final int DIAS_MAXIMOS_SERIE_DIARIA = 92;

    private final VentaRepository ventaRepository;
    private final VentaDetalleRepository detalleRepository;

    public record PuntoSerie(LocalDate fecha, BigDecimal total, long comprobantes) {}

    public record RepuestoVendido(String nombre, long unidades, BigDecimal importe) {}

    public record ClienteFrecuente(String nombre, String dni, long compras, BigDecimal importe) {}

    public record ResumenVentas(
            LocalDate desde,
            LocalDate hasta,
            BigDecimal total,
            BigDecimal baseImponible,
            BigDecimal igv,
            long comprobantes,
            long anuladas,
            BigDecimal ticketPromedio,

            Double variacionTotal,
            boolean seriePorMes,
            List<PuntoSerie> serie,
            List<RepuestoVendido> topRepuestos,
            List<ClienteFrecuente> topClientes,
            List<Venta> ultimas) {}

    public record DatosReporte(List<Venta> ventas, List<VentaDetalle> detalles, ResumenVentas resumen) {}

    public ResumenVentas resumen(LocalDate desde, LocalDate hasta) {
        List<Venta> ventas = ventaRepository.listarPeriodo(VentaService.inicio(desde), VentaService.fin(hasta));
        List<Venta> emitidas = ventas.stream().filter(v -> !v.isAnulada()).toList();

        BigDecimal total = sumar(emitidas, Venta::getTotal);
        BigDecimal base = sumar(emitidas, Venta::getBaseImponible);
        BigDecimal igv = sumar(emitidas, Venta::getIgv);
        BigDecimal ticket = emitidas.isEmpty()
                ? BigDecimal.ZERO.setScale(2)
                : total.divide(BigDecimal.valueOf(emitidas.size()), 2, RoundingMode.HALF_UP);

        long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
        BigDecimal totalAnterior = ventaRepository.listarPeriodo(
                        VentaService.inicio(desde.minusDays(dias)), VentaService.fin(desde.minusDays(1))).stream()
                .filter(v -> !v.isAnulada())
                .map(Venta::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Double variacion = totalAnterior.signum() == 0
                ? null
                : total.subtract(totalAnterior).doubleValue() / totalAnterior.doubleValue() * 100;

        boolean porMes = dias > DIAS_MAXIMOS_SERIE_DIARIA;

        return new ResumenVentas(desde, hasta, total, base, igv, emitidas.size(),
                ventas.size() - emitidas.size(), ticket, variacion, porMes,
                serie(emitidas, desde, hasta, porMes),
                repuestos(desde, hasta),
                clientes(emitidas),
                ventaRepository.ultimas());
    }

    public DatosReporte datosReporte(LocalDate desde, LocalDate hasta) {
        return new DatosReporte(
                ventaRepository.listarPeriodo(VentaService.inicio(desde), VentaService.fin(hasta)),
                detalleRepository.listarPeriodo(VentaService.inicio(desde), VentaService.fin(hasta)),
                resumen(desde, hasta));
    }

    private List<PuntoSerie> serie(List<Venta> ventas, LocalDate desde, LocalDate hasta, boolean porMes) {
        Map<LocalDate, BigDecimal> totales = new TreeMap<>();
        Map<LocalDate, Long> conteo = new TreeMap<>();
        for (Venta v : ventas) {
            LocalDate clave = clave(v.getFecha().toLocalDate(), porMes);
            totales.merge(clave, v.getTotal(), BigDecimal::add);
            conteo.merge(clave, 1L, Long::sum);
        }

        List<PuntoSerie> puntos = new ArrayList<>();
        LocalDate cursor = clave(desde, porMes);
        while (!cursor.isAfter(hasta)) {
            puntos.add(new PuntoSerie(cursor,
                    totales.getOrDefault(cursor, BigDecimal.ZERO),
                    conteo.getOrDefault(cursor, 0L)));
            cursor = porMes ? cursor.plusMonths(1) : cursor.plusDays(1);
        }
        return puntos;
    }

    private static LocalDate clave(LocalDate fecha, boolean porMes) {
        return porMes ? YearMonth.from(fecha).atDay(1) : fecha;
    }

    private List<RepuestoVendido> repuestos(LocalDate desde, LocalDate hasta) {
        return ventaRepository.resumenPorRepuesto(EstadoVenta.EMITIDA,
                        VentaService.inicio(desde), VentaService.fin(hasta)).stream()
                .map(f -> new RepuestoVendido((String) f[0], ((Number) f[1]).longValue(), VentaService.decimal(f[2])))
                .toList();
    }

    private List<ClienteFrecuente> clientes(List<Venta> ventas) {
        Map<Long, ClienteFrecuente> porCliente = new LinkedHashMap<>();
        for (Venta v : ventas) {
            porCliente.merge(v.getCliente().getId(),
                    new ClienteFrecuente(v.getCliente().getNombreCompleto(), v.getCliente().getDni(), 1, v.getTotal()),
                    (a, b) -> new ClienteFrecuente(a.nombre(), a.dni(), a.compras() + 1, a.importe().add(b.importe())));
        }
        return porCliente.values().stream()
                .sorted(Comparator.comparing(ClienteFrecuente::importe).reversed())
                .toList();
    }

    private static BigDecimal sumar(List<Venta> ventas, Function<Venta, BigDecimal> campo) {
        return ventas.stream().map(campo).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }
}
