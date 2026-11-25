package com.taller.mecanico.service;

import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.EstadoOrden;
import com.taller.mecanico.model.LineaRepuesto;
import com.taller.mecanico.model.LineaServicio;
import com.taller.mecanico.model.OrdenServicio;
import com.taller.mecanico.model.MetodoPago;
import com.taller.mecanico.model.Permiso;
import com.taller.mecanico.model.Repuesto;
import com.taller.mecanico.model.Servicio;
import com.taller.mecanico.model.TipoMovimiento;
import com.taller.mecanico.model.Usuario;
import com.taller.mecanico.model.Vehiculo;
import com.taller.mecanico.repository.OrdenServicioRepository;
import com.taller.mecanico.repository.RepuestoRepository;
import com.taller.mecanico.repository.ServicioRepository;
import com.taller.mecanico.repository.UsuarioRepository;
import com.taller.mecanico.repository.VehiculoRepository;
import com.taller.mecanico.seguridad.Sesion;
import com.taller.mecanico.service.CalculadoraIgv.Totales;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
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

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OrdenServicioService {
    private static final int LONGITUD_MAXIMA_PROBLEMA = 500;
    private static final int LONGITUD_MAXIMA_DIAGNOSTICO = 1000;

    private final OrdenServicioRepository repository;
    private final VehiculoRepository vehiculoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ServicioRepository servicioRepository;
    private final RepuestoRepository repuestoRepository;
    private final InventarioService inventarioService;
    private final Sesion sesion;

    public record NuevaOrden(Long vehiculoId, Long mecanicoId, Integer kilometraje, String problemaReportado) {}

    public record ResumenTaller(long enTaller, long listasParaEntrega, long ingresadasEnElMes,
                                BigDecimal facturadoEnElMes) {}

    public Page<OrdenServicio> listar(String texto, EstadoOrden estado, int pagina, int tamanio) {
        Long soloDelMecanico = sesion.tiene(Permiso.VER_TODAS_LAS_ORDENES) ? null : sesion.usuarioActual().getId();
        return repository.buscar(texto == null ? "" : texto.trim(), estado, soloDelMecanico,
                PageRequest.of(pagina, tamanio, Sort.by("fechaIngreso").descending()));
    }

    public OrdenServicio obtener(Long id) {
        return conDetalle(buscarConAcceso(id));
    }

    public ResumenTaller resumen() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime inicioDeMes = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        return new ResumenTaller(
                repository.countByEstadoIn(EstadoOrden.enTaller()),
                repository.countByEstado(EstadoOrden.LISTO),
                repository.countByFechaIngresoBetween(inicioDeMes, ahora),
                aDecimal(repository.sumarTotal(EstadoOrden.ENTREGADO, inicioDeMes, ahora)));
    }

    @Transactional
    public OrdenServicio registrar(NuevaOrden nueva) {
        sesion.exigir(Permiso.CREAR_ORDENES);
        validarNuevaOrden(nueva);
        Vehiculo vehiculo = vehiculoRepository.findById(nueva.vehiculoId())
                .orElseThrow(() -> new NegocioException("El vehiculo ya no existe en la base de datos."));
        vehiculo.actualizarKilometraje(nueva.kilometraje());

        OrdenServicio orden = repository.save(OrdenServicio.builder()
                .codigo(OrdenServicio.formatearCodigo(repository.ultimoNumero() + 1))
                .fechaIngreso(LocalDateTime.now())
                .vehiculo(vehiculo)
                .mecanico(nueva.mecanicoId() == null ? null : buscarMecanico(nueva.mecanicoId()))
                .kilometrajeIngreso(nueva.kilometraje())
                .problemaReportado(nueva.problemaReportado().trim())
                .estado(EstadoOrden.RECIBIDO)
                .build());
        log.info("Orden registrada: {} placa={}", orden.getCodigo(), vehiculo.getPlaca());
        return orden;
    }

    @Transactional
    public OrdenServicio asignarMecanico(Long ordenId, Long mecanicoId) {
        sesion.exigir(Permiso.CREAR_ORDENES);
        OrdenServicio orden = buscarConAcceso(ordenId);
        exigirEnTaller(orden);
        orden.setMecanico(buscarMecanico(mecanicoId));
        return conDetalle(orden);
    }

    @Transactional
    public OrdenServicio guardarDiagnostico(Long ordenId, String diagnostico) {
        OrdenServicio orden = buscarParaTrabajar(ordenId);
        String texto = diagnostico == null ? "" : diagnostico.trim();
        if (texto.length() > LONGITUD_MAXIMA_DIAGNOSTICO) {
            throw new NegocioException("El diagnostico no puede superar " + LONGITUD_MAXIMA_DIAGNOSTICO + " caracteres.");
        }
        orden.setDiagnostico(texto.isEmpty() ? null : texto);
        return conDetalle(orden);
    }

    @Transactional
    public OrdenServicio cambiarEstado(Long ordenId, EstadoOrden destino) {
        if (destino == EstadoOrden.ENTREGADO) {
            throw new NegocioException("Para entregar la orden registra primero el pago.");
        }
        OrdenServicio orden = buscarConAcceso(ordenId);
        sesion.exigir(destino.getPermisoRequerido());
        validarCambioDeEstado(orden, destino);
        if (destino == EstadoOrden.CANCELADO) {
            devolverRepuestos(orden);
        }
        orden.setEstado(destino);
        log.info("Orden {}: estado {}", orden.getCodigo(), destino);
        return conDetalle(orden);
    }

    @Transactional
    public OrdenServicio entregar(Long ordenId, MetodoPago metodoPago) {
        if (metodoPago == null) {
            throw new NegocioException("Selecciona el metodo de pago.");
        }
        OrdenServicio orden = buscarConAcceso(ordenId);
        sesion.exigir(EstadoOrden.ENTREGADO.getPermisoRequerido());
        validarCambioDeEstado(orden, EstadoOrden.ENTREGADO);
        orden.setMetodoPago(metodoPago);
        orden.setFechaEntrega(LocalDateTime.now());
        orden.setEstado(EstadoOrden.ENTREGADO);
        log.info("Orden {} entregada, pago {}", orden.getCodigo(), metodoPago);
        return conDetalle(orden);
    }

    @Transactional
    public OrdenServicio agregarServicio(Long ordenId, Long servicioId) {
        OrdenServicio orden = buscarParaTrabajar(ordenId);
        Servicio servicio = servicioRepository.findById(servicioId)
                .filter(Servicio::isActivo)
                .orElseThrow(() -> new NegocioException("El servicio no esta disponible."));
        boolean yaRegistrado = orden.getServicios().stream()
                .anyMatch(l -> l.getServicio().getId().equals(servicioId));
        if (yaRegistrado) {
            throw new NegocioException(servicio.getNombre() + " ya figura en la orden.");
        }
        orden.agregarServicio(servicio);
        return guardarConTotales(orden);
    }

    @Transactional
    public OrdenServicio quitarServicio(Long ordenId, Long lineaId) {
        OrdenServicio orden = buscarParaTrabajar(ordenId);
        orden.getServicios().removeIf(l -> l.getId().equals(lineaId));
        return guardarConTotales(orden);
    }

    @Transactional
    public OrdenServicio agregarRepuesto(Long ordenId, Long repuestoId, int cantidad) {
        if (cantidad <= 0) {
            throw new NegocioException("La cantidad debe ser mayor a cero.");
        }
        OrdenServicio orden = buscarParaTrabajar(ordenId);
        Repuesto repuesto = repuestoRepository.findById(repuestoId)
                .orElseThrow(() -> new NegocioException("El repuesto ya no existe en la base de datos."));
        inventarioService.salida(repuesto, cantidad, TipoMovimiento.CONSUMO_ORDEN, orden.getCodigo(),
                LocalDateTime.now(), null, null);
        orden.agregarRepuesto(repuesto, cantidad);
        return guardarConTotales(orden);
    }

    @Transactional
    public OrdenServicio quitarRepuesto(Long ordenId, Long lineaId) {
        OrdenServicio orden = buscarParaTrabajar(ordenId);
        LineaRepuesto linea = orden.getRepuestos().stream()
                .filter(l -> l.getId().equals(lineaId))
                .findFirst()
                .orElseThrow(() -> new NegocioException("El repuesto ya no figura en la orden."));
        devolverAlInventario(orden, linea);
        orden.getRepuestos().remove(linea);
        return guardarConTotales(orden);
    }

    private void validarNuevaOrden(NuevaOrden nueva) {
        if (nueva.vehiculoId() == null) {
            throw new NegocioException("Selecciona el vehiculo.");
        }
        if (nueva.kilometraje() == null || nueva.kilometraje() < 0) {
            throw new NegocioException("Ingresa el kilometraje actual del vehiculo.");
        }
        String problema = nueva.problemaReportado() == null ? "" : nueva.problemaReportado().trim();
        if (problema.isEmpty()) {
            throw new NegocioException("Describe el problema que reporta el cliente.");
        }
        if (problema.length() > LONGITUD_MAXIMA_PROBLEMA) {
            throw new NegocioException("El problema reportado no puede superar " + LONGITUD_MAXIMA_PROBLEMA + " caracteres.");
        }
    }

    private void validarCambioDeEstado(OrdenServicio orden, EstadoOrden destino) {
        if (!orden.getEstado().puedePasarA(destino)) {
            throw new NegocioException("La orden " + orden.getCodigo() + " no puede pasar de \""
                    + orden.getEstado() + "\" a \"" + destino + "\".");
        }
        if (destino == EstadoOrden.EN_REPARACION && orden.getMecanico() == null) {
            throw new NegocioException("Asigna un mecanico antes de iniciar la reparacion.");
        }
        boolean sinTrabajos = orden.getServicios().isEmpty() && orden.getRepuestos().isEmpty();
        if (destino == EstadoOrden.LISTO && sinTrabajos) {
            throw new NegocioException("Registra al menos un servicio o repuesto antes de marcarla como lista.");
        }
    }

    private void devolverRepuestos(OrdenServicio orden) {
        orden.getRepuestos().forEach(linea -> devolverAlInventario(orden, linea));
    }

    private void devolverAlInventario(OrdenServicio orden, LineaRepuesto linea) {
        inventarioService.entrada(linea.getRepuesto(), linea.getCantidad(), null, TipoMovimiento.DEVOLUCION_ORDEN,
                orden.getCodigo(), LocalDateTime.now(), null, null);
    }

    private OrdenServicio guardarConTotales(OrdenServicio orden) {
        BigDecimal totalServicios = orden.getServicios().stream()
                .map(LineaServicio::getPrecio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRepuestos = orden.getRepuestos().stream()
                .map(LineaRepuesto::getImporte)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Totales totales = CalculadoraIgv.calcular(List.of(totalServicios, totalRepuestos), true, true);

        orden.setTotalServicios(totalServicios);
        orden.setTotalRepuestos(totalRepuestos);
        orden.setBaseImponible(totales.baseImponible());
        orden.setIgv(totales.igv());
        orden.setTotal(totales.total());
        return conDetalle(repository.saveAndFlush(orden));
    }

    private OrdenServicio buscarParaTrabajar(Long ordenId) {
        sesion.exigir(Permiso.TRABAJAR_ORDENES);
        OrdenServicio orden = buscarConAcceso(ordenId);
        if (!orden.getEstado().esEditable()) {
            throw new NegocioException("La orden " + orden.getCodigo() + " esta en estado \""
                    + orden.getEstado() + "\" y ya no se puede modificar.");
        }
        return orden;
    }

    private OrdenServicio buscarConAcceso(Long ordenId) {
        OrdenServicio orden = repository.findById(ordenId)
                .orElseThrow(() -> new NegocioException("La orden ya no existe en la base de datos."));
        boolean esAjena = !sesion.tiene(Permiso.VER_TODAS_LAS_ORDENES)
                && !orden.estaAsignadaA(sesion.usuarioActual());
        if (esAjena) {
            throw new NegocioException("La orden " + orden.getCodigo() + " esta asignada a otro mecanico.");
        }
        return orden;
    }

    private void exigirEnTaller(OrdenServicio orden) {
        if (!orden.getEstado().estaEnTaller()) {
            throw new NegocioException("La orden " + orden.getCodigo() + " ya esta cerrada.");
        }
    }

    private Usuario buscarMecanico(Long mecanicoId) {
        return usuarioRepository.findById(mecanicoId)
                .filter(Usuario::esMecanico)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new NegocioException("El mecanico seleccionado no esta disponible."));
    }

    private static BigDecimal aDecimal(Number numero) {
        return new BigDecimal(numero.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private OrdenServicio conDetalle(OrdenServicio orden) {
        Hibernate.initialize(orden.getServicios());
        Hibernate.initialize(orden.getRepuestos());
        return orden;
    }
}
