package com.taller.mecanico.repository;

import com.taller.mecanico.model.EstadoVenta;
import com.taller.mecanico.model.Venta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Long> {
    @Query("SELECT MAX(v.numero) FROM Venta v WHERE v.serie = :serie")
    Integer ultimoNumero(@Param("serie") String serie);

    boolean existsByClienteId(Long clienteId);

    @Query("SELECT MIN(v.fecha) FROM Venta v")
    LocalDateTime primeraFecha();

    @Query(value = """
            SELECT v FROM Venta v
            JOIN FETCH v.cliente c
            WHERE v.fecha BETWEEN :desde AND :hasta
              AND (LOWER(v.comprobante)                          LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(CONCAT(c.nombre, ' ', c.apellido))      LIKE LOWER(CONCAT('%', :texto, '%'))
                OR c.dni                                         LIKE CONCAT('%', :texto, '%'))
            """,
            countQuery = """
            SELECT COUNT(v) FROM Venta v
            JOIN v.cliente c
            WHERE v.fecha BETWEEN :desde AND :hasta
              AND (LOWER(v.comprobante)                          LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(CONCAT(c.nombre, ' ', c.apellido))      LIKE LOWER(CONCAT('%', :texto, '%'))
                OR c.dni                                         LIKE CONCAT('%', :texto, '%'))
            """)
    Page<Venta> buscar(@Param("texto") String texto,
                       @Param("desde") LocalDateTime desde,
                       @Param("hasta") LocalDateTime hasta,
                       Pageable pageable);

    @Query("""
            SELECT COUNT(v), COALESCE(SUM(v.baseImponible), 0), COALESCE(SUM(v.igv), 0), COALESCE(SUM(v.total), 0)
            FROM Venta v
            JOIN v.cliente c
            WHERE v.estado = :estado
              AND v.fecha BETWEEN :desde AND :hasta
              AND (LOWER(v.comprobante)                          LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(CONCAT(c.nombre, ' ', c.apellido))      LIKE LOWER(CONCAT('%', :texto, '%'))
                OR c.dni                                         LIKE CONCAT('%', :texto, '%'))
            """)
    List<Object[]> totalizar(@Param("texto") String texto,
                             @Param("estado") EstadoVenta estado,
                             @Param("desde") LocalDateTime desde,
                             @Param("hasta") LocalDateTime hasta);

    @Query("""
            SELECT DISTINCT v FROM Venta v
            JOIN FETCH v.cliente
            LEFT JOIN FETCH v.detalles
            WHERE v.id = :id
            """)
    Optional<Venta> buscarConDetalle(@Param("id") Long id);

    @Query("""
            SELECT v FROM Venta v
            JOIN FETCH v.cliente
            WHERE v.fecha BETWEEN :desde AND :hasta
            ORDER BY v.fecha
            """)
    List<Venta> listarPeriodo(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    @Query("""
            SELECT v FROM Venta v
            JOIN FETCH v.cliente
            ORDER BY v.fecha DESC, v.id DESC
            LIMIT 8
            """)
    List<Venta> ultimas();

    @Query("""
            SELECT d.nombreRepuesto, SUM(d.cantidad), SUM(d.importe)
            FROM VentaDetalle d
            JOIN d.venta v
            WHERE v.estado = :estado AND v.fecha BETWEEN :desde AND :hasta
            GROUP BY d.repuesto.id, d.nombreRepuesto
            ORDER BY SUM(d.importe) DESC
            """)
    List<Object[]> resumenPorRepuesto(@Param("estado") EstadoVenta estado,
                                      @Param("desde") LocalDateTime desde,
                                      @Param("hasta") LocalDateTime hasta);
}
