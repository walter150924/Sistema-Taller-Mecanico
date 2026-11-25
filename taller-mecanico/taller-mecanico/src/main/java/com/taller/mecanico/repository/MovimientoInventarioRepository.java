package com.taller.mecanico.repository;

import com.taller.mecanico.model.MovimientoInventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {
    @Query("""
            SELECT m FROM MovimientoInventario m
            WHERE m.repuesto.id = :repuestoId
            ORDER BY m.fecha, m.id
            """)
    List<MovimientoInventario> kardex(@Param("repuestoId") Long repuestoId);

    boolean existsByVentaId(Long ventaId);

    boolean existsByRepuestoId(Long repuestoId);
}
