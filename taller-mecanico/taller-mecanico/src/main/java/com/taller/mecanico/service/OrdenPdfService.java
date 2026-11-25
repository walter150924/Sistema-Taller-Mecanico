package com.taller.mecanico.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.taller.mecanico.config.DatosEmpresa;
import com.taller.mecanico.model.LineaRepuesto;
import com.taller.mecanico.model.LineaServicio;
import com.taller.mecanico.model.OrdenServicio;
import com.taller.mecanico.model.Vehiculo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class OrdenPdfService {
    private static final Color ACENTO = new Color(234, 88, 12);
    private static final Color TEXTO = new Color(31, 35, 40);
    private static final Color TEXTO_SUAVE = new Color(101, 109, 118);
    private static final Color BORDE = new Color(216, 222, 228);
    private static final Color FONDO_SUAVE = new Color(246, 248, 250);
    private static final String SIN_DATO = "—";

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DecimalFormat IMPORTE =
            new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));

    private final OrdenServicioService ordenService;
    private final DatosEmpresa empresa;

    private final Font fuenteMarca = fuente(20, Font.BOLD, ACENTO);
    private final Font fuenteTitulo = fuente(13, Font.BOLD, TEXTO);
    private final Font fuenteNormal = fuente(9, Font.NORMAL, TEXTO);
    private final Font fuenteNegrita = fuente(9, Font.BOLD, TEXTO);
    private final Font fuenteSuave = fuente(8.5f, Font.NORMAL, TEXTO_SUAVE);
    private final Font fuenteEtiqueta = fuente(7.5f, Font.BOLD, TEXTO_SUAVE);
    private final Font fuenteCabecera = fuente(8, Font.BOLD, Color.WHITE);

    public void generar(Long ordenId, File destino) throws IOException {
        OrdenServicio orden = ordenService.obtener(ordenId);
        try (OutputStream salida = new FileOutputStream(destino)) {
            escribir(orden, salida);
        }
    }

    private void escribir(OrdenServicio orden, OutputStream salida) {
        Document documento = new Document(PageSize.A4, 40, 40, 40, 50);
        try {
            PdfWriter.getInstance(documento, salida);
            documento.addTitle("Orden de servicio " + orden.getCodigo());
            documento.addAuthor(empresa.getRazonSocial());
            documento.open();

            documento.add(cabecera(orden));
            documento.add(espacio(12));
            documento.add(datosGenerales(orden));
            documento.add(espacio(10));
            documento.add(bloqueTexto("PROBLEMA REPORTADO", orden.getProblemaReportado()));
            documento.add(espacio(6));
            documento.add(bloqueTexto("DIAGNOSTICO", orden.getDiagnostico()));
            documento.add(espacio(12));
            documento.add(tablaServicios(orden));
            documento.add(espacio(10));
            documento.add(tablaRepuestos(orden));
            documento.add(espacio(10));
            documento.add(totales(orden));
            documento.add(espacio(10));
            documento.add(new Paragraph("SON: " + NumeroALetras.importe(orden.getTotal()), fuenteNegrita));
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF: " + e.getMessage(), e);
        } finally {
            documento.close();
        }
    }

    private PdfPTable cabecera(OrdenServicio orden) {
        PdfPTable tabla = new PdfPTable(new float[]{62, 38});
        tabla.setWidthPercentage(100);

        PdfPCell emisor = sinBorde();
        emisor.addElement(new Paragraph(empresa.getNombreComercial(), fuenteMarca));
        emisor.addElement(new Paragraph(empresa.getRazonSocial() + "  ·  RUC " + empresa.getRuc(), fuenteNegrita));
        emisor.addElement(new Paragraph(empresa.getDireccion(), fuenteSuave));
        tabla.addCell(emisor);

        PdfPCell caja = new PdfPCell();
        caja.setBorderColor(ACENTO);
        caja.setBorderWidth(1.2f);
        caja.setPadding(10);
        Paragraph titulo = new Paragraph("ORDEN DE SERVICIO", fuenteTitulo);
        titulo.setAlignment(Element.ALIGN_CENTER);
        Paragraph codigo = new Paragraph(orden.getCodigo(), fuente(14, Font.BOLD, ACENTO));
        codigo.setAlignment(Element.ALIGN_CENTER);
        Paragraph estado = new Paragraph(orden.getEstado().getDescripcion().toUpperCase(), fuenteSuave);
        estado.setAlignment(Element.ALIGN_CENTER);
        caja.addElement(titulo);
        caja.addElement(codigo);
        caja.addElement(estado);
        tabla.addCell(caja);
        return tabla;
    }

    private PdfPTable datosGenerales(OrdenServicio orden) {
        Vehiculo vehiculo = orden.getVehiculo();
        PdfPTable tabla = new PdfPTable(new float[]{17, 33, 17, 33});
        tabla.setWidthPercentage(100);
        agregarDato(tabla, "CLIENTE", orden.getCliente().getNombreCompleto().toUpperCase());
        agregarDato(tabla, "DNI", orden.getCliente().getDni());
        agregarDato(tabla, "VEHICULO", vehiculo.getDescripcion());
        agregarDato(tabla, "PLACA", vehiculo.getPlaca());
        agregarDato(tabla, "KILOMETRAJE", String.format(Locale.US, "%,d km", orden.getKilometrajeIngreso()));
        agregarDato(tabla, "MECANICO", orden.getNombreMecanico());
        agregarDato(tabla, "INGRESO", orden.getFechaIngreso().format(FECHA_HORA));
        agregarDato(tabla, "ENTREGA", orden.getFechaEntrega() == null ? SIN_DATO : orden.getFechaEntrega().format(FECHA_HORA));
        return enMarco(tabla);
    }

    private PdfPTable bloqueTexto(String etiqueta, String contenido) {
        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);
        Phrase frase = new Phrase();
        frase.add(new Chunk(etiqueta + "\n", fuenteEtiqueta));
        frase.add(new Chunk(contenido == null || contenido.isBlank() ? SIN_DATO : contenido, fuenteNormal));
        PdfPCell celda = new PdfPCell(frase);
        celda.setBorderColor(BORDE);
        celda.setPadding(8);
        tabla.addCell(celda);
        return tabla;
    }

    private PdfPTable tablaServicios(OrdenServicio orden) {
        PdfPTable tabla = tablaConCabecera(new float[]{8, 72, 20}, "ÍTEM", "SERVICIO / MANO DE OBRA", "IMPORTE");
        int item = 1;
        for (LineaServicio linea : orden.getServicios()) {
            tabla.addCell(celda(String.valueOf(item++), Element.ALIGN_CENTER, fuenteNormal));
            tabla.addCell(celda(linea.getServicio().getNombre(), Element.ALIGN_LEFT, fuenteNormal));
            tabla.addCell(celda(IMPORTE.format(linea.getPrecio()), Element.ALIGN_RIGHT, fuenteNegrita));
        }
        agregarFilaVaciaSiCorresponde(tabla, orden.getServicios().isEmpty(), 3, "Sin servicios registrados");
        return tabla;
    }

    private PdfPTable tablaRepuestos(OrdenServicio orden) {
        PdfPTable tabla = tablaConCabecera(new float[]{8, 14, 40, 10, 14, 14},
                "ÍTEM", "CÓDIGO", "REPUESTO", "CANT.", "P. UNIT.", "IMPORTE");
        int item = 1;
        for (LineaRepuesto linea : orden.getRepuestos()) {
            tabla.addCell(celda(String.valueOf(item++), Element.ALIGN_CENTER, fuenteNormal));
            tabla.addCell(celda(linea.getRepuesto().getCodigoRepuesto(), Element.ALIGN_LEFT, fuenteSuave));
            tabla.addCell(celda(linea.getRepuesto().getNombre(), Element.ALIGN_LEFT, fuenteNormal));
            tabla.addCell(celda(String.valueOf(linea.getCantidad()), Element.ALIGN_CENTER, fuenteNormal));
            tabla.addCell(celda(IMPORTE.format(linea.getPrecioUnitario()), Element.ALIGN_RIGHT, fuenteNormal));
            tabla.addCell(celda(IMPORTE.format(linea.getImporte()), Element.ALIGN_RIGHT, fuenteNegrita));
        }
        agregarFilaVaciaSiCorresponde(tabla, orden.getRepuestos().isEmpty(), 6, "Sin repuestos registrados");
        return tabla;
    }

    private PdfPTable totales(OrdenServicio orden) {
        PdfPTable externo = new PdfPTable(new float[]{55, 45});
        externo.setWidthPercentage(100);
        PdfPCell nota = sinBorde();
        nota.addElement(new Paragraph("Los precios incluyen IGV.", fuenteSuave));
        nota.addElement(new Paragraph(orden.getMetodoPago() == null
                ? "Pago: pendiente"
                : "Metodo de pago: " + orden.getMetodoPago().getDescripcion(), fuenteNegrita));
        externo.addCell(nota);

        PdfPTable tabla = new PdfPTable(new float[]{60, 40});
        tabla.setWidthPercentage(100);
        filaTotal(tabla, "MANO DE OBRA", orden.getTotalServicios(), fuenteNormal);
        filaTotal(tabla, "REPUESTOS", orden.getTotalRepuestos(), fuenteNormal);
        filaTotal(tabla, "OP. GRAVADA", orden.getBaseImponible(), fuenteNormal);
        filaTotal(tabla, "IGV (18%)", orden.getIgv(), fuenteNormal);
        filaTotal(tabla, "TOTAL", orden.getTotal(), fuenteNegrita);
        PdfPCell contenedor = sinBorde();
        contenedor.addElement(tabla);
        externo.addCell(contenedor);
        return externo;
    }

    private PdfPTable tablaConCabecera(float[] anchos, String... titulos) {
        PdfPTable tabla = new PdfPTable(anchos);
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        for (String titulo : titulos) {
            PdfPCell celda = new PdfPCell(new Phrase(titulo, fuenteCabecera));
            celda.setBackgroundColor(ACENTO);
            celda.setBorder(Rectangle.NO_BORDER);
            celda.setPadding(6);
            tabla.addCell(celda);
        }
        return tabla;
    }

    private void agregarFilaVaciaSiCorresponde(PdfPTable tabla, boolean vacia, int columnas, String texto) {
        if (vacia) {
            PdfPCell celda = celda(texto, Element.ALIGN_CENTER, fuenteSuave);
            celda.setColspan(columnas);
            tabla.addCell(celda);
        }
    }

    private void filaTotal(PdfPTable tabla, String etiqueta, BigDecimal valor, Font fuente) {
        tabla.addCell(celda(etiqueta, Element.ALIGN_LEFT, fuente));
        tabla.addCell(celda("S/ " + IMPORTE.format(valor), Element.ALIGN_RIGHT, fuente));
    }

    private void agregarDato(PdfPTable tabla, String etiqueta, String valor) {
        PdfPCell celdaEtiqueta = sinBorde();
        celdaEtiqueta.setPhrase(new Phrase(etiqueta, fuenteEtiqueta));
        celdaEtiqueta.setPaddingBottom(5);
        tabla.addCell(celdaEtiqueta);
        PdfPCell celdaValor = sinBorde();
        celdaValor.setPhrase(new Phrase(valor, fuenteNormal));
        celdaValor.setPaddingBottom(5);
        tabla.addCell(celdaValor);
    }

    private PdfPTable enMarco(PdfPTable contenido) {
        PdfPTable marco = new PdfPTable(1);
        marco.setWidthPercentage(100);
        PdfPCell celda = new PdfPCell(contenido);
        celda.setBorderColor(BORDE);
        celda.setPadding(8);
        celda.setBackgroundColor(FONDO_SUAVE);
        marco.addCell(celda);
        return marco;
    }

    private PdfPCell celda(String texto, int alineacion, Font fuente) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setHorizontalAlignment(alineacion);
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        celda.setBorder(Rectangle.BOTTOM);
        celda.setBorderColor(BORDE);
        celda.setPadding(6);
        return celda;
    }

    private static PdfPCell sinBorde() {
        PdfPCell celda = new PdfPCell();
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPadding(0);
        return celda;
    }

    private static Paragraph espacio(float alto) {
        Paragraph parrafo = new Paragraph(" ");
        parrafo.setLeading(alto);
        return parrafo;
    }

    private static Font fuente(float tamanio, int estilo, Color color) {
        return new Font(Font.HELVETICA, tamanio, estilo, color);
    }
}
