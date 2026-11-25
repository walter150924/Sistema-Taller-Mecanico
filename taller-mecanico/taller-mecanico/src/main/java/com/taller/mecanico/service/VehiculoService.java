package com.taller.mecanico.service;

import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.OrdenServicio;
import com.taller.mecanico.model.Permiso;
import com.taller.mecanico.model.Vehiculo;
import com.taller.mecanico.repository.OrdenServicioRepository;
import com.taller.mecanico.repository.VehiculoRepository;
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
public class VehiculoService {
    private static final int LIMITE_SUGERENCIAS = 8;

    private final VehiculoRepository repository;
    private final OrdenServicioRepository ordenRepository;
    private final Sesion sesion;

    public Page<Vehiculo> listar(String texto, int pagina, int tamanio) {
        return repository.buscar(texto == null ? "" : texto.trim(),
                PageRequest.of(pagina, tamanio, Sort.by("placa")));
    }

    public List<Vehiculo> autocompletar(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        return listar(texto, 0, LIMITE_SUGERENCIAS).getContent();
    }

    public List<OrdenServicio> historial(Long vehiculoId) {
        return ordenRepository.findByVehiculoIdOrderByFechaIngresoDesc(vehiculoId);
    }

    @Transactional
    public Vehiculo guardar(@Valid Vehiculo vehiculo) {
        sesion.exigir(Permiso.GESTIONAR_VEHICULOS);
        repository.findByPlaca(vehiculo.getPlaca())
                .filter(existente -> !existente.getId().equals(vehiculo.getId()))
                .ifPresent(existente -> {
                    throw new NegocioException("Ya existe un vehiculo con la placa " + vehiculo.getPlaca());
                });
        return repository.save(vehiculo);
    }

    @Transactional
    public void eliminar(Long id) {
        sesion.exigir(Permiso.GESTIONAR_VEHICULOS);
        if (!repository.existsById(id)) {
            throw new NegocioException("El vehiculo ya no existe en la base de datos.");
        }
        if (ordenRepository.existsByVehiculoId(id)) {
            throw new NegocioException("No se puede eliminar: el vehiculo tiene ordenes de servicio registradas.");
        }
        repository.deleteById(id);
    }
}
