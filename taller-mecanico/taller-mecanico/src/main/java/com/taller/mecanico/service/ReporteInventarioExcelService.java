package com.taller.mecanico.service;

import com.taller.mecanico.config.DatosEmpresa;
import com.taller.mecanico.model.Compra;
import com.taller.mecanico.model.CompraDetalle;
import com.taller.mecanico.model.MovimientoInventario;
import com.taller.mecanico.model.Repuesto;
import com.taller.mecanico.repository.CompraRepository;
import com.taller.mecanico.repository.MovimientoInventarioRepository;
import com.taller.mecanico.service.InventarioService.ResumenInventario;
import com.taller.mecanico.service.LibroExcel.Estilos;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
@Transactional(readOnly = true)
public class ReporteInventarioExcelService {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final InventarioService inventarioService;
    private final MovimientoInventarioRepository movimientoRepository;
    private final CompraRepository compraRepository;
    private final DatosEmpresa empresa;

    public void generarInventario(File destino) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(); OutputStream salida = new FileOutputStream(destino)) {
            Estilos e = new Estilos(libro);
            hojaInventario(libro, e, inventarioService.listarTodo(), inventarioService.resumen());
            hojaKardex(libro, e, movimientoRepository.findAll(Sort.by("repuesto.codigoRepuesto", "fecha", "id")));
            libro.getProperties().getCoreProperties().setTitle("Inventario valorizado");
            libro.getProperties().getCoreProperties().setCreator(empresa.getRazonSocial());
            libro.write(salida);
        }
    }

    private void hojaInventario(XSSFWorkbook libro, Estilos e, List<Repuesto> repuestos, ResumenInventario r) {
        Sheet hoja = libro.createSheet("Inventario");
        String[] titulos = {"Codigo", "Repuesto", "Categoria", "Stock", "Stock minimo", "Estado",
                "Costo promedio", "Valor al costo", "Precio venta", "Valor a precio de venta"};
        int[] anchos = {12, 42, 26, 10, 13, 13, 16, 17, 14, 22};
        prepararTabla(hoja, e, titulos, anchos);

        int f = 1;
        for (Repuesto p : repuestos) {
            Row fila = hoja.createRow(f++);
            texto(fila, 0, p.getCodigoRepuesto(), e.celda);
            texto(fila, 1, p.getNombre(), e.celda);
            texto(fila, 2, p.getCategoria() == null ? "Sin categoria" : p.getCategoria().getNombre(), e.celda);
            entero(fila, 3, p.getStock(), e.entero);
            entero(fila, 4, p.getStockMinimo(), e.entero);
            XSSFCellStyle estado = p.isSinStock() ? e.estadoAnulada : p.isStockBajo() ? e.estadoAdvertencia : e.estadoEmitida;
            texto(fila, 5, p.isSinStock() ? "SIN STOCK" : p.isStockBajo() ? "BAJO" : "OK", estado);
            numero(fila, 6, p.getCostoPromedio() == null ? BigDecimal.ZERO : p.getCostoPromedio(), e.moneda);
            formula(fila, 7, "D" + f + "*G" + f, e.monedaNegrita);
            numero(fila, 8, p.getPrecio(), e.moneda);
            formula(fila, 9, "D" + f + "*I" + f, e.moneda);
        }
        cerrarTabla(hoja, e, titulos.length, f, new int[]{3}, new int[]{7, 9});

        int c = titulos.length + 1;
        hoja.setColumnWidth(c, 26 * 256);
        hoja.setColumnWidth(c + 1, 18 * 256);
        Object[][] kpis = {
                {"Repuestos", r.repuestos()},
                {"Unidades en stock", r.unidades()},
                {"Valor al costo (sin IGV)", r.valorCosto()},
                {"Valor a precio de venta", r.valorVenta()},
                {"Repuestos con stock bajo", r.stockBajo()},
                {"Repuestos sin stock", r.sinStock()},
        };
        Row titulo = fila(hoja, 0);
        texto(titulo, c, "Resumen al " + LocalDate.now().format(FECHA), e.cabecera);
        texto(titulo, c + 1, "", e.cabecera);
        for (int i = 0; i < kpis.length; i++) {
            Row fila = fila(hoja, i + 1);
            texto(fila, c, (String) kpis[i][0], e.celdaNegrita);
            if (kpis[i][1] instanceof BigDecimal b) {
                numero(fila, c + 1, b, e.moneda);
            } else {
                entero(fila, c + 1, ((Number) kpis[i][1]).longValue(), e.entero);
            }
        }
    }

    private void hojaKardex(XSSFWorkbook libro, Estilos e, List<MovimientoInventario> movimientos) {
        Sheet hoja = libro.createSheet("Kardex");
        String[] titulos = {"Codigo", "Repuesto", "Fecha", "Movimiento", "Documento", "Entrada", "Salida", "Saldo",
                "Costo unit. (sin IGV)"};
        int[] anchos = {12, 40, 18, 22, 30, 10, 10, 10, 20};
        prepararTabla(hoja, e, titulos, anchos);

        int f = 1;
        for (MovimientoInventario m : movimientos) {
            Row fila = hoja.createRow(f++);
            texto(fila, 0, m.getRepuesto().getCodigoRepuesto(), e.celda);
            texto(fila, 1, m.getRepuesto().getNombre(), e.celda);
            fechaHora(fila, 2, m.getFecha(), e.fechaHora);
            texto(fila, 3, m.getTipo().getDescripcion(), m.getTipo().isEntrada() ? e.estadoEmitida : e.estadoAdvertencia);
            texto(fila, 4, m.getDocumento(), e.celda);
            entero(fila, 5, m.getEntrada(), e.entero);
            entero(fila, 6, m.getSalida(), e.entero);
            entero(fila, 7, m.getStockResultante(), e.entero);
            if (m.getCostoUnitario() != null) {
                numero(fila, 8, m.getCostoUnitario(), e.moneda);
            } else {
                texto(fila, 8, "", e.celda);
            }
        }
        cerrarTabla(hoja, e, titulos.length, f, new int[]{5, 6}, new int[]{});
    }

    public void generarCompras(LocalDate desde, LocalDate hasta, File destino) throws IOException {
        List<Compra> compras = compraRepository.findAll(Sort.by("fecha", "id")).stream()
                .filter(c -> !c.getFecha().isBefore(VentaService.inicio(desde)) && !c.getFecha().isAfter(VentaService.fin(hasta)))
                .toList();
        try (XSSFWorkbook libro = new XSSFWorkbook(); OutputStream salida = new FileOutputStream(destino)) {
            Estilos e = new Estilos(libro);

            Sheet hoja = libro.createSheet("Compras");
            String[] titulos = {"Codigo", "Fecha", "Proveedor", "RUC", "Documento", "Fecha doc.",
                    "Base imponible", "IGV", "Total", "Estado"};
            int[] anchos = {14, 18, 42, 13, 26, 12, 16, 13, 15, 13};
            prepararTabla(hoja, e, titulos, anchos);
            int f = 1;
            for (Compra c : compras) {
                boolean anulada = c.isAnulada();
                Row fila = hoja.createRow(f++);
                texto(fila, 0, c.getCodigo(), anulada ? e.anulada : e.celda);
                fechaHora(fila, 1, c.getFecha(), e.fechaHora);
                texto(fila, 2, c.getProveedor().getRazonSocial(), anulada ? e.anulada : e.celda);
                texto(fila, 3, c.getProveedor().getRuc(), e.celda);
                texto(fila, 4, c.getTipoDocumento().getDescripcion() + " " + c.getNumeroDocumento(), e.celda);
                fecha(fila, 5, c.getFechaDocumento(), e.fecha);
                numero(fila, 6, anulada ? BigDecimal.ZERO : c.getBaseImponible(), e.moneda);
                numero(fila, 7, anulada ? BigDecimal.ZERO : c.getIgv(), e.moneda);
                numero(fila, 8, anulada ? BigDecimal.ZERO : c.getTotal(), e.monedaNegrita);
                texto(fila, 9, c.getEstado().name(), anulada ? e.estadoAnulada : e.estadoEmitida);
            }
            cerrarTabla(hoja, e, titulos.length, f, new int[]{}, new int[]{6, 7, 8});

            Sheet detalle = libro.createSheet("Detalle");
            String[] tDet = {"Codigo", "Fecha", "Proveedor", "Item", "Cod. repuesto", "Repuesto", "Cantidad",
                    "Costo unitario", "Importe", "Estado"};
            int[] aDet = {14, 18, 34, 7, 13, 40, 11, 15, 14, 13};
            prepararTabla(detalle, e, tDet, aDet);
            f = 1;
            for (Compra c : compras) {
                for (CompraDetalle d : c.getDetalles()) {
                    Row fila = detalle.createRow(f++);
                    texto(fila, 0, c.getCodigo(), e.celda);
                    fechaHora(fila, 1, c.getFecha(), e.fechaHora);
                    texto(fila, 2, c.getProveedor().getRazonSocial(), e.celda);
                    entero(fila, 3, d.getItem(), e.entero);
                    texto(fila, 4, d.getCodigoRepuesto(), e.celda);
                    texto(fila, 5, d.getNombreRepuesto(), c.isAnulada() ? e.anulada : e.celda);
                    entero(fila, 6, c.isAnulada() ? 0 : d.getCantidad(), e.entero);
                    numero(fila, 7, d.getCostoUnitario(), e.moneda);
                    numero(fila, 8, c.isAnulada() ? BigDecimal.ZERO : d.getImporte(), e.monedaNegrita);
                    texto(fila, 9, c.getEstado().name(), c.isAnulada() ? e.estadoAnulada : e.estadoEmitida);
                }
            }
            cerrarTabla(detalle, e, tDet.length, f, new int[]{6}, new int[]{8});

            libro.getProperties().getCoreProperties().setTitle("Compras del " + desde.format(FECHA) + " al " + hasta.format(FECHA)
                    + " (generado " + LocalDateTime.now().format(FECHA_HORA) + ")");
            libro.getProperties().getCoreProperties().setCreator(empresa.getRazonSocial());
            libro.write(salida);
        }
    }

    private static Row fila(Sheet hoja, int indice) {
        Row r = hoja.getRow(indice);
        return r != null ? r : hoja.createRow(indice);
    }
}
