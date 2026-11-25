package com.taller.mecanico.seeder;

import com.taller.mecanico.model.Cliente;
import com.taller.mecanico.model.MetodoPago;
import com.taller.mecanico.model.Repuesto;
import com.taller.mecanico.model.TipoComprobante;
import com.taller.mecanico.model.Venta;
import com.taller.mecanico.repository.ClienteRepository;
import com.taller.mecanico.repository.RepuestoRepository;
import com.taller.mecanico.repository.VentaRepository;
import com.taller.mecanico.service.VentaService;
import com.taller.mecanico.service.VentaService.LineaCarrito;
import com.taller.mecanico.service.VentaService.NuevaVenta;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class VentaSeeder {
    private static final int CANTIDAD = 120;
    private static final int DIAS_HISTORIA = 120;
    private static final long SEMILLA = 2026L;
    private static final BigDecimal PRECIO_ECONOMICO = new BigDecimal("100");

    private final VentaService ventaService;
    private final ComprasSimuladas compras;
    private final VentaRepository ventaRepository;
    private final ClienteRepository clienteRepository;
    private final RepuestoRepository repuestoRepository;

    @Transactional
    public int sembrar() {
        if (ventaRepository.count() > 0) {
            return 0;
        }
        List<Cliente> clientes = clienteRepository.findAll();
        List<Repuesto> repuestos = repuestoRepository.findAll();
        if (clientes.isEmpty() || repuestos.isEmpty()) {
            return 0;
        }

        List<Repuesto> economicos = repuestos.stream()
                .filter(p -> p.getPrecio().compareTo(PRECIO_ECONOMICO) < 0)
                .toList();

        Random azar = new Random(SEMILLA);
        LocalDate hoy = LocalDate.now();

        List<LocalDateTime> fechas = new ArrayList<>();
        while (fechas.size() < CANTIDAD) {
            LocalDate dia = hoy.minusDays(azar.nextInt(DIAS_HISTORIA));
            boolean finDeSemana = dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY;
            if (!finDeSemana && azar.nextDouble() < 0.35) {
                continue;
            }
            LocalDateTime momento = dia.atTime(8 + azar.nextInt(13), azar.nextInt(60), azar.nextInt(60));
            if (momento.isAfter(LocalDateTime.now())) {
                momento = LocalDateTime.now().minusMinutes(1 + azar.nextInt(90));
            }
            fechas.add(momento);
        }
        fechas.sort(Comparator.naturalOrder());

        int registradas = 0;
        LocalDateTime anterior = fechas.get(0).minusDays(1);
        for (LocalDateTime fecha : fechas) {
            boolean esBoleta = azar.nextDouble() < 0.85;
            boolean aplicaIgv = esBoleta || azar.nextDouble() < 0.5;
            boolean incluyeIgv = !aplicaIgv || azar.nextDouble() < 0.9;

            List<LineaCarrito> lineas = new ArrayList<>();
            Set<Long> usados = new HashSet<>();
            int items = 1 + azar.nextInt(5);
            while (lineas.size() < items) {
                List<Repuesto> fuente = azar.nextDouble() < 0.85 && !economicos.isEmpty() ? economicos : repuestos;
                Repuesto p = fuente.get(azar.nextInt(fuente.size()));
                if (!usados.add(p.getId())) {
                    continue;
                }
                boolean caro = p.getPrecio().compareTo(PRECIO_ECONOMICO) >= 0;
                int cantidad = caro ? 1 : 1 + azar.nextInt(azar.nextDouble() < 0.7 ? 3 : 8);
                lineas.add(new LineaCarrito(p.getId(), cantidad, p.getPrecio()));
            }

            reponerSiFalta(lineas, repuestos, anterior, fecha, azar);
            anterior = fecha;

            Venta venta = ventaService.registrar(new NuevaVenta(
                    esBoleta ? TipoComprobante.BOLETA : TipoComprobante.NOTA_VENTA,
                    clientes.get(azar.nextInt(clientes.size())).getId(),
                    aplicaIgv, incluyeIgv,
                    azar.nextDouble() < 0.1 ? "Venta en mostrador" : null,
                    lineas, metodoAleatorio(azar)), fecha);
            registradas++;

            if (azar.nextDouble() < 0.04) {
                ventaService.anular(venta.getId(), fecha.plusSeconds(1));
            }
        }
        return registradas;
    }

    private void reponerSiFalta(List<LineaCarrito> lineas, List<Repuesto> repuestos,
                                LocalDateTime anterior, LocalDateTime fecha, Random azar) {
        Map<Repuesto, Integer> faltantes = new LinkedHashMap<>();
        for (LineaCarrito l : lineas) {
            Repuesto p = repuestos.stream().filter(x -> x.getId().equals(l.repuestoId())).findFirst().orElseThrow();
            if (p.getStock() < l.cantidad()) {
                faltantes.put(p, ComprasSimuladas.cantidadReposicion(p, azar) + l.cantidad());
            }
        }
        if (!faltantes.isEmpty()) {
            LocalDateTime momento = fecha.minusHours(2);
            compras.comprar(faltantes, momento.isAfter(anterior) ? momento : anterior.plusMinutes(1), azar);
        }
    }

    private static MetodoPago metodoAleatorio(Random azar) {
        return MetodoPago.values()[azar.nextInt(MetodoPago.values().length)];
    }
}
