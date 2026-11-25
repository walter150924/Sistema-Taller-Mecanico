package com.taller.mecanico.seeder;

import com.taller.mecanico.model.Repuesto;
import com.taller.mecanico.model.TipoMovimiento;
import com.taller.mecanico.model.Venta;
import com.taller.mecanico.model.VentaDetalle;
import com.taller.mecanico.repository.CompraRepository;
import com.taller.mecanico.repository.MovimientoInventarioRepository;
import com.taller.mecanico.repository.RepuestoRepository;
import com.taller.mecanico.repository.VentaRepository;
import com.taller.mecanico.service.InventarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
@RequiredArgsConstructor
public class InventarioSeeder {
    private static final long SEMILLA = 7L;
    private static final int REPUESTOS_POR_COMPRA = 20;
    private static final int DIAS_ANTES_DE_LA_PRIMERA_VENTA = 5;
    private static final int DIAS_HISTORIA = 125;

    private final ComprasSimuladas compras;
    private final CompraRepository compraRepository;
    private final RepuestoRepository repuestoRepository;
    private final VentaRepository ventaRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final InventarioService inventarioService;

    @Transactional
    public int sembrar() {
        if (compraRepository.count() > 0) {
            return 0;
        }
        List<Repuesto> repuestos = repuestoRepository.findAll(Sort.by("id"));
        if (repuestos.isEmpty()) {
            return 0;
        }

        List<Venta> previas = ventaRepository.findAll().stream()
                .filter(v -> !movimientoRepository.existsByVentaId(v.getId()))
                .sorted(Comparator.comparing(Venta::getFecha).thenComparing(Venta::getId))
                .toList();
        Map<Long, Integer> vendidas = new HashMap<>();
        for (Venta v : previas) {
            if (!v.isAnulada()) {
                for (VentaDetalle d : v.getDetalles()) {
                    vendidas.merge(d.getRepuesto().getId(), d.getCantidad(), Integer::sum);
                }
            }
        }

        LocalDateTime apertura = previas.isEmpty()
                ? LocalDateTime.now().minusDays(DIAS_HISTORIA).withHour(9).withMinute(0)
                : previas.get(0).getFecha().minusDays(DIAS_ANTES_DE_LA_PRIMERA_VENTA).withHour(9).withMinute(0);

        Random azar = new Random(SEMILLA);
        int registradas = 0;
        for (int i = 0; i < repuestos.size(); i += REPUESTOS_POR_COMPRA) {
            Map<Repuesto, Integer> lote = new LinkedHashMap<>();
            for (Repuesto p : repuestos.subList(i, Math.min(repuestos.size(), i + REPUESTOS_POR_COMPRA))) {
                lote.put(p, ComprasSimuladas.cantidadReposicion(p, azar) + vendidas.getOrDefault(p.getId(), 0));
            }
            compras.comprar(lote, apertura.plusMinutes(15L * registradas), azar);
            registradas++;
        }

        for (Venta v : previas) {
            if (v.isAnulada()) {
                continue;
            }
            for (VentaDetalle d : new ArrayList<>(v.getDetalles())) {
                inventarioService.salida(d.getRepuesto(), d.getCantidad(), TipoMovimiento.VENTA,
                        v.getComprobante(), v.getFecha(), v.getId(), null);
            }
        }
        return registradas;
    }
}
