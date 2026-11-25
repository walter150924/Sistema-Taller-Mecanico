package com.taller.mecanico.service;

import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.Permiso;
import com.taller.mecanico.seguridad.Sesion;
import com.taller.mecanico.repository.VehiculoRepository;
import com.taller.mecanico.model.Cliente;
import com.taller.mecanico.repository.ClienteRepository;
import com.taller.mecanico.repository.VentaRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Service
@Validated
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ClienteService {
    private static final int TAMANIO_PAGINA = 15;
    private static final int LIMITE_SUGERENCIAS = 8;

    private final ClienteRepository repository;
    private final VentaRepository ventaRepository;
    private final VehiculoRepository vehiculoRepository;
    private final Sesion sesion;

    public Page<Cliente> listar(String filtro, int pagina) {
        return listar(filtro, pagina, TAMANIO_PAGINA);
    }

    public Page<Cliente> listar(String filtro, int pagina, int tamanio) {
        Pageable pageable = PageRequest.of(pagina, tamanio, Sort.by("id").ascending());
        if (filtro == null || filtro.isBlank()) {
            return repository.findAll(pageable);
        }
        return repository.buscar(filtro.trim(), pageable);
    }

    public List<Cliente> autocompletar(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        String t = texto.trim();
        Specification<Cliente> filtro = t.matches("\\d+")
                ? (raiz, q, cb) -> cb.like(raiz.get("dni"), t + "%")
                : porPalabras(t);
        return repository.findAll(filtro,
                PageRequest.of(0, LIMITE_SUGERENCIAS, Sort.by("apellido", "nombre"))).getContent();
    }

    public Optional<Cliente> buscarPorDni(String dni) {
        return dni == null ? Optional.empty() : repository.findByDni(dni.trim());
    }

    private static Specification<Cliente> porPalabras(String texto) {
        Specification<Cliente> spec = Specification.where(null);
        for (String palabra : texto.toLowerCase().split("\\s+")) {
            String patron = "%" + palabra + "%";
            spec = spec.and((raiz, q, cb) -> cb.or(
                    cb.like(cb.lower(raiz.get("nombre")), patron),
                    cb.like(cb.lower(raiz.get("apellido")), patron)));
        }
        return spec;
    }

    @Transactional
    public Cliente guardar(@Valid Cliente cliente) {
        sesion.exigir(Permiso.GESTIONAR_CLIENTES);
        repository.findByDni(cliente.getDni()).ifPresent(existente -> {
            boolean esOtroCliente = cliente.getId() == null
                    || !existente.getId().equals(cliente.getId());
            if (esOtroCliente) {
                throw new NegocioException(
                        "Ya existe un cliente registrado con el DNI " + cliente.getDni());
            }
        });

        Cliente guardado = repository.save(cliente);
        log.info("Cliente guardado: id={} dni={}", guardado.getId(), guardado.getDni());
        return guardado;
    }

    @Transactional
    public void eliminar(Long id) {
        sesion.exigir(Permiso.GESTIONAR_CLIENTES);
        if (!repository.existsById(id)) {
            throw new NegocioException("El cliente ya no existe en la base de datos.");
        }
        if (ventaRepository.existsByClienteId(id)) {
            throw new NegocioException("No se puede eliminar: el cliente tiene ventas registradas.");
        }
        if (vehiculoRepository.existsByClienteId(id)) {
            throw new NegocioException("No se puede eliminar: el cliente tiene vehiculos registrados.");
        }
        repository.deleteById(id);
    }

    public long contar() {
        return repository.count();
    }
}
