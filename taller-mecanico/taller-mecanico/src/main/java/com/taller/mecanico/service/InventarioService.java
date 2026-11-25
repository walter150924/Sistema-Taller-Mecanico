package com.taller.mecanico.service;

import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.MovimientoInventario;
import com.taller.mecanico.model.Repuesto;
import com.taller.mecanico.model.TipoMovimiento;
import com.taller.mecanico.repository.MovimientoInventarioRepository;
import com.taller.mecanico.repository.RepuestoRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventarioService {
    private final RepuestoRepository repuestoRepository;
    private final MovimientoInventarioRepository movimientoRepository;

    @Getter
    @RequiredArgsConstructor
    public enum FiltroStock {
        TODOS("Todos los repuestos"),
        DISPONIBLE("Stock suficiente"),
        BAJO("Stock bajo"),
        SIN_STOCK("Sin stock");

        private final String etiqueta;

        @Override
        public String toString() {
            return etiqueta;
        }
    }

    public record ResumenInventario(long repuestos, long unidades, BigDecimal valorCosto,
                                    BigDecimal valorVenta, long stockBajo, long sinStock) {}

    public Page<Repuesto> listar(String texto, FiltroStock filtro, int pagina, int tamanio) {
        return repuestoRepository.inventario(texto == null ? "" : texto.trim(),
                (filtro == null ? FiltroStock.TODOS : filtro).name(),
                PageRequest.of(pagina, tamanio, Sort.by("codigoRepuesto")));
    }

    public List<Repuesto> listarTodo() {
        return repuestoRepository.listarConCategoria();
    }

    public ResumenInventario resumen() {
        List<Repuesto> repuestos = repuestoRepository.findAll();
        long unidades = 0;
        long bajo = 0;
        long sin = 0;
        BigDecimal costo = BigDecimal.ZERO;
        BigDecimal venta = BigDecimal.ZERO;
        for (Repuesto p : repuestos) {
            int stock = Math.max(0, p.getStock());
            unidades += stock;
            if (p.isSinStock()) {
                sin++;
            } else if (p.isStockBajo()) {
                bajo++;
            }
            costo = costo.add(valorizado(p));
            venta = venta.add(p.getPrecio().multiply(BigDecimal.valueOf(stock)));
        }
        return new ResumenInventario(repuestos.size(), unidades,
                costo.setScale(2, RoundingMode.HALF_UP), venta.setScale(2, RoundingMode.HALF_UP), bajo, sin);
    }

    public List<MovimientoInventario> kardex(Long repuestoId) {
        return movimientoRepository.kardex(repuestoId);
    }

    public static BigDecimal valorizado(Repuesto p) {
        if (p.getCostoPromedio() == null || p.getStock() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return p.getCostoPromedio().multiply(BigDecimal.valueOf(p.getStock())).setScale(2, RoundingMode.HALF_UP);
    }

    public void validarDisponible(Map<Repuesto, Integer> pedido) {
        String faltantes = pedido.entrySet().stream()
                .filter(e -> e.getValue() > e.getKey().getStock())
                .map(e -> e.getKey().getNombre() + " (disponible " + Math.max(0, e.getKey().getStock())
                        + ", solicitado " + e.getValue() + ")")
                .collect(Collectors.joining("; "));
        if (!faltantes.isEmpty()) {
            throw new NegocioException("Stock insuficiente: " + faltantes + ".");
        }
    }

    @Transactional
    public MovimientoInventario entrada(Repuesto repuesto, int cantidad, BigDecimal costoUnitario,
                                        TipoMovimiento tipo, String documento, LocalDateTime fecha,
                                        Long ventaId, Long compraId) {
        if (!tipo.isEntrada()) {
            throw new IllegalArgumentException(tipo + " no es un movimiento de entrada");
        }
        int anterior = Math.max(0, repuesto.getStock());
        if (tipo == TipoMovimiento.COMPRA && costoUnitario != null) {
            repuesto.setCostoPromedio(costoPromedio(repuesto.getCostoPromedio(), anterior, costoUnitario, cantidad));
        }
        repuesto.setStock(repuesto.getStock() + cantidad);
        return registrar(repuesto, tipo, cantidad, costoUnitario, documento, fecha, ventaId, compraId);
    }

    @Transactional
    public MovimientoInventario salida(Repuesto repuesto, int cantidad, TipoMovimiento tipo, String documento,
                                       LocalDateTime fecha, Long ventaId, Long compraId) {
        if (tipo.isEntrada()) {
            throw new IllegalArgumentException(tipo + " no es un movimiento de salida");
        }
        if (cantidad > repuesto.getStock()) {
            throw new NegocioException("Stock insuficiente de " + repuesto.getNombre() + ": disponible "
                    + Math.max(0, repuesto.getStock()) + ", se requieren " + cantidad + ".");
        }
        repuesto.setStock(repuesto.getStock() - cantidad);
        return registrar(repuesto, tipo, cantidad, null, documento, fecha, ventaId, compraId);
    }

    private MovimientoInventario registrar(Repuesto repuesto, TipoMovimiento tipo, int cantidad, BigDecimal costo,
                                           String documento, LocalDateTime fecha, Long ventaId, Long compraId) {
        return movimientoRepository.save(MovimientoInventario.builder()
                .repuesto(repuesto)
                .tipo(tipo)
                .cantidad(cantidad)
                .stockResultante(repuesto.getStock())
                .costoUnitario(costo)
                .documento(documento)
                .fecha(fecha)
                .ventaId(ventaId)
                .compraId(compraId)
                .build());
    }

    static BigDecimal costoPromedio(BigDecimal actual, int stock, BigDecimal nuevo, int cantidad) {
        if (actual == null || stock <= 0) {
            return nuevo.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal total = actual.multiply(BigDecimal.valueOf(stock))
                .add(nuevo.multiply(BigDecimal.valueOf(cantidad)));
        return total.divide(BigDecimal.valueOf((long) stock + cantidad), 4, RoundingMode.HALF_UP);
    }
}
