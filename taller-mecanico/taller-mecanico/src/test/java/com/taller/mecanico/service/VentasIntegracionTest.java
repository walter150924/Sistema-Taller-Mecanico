package com.taller.mecanico.service;

import com.taller.mecanico.exception.NegocioException;
import com.taller.mecanico.model.Cliente;
import com.taller.mecanico.model.MetodoPago;
import com.taller.mecanico.model.Repuesto;
import com.taller.mecanico.model.TipoComprobante;
import com.taller.mecanico.model.Venta;
import com.taller.mecanico.repository.VentaRepository;
import com.taller.mecanico.service.VentaService.LineaCarrito;
import com.taller.mecanico.service.VentaService.NuevaVenta;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class VentasIntegracionTest {
    @DynamicPropertySource
    static void baseTemporal(DynamicPropertyRegistry registro) throws Exception {
        File db = Files.createTempFile("ventas-test", ".db").toFile();
        db.deleteOnExit();
        registro.add("spring.datasource.url", () -> "jdbc:sqlite:" + db.getAbsolutePath());
        registro.add("app.seeder.enabled", () -> "true");
        registro.add("spring.jpa.show-sql", () -> "false");
        registro.add("logging.level.org.hibernate.SQL", () -> "WARN");
    }

    @Autowired VentaService ventaService;
    @Autowired VentaRepository ventaRepository;
    @Autowired ClienteService clienteService;
    @Autowired RepuestoService repuestoService;
    @Autowired ReporteVentasService reporteService;
    @Autowired ComprobantePdfService pdfService;
    @Autowired ReporteExcelService excelService;
    @Autowired UsuarioService usuarioService;

    @BeforeEach
    void iniciarSesionComoAdministrador() {
        usuarioService.iniciarSesion("admin", "admin123");
    }

    @Test
    void seederGeneraVentasConsistentes() {
        List<Venta> ventas = ventaRepository.findAll();
        assertThat(ventas).hasSizeGreaterThanOrEqualTo(120);

        Set<String> comprobantes = new HashSet<>();
        for (Venta v : ventas) {
            assertThat(comprobantes.add(v.getComprobante())).as("comprobante unico").isTrue();
            assertThat(v.getBaseImponible().add(v.getIgv())).isEqualByComparingTo(v.getTotal());
            if (!v.isAplicaIgv()) {
                assertThat(v.getIgv()).isEqualByComparingTo("0");
            }
        }
        assertThat(ventas).anyMatch(Venta::isAnulada);
    }

    @Test
    void autocompletadoDeClientesYRepuestos() {
        Cliente alguien = clienteService.listar("", 0, 1).getContent().get(0);

        assertThat(clienteService.autocompletar(alguien.getDni().substring(0, 4)))
                .allMatch(p -> p.getDni().startsWith(alguien.getDni().substring(0, 4)));

        String inverso = alguien.getApellido().split(" ")[0] + " " + alguien.getNombre();
        assertThat(clienteService.autocompletar(inverso)).contains(alguien);
        assertThat(clienteService.buscarPorDni(alguien.getDni())).contains(alguien);

        assertThat(repuestoService.autocompletar("aceite")).isNotEmpty()
                .allMatch(p -> p.getNombre().toLowerCase().contains("aceite"));
        assertThat(repuestoService.autocompletar("REP-001")).extracting(Repuesto::getCodigoRepuesto)
                .contains("REP-001");
    }

    @Test
    void registraVentaConCorrelativoEIgv() {
        Cliente cliente = clienteService.listar("", 0, 1).getContent().get(0);
        Repuesto repuesto = repuestoService.autocompletar("REP-001").get(0);
        String esperado = ventaService.siguienteComprobante(TipoComprobante.BOLETA);

        Venta venta = ventaService.registrar(new NuevaVenta(TipoComprobante.BOLETA, cliente.getId(),
                true, false, "Prueba", List.of(new LineaCarrito(repuesto.getId(), 2, new BigDecimal("50.00"))), MetodoPago.EFECTIVO));

        assertThat(venta.getComprobante()).isEqualTo(esperado);
        assertThat(venta.getBaseImponible()).isEqualByComparingTo("100.00");
        assertThat(venta.getIgv()).isEqualByComparingTo("18.00");
        assertThat(venta.getTotal()).isEqualByComparingTo("118.00");

        assertThatThrownBy(() -> clienteService.eliminar(cliente.getId()))
                .isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> repuestoService.eliminar(repuesto.getId()))
                .isInstanceOf(NegocioException.class);
    }

    @Test
    void generaPdfYExcel() throws Exception {
        Venta venta = ventaRepository.findAll().get(0);
        File pdf = Files.createTempFile("comprobante", ".pdf").toFile();
        pdfService.generar(venta.getId(), pdf);
        assertThat(Files.readString(pdf.toPath(), StandardCharsets.ISO_8859_1)).startsWith("%PDF");

        LocalDate hasta = LocalDate.now();
        LocalDate desde = hasta.minusDays(119);
        File xlsx = Files.createTempFile("reporte", ".xlsx").toFile();
        excelService.generar(desde, hasta, xlsx);
        try (XSSFWorkbook libro = new XSSFWorkbook(new FileInputStream(xlsx))) {
            assertThat(libro.getNumberOfSheets()).isEqualTo(5);
            assertThat(libro.getSheet("Ventas").getLastRowNum()).isGreaterThan(200);
            assertThat(libro.getSheet("Detalle").getLastRowNum()).isGreaterThan(libro.getSheet("Ventas").getLastRowNum());
        }

        var diario = reporteService.resumen(hasta.minusDays(89), hasta);
        assertThat(diario.seriePorMes()).isFalse();
        assertThat(diario.serie()).hasSize(90);
        assertThat(diario.baseImponible().add(diario.igv())).isEqualByComparingTo(diario.total());

        var mensual = reporteService.resumen(desde, hasta);
        assertThat(mensual.seriePorMes()).isTrue();
        assertThat(mensual.comprobantes() + mensual.anuladas()).isGreaterThanOrEqualTo(250);
    }
}
