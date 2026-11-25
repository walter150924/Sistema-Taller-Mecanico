package com.taller.mecanico.service;

import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.Permiso;
import com.taller.mecanico.model.Rol;
import com.taller.mecanico.model.Usuario;
import com.taller.mecanico.repository.OrdenServicioRepository;
import com.taller.mecanico.repository.UsuarioRepository;
import com.taller.mecanico.seguridad.CifradorClave;
import com.taller.mecanico.seguridad.Sesion;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Service
@Validated
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UsuarioService {
    public static final int LONGITUD_MINIMA_CLAVE = 6;
    private static final String CREDENCIALES_INVALIDAS = "Usuario o contraseña incorrectos.";

    private final UsuarioRepository repository;
    private final OrdenServicioRepository ordenRepository;
    private final CifradorClave cifrador;
    private final Sesion sesion;

    public Page<Usuario> listar(String texto, int pagina, int tamanio) {
        return repository.buscar(texto == null ? "" : texto.trim(),
                PageRequest.of(pagina, tamanio, Sort.by("rol", "nombreCompleto")));
    }

    public List<Usuario> mecanicosActivos() {
        return repository.findByRolAndActivoTrueOrderByNombreCompleto(Rol.MECANICO);
    }

    public Usuario iniciarSesion(String nombreUsuario, String clave) {
        Usuario usuario = repository.findByNombreUsuario(nombreUsuario.trim().toLowerCase())
                .filter(Usuario::isActivo)
                .filter(u -> cifrador.coincide(clave, u.getClaveCifrada()))
                .orElseThrow(() -> new NegocioException(CREDENCIALES_INVALIDAS));
        sesion.iniciar(usuario);
        log.info("Inicio de sesion: {} ({})", usuario.getNombreUsuario(), usuario.getRol());
        return usuario;
    }

    @Transactional
    public Usuario guardar(@Valid Usuario usuario, String claveNueva) {
        sesion.exigir(Permiso.GESTIONAR_USUARIOS);
        validarNombreUnico(usuario);
        validarQueNoSeDesactiveASiMismo(usuario);
        validarQueQuedeUnAdministrador(usuario);
        asignarClave(usuario, claveNueva);
        return repository.save(usuario);
    }

    @Transactional
    public Usuario registrarSiNoExiste(String nombreUsuario, String nombreCompleto, Rol rol, String clave) {
        return repository.findByNombreUsuario(nombreUsuario).orElseGet(() -> repository.save(Usuario.builder()
                .nombreUsuario(nombreUsuario)
                .nombreCompleto(nombreCompleto)
                .rol(rol)
                .claveCifrada(cifrador.cifrar(clave))
                .build()));
    }

    @Transactional
    public void eliminar(Long id) {
        sesion.exigir(Permiso.GESTIONAR_USUARIOS);
        Usuario usuario = repository.findById(id)
                .orElseThrow(() -> new NegocioException("El usuario ya no existe en la base de datos."));
        if (usuario.getId().equals(sesion.usuarioActual().getId())) {
            throw new NegocioException("No puedes eliminar tu propio usuario.");
        }
        if (ordenRepository.existsByMecanicoId(id)) {
            throw new NegocioException("No se puede eliminar: tiene ordenes de servicio asignadas. Desactivalo en su lugar.");
        }
        if (esUltimoAdministradorActivo(usuario)) {
            throw new NegocioException("Debe quedar al menos un administrador activo.");
        }
        repository.delete(usuario);
    }

    public boolean hayAdministradorActivo() {
        return repository.countByRolAndActivoTrue(Rol.ADMINISTRADOR) > 0;
    }

    private void validarNombreUnico(Usuario usuario) {
        repository.findByNombreUsuario(usuario.getNombreUsuario())
                .filter(existente -> !existente.getId().equals(usuario.getId()))
                .ifPresent(existente -> {
                    throw new NegocioException("El usuario " + usuario.getNombreUsuario() + " ya esta registrado.");
                });
    }

    private void validarQueNoSeDesactiveASiMismo(Usuario usuario) {
        boolean esElUsuarioActual = usuario.getId() != null
                && usuario.getId().equals(sesion.usuarioActual().getId());
        if (esElUsuarioActual && (!usuario.isActivo() || usuario.getRol() != Rol.ADMINISTRADOR)) {
            throw new NegocioException("No puedes desactivar ni quitar el rol de administrador a tu propio usuario.");
        }
    }

    private void validarQueQuedeUnAdministrador(Usuario usuario) {
        if (usuario.getId() == null) {
            return;
        }
        boolean dejaDeSerAdministrador = usuario.getRol() != Rol.ADMINISTRADOR || !usuario.isActivo();
        Usuario anterior = repository.findById(usuario.getId()).orElseThrow();
        if (dejaDeSerAdministrador && esUltimoAdministradorActivo(anterior)) {
            throw new NegocioException("Debe quedar al menos un administrador activo.");
        }
    }

    private boolean esUltimoAdministradorActivo(Usuario usuario) {
        return usuario.getRol() == Rol.ADMINISTRADOR && usuario.isActivo()
                && repository.countByRolAndActivoTrue(Rol.ADMINISTRADOR) <= 1;
    }

    private void asignarClave(Usuario usuario, String claveNueva) {
        boolean sinClaveNueva = claveNueva == null || claveNueva.isBlank();
        if (sinClaveNueva && usuario.getId() == null) {
            throw new NegocioException("La contraseña es obligatoria para un usuario nuevo.");
        }
        if (sinClaveNueva) {
            usuario.setClaveCifrada(repository.findById(usuario.getId()).orElseThrow().getClaveCifrada());
            return;
        }
        if (claveNueva.length() < LONGITUD_MINIMA_CLAVE) {
            throw new NegocioException("La contraseña debe tener al menos " + LONGITUD_MINIMA_CLAVE + " caracteres.");
        }
        usuario.setClaveCifrada(cifrador.cifrar(claveNueva));
    }
}
