package com.taller.mecanico.repository;

import com.taller.mecanico.model.Rol;
import com.taller.mecanico.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByNombreUsuario(String nombreUsuario);

    List<Usuario> findByRolAndActivoTrueOrderByNombreCompleto(Rol rol);

    long countByRolAndActivoTrue(Rol rol);

    @Query("""
            SELECT u FROM Usuario u
            WHERE LOWER(u.nombreUsuario)  LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(u.nombreCompleto) LIKE LOWER(CONCAT('%', :texto, '%'))
            """)
    Page<Usuario> buscar(@Param("texto") String texto, Pageable pageable);
}
