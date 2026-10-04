package com.proyectogrado.analitica_service.pdf;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.proyectogrado.analitica_service.dto.ReportePdfRequest;
import com.proyectogrado.analitica_service.dto.ReportePdfRequest.Indicador;
import com.proyectogrado.analitica_service.dto.ReportePdfRequest.Seccion;
import com.proyectogrado.analitica_service.dto.ReportePdfRequest.Tabla;

import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Arma el PDF de un reporte de analítica con OpenPDF: encabezado con la
 * organización y el período, indicadores, y cada gráfica con su
 * explicación y su tabla.
 */
@Component
public class GeneradorPdf {

    // Colores de VITA+
    private static final Color NAVY = new Color(0x12, 0x35, 0x5b);
    private static final Color GRIS_TEXTO = new Color(0x58, 0x65, 0x76);
    private static final Color GRIS_TENUE = new Color(0x93, 0xa0, 0xb0);
    private static final Color BORDE = new Color(0xe3, 0xe9, 0xf1);
    private static final Color FONDO = new Color(0xf5, 0xf8, 0xfc);
    private static final Color ROJO = new Color(0x9e, 0x29, 0x29);
    private static final Color VERDE = new Color(0x00, 0x63, 0x00);

    private static final Font MARCA = new Font(Font.HELVETICA, 9, Font.BOLD, GRIS_TENUE);
    private static final Font TITULO = new Font(Font.HELVETICA, 20, Font.BOLD, NAVY);
    private static final Font SUBTITULO = new Font(Font.HELVETICA, 10, Font.NORMAL, GRIS_TEXTO);
    private static final Font DATO = new Font(Font.HELVETICA, 9, Font.NORMAL, GRIS_TEXTO);
    private static final Font DATO_FUERTE = new Font(Font.HELVETICA, 9, Font.BOLD, NAVY);
    private static final Font KPI_ETIQUETA = new Font(Font.HELVETICA, 8, Font.BOLD, GRIS_TEXTO);
    private static final Font KPI_VALOR = new Font(Font.HELVETICA, 16, Font.BOLD, NAVY);
    private static final Font SECCION = new Font(Font.HELVETICA, 12, Font.BOLD, NAVY);
    private static final Font TABLA_ENCABEZADO = new Font(Font.HELVETICA, 8, Font.BOLD, NAVY);
    private static final Font TABLA_CELDA = new Font(Font.HELVETICA, 8, Font.NORMAL, GRIS_TEXTO);
    private static final Font NOTA = new Font(Font.HELVETICA, 8, Font.ITALIC, GRIS_TEXTO);
    private static final Font EXPLICACION = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, GRIS_TEXTO);

    private static final DateTimeFormatter FORMATO_GENERADO =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, h:mm a", new Locale("es", "CO"));

    public byte[] generar(ReportePdfRequest reporte, String organizacion) throws DocumentException, IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 40, 40, 40, 50);
        PdfWriter writer = PdfWriter.getInstance(documento, salida);
        writer.setPageEvent(new PiePagina());

        documento.addTitle(texto(reporte.titulo()));
        documento.addAuthor("VITA+");
        documento.open();

        encabezado(documento, reporte, organizacion);

        if (reporte.indicadores() != null && !reporte.indicadores().isEmpty()) {
            documento.add(indicadores(reporte.indicadores()));
        }

        if (reporte.secciones() != null) {
            for (Seccion seccion : reporte.secciones()) {
                seccion(documento, seccion);
            }
        }

        if (reporte.nota() != null && !reporte.nota().isBlank()) {
            Paragraph nota = new Paragraph(texto(reporte.nota()), NOTA);
            nota.setSpacingBefore(14);
            documento.add(nota);
        }

        documento.close();
        return salida.toByteArray();
    }

    // ---------- Encabezado ----------

    private void encabezado(Document documento, ReportePdfRequest reporte, String organizacion) throws DocumentException {
        documento.add(new Paragraph("VITA+  ·  ANALÍTICA", MARCA));

        Paragraph titulo = new Paragraph(texto(reporte.titulo()), TITULO);
        titulo.setSpacingBefore(4);
        documento.add(titulo);

        if (reporte.descripcion() != null && !reporte.descripcion().isBlank()) {
            Paragraph descripcion = new Paragraph(texto(reporte.descripcion()), SUBTITULO);
            descripcion.setSpacingBefore(2);
            documento.add(descripcion);
        }

        Paragraph datos = new Paragraph();
        datos.setSpacingBefore(10);
        datos.setSpacingAfter(14);
        datos.add(new Chunk("Organización: ", DATO));
        datos.add(new Chunk(texto(organizacion), DATO_FUERTE));
        if (reporte.periodo() != null && !reporte.periodo().isBlank()) {
            datos.add(new Chunk("     Período: ", DATO));
            datos.add(new Chunk(texto(reporte.periodo()), DATO_FUERTE));
        }
        datos.add(new Chunk("     Generado: ", DATO));
        datos.add(new Chunk(LocalDateTime.now().format(FORMATO_GENERADO), DATO_FUERTE));
        documento.add(datos);
    }

    // ---------- Indicadores ----------

    private PdfPTable indicadores(List<Indicador> lista) throws DocumentException {
        int columnas = lista.size() <= 4 ? lista.size() : 3;
        PdfPTable tabla = new PdfPTable(columnas);
        tabla.setWidthPercentage(100);
        tabla.setSpacingAfter(10);

        for (Indicador indicador : lista) {
            PdfPCell celda = new PdfPCell();
            celda.setBorderColor(BORDE);
            celda.setBorderWidth(0.75f);
            celda.setBorderWidthTop(3);
            celda.setBorderColorTop(colorTono(indicador.tono(), NAVY));
            celda.setPadding(8);
            celda.setPaddingBottom(10);

            celda.addElement(new Paragraph(texto(indicador.etiqueta()).toUpperCase(new Locale("es")), KPI_ETIQUETA));
            Paragraph valor = new Paragraph(texto(indicador.valor()), KPI_VALOR);
            valor.setSpacingBefore(2);
            celda.addElement(valor);
            if (indicador.detalle() != null && !indicador.detalle().isBlank()) {
                Font fuente = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, colorTono(indicador.tono(), GRIS_TEXTO));
                celda.addElement(new Paragraph(texto(indicador.detalle()), fuente));
            }
            tabla.addCell(celda);
        }

        // Completar la última fila para que no quede una celda sin borde
        int sobrantes = (columnas - lista.size() % columnas) % columnas;
        for (int i = 0; i < sobrantes; i++) {
            PdfPCell vacia = new PdfPCell(new Phrase(""));
            vacia.setBorder(Rectangle.NO_BORDER);
            tabla.addCell(vacia);
        }
        return tabla;
    }

    // ---------- Secciones (gráfica + explicación + tabla) ----------

    private void seccion(Document documento, Seccion seccion) throws DocumentException, IOException {
        // Título, descripción, gráfica y explicación van en un solo bloque:
        // si no caben en lo que queda de la página, pasan juntos a la
        // siguiente (así el título nunca queda separado de su gráfica, ni la
        // gráfica de su explicación).
        PdfPTable bloque = new PdfPTable(1);
        bloque.setWidthPercentage(100);
        bloque.setSpacingBefore(14);
        bloque.setKeepTogether(true);

        PdfPCell celda = new PdfPCell();
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPadding(0);

        celda.addElement(new Paragraph(texto(seccion.titulo()), SECCION));
        if (seccion.descripcion() != null && !seccion.descripcion().isBlank()) {
            celda.addElement(new Paragraph(texto(seccion.descripcion()), SUBTITULO));
        }

        Image imagen = imagen(seccion.imagen());
        if (imagen != null) {
            float ancho = documento.getPageSize().getWidth() - documento.leftMargin() - documento.rightMargin();
            imagen.scaleToFit(ancho, 300);
            imagen.setAlignment(Element.ALIGN_CENTER);
            imagen.setSpacingBefore(6);
            celda.addElement(imagen);
        }

        if (seccion.explicacion() != null && !seccion.explicacion().isBlank()) {
            Paragraph explicacion = new Paragraph(texto(seccion.explicacion()), EXPLICACION);
            explicacion.setAlignment(Element.ALIGN_JUSTIFIED);
            explicacion.setLeading(13.5f);
            explicacion.setSpacingBefore(8);
            celda.addElement(explicacion);
        }

        bloque.addCell(celda);
        documento.add(bloque);

        Tabla datos = seccion.tabla();
        if (datos != null && datos.columnas() != null && !datos.columnas().isEmpty()) {
            documento.add(tabla(datos));
        }
    }

    private PdfPTable tabla(Tabla datos) {
        PdfPTable tabla = new PdfPTable(datos.columnas().size());
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(8);
        tabla.setHeaderRows(1); // el encabezado se repite si la tabla cambia de página

        for (String columna : datos.columnas()) {
            PdfPCell celda = new PdfPCell(new Phrase(texto(columna), TABLA_ENCABEZADO));
            celda.setBackgroundColor(FONDO);
            celda.setBorderColor(BORDE);
            celda.setPadding(5);
            tabla.addCell(celda);
        }

        if (datos.filas() == null || datos.filas().isEmpty()) {
            PdfPCell vacia = new PdfPCell(new Phrase("Sin datos", TABLA_CELDA));
            vacia.setColspan(datos.columnas().size());
            vacia.setBorderColor(BORDE);
            vacia.setPadding(5);
            tabla.addCell(vacia);
            return tabla;
        }

        for (List<String> fila : datos.filas()) {
            for (int i = 0; i < datos.columnas().size(); i++) {
                String valor = fila != null && i < fila.size() ? fila.get(i) : "";
                PdfPCell celda = new PdfPCell(new Phrase(texto(valor), TABLA_CELDA));
                celda.setBorderColor(BORDE);
                celda.setPadding(5);
                // Los números (y "120/80", "98 %", "—") a la derecha; el texto, a la izquierda
                if (i > 0 && esNumerico(texto(valor))) {
                    celda.setHorizontalAlignment(Element.ALIGN_RIGHT);
                }
                tabla.addCell(celda);
            }
        }
        return tabla;
    }

    /** Imagen PNG en base64 ("data:image/png;base64,..." o solo el base64). */
    private Image imagen(String dataUrl) throws IOException {
        if (dataUrl == null || dataUrl.isBlank()) {
            return null;
        }
        String base64 = dataUrl.contains(",") ? dataUrl.substring(dataUrl.indexOf(',') + 1) : dataUrl;
        try {
            return Image.getInstance(Base64.getDecoder().decode(base64));
        } catch (IllegalArgumentException | DocumentException e) {
            return null; // imagen inválida: se omite y queda la tabla
        }
    }

    // ---------- Utilidades ----------

    /** Valor sin letras: cifras, porcentajes, presiones ("120/80") o "—". */
    private static boolean esNumerico(String valor) {
        return valor != null && !valor.isBlank() && valor.matches("[\\d\\s.,/%()!+\\-—–°]+");
    }

    private Color colorTono(String tono, Color porDefecto) {
        if ("alerta".equals(tono)) return ROJO;
        if ("bueno".equals(tono)) return VERDE;
        return porDefecto;
    }

    /**
     * Las fuentes estándar del PDF (Helvetica, Latin-1) no tienen algunos
     * símbolos que usa la web: se reemplazan por equivalentes.
     */
    private static String texto(String valor) {
        if (valor == null) {
            return "";
        }
        return valor
                .replace("⚠", "(!)")
                .replace("✕", "x")
                .replace("≥", ">=")
                .replace("≤", "<=")
                // Espacios especiales de las fechas en español ("5:08 p. m.")
                .replace(' ', ' ')
                .replace(' ', ' ');
    }

    /** Pie de página con el número de página. */
    private static class PiePagina extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document documento) {
            Phrase pie = new Phrase("VITA+  ·  Reporte de analítica  ·  Página " + writer.getPageNumber(),
                    new Font(Font.HELVETICA, 8, Font.NORMAL, GRIS_TENUE));
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER, pie,
                    (documento.left() + documento.right()) / 2, documento.bottom() - 25, 0);
        }
    }
}
