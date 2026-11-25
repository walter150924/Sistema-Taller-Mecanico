package com.taller.mecanico.service;

import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.EstadoOrden;
import com.taller.mecanico.model.MetodoPago;
import com.taller.mecanico.model.OrdenServicio;
import com.taller.mecanico.model.Repuesto;
import com.taller.mecanico.model.Servicio;
import com.taller.mecanico.model.Usuario;
import com.taller.mecanico.model.Vehiculo;
import com.taller.mecanico.repository.RepuestoRepository;
import com.taller.mecanico.seguridad.Sesion;
import com.taller.mecanico.service.OrdenServicioService.NuevaOrden;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OrdenServicioIntegracionTest {
    private static final String CLAVE_DE_PRUEBA = "taller123";

    @DynamicPropertySource
    static void baseTemporal(DynamicPropertyRegistry registro) throws Exception {
        File db = Files.createTempFile("ordenes-test", ".db").toFile();
        db.deleteOnExit();
        registro.add("spring.datasource.url", () -> "jdbc:sqlite:" + db.getAbsolutePath());
        registro.add("app.seeder.enabled", () -> "true");
        registro.add("spring.jpa.show-sql", () -> "false");
    }

    @Autowired OrdenServicioService ordenService;
    @Autowired UsuarioService usuarioService;
    @Autowired VehiculoService vehiculoService;
    @Autowired ServicioService servicioService;
    @Autowired RepuestoRepository repuestoRepository;
    @Autowired Sesion sesion;

    @AfterEach
    void cerrarSesion() {
        sesion.cerrar();
    }

    @Test
    void completaElFlujoDeUnaOrdenYDescuentaElInventario() {
        Usuario mecanico = usuarioService.iniciarSesion("jquispe", CLAVE_DE_PRUEBA);
        usuarioService.iniciarSesion("recepcion", CLAVE_DE_PRUEBA);
        OrdenServicio orden = registrarOrden(mecanico);
        Repuesto repuesto = repuestoConStock();
        int stockInicial = repuesto.getStock();

        usuarioService.iniciarSesion("jquispe", CLAVE_DE_PRUEBA);
        ordenService.cambiarEstado(orden.getId(), EstadoOrden.EN_DIAGNOSTICO);
        ordenService.guardarDiagnostico(orden.getId(), "Pastillas gastadas");
        ordenService.agregarServicio(orden.getId(), primerServicio().getId());
        ordenService.agregarRepuesto(orden.getId(), repuesto.getId(), 2);
        ordenService.cambiarEstado(orden.getId(), EstadoOrden.EN_REPARACION);
        OrdenServicio lista = ordenService.cambiarEstado(orden.getId(), EstadoOrden.LISTO);

        assertThat(stockDe(repuesto)).isEqualTo(stockInicial - 2);
        assertThat(lista.getBaseImponible().add(lista.getIgv())).isEqualByComparingTo(lista.getTotal());
        assertThat(lista.getTotal()).isEqualByComparingTo(lista.getTotalServicios().add(lista.getTotalRepuestos()));
        assertThatThrownBy(() -> ordenService.entregar(orden.getId(), MetodoPago.EFECTIVO))
                .isInstanceOf(NegocioException.class);

        usuarioService.iniciarSesion("recepcion", CLAVE_DE_PRUEBA);
        assertThatThrownBy(() -> ordenService.entregar(orden.getId(), null))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("metodo de pago");
        OrdenServicio entregada = ordenService.entregar(orden.getId(), MetodoPago.YAPE);
        assertThat(entregada.getFechaEntrega()).isNotNull();
        assertThat(entregada.getMetodoPago()).isEqualTo(MetodoPago.YAPE);
        assertThat(vehiculoService.historial(orden.getVehiculo().getId())).extracting(OrdenServicio::getCodigo)
                .contains(orden.getCodigo());
    }

    @Test
    void alCancelarDevuelveLosRepuestosAlInventario() {
        usuarioService.iniciarSesion("recepcion", CLAVE_DE_PRUEBA);
        OrdenServicio orden = registrarOrden(null);
        Repuesto repuesto = repuestoConStock();
        int stockInicial = repuesto.getStock();

        ordenService.agregarRepuesto(orden.getId(), repuesto.getId(), 3);
        assertThat(stockDe(repuesto)).isEqualTo(stockInicial - 3);

        ordenService.cambiarEstado(orden.getId(), EstadoOrden.CANCELADO);
        assertThat(stockDe(repuesto)).isEqualTo(stockInicial);
    }

    @Test
    void unMecanicoSoloVeSusOrdenes() {
        Usuario asignado = usuarioService.iniciarSesion("jquispe", CLAVE_DE_PRUEBA);
        usuarioService.iniciarSesion("recepcion", CLAVE_DE_PRUEBA);
        OrdenServicio orden = registrarOrden(asignado);

        usuarioService.iniciarSesion("pmamani", CLAVE_DE_PRUEBA);
        assertThat(ordenService.listar("", null, 0, 50).getContent()).extracting(OrdenServicio::getId)
                .doesNotContain(orden.getId());
        assertThatThrownBy(() -> ordenService.obtener(orden.getId()))
                .isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> ordenService.registrar(new NuevaOrden(orden.getVehiculo().getId(), null, 1, "x")))
                .isInstanceOf(NegocioException.class);
    }

    @Test
    void rechazaCredencialesIncorrectas() {
        assertThatThrownBy(() -> usuarioService.iniciarSesion("admin", "incorrecta"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("incorrectos");
    }

    private OrdenServicio registrarOrden(Usuario mecanico) {
        Vehiculo vehiculo = vehiculoService.listar("", 0, 1).getContent().get(0);
        return ordenService.registrar(new NuevaOrden(vehiculo.getId(),
                mecanico == null ? null : mecanico.getId(),
                vehiculo.getKilometraje() + 100, "Ruido al frenar"));
    }

    private Repuesto repuestoConStock() {
        return repuestoRepository.findAll().stream()
                .filter(r -> r.getStock() >= 5)
                .findFirst()
                .orElseThrow();
    }

    private Servicio primerServicio() {
        return servicioService.activos().get(0);
    }

    private int stockDe(Repuesto repuesto) {
        return repuestoRepository.findById(repuesto.getId()).orElseThrow().getStock();
    }
}
