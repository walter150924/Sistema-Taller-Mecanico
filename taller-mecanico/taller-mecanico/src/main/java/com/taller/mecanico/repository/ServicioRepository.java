package com.taller.mecanico.repository;

import com.taller.mecanico.model.Servicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServicioRepository extends JpaRepository<Servicio, Long> {
    Optional<Servicio> findByNombreIgnoreCase(String nombre);

    List<Servicio> findByActivoTrueOrderByNombre();

    @Query("""
            SELECT s FROM Servicio s
            WHERE LOWER(s.nombre) LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(COALESCE(s.descripcion, '')) LIKE LOWER(CONCAT('%', :texto, '%'))
            """)
    Page<Servicio> buscar(@Param("texto") String texto, Pageable pageable);
}
