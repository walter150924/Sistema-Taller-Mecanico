package com.taller.mecanico.service;

import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.Permiso;
import com.taller.mecanico.model.Servicio;
import com.taller.mecanico.repository.OrdenServicioRepository;
import com.taller.mecanico.repository.ServicioRepository;
import com.taller.mecanico.seguridad.Sesion;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServicioService {
    private final ServicioRepository repository;
    private final OrdenServicioRepository ordenRepository;
    private final Sesion sesion;

    public Page<Servicio> listar(String texto, int pagina, int tamanio) {
        return repository.buscar(texto == null ? "" : texto.trim(),
                PageRequest.of(pagina, tamanio, Sort.by("nombre")));
    }

    public List<Servicio> activos() {
        return repository.findByActivoTrueOrderByNombre();
    }

    @Transactional
    public Servicio guardar(@Valid Servicio servicio) {
        sesion.exigir(Permiso.GESTIONAR_CATALOGO);
        repository.findByNombreIgnoreCase(servicio.getNombre())
                .filter(existente -> !existente.getId().equals(servicio.getId()))
                .ifPresent(existente -> {
                    throw new NegocioException("Ya existe el servicio " + existente.getNombre());
                });
        return repository.save(servicio);
    }

    @Transactional
    public Servicio registrarSiNoExiste(Servicio servicio) {
        return repository.findByNombreIgnoreCase(servicio.getNombre()).orElseGet(() -> repository.save(servicio));
    }

    @Transactional
    public void eliminar(Long id) {
        sesion.exigir(Permiso.GESTIONAR_CATALOGO);
        if (!repository.existsById(id)) {
            throw new NegocioException("El servicio ya no existe en la base de datos.");
        }
        if (ordenRepository.usaServicio(id)) {
            throw new NegocioException("No se puede eliminar: figura en ordenes de servicio. Desactivalo en su lugar.");
        }
        repository.deleteById(id);
    }
}
