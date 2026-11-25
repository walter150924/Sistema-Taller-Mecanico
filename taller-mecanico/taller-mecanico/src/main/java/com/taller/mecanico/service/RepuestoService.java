package com.taller.mecanico.service;

import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.Permiso;
import com.taller.mecanico.seguridad.Sesion;
import com.taller.mecanico.repository.OrdenServicioRepository;
import com.taller.mecanico.model.Categoria;
import com.taller.mecanico.model.Repuesto;
import com.taller.mecanico.repository.CategoriaRepository;
import com.taller.mecanico.repository.CompraDetalleRepository;
import com.taller.mecanico.repository.RepuestoRepository;
import com.taller.mecanico.repository.VentaDetalleRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Service
@Validated
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RepuestoService {
    private static final int TAMANIO_PAGINA = 15;
    private static final int LIMITE_SUGERENCIAS = 10;

    private final RepuestoRepository repository;
    private final CategoriaRepository categoriaRepository;
    private final VentaDetalleRepository ventaDetalleRepository;
    private final CompraDetalleRepository compraDetalleRepository;
    private final OrdenServicioRepository ordenRepository;
    private final Sesion sesion;

    public Page<Repuesto> listar(String filtro, int pagina) {
        return listar(filtro, pagina, TAMANIO_PAGINA);
    }

    public Page<Repuesto> listar(String filtro, int pagina, int tamanio) {
        Pageable pageable = PageRequest.of(pagina, tamanio, Sort.by("id").ascending());
        if (filtro == null || filtro.isBlank()) {
            return repository.findAllConCategoria(pageable);
        }
        return repository.buscar(filtro.trim(), pageable);
    }

    public List<Repuesto> autocompletar(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        Specification<Repuesto> spec = Specification.where(null);
        for (String palabra : texto.trim().toLowerCase().split("\\s+")) {
            String patron = "%" + palabra + "%";
            spec = spec.and((raiz, q, cb) -> cb.or(
                    cb.like(cb.lower(raiz.get("codigoRepuesto")), patron),
                    cb.like(cb.lower(raiz.get("nombre")), patron)));
        }
        return repository.findAll(spec,
                PageRequest.of(0, LIMITE_SUGERENCIAS, Sort.by("nombre"))).getContent();
    }

    public List<Categoria> listarCategorias() {
        return categoriaRepository.findAll(Sort.by("nombre").ascending());
    }

    @Transactional
    public Repuesto guardar(@Valid Repuesto repuesto) {
        sesion.exigir(Permiso.GESTIONAR_CATALOGO);
        repository.findByCodigoRepuesto(repuesto.getCodigoRepuesto()).ifPresent(existente -> {
            boolean esOtroRepuesto = repuesto.getId() == null
                    || !existente.getId().equals(repuesto.getId());
            if (esOtroRepuesto) {
                throw new NegocioException(
                        "Ya existe un repuesto registrado con el codigo " + repuesto.getCodigoRepuesto());
            }
        });

        if (repuesto.getId() != null) {
            repository.findById(repuesto.getId()).ifPresent(actual -> {
                repuesto.setStock(actual.getStock());
                repuesto.setCostoPromedio(actual.getCostoPromedio());
            });
        } else {
            repuesto.setStock(0);
            repuesto.setCostoPromedio(null);
        }
        if (repuesto.getStockMinimo() == null) {
            repuesto.setStockMinimo(5);
        }
        Repuesto guardado = repository.save(repuesto);
        log.info("Repuesto guardado: id={} codigo={}", guardado.getId(), guardado.getCodigoRepuesto());
        return guardado;
    }

    @Transactional
    public void eliminar(Long id) {
        sesion.exigir(Permiso.GESTIONAR_CATALOGO);
        if (!repository.existsById(id)) {
            throw new NegocioException("El repuesto ya no existe en la base de datos.");
        }
        if (ventaDetalleRepository.existsByRepuestoId(id)) {
            throw new NegocioException("No se puede eliminar: el repuesto figura en ventas registradas.");
        }
        if (compraDetalleRepository.existsByRepuestoId(id)) {
            throw new NegocioException("No se puede eliminar: el repuesto figura en compras registradas.");
        }
        if (ordenRepository.usaRepuesto(id)) {
            throw new NegocioException("No se puede eliminar: el repuesto figura en ordenes de servicio.");
        }
        repository.deleteById(id);
    }

    public long contar() {
        return repository.count();
    }
}
