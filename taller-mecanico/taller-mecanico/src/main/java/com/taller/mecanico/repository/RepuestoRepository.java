package com.taller.mecanico.repository;

import com.taller.mecanico.model.Repuesto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface RepuestoRepository extends JpaRepository<Repuesto, Long>, JpaSpecificationExecutor<Repuesto> {
    Optional<Repuesto> findByCodigoRepuesto(String codigoRepuesto);

    boolean existsByCodigoRepuesto(String codigoRepuesto);

    @Query("""
            SELECT p FROM Repuesto p
            LEFT JOIN FETCH p.categoria
            """)
    Page<Repuesto> findAllConCategoria(Pageable pageable);

    @Query("""
            SELECT p FROM Repuesto p
            LEFT JOIN FETCH p.categoria
            WHERE LOWER(p.nombre)          LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(p.codigoRepuesto)  LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(p.descripcion)     LIKE LOWER(CONCAT('%', :texto, '%'))
            """)
    Page<Repuesto> buscar(@Param("texto") String texto, Pageable pageable);

    @Query(value = """
            SELECT p FROM Repuesto p
            LEFT JOIN FETCH p.categoria
            WHERE (LOWER(p.nombre)         LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(p.codigoRepuesto) LIKE LOWER(CONCAT('%', :texto, '%')))
              AND (:estado = 'TODOS'
                OR (:estado = 'SIN_STOCK'  AND p.stock <= 0)
                OR (:estado = 'BAJO'       AND p.stock > 0 AND p.stock <= p.stockMinimo)
                OR (:estado = 'DISPONIBLE' AND p.stock > p.stockMinimo))
            """,
            countQuery = """
            SELECT COUNT(p) FROM Repuesto p
            WHERE (LOWER(p.nombre)         LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(p.codigoRepuesto) LIKE LOWER(CONCAT('%', :texto, '%')))
              AND (:estado = 'TODOS'
                OR (:estado = 'SIN_STOCK'  AND p.stock <= 0)
                OR (:estado = 'BAJO'       AND p.stock > 0 AND p.stock <= p.stockMinimo)
                OR (:estado = 'DISPONIBLE' AND p.stock > p.stockMinimo))
            """)
    Page<Repuesto> inventario(@Param("texto") String texto, @Param("estado") String estado, Pageable pageable);

    @Query("SELECT p FROM Repuesto p LEFT JOIN FETCH p.categoria ORDER BY p.codigoRepuesto")
    List<Repuesto> listarConCategoria();

    @Query("""
            SELECT COALESCE(c.nombre, 'Sin categoria'), COUNT(p), AVG(p.precio)
            FROM Repuesto p
            LEFT JOIN p.categoria c
            GROUP BY c.id, c.nombre
            ORDER BY COUNT(p) DESC
            """)
    List<Object[]> resumenPorCategoria();

    @Query("SELECT p.precio FROM Repuesto p")
    List<BigDecimal> listarPrecios();

    @Query("SELECT p FROM Repuesto p LEFT JOIN FETCH p.categoria ORDER BY p.precio DESC LIMIT 5")
    List<Repuesto> top5MasCaros();
}
