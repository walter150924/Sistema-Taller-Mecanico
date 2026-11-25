package com.taller.mecanico.repository;

import com.taller.mecanico.model.Vehiculo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VehiculoRepository extends JpaRepository<Vehiculo, Long> {
    Optional<Vehiculo> findByPlaca(String placa);

    boolean existsByClienteId(Long clienteId);

    @Query(value = """
            SELECT v FROM Vehiculo v JOIN FETCH v.cliente c
            WHERE LOWER(v.placa)    LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(v.marca)    LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(v.modelo)   LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(c.nombre)   LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(c.apellido) LIKE LOWER(CONCAT('%', :texto, '%'))
               OR c.dni             LIKE CONCAT('%', :texto, '%')
            """,
            countQuery = """
            SELECT COUNT(v) FROM Vehiculo v JOIN v.cliente c
            WHERE LOWER(v.placa)    LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(v.marca)    LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(v.modelo)   LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(c.nombre)   LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(c.apellido) LIKE LOWER(CONCAT('%', :texto, '%'))
               OR c.dni             LIKE CONCAT('%', :texto, '%')
            """)
    Page<Vehiculo> buscar(@Param("texto") String texto, Pageable pageable);
}
