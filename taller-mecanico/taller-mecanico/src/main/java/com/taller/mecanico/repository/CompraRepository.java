package com.taller.mecanico.repository;

import com.taller.mecanico.model.Compra;
import com.taller.mecanico.model.EstadoCompra;
import com.taller.mecanico.model.TipoDocumentoCompra;
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
public interface CompraRepository extends JpaRepository<Compra, Long> {
    @Query("SELECT MAX(c.numero) FROM Compra c")
    Integer ultimoNumero();

    boolean existsByProveedorId(Long proveedorId);

    List<Compra> findByProveedorIdOrderByFechaDesc(Long proveedorId);

    boolean existsByProveedorIdAndTipoDocumentoAndNumeroDocumentoAndEstado(
            Long proveedorId, TipoDocumentoCompra tipo, String numeroDocumento,
            EstadoCompra estado);

    @Query(value = """
            SELECT c FROM Compra c
            JOIN FETCH c.proveedor p
            WHERE c.fecha BETWEEN :desde AND :hasta
              AND (LOWER(c.codigo)           LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(c.numeroDocumento)  LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(p.razonSocial)      LIKE LOWER(CONCAT('%', :texto, '%'))
                OR p.ruc                     LIKE CONCAT('%', :texto, '%'))
            """,
            countQuery = """
            SELECT COUNT(c) FROM Compra c
            JOIN c.proveedor p
            WHERE c.fecha BETWEEN :desde AND :hasta
              AND (LOWER(c.codigo)           LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(c.numeroDocumento)  LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(p.razonSocial)      LIKE LOWER(CONCAT('%', :texto, '%'))
                OR p.ruc                     LIKE CONCAT('%', :texto, '%'))
            """)
    Page<Compra> buscar(@Param("texto") String texto,
                        @Param("desde") LocalDateTime desde,
                        @Param("hasta") LocalDateTime hasta,
                        Pageable pageable);

    @Query("""
            SELECT COUNT(c), COALESCE(SUM(c.baseImponible), 0), COALESCE(SUM(c.igv), 0), COALESCE(SUM(c.total), 0)
            FROM Compra c
            JOIN c.proveedor p
            WHERE c.estado = :estado
              AND c.fecha BETWEEN :desde AND :hasta
              AND (LOWER(c.codigo)           LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(c.numeroDocumento)  LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(p.razonSocial)      LIKE LOWER(CONCAT('%', :texto, '%'))
                OR p.ruc                     LIKE CONCAT('%', :texto, '%'))
            """)
    List<Object[]> totalizar(@Param("texto") String texto,
                             @Param("estado") EstadoCompra estado,
                             @Param("desde") LocalDateTime desde,
                             @Param("hasta") LocalDateTime hasta);

    @Query("""
            SELECT DISTINCT c FROM Compra c
            JOIN FETCH c.proveedor
            LEFT JOIN FETCH c.detalles
            WHERE c.id = :id
            """)
    Optional<Compra> buscarConDetalle(@Param("id") Long id);
}
