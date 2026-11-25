package com.taller.mecanico.repository;

import com.taller.mecanico.model.VentaDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VentaDetalleRepository extends JpaRepository<VentaDetalle, Long> {
    boolean existsByRepuestoId(Long repuestoId);

    @Query("""
            SELECT d FROM VentaDetalle d
            JOIN FETCH d.venta v
            JOIN FETCH v.cliente
            WHERE v.fecha BETWEEN :desde AND :hasta
            ORDER BY v.fecha, v.id, d.item
            """)
    List<VentaDetalle> listarPeriodo(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
}
