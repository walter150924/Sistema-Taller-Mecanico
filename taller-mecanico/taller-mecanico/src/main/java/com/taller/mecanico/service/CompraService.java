package com.taller.mecanico.service;

import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.Compra;
import com.taller.mecanico.model.CompraDetalle;
import com.taller.mecanico.model.EstadoCompra;
import com.taller.mecanico.model.Repuesto;
import com.taller.mecanico.model.Proveedor;
import com.taller.mecanico.model.TipoDocumentoCompra;
import com.taller.mecanico.model.TipoMovimiento;
import com.taller.mecanico.repository.CompraRepository;
import com.taller.mecanico.repository.RepuestoRepository;
import com.taller.mecanico.repository.ProveedorRepository;
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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CompraService {
    private static final String PATRON_RUC = "(10|15|17|20)\\d{9}";

    private final CompraRepository repository;
    private final ProveedorRepository proveedorRepository;
    private final RepuestoRepository repuestoRepository;
    private final InventarioService inventarioService;

    public record LineaCompra(Long repuestoId, int cantidad, BigDecimal costoUnitario) {}

    public record NuevaCompra(String rucProveedor,
                              String razonSocialProveedor,
                              TipoDocumentoCompra tipoDocumento,
                              String numeroDocumento,
                              LocalDate fechaDocumento,
                              boolean aplicaIgv,
                              boolean preciosIncluyenIgv,
                              String observacion,
                              List<LineaCompra> lineas) {}

    public record Totalizado(long compras, BigDecimal baseImponible, BigDecimal igv, BigDecimal total) {}

    public Page<Compra> listar(String texto, LocalDate desde, LocalDate hasta, int pagina, int tamanio) {
        return repository.buscar(texto == null ? "" : texto.trim(),
                VentaService.inicio(desde), VentaService.fin(hasta),
                PageRequest.of(pagina, tamanio, Sort.by("fecha").descending().and(Sort.by("id").descending())));
    }

    public Totalizado totalizar(String texto, LocalDate desde, LocalDate hasta) {
        Object[] fila = repository.totalizar(texto == null ? "" : texto.trim(), EstadoCompra.REGISTRADA,
                VentaService.inicio(desde), VentaService.fin(hasta)).get(0);
        return new Totalizado(((Number) fila[0]).longValue(), VentaService.decimal(fila[1]),
                VentaService.decimal(fila[2]), VentaService.decimal(fila[3]));
    }

    public Compra obtener(Long id) {
        return repository.buscarConDetalle(id)
                .orElseThrow(() -> new NegocioException("La compra ya no existe en la base de datos."));
    }

    public String siguienteCodigo() {
        return Compra.formatearCodigo(siguienteNumero());
    }

    @Transactional
    public Compra registrar(NuevaCompra solicitud) {
        return registrar(solicitud, LocalDateTime.now());
    }

    @Transactional
    public Compra registrar(NuevaCompra s, LocalDateTime fecha) {
        validar(s, fecha);
        Proveedor proveedor = proveedor(s.rucProveedor().trim(), s.razonSocialProveedor().trim());
        String numeroDocumento = s.numeroDocumento().trim().toUpperCase();
        if (proveedor.getId() != null && repository.existsByProveedorIdAndTipoDocumentoAndNumeroDocumentoAndEstado(
                proveedor.getId(), s.tipoDocumento(), numeroDocumento, EstadoCompra.REGISTRADA)) {
            throw new NegocioException("Ya se registro la " + s.tipoDocumento().getDescripcion().toLowerCase()
                    + " " + numeroDocumento + " de " + proveedor.getRazonSocial() + ".");
        }

        Map<Long, Repuesto> repuestos = repuestoRepository.findAllById(
                        s.lineas().stream().map(LineaCompra::repuestoId).toList()).stream()
                .collect(Collectors.toMap(Repuesto::getId, Function.identity()));

        int numero = siguienteNumero();
        Compra compra = Compra.builder()
                .codigo(Compra.formatearCodigo(numero))
                .numero(numero)
                .fecha(fecha)
                .proveedor(proveedor)
                .tipoDocumento(s.tipoDocumento())
                .numeroDocumento(numeroDocumento)
                .fechaDocumento(s.fechaDocumento())
                .aplicaIgv(s.aplicaIgv())
                .preciosIncluyenIgv(s.preciosIncluyenIgv())
                .estado(EstadoCompra.REGISTRADA)
                .observacion(s.observacion() == null || s.observacion().isBlank() ? null : s.observacion().trim())
                .build();

        for (LineaCompra linea : s.lineas()) {
            Repuesto repuesto = repuestos.get(linea.repuestoId());
            if (repuesto == null) {
                throw new NegocioException("Un repuesto de la compra ya no existe en el catalogo.");
            }
            compra.agregarDetalle(CompraDetalle.builder()
                    .repuesto(repuesto)
                    .codigoRepuesto(repuesto.getCodigoRepuesto())
                    .nombreRepuesto(repuesto.getNombre())
                    .cantidad(linea.cantidad())
                    .costoUnitario(linea.costoUnitario().setScale(4, RoundingMode.HALF_UP))
                    .importe(CalculadoraIgv.importe(linea.cantidad(), linea.costoUnitario()))
                    .build());
        }

        Totales t = CalculadoraIgv.calcular(compra.getDetalles().stream().map(CompraDetalle::getImporte).toList(),
                compra.isAplicaIgv(), compra.isPreciosIncluyenIgv());
        compra.setBaseImponible(t.baseImponible());
        compra.setIgv(t.igv());
        compra.setTotal(t.total());
        compra.setTasaIgv(t.tasa());

        Compra guardada = repository.save(compra);
        for (CompraDetalle d : guardada.getDetalles()) {
            inventarioService.entrada(d.getRepuesto(), d.getCantidad(), costoSinIgv(guardada, d.getCostoUnitario()),
                    TipoMovimiento.COMPRA, guardada.getCodigo() + " · " + guardada.getNumeroDocumento(),
                    fecha, null, guardada.getId());
        }
        log.info("Compra registrada: {} total={}", guardada.getCodigo(), guardada.getTotal());
        return guardada;
    }

    @Transactional
    public void anular(Long id) {
        Compra compra = obtener(id);
        if (compra.isAnulada()) {
            throw new NegocioException("La compra " + compra.getCodigo() + " ya estaba anulada.");
        }
        Map<Repuesto, Integer> retiro = compra.getDetalles().stream()
                .collect(Collectors.groupingBy(CompraDetalle::getRepuesto,
                        Collectors.summingInt(CompraDetalle::getCantidad)));
        try {
            inventarioService.validarDisponible(retiro);
        } catch (NegocioException e) {
            throw new NegocioException("No se puede anular: parte de la mercaderia ya se vendio. " + e.getMessage());
        }
        LocalDateTime ahora = LocalDateTime.now();
        for (CompraDetalle d : compra.getDetalles()) {
            inventarioService.salida(d.getRepuesto(), d.getCantidad(), TipoMovimiento.ANULACION_COMPRA,
                    compra.getCodigo() + " (anulada)", ahora, null, compra.getId());
        }
        compra.setEstado(EstadoCompra.ANULADA);
        log.info("Compra anulada: {}", compra.getCodigo());
    }

    static BigDecimal costoSinIgv(Compra compra, BigDecimal costoDocumento) {
        if (compra.isAplicaIgv() && compra.isPreciosIncluyenIgv()) {
            return costoDocumento.divide(BigDecimal.ONE.add(CalculadoraIgv.TASA_IGV), 4, RoundingMode.HALF_UP);
        }
        return costoDocumento;
    }

    private Proveedor proveedor(String ruc, String razonSocial) {
        return proveedorRepository.findByRuc(ruc)
                .orElseGet(() -> proveedorRepository.save(Proveedor.builder()
                        .ruc(ruc)
                        .razonSocial(razonSocial.toUpperCase())
                        .build()));
    }

    private void validar(NuevaCompra s, LocalDateTime fecha) {
        if (s.rucProveedor() == null || !s.rucProveedor().trim().matches(PATRON_RUC)) {
            throw new NegocioException("Ingresa un RUC de proveedor valido (11 digitos).");
        }
        if (s.razonSocialProveedor() == null || s.razonSocialProveedor().isBlank()) {
            throw new NegocioException("Ingresa la razon social del proveedor.");
        }
        if (s.tipoDocumento() == null) {
            throw new NegocioException("Selecciona el tipo de documento del proveedor.");
        }
        if (s.numeroDocumento() == null || s.numeroDocumento().isBlank()) {
            throw new NegocioException("Ingresa la serie y numero del documento del proveedor.");
        }
        if (s.fechaDocumento() == null || s.fechaDocumento().isAfter(fecha.toLocalDate())) {
            throw new NegocioException("La fecha del documento no puede ser posterior a hoy.");
        }
        if (s.lineas() == null || s.lineas().isEmpty()) {
            throw new NegocioException("Agrega al menos un repuesto a la compra.");
        }
        for (LineaCompra l : s.lineas()) {
            if (l.cantidad() < 1) {
                throw new NegocioException("Todas las cantidades deben ser mayores a cero.");
            }
            if (l.costoUnitario() == null || l.costoUnitario().signum() <= 0) {
                throw new NegocioException("Todos los costos deben ser mayores a cero.");
            }
        }
    }

    private int siguienteNumero() {
        Integer ultimo = repository.ultimoNumero();
        return ultimo == null ? 1 : ultimo + 1;
    }
}
