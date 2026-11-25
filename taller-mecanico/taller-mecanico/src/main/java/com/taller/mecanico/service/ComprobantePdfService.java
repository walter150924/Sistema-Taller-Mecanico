package com.taller.mecanico.service;

import com.taller.mecanico.config.DatosEmpresa;
import com.taller.mecanico.model.Venta;
import com.taller.mecanico.model.VentaDetalle;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
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
public class ComprobantePdfService {
    private static final Color ACENTO = new Color(234, 88, 12);
    private static final Color ACENTO_SUAVE = new Color(255, 241, 230);
    private static final Color TEXTO = new Color(31, 35, 40);
    private static final Color TEXTO_SUAVE = new Color(101, 109, 118);
    private static final Color BORDE = new Color(216, 222, 228);
    private static final Color FILA_ALTERNA = new Color(246, 248, 250);
    private static final Color PELIGRO = new Color(207, 34, 46);

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DecimalFormat IMPORTE =
            new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));

    private final VentaService ventaService;
    private final DatosEmpresa empresa;

    private final Font fTitulo = fuente(15, Font.BOLD, TEXTO);
    private final Font fNormal = fuente(9, Font.NORMAL, TEXTO);
    private final Font fNegrita = fuente(9, Font.BOLD, TEXTO);
    private final Font fSuave = fuente(8.5f, Font.NORMAL, TEXTO_SUAVE);
    private final Font fEtiqueta = fuente(7.5f, Font.BOLD, TEXTO_SUAVE);
    private final Font fCabecera = fuente(8, Font.BOLD, Color.WHITE);
    private final Font fCajaRuc = fuente(11, Font.BOLD, TEXTO);
    private final Font fCajaTipo = fuente(10.5f, Font.BOLD, Color.WHITE);
    private final Font fCajaNumero = fuente(13, Font.BOLD, ACENTO);
    private final Font fTotal = fuente(11, Font.BOLD, Color.WHITE);

    public void generar(Long ventaId, File destino) throws IOException {
        Venta venta = ventaService.obtener(ventaId);
        try (OutputStream salida = new FileOutputStream(destino)) {
            escribir(venta, salida);
        }
    }

    void escribir(Venta venta, OutputStream salida) {
        Document doc = new Document(PageSize.A4, 40, 40, 40, 50);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            writer.setPageEvent(new PiePagina(venta));
            doc.addTitle(venta.getTipoComprobante().getDescripcion() + " " + venta.getComprobante());
            doc.addAuthor(empresa.getRazonSocial());
            doc.open();

            doc.add(cabecera(venta));
            doc.add(espacio(14));
            doc.add(datosCliente(venta));
            doc.add(espacio(14));
            doc.add(tablaItems(venta));
            doc.add(espacio(10));
            doc.add(totales(venta));
            doc.add(espacio(12));
            doc.add(importeEnLetras(venta));
            if (venta.getMetodoPago() != null) {
                doc.add(espacio(8));
                Paragraph pago = new Paragraph();
                pago.add(new Chunk("METODO DE PAGO: ", fEtiqueta));
                pago.add(new Chunk(venta.getMetodoPago().getDescripcion(), fNormal));
                doc.add(pago);
            }
            if (venta.getObservacion() != null) {
                doc.add(espacio(8));
                Paragraph obs = new Paragraph();
                obs.add(new Chunk("OBSERVACIONES: ", fEtiqueta));
                obs.add(new Chunk(venta.getObservacion(), fNormal));
                doc.add(obs);
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF: " + e.getMessage(), e);
        } finally {
            doc.close();
        }
    }

    private PdfPTable cabecera(Venta venta) {
        PdfPTable tabla = new PdfPTable(new float[]{62, 38});
        tabla.setWidthPercentage(100);

        PdfPCell emisor = sinBorde();
        emisor.setVerticalAlignment(Element.ALIGN_MIDDLE);
        emisor.addElement(new Paragraph(empresa.getNombreComercial(), fuente(20, Font.BOLD, ACENTO)));
        emisor.addElement(new Paragraph(empresa.getRazonSocial(), fNegrita));
        emisor.addElement(new Paragraph(empresa.getDireccion(), fSuave));
        StringBuilder contacto = new StringBuilder();
        if (!empresa.getTelefono().isBlank()) {
            contacto.append("Telf. ").append(empresa.getTelefono());
        }
        if (!empresa.getEmail().isBlank()) {
            contacto.append(contacto.isEmpty() ? "" : "  ·  ").append(empresa.getEmail());
        }
        if (!contacto.isEmpty()) {
            emisor.addElement(new Paragraph(contacto.toString(), fSuave));
        }
        tabla.addCell(emisor);

        PdfPTable caja = new PdfPTable(1);
        caja.setWidthPercentage(100);
        caja.addCell(celdaCaja(new Phrase("R.U.C. " + empresa.getRuc(), fCajaRuc), Color.WHITE, 10, 4));
        caja.addCell(celdaCaja(new Phrase(venta.getTipoComprobante().getDescripcion().toUpperCase(), fCajaTipo),
                ACENTO, 7, 7));
        caja.addCell(celdaCaja(new Phrase(venta.getComprobante(), fCajaNumero), Color.WHITE, 6, 10));

        PdfPCell contenedor = new PdfPCell(caja);
        contenedor.setBorderColor(ACENTO);
        contenedor.setBorderWidth(1.2f);
        contenedor.setPadding(0);
        tabla.addCell(contenedor);
        return tabla;
    }

    private PdfPTable datosCliente(Venta venta) {
        PdfPTable tabla = new PdfPTable(new float[]{17, 43, 17, 23});
        tabla.setWidthPercentage(100);

        agregarDato(tabla, "CLIENTE", venta.getCliente().getNombreCompleto().toUpperCase());
        agregarDato(tabla, "FECHA DE EMISIÓN", venta.getFecha().format(FECHA));
        agregarDato(tabla, "DNI", venta.getCliente().getDni());
        agregarDato(tabla, "HORA", venta.getFecha().format(HORA));
        agregarDato(tabla, "CORREO", venta.getCliente().getEmail() == null ? "—" : venta.getCliente().getEmail());
        agregarDato(tabla, "MONEDA", "SOLES (PEN)");
        agregarDato(tabla, "TELÉFONO", venta.getCliente().getTelefono() == null ? "—" : venta.getCliente().getTelefono());
        agregarDato(tabla, "CONDICIÓN", "CONTADO");

        PdfPTable marco = new PdfPTable(1);
        marco.setWidthPercentage(100);
        PdfPCell celda = new PdfPCell(tabla);
        celda.setBorderColor(BORDE);
        celda.setPadding(8);
        celda.setBackgroundColor(FILA_ALTERNA);
        marco.addCell(celda);
        return marco;
    }

    private PdfPTable tablaItems(Venta venta) {
        PdfPTable tabla = new PdfPTable(new float[]{7, 13, 44, 9, 13, 14});
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);

        String[] titulos = {"ÍTEM", "CÓDIGO", "DESCRIPCIÓN", "CANT.", "P. UNIT.", "IMPORTE"};
        int[] alineacion = {Element.ALIGN_CENTER, Element.ALIGN_LEFT, Element.ALIGN_LEFT,
                Element.ALIGN_CENTER, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT};
        for (int i = 0; i < titulos.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(titulos[i], fCabecera));
            c.setBackgroundColor(ACENTO);
            c.setBorder(Rectangle.NO_BORDER);
            c.setHorizontalAlignment(alineacion[i]);
            c.setPaddingTop(7);
            c.setPaddingBottom(7);
            c.setPaddingLeft(6);
            c.setPaddingRight(6);
            tabla.addCell(c);
        }

        int fila = 0;
        for (VentaDetalle d : venta.getDetalles()) {
            Color fondo = fila++ % 2 == 0 ? Color.WHITE : FILA_ALTERNA;
            tabla.addCell(celdaItem(String.valueOf(d.getItem()), Element.ALIGN_CENTER, fondo, fNormal));
            tabla.addCell(celdaItem(d.getCodigoRepuesto(), Element.ALIGN_LEFT, fondo, fSuave));
            tabla.addCell(celdaItem(d.getNombreRepuesto(), Element.ALIGN_LEFT, fondo, fNormal));
            tabla.addCell(celdaItem(String.valueOf(d.getCantidad()), Element.ALIGN_CENTER, fondo, fNormal));
            tabla.addCell(celdaItem(IMPORTE.format(d.getPrecioUnitario()), Element.ALIGN_RIGHT, fondo, fNormal));
            tabla.addCell(celdaItem(IMPORTE.format(d.getImporte()), Element.ALIGN_RIGHT, fondo, fNegrita));
        }
        return tabla;
    }

    private PdfPTable totales(Venta venta) {
        PdfPTable externo = new PdfPTable(new float[]{55, 45});
        externo.setWidthPercentage(100);

        PdfPCell nota = sinBorde();
        String modalidad = !venta.isAplicaIgv()
                ? "Operación inafecta / exonerada de IGV."
                : venta.isPreciosIncluyenIgv()
                ? "Los precios unitarios incluyen IGV."
                : "Los precios unitarios no incluyen IGV.";
        nota.addElement(new Paragraph(modalidad, fSuave));
        nota.addElement(new Paragraph("Artículos: " + venta.getDetalles().stream()
                .mapToInt(VentaDetalle::getCantidad).sum(), fSuave));
        externo.addCell(nota);

        PdfPTable t = new PdfPTable(new float[]{60, 40});
        t.setWidthPercentage(100);
        String etiquetaBase = venta.isAplicaIgv() ? "OP. GRAVADA" : "OP. EXONERADA";
        filaTotal(t, etiquetaBase, "S/ " + IMPORTE.format(venta.getBaseImponible()), false);
        String tasa = venta.getTasaIgv().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString();
        filaTotal(t, "IGV (" + tasa + "%)", "S/ " + IMPORTE.format(venta.getIgv()), false);
        filaTotal(t, "IMPORTE TOTAL", "S/ " + IMPORTE.format(venta.getTotal()), true);

        PdfPCell celda = new PdfPCell(t);
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPadding(0);
        externo.addCell(celda);
        return externo;
    }

    private PdfPTable importeEnLetras(Venta venta) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        Phrase frase = new Phrase();
        frase.add(new Chunk("SON: ", fEtiqueta));
        frase.add(new Chunk(NumeroALetras.importe(venta.getTotal()), fNegrita));
        PdfPCell c = new PdfPCell(frase);
        c.setBorderColor(BORDE);
        c.setBackgroundColor(ACENTO_SUAVE);
        c.setPadding(8);
        t.addCell(c);
        return t;
    }

    private void agregarDato(PdfPTable tabla, String etiqueta, String valor) {
        PdfPCell e = sinBorde();
        e.setPhrase(new Phrase(etiqueta, fEtiqueta));
        e.setPaddingBottom(5);
        tabla.addCell(e);
        PdfPCell v = sinBorde();
        v.setPhrase(new Phrase(valor, fNormal));
        v.setPaddingBottom(5);
        tabla.addCell(v);
    }

    private PdfPCell celdaCaja(Phrase texto, Color fondo, float arriba, float abajo) {
        PdfPCell c = new PdfPCell(texto);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setBorder(Rectangle.NO_BORDER);
        c.setBackgroundColor(fondo);
        c.setPaddingTop(arriba);
        c.setPaddingBottom(abajo);
        return c;
    }

    private PdfPCell celdaItem(String texto, int alineacion, Color fondo, Font fuente) {
        PdfPCell c = new PdfPCell(new Phrase(texto, fuente));
        c.setHorizontalAlignment(alineacion);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setBackgroundColor(fondo);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(BORDE);
        c.setPaddingTop(6);
        c.setPaddingBottom(7);
        c.setPaddingLeft(6);
        c.setPaddingRight(6);
        return c;
    }

    private void filaTotal(PdfPTable t, String etiqueta, String valor, boolean destacado) {
        Font fe = destacado ? fTotal : fNegrita;
        Font fv = destacado ? fTotal : fNormal;
        PdfPCell e = new PdfPCell(new Phrase(etiqueta, fe));
        PdfPCell v = new PdfPCell(new Phrase(valor, fv));
        for (PdfPCell c : new PdfPCell[]{e, v}) {
            c.setBorder(Rectangle.BOTTOM);
            c.setBorderColor(BORDE);
            c.setPadding(6);
            if (destacado) {
                c.setBackgroundColor(ACENTO);
                c.setBorder(Rectangle.NO_BORDER);
                c.setPaddingTop(8);
                c.setPaddingBottom(9);
            }
        }
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(e);
        t.addCell(v);
    }

    private static PdfPCell sinBorde() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(0);
        return c;
    }

    private static Paragraph espacio(float alto) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(alto);
        return p;
    }

    private static Font fuente(float tamanio, int estilo, Color color) {
        return new Font(Font.HELVETICA, tamanio, estilo, color);
    }

    private class PiePagina extends PdfPageEventHelper {
        private final Venta venta;

        PiePagina(Venta venta) {
            this.venta = venta;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float centro = (document.left() + document.right()) / 2;
            float y = document.bottom() - 22;

            cb.setColorStroke(BORDE);
            cb.setLineWidth(0.6f);
            cb.moveTo(document.left(), y + 12);
            cb.lineTo(document.right(), y + 12);
            cb.stroke();

            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                    new Phrase("Representación impresa de la " + venta.getTipoComprobante().getDescripcion()
                            + "  ·  Gracias por su compra", fSuave), centro, y, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase("Pág. " + writer.getPageNumber(), fSuave), document.right(), y, 0);

            if (venta.isAnulada()) {
                PdfContentByte debajo = writer.getDirectContentUnder();
                debajo.saveState();
                PdfGState estado = new PdfGState();
                estado.setFillOpacity(0.12f);
                debajo.setGState(estado);
                try {
                    debajo.beginText();
                    debajo.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, false), 110);
                    debajo.setColorFill(PELIGRO);
                    debajo.showTextAligned(Element.ALIGN_CENTER, "ANULADO",
                            centro, (document.top() + document.bottom()) / 2, 40);
                    debajo.endText();
                } catch (DocumentException | IOException e) {
                    throw new IllegalStateException(e);
                }
                debajo.restoreState();
            }
        }
    }
}
