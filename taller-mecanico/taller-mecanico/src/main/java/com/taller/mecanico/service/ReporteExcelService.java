package com.taller.mecanico.service;

import com.taller.mecanico.config.DatosEmpresa;
import com.taller.mecanico.model.Venta;
import com.taller.mecanico.model.VentaDetalle;
import com.taller.mecanico.service.ReporteVentasService.ClienteFrecuente;
import com.taller.mecanico.service.ReporteVentasService.DatosReporte;
import com.taller.mecanico.service.ReporteVentasService.RepuestoVendido;
import com.taller.mecanico.service.ReporteVentasService.PuntoSerie;
import com.taller.mecanico.service.ReporteVentasService.ResumenVentas;
import com.taller.mecanico.service.LibroExcel.Estilos;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.taller.mecanico.service.LibroExcel.*;

@Service
@RequiredArgsConstructor
public class ReporteExcelService {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ReporteVentasService reporteService;
    private final DatosEmpresa empresa;

    public void generar(LocalDate desde, LocalDate hasta, File destino) throws IOException {
        DatosReporte datos = reporteService.datosReporte(desde, hasta);
        try (OutputStream salida = new FileOutputStream(destino)) {
            escribir(datos, salida);
        }
    }

    void escribir(DatosReporte datos, OutputStream salida) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Estilos e = new Estilos(libro);
            hojaResumen(libro, e, datos.resumen());
            hojaVentas(libro, e, datos.ventas());
            hojaDetalle(libro, e, datos.detalles());
            hojaRepuestos(libro, e, datos.resumen().topRepuestos());
            hojaClientes(libro, e, datos.resumen().topClientes());
            libro.getProperties().getCoreProperties().setTitle("Reporte de ventas");
            libro.getProperties().getCoreProperties().setCreator(empresa.getRazonSocial());
            libro.write(salida);
        }
    }

    private void hojaResumen(XSSFWorkbook libro, Estilos e, ResumenVentas r) {
        Sheet hoja = libro.createSheet("Resumen");
        hoja.setDisplayGridlines(false);
        hoja.setColumnWidth(0, 30 * 256);
        hoja.setColumnWidth(1, 20 * 256);
        hoja.setColumnWidth(2, 20 * 256);

        int f = 0;
        texto(hoja.createRow(f++), 0, "Reporte de ventas", e.titulo);
        texto(hoja.createRow(f++), 0, empresa.getRazonSocial() + "  ·  RUC " + empresa.getRuc(), e.subtitulo);
        texto(hoja.createRow(f++), 0, "Periodo: " + r.desde().format(FECHA) + " al " + r.hasta().format(FECHA)
                + "   ·   Generado: " + LocalDateTime.now().format(FECHA_HORA), e.subtitulo);
        f++;

        cabecera(hoja.createRow(f++), e, "Indicador", "Valor");
        Object[][] kpis = {
                {"Total vendido", r.total()},
                {"Base imponible", r.baseImponible()},
                {"IGV", r.igv()},
                {"Comprobantes emitidos", r.comprobantes()},
                {"Comprobantes anulados", r.anuladas()},
                {"Ticket promedio", r.ticketPromedio()},
        };
        for (Object[] k : kpis) {
            Row fila = hoja.createRow(f++);
            texto(fila, 0, (String) k[0], e.celdaNegrita);
            if (k[1] instanceof BigDecimal b) {
                numero(fila, 1, b, e.moneda);
            } else {
                entero(fila, 1, ((Number) k[1]).longValue(), e.entero);
            }
        }
        f++;

        texto(hoja.createRow(f++), 0, r.seriePorMes() ? "Ventas por mes" : "Ventas por dia", e.seccion);
        cabecera(hoja.createRow(f++), e, r.seriePorMes() ? "Mes" : "Fecha", "Comprobantes", "Total");
        int primera = f;
        for (PuntoSerie p : r.serie()) {
            Row fila = hoja.createRow(f++);
            fecha(fila, 0, p.fecha(), r.seriePorMes() ? e.mes : e.fecha);
            entero(fila, 1, p.comprobantes(), e.entero);
            numero(fila, 2, p.total(), e.moneda);
        }
        filaTotales(hoja.createRow(f), e, primera, f - 1, 0, new int[]{1}, new int[]{2});
    }

    private void hojaVentas(XSSFWorkbook libro, Estilos e, List<Venta> ventas) {
        Sheet hoja = libro.createSheet("Ventas");
        String[] titulos = {"Comprobante", "Tipo", "Fecha", "Cliente", "DNI", "IGV",
                "Base imponible", "IGV (S/)", "Total", "Estado"};
        int[] anchos = {16, 26, 18, 34, 12, 14, 16, 14, 16, 12};
        prepararTabla(hoja, e, titulos, anchos);

        int f = 1;
        for (Venta v : ventas) {
            Row fila = hoja.createRow(f++);
            boolean anulada = v.isAnulada();
            texto(fila, 0, v.getComprobante(), anulada ? e.anulada : e.celda);
            texto(fila, 1, v.getTipoComprobante().getDescripcion(), anulada ? e.anulada : e.celda);
            fechaHora(fila, 2, v.getFecha(), e.fechaHora);
            texto(fila, 3, v.getCliente().getNombreCompleto(), anulada ? e.anulada : e.celda);
            texto(fila, 4, v.getCliente().getDni(), e.celda);
            texto(fila, 5, !v.isAplicaIgv() ? "Exonerado" : v.isPreciosIncluyenIgv() ? "Incluido" : "Agregado", e.celda);

            numero(fila, 6, anulada ? BigDecimal.ZERO : v.getBaseImponible(), e.moneda);
            numero(fila, 7, anulada ? BigDecimal.ZERO : v.getIgv(), e.moneda);
            numero(fila, 8, anulada ? BigDecimal.ZERO : v.getTotal(), e.monedaNegrita);
            texto(fila, 9, v.getEstado().name(), anulada ? e.estadoAnulada : e.estadoEmitida);
        }
        cerrarTabla(hoja, e, titulos.length, f, new int[]{}, new int[]{6, 7, 8});
    }

    private void hojaDetalle(XSSFWorkbook libro, Estilos e, List<VentaDetalle> detalles) {
        Sheet hoja = libro.createSheet("Detalle");
        String[] titulos = {"Comprobante", "Fecha", "Cliente", "Item", "Codigo", "Repuesto",
                "Cantidad", "P. unitario", "Importe", "Estado"};
        int[] anchos = {16, 18, 30, 7, 12, 40, 11, 14, 14, 12};
        prepararTabla(hoja, e, titulos, anchos);

        int f = 1;
        for (VentaDetalle d : detalles) {
            Venta v = d.getVenta();
            Row fila = hoja.createRow(f++);
            texto(fila, 0, v.getComprobante(), e.celda);
            fechaHora(fila, 1, v.getFecha(), e.fechaHora);
            texto(fila, 2, v.getCliente().getNombreCompleto(), e.celda);
            entero(fila, 3, d.getItem(), e.entero);
            texto(fila, 4, d.getCodigoRepuesto(), e.celda);
            texto(fila, 5, d.getNombreRepuesto(), v.isAnulada() ? e.anulada : e.celda);
            entero(fila, 6, d.getCantidad(), e.entero);
            numero(fila, 7, d.getPrecioUnitario(), e.moneda);
            numero(fila, 8, v.isAnulada() ? BigDecimal.ZERO : d.getImporte(), e.monedaNegrita);
            texto(fila, 9, v.getEstado().name(), v.isAnulada() ? e.estadoAnulada : e.estadoEmitida);
        }
        cerrarTabla(hoja, e, titulos.length, f, new int[]{6}, new int[]{8});
    }

    private void hojaRepuestos(XSSFWorkbook libro, Estilos e, List<RepuestoVendido> repuestos) {
        Sheet hoja = libro.createSheet("Por repuesto");
        String[] titulos = {"#", "Repuesto", "Unidades", "Importe", "% del total"};
        int[] anchos = {6, 46, 12, 16, 13};
        prepararTabla(hoja, e, titulos, anchos);

        int f = 1;
        int ultima = repuestos.size();
        for (RepuestoVendido p : repuestos) {
            Row fila = hoja.createRow(f);
            entero(fila, 0, f, e.entero);
            texto(fila, 1, p.nombre(), e.celda);
            entero(fila, 2, p.unidades(), e.entero);
            numero(fila, 3, p.importe(), e.monedaNegrita);
            formula(fila, 4, "IFERROR(D" + (f + 1) + "/SUM($D$2:$D$" + (ultima + 1) + "),0)", e.porcentaje);
            f++;
        }
        cerrarTabla(hoja, e, titulos.length, f, new int[]{2}, new int[]{3});
    }

    private void hojaClientes(XSSFWorkbook libro, Estilos e, List<ClienteFrecuente> clientes) {
        Sheet hoja = libro.createSheet("Por cliente");
        String[] titulos = {"#", "Cliente", "DNI", "Compras", "Importe", "Ticket promedio"};
        int[] anchos = {6, 36, 12, 11, 16, 16};
        prepararTabla(hoja, e, titulos, anchos);

        int f = 1;
        for (ClienteFrecuente c : clientes) {
            Row fila = hoja.createRow(f);
            entero(fila, 0, f, e.entero);
            texto(fila, 1, c.nombre(), e.celda);
            texto(fila, 2, c.dni(), e.celda);
            entero(fila, 3, c.compras(), e.entero);
            numero(fila, 4, c.importe(), e.monedaNegrita);
            formula(fila, 5, "IFERROR(E" + (f + 1) + "/D" + (f + 1) + ",0)", e.moneda);
            f++;
        }
        cerrarTabla(hoja, e, titulos.length, f, new int[]{3}, new int[]{4});
    }
}
