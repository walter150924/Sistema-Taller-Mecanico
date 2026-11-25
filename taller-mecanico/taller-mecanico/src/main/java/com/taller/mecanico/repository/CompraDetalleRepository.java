package com.taller.mecanico.repository;

import com.taller.mecanico.model.CompraDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompraDetalleRepository extends JpaRepository<CompraDetalle, Long> {
    boolean existsByRepuestoId(Long repuestoId);
}
