package com.taller.mecanico.repository;

import com.taller.mecanico.model.EstadoOrden;
import com.taller.mecanico.model.OrdenServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface OrdenServicioRepository extends JpaRepository<OrdenServicio, Long> {
    boolean existsByVehiculoId(Long vehiculoId);

    boolean existsByMecanicoId(Long mecanicoId);

    long countByEstadoIn(Collection<EstadoOrden> estados);

    long countByEstado(EstadoOrden estado);

    long countByFechaIngresoBetween(LocalDateTime desde, LocalDateTime hasta);

    List<OrdenServicio> findByVehiculoIdOrderByFechaIngresoDesc(Long vehiculoId);

    @Query("SELECT COALESCE(MAX(o.id), 0) FROM OrdenServicio o")
    long ultimoNumero();

    @Query(value = """
            SELECT o FROM OrdenServicio o
            JOIN FETCH o.vehiculo v JOIN FETCH v.cliente c LEFT JOIN FETCH o.mecanico m
            WHERE (:estado IS NULL OR o.estado = :estado)
              AND (:mecanicoId IS NULL OR m.id = :mecanicoId)
              AND (LOWER(o.codigo)   LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(v.placa)    LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(c.nombre)   LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(c.apellido) LIKE LOWER(CONCAT('%', :texto, '%')))
            """,
            countQuery = """
            SELECT COUNT(o) FROM OrdenServicio o
            JOIN o.vehiculo v JOIN v.cliente c LEFT JOIN o.mecanico m
            WHERE (:estado IS NULL OR o.estado = :estado)
              AND (:mecanicoId IS NULL OR m.id = :mecanicoId)
              AND (LOWER(o.codigo)   LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(v.placa)    LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(c.nombre)   LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(c.apellido) LIKE LOWER(CONCAT('%', :texto, '%')))
            """)
    Page<OrdenServicio> buscar(@Param("texto") String texto,
                               @Param("estado") EstadoOrden estado,
                               @Param("mecanicoId") Long mecanicoId,
                               Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(o.total), 0) FROM OrdenServicio o
            WHERE o.estado = :estado AND o.fechaEntrega BETWEEN :desde AND :hasta
            """)
    Number sumarTotal(@Param("estado") EstadoOrden estado,
                          @Param("desde") LocalDateTime desde,
                          @Param("hasta") LocalDateTime hasta);

    @Query("SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END FROM LineaServicio l WHERE l.servicio.id = :servicioId")
    boolean usaServicio(@Param("servicioId") Long servicioId);

    @Query("SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END FROM LineaRepuesto l WHERE l.repuesto.id = :repuestoId")
    boolean usaRepuesto(@Param("repuestoId") Long repuestoId);
}
