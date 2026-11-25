package com.taller.mecanico.repository;

import com.taller.mecanico.model.Proveedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProveedorRepository extends JpaRepository<Proveedor, Long>, JpaSpecificationExecutor<Proveedor> {
    Optional<Proveedor> findByRuc(String ruc);

    @Query("""
            SELECT p FROM Proveedor p
            WHERE p.ruc LIKE CONCAT('%', :texto, '%')
               OR LOWER(p.razonSocial) LIKE LOWER(CONCAT('%', :texto, '%'))
            """)
    Page<Proveedor> buscar(@Param("texto") String texto, Pageable pageable);
}
