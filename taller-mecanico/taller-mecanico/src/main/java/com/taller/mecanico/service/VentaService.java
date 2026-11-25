package com.taller.mecanico.service;

import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.Cliente;
import com.taller.mecanico.model.EstadoVenta;
import com.taller.mecanico.model.MetodoPago;
import com.taller.mecanico.model.Repuesto;
import com.taller.mecanico.model.TipoComprobante;
import com.taller.mecanico.model.TipoMovimiento;
import com.taller.mecanico.model.Venta;
import com.taller.mecanico.model.VentaDetalle;
import com.taller.mecanico.repository.ClienteRepository;
import com.taller.mecanico.repository.RepuestoRepository;
import com.taller.mecanico.repository.VentaRepository;
import com.taller.mecanico.service.CalculadoraIgv.Totales;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class VentaService {
    private static final LocalDate FECHA_MINIMA = LocalDate.of(2000, 1, 1);
    private static final LocalDate FECHA_MAXIMA = LocalDate.of(2100, 12, 31);
    private static final LocalTime FIN_DEL_DIA = LocalTime.of(23, 59, 59, 999_000_000);

    private final VentaRepository repository;
    private final ClienteRepository clienteRepository;
    private final RepuestoRepository repuestoRepository;
    private final InventarioService inventarioService;

    public record LineaCarrito(Long repuestoId, int cantidad, BigDecimal precioUnitario) {}

    public record NuevaVenta(TipoComprobante tipo,
                             Long clienteId,
                             boolean aplicaIgv,
                             boolean preciosIncluyenIgv,
                             String observacion,
                             List<LineaCarrito> lineas,
                             MetodoPago metodoPago) {}

    public record Totalizado(long comprobantes, BigDecimal baseImponible, BigDecimal igv, BigDecimal total) {}

    public Page<Venta> listar(String texto, LocalDate desde, LocalDate hasta, int pagina, int tamanio) {
        return repository.buscar(
                texto == null ? "" : texto.trim(),
                inicio(desde), fin(hasta),
                PageRequest.of(pagina, tamanio, Sort.by("fecha").descending().and(Sort.by("id").descending())));
    }

    public Totalizado totalizar(String texto, LocalDate desde, LocalDate hasta) {
        Object[] fila = repository.totalizar(texto == null ? "" : texto.trim(), EstadoVenta.EMITIDA,
                inicio(desde), fin(hasta)).get(0);
        return new Totalizado(((Number) fila[0]).longValue(), decimal(fila[1]), decimal(fila[2]), decimal(fila[3]));
    }

    public Venta obtener(Long id) {
        return repository.buscarConDetalle(id)
                .orElseThrow(() -> new NegocioException("La venta ya no existe en la base de datos."));
    }

    public LocalDate primeraFecha() {
        LocalDateTime primera = repository.primeraFecha();
        return primera == null ? LocalDate.now() : primera.toLocalDate();
    }

    public String siguienteComprobante(TipoComprobante tipo) {
        return Venta.formatearComprobante(tipo.getSerie(), siguienteNumero(tipo.getSerie()));
    }

    @Transactional
    public Venta registrar(NuevaVenta solicitud) {
        return registrar(solicitud, LocalDateTime.now());
    }

    @Transactional
    public Venta registrar(NuevaVenta solicitud, LocalDateTime fecha) {
        validar(solicitud);

        Cliente cliente = clienteRepository.findById(solicitud.clienteId())
                .orElseThrow(() -> new NegocioException("El cliente seleccionado ya no existe."));
        Map<Long, Repuesto> repuestos = repuestoRepository.findAllById(
                        solicitud.lineas().stream().map(LineaCarrito::repuestoId).toList()).stream()
                .collect(Collectors.toMap(Repuesto::getId, Function.identity()));

        TipoComprobante tipo = solicitud.tipo();
        int numero = siguienteNumero(tipo.getSerie());
        Venta venta = Venta.builder()
                .tipoComprobante(tipo)
                .serie(tipo.getSerie())
                .numero(numero)
                .comprobante(Venta.formatearComprobante(tipo.getSerie(), numero))
                .fecha(fecha)
                .cliente(cliente)
                .aplicaIgv(solicitud.aplicaIgv())
                .preciosIncluyenIgv(solicitud.preciosIncluyenIgv())
                .estado(EstadoVenta.EMITIDA)
                .metodoPago(solicitud.metodoPago())
                .observacion(textoONull(solicitud.observacion()))
                .build();

        if (solicitud.lineas().stream().anyMatch(l -> !repuestos.containsKey(l.repuestoId()))) {
            throw new NegocioException("Un repuesto del carrito ya no existe en el catalogo.");
        }

        inventarioService.validarDisponible(solicitud.lineas().stream()
                .collect(Collectors.groupingBy(l -> repuestos.get(l.repuestoId()),
                        Collectors.summingInt(LineaCarrito::cantidad))));

        for (LineaCarrito linea : solicitud.lineas()) {
            Repuesto repuesto = repuestos.get(linea.repuestoId());
            venta.agregarDetalle(VentaDetalle.builder()
                    .repuesto(repuesto)
                    .codigoRepuesto(repuesto.getCodigoRepuesto())
                    .nombreRepuesto(repuesto.getNombre())
                    .cantidad(linea.cantidad())
                    .precioUnitario(linea.precioUnitario())
                    .importe(CalculadoraIgv.importe(linea.cantidad(), linea.precioUnitario()))
                    .build());
        }

        Totales totales = CalculadoraIgv.calcular(
                venta.getDetalles().stream().map(VentaDetalle::getImporte).toList(),
                venta.isAplicaIgv(), venta.isPreciosIncluyenIgv());
        venta.setBaseImponible(totales.baseImponible());
        venta.setIgv(totales.igv());
        venta.setTotal(totales.total());
        venta.setTasaIgv(totales.tasa());

        Venta guardada = repository.save(venta);
        for (VentaDetalle d : guardada.getDetalles()) {
            inventarioService.salida(d.getRepuesto(), d.getCantidad(), TipoMovimiento.VENTA,
                    guardada.getComprobante(), fecha, guardada.getId(), null);
        }
        log.info("Venta registrada: {} total={}", guardada.getComprobante(), guardada.getTotal());
        return guardada;
    }

    @Transactional
    public void anular(Long id) {
        anular(id, LocalDateTime.now());
    }

    @Transactional
    public void anular(Long id, LocalDateTime ahora) {
        Venta venta = obtener(id);
        if (venta.isAnulada()) {
            throw new NegocioException("El comprobante " + venta.getComprobante() + " ya estaba anulado.");
        }
        for (VentaDetalle d : venta.getDetalles()) {
            inventarioService.entrada(d.getRepuesto(), d.getCantidad(), null, TipoMovimiento.ANULACION_VENTA,
                    venta.getComprobante() + " (anulada)", ahora, venta.getId(), null);
        }
        venta.setEstado(EstadoVenta.ANULADA);
        log.info("Venta anulada: {}", venta.getComprobante());
    }

    private void validar(NuevaVenta s) {
        if (s.tipo() == null) {
            throw new NegocioException("Selecciona el tipo de comprobante.");
        }
        if (s.clienteId() == null) {
            throw new NegocioException("Selecciona un cliente para la venta.");
        }
        if (s.metodoPago() == null) {
            throw new NegocioException("Selecciona el metodo de pago.");
        }
        if (s.lineas() == null || s.lineas().isEmpty()) {
            throw new NegocioException("Agrega al menos un repuesto al carrito.");
        }
        for (LineaCarrito l : s.lineas()) {
            if (l.cantidad() < 1) {
                throw new NegocioException("Todas las cantidades deben ser mayores a cero.");
            }
            if (l.precioUnitario() == null || l.precioUnitario().signum() <= 0) {
                throw new NegocioException("Todos los precios deben ser mayores a cero.");
            }
        }
    }

    private int siguienteNumero(String serie) {
        Integer ultimo = repository.ultimoNumero(serie);
        return ultimo == null ? 1 : ultimo + 1;
    }

    static LocalDateTime inicio(LocalDate fecha) {
        return (fecha == null ? FECHA_MINIMA : fecha).atStartOfDay();
    }

    static LocalDateTime fin(LocalDate fecha) {
        return (fecha == null ? FECHA_MAXIMA : fecha).atTime(FIN_DEL_DIA);
    }

    static BigDecimal decimal(Object valor) {
        if (valor instanceof BigDecimal b) {
            return b.setScale(2, RoundingMode.HALF_UP);
        }
        return valor == null
                ? BigDecimal.ZERO.setScale(2)
                : new BigDecimal(valor.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private static String textoONull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
