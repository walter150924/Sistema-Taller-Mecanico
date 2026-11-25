package com.taller.mecanico.repository;

import com.taller.mecanico.model.Categoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    Optional<Categoria> findByCodigoCategoria(String codigoCategoria);

    boolean existsByCodigoCategoria(String codigoCategoria);

    @Query("""
            SELECT c FROM Categoria c
            WHERE LOWER(c.nombre)           LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(c.codigoCategoria)  LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(c.descripcion)      LIKE LOWER(CONCAT('%', :texto, '%'))
            """)
    Page<Categoria> buscar(@Param("texto") String texto, Pageable pageable);
}
