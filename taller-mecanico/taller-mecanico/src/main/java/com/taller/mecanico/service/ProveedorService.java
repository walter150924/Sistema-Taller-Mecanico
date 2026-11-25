package com.taller.mecanico.service;

import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.Compra;
import com.taller.mecanico.model.Permiso;
import com.taller.mecanico.model.Proveedor;
import com.taller.mecanico.repository.CompraRepository;
import com.taller.mecanico.repository.ProveedorRepository;
import com.taller.mecanico.seguridad.Sesion;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProveedorService {
    private static final int LIMITE_SUGERENCIAS = 8;

    private final ProveedorRepository repository;
    private final CompraRepository compraRepository;
    private final Sesion sesion;

    public Page<Proveedor> listar(String texto, int pagina, int tamanio) {
        return repository.buscar(texto == null ? "" : texto.trim(),
                PageRequest.of(pagina, tamanio, Sort.by("razonSocial")));
    }

    public List<Compra> compras(Long proveedorId) {
        return compraRepository.findByProveedorIdOrderByFechaDesc(proveedorId);
    }

    @Transactional
    public Proveedor guardar(@Valid Proveedor proveedor) {
        sesion.exigir(Permiso.GESTIONAR_COMPRAS);
        repository.findByRuc(proveedor.getRuc())
                .filter(existente -> !existente.getId().equals(proveedor.getId()))
                .ifPresent(existente -> {
                    throw new NegocioException("El RUC " + proveedor.getRuc() + " ya pertenece a "
                            + existente.getRazonSocial() + ".");
                });
        return repository.save(proveedor);
    }

    @Transactional
    public void eliminar(Long id) {
        sesion.exigir(Permiso.GESTIONAR_COMPRAS);
        if (!repository.existsById(id)) {
            throw new NegocioException("El proveedor ya no existe en la base de datos.");
        }
        if (compraRepository.existsByProveedorId(id)) {
            throw new NegocioException("No se puede eliminar: el proveedor tiene compras registradas.");
        }
        repository.deleteById(id);
    }

    public List<Proveedor> autocompletar(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        String t = texto.trim();
        Specification<Proveedor> spec;
        if (t.matches("\\d+")) {
            spec = (raiz, q, cb) -> cb.like(raiz.get("ruc"), t + "%");
        } else {
            spec = Specification.where(null);
            for (String palabra : t.toLowerCase().split("\\s+")) {
                String patron = "%" + palabra + "%";
                spec = spec.and((raiz, q, cb) -> cb.like(cb.lower(raiz.get("razonSocial")), patron));
            }
        }
        return repository.findAll(spec, PageRequest.of(0, LIMITE_SUGERENCIAS, Sort.by("razonSocial"))).getContent();
    }

    public Optional<Proveedor> buscarPorRuc(String ruc) {
        return ruc == null ? Optional.empty() : repository.findByRuc(ruc.trim());
    }
}
