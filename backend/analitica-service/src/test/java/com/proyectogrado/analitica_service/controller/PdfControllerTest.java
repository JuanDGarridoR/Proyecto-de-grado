package com.proyectogrado.analitica_service.controller;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.proyectogrado.analitica_service.pdf.GeneradorPdf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Descarga de los reportes de analítica en PDF (RF-18 y RF-21): el PDF lleva
 * el nombre de la organización, los indicadores, la gráfica y la tabla, y se
 * rechazan los reportes sin título o desproporcionados.
 */
class PdfControllerTest {

    /** PNG de 1 x 1 píxel, como las imágenes que exporta ECharts. */
    private static final String GRAFICA =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrganizacionActual organizacionActual = mock(OrganizacionActual.class);
        when(organizacionActual.de(30)).thenReturn(new OrganizacionActual.Organizacion(5, "Fundación Entrenubes"));

        mockMvc = MockMvcBuilders.standaloneSetup(new PdfController(organizacionActual, new GeneradorPdf())).build();
    }

    private String reporte(String titulo, int indicadores, int filas) {
        String listaIndicadores = String.join(",", Collections.nCopies(indicadores,
                "{\"etiqueta\": \"Valores fuera de rango\", \"valor\": \"1\", \"detalle\": \"Última medición\", \"tono\": \"alerta\"}"));
        String listaFilas = String.join(",", Collections.nCopies(filas, "[\"Rosa Díaz\", \"150/95\"]"));
        return """
                {"titulo": "%s", "descripcion": "Signos vitales de las personas vinculadas",
                 "periodo": "Últimos 90 días", "indicadores": [%s],
                 "secciones": [{"titulo": "Presión arterial", "descripcion": "Última medición por persona",
                                "imagen": "%s", "explicacion": "Una persona tiene la presión alta.",
                                "tabla": {"columnas": ["Persona", "Presión"], "filas": [%s]}}],
                 "nota": "Los rangos son orientativos."}
                """.formatted(titulo, listaIndicadores, GRAFICA, listaFilas);
    }

    @Test
    void elPdfLlevaLaOrganizacionLosIndicadoresYLaTabla() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/analitica/reportes/pdf")
                        .header("X-User-Id", 30)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reporte("Salud de la población", 1, 1)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        String disposicion = resultado.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposicion.startsWith("attachment"), disposicion);
        // Nombre del archivo sin tildes ni espacios.
        assertTrue(disposicion.contains("reporte-salud-de-la-poblacion-"), disposicion);

        byte[] pdf = resultado.getResponse().getContentAsByteArray();
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.US_ASCII));

        PdfReader lector = new PdfReader(pdf);
        String texto = new PdfTextExtractor(lector).getTextFromPage(1);
        lector.close();

        assertTrue(texto.contains("Salud de la población"), texto);
        assertTrue(texto.contains("Fundación Entrenubes"), texto);
        assertTrue(texto.contains("Rosa Díaz"), texto);
        assertTrue(texto.contains("150/95"), texto);
    }

    @Test
    void unReporteSinTituloSeRechaza() throws Exception {
        mockMvc.perform(post("/api/analitica/reportes/pdf")
                        .header("X-User-Id", 30)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reporte(" ", 1, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("El reporte no tiene título"));
    }

    @Test
    void unReporteDesproporcionadoSeRechaza() throws Exception {
        mockMvc.perform(post("/api/analitica/reportes/pdf")
                        .header("X-User-Id", 30)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reporte("Salud de la población", 9, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("El reporte tiene demasiados indicadores"));

        mockMvc.perform(post("/api/analitica/reportes/pdf")
                        .header("X-User-Id", 30)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reporte("Salud de la población", 1, 1001)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Una de las tablas tiene demasiadas filas"));
    }

    @Test
    void soloUnaOrganizacionDescargaReportes() throws Exception {
        mockMvc.perform(post("/api/analitica/reportes/pdf")
                        .header("X-User-Id", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reporte("Salud de la población", 1, 1)))
                .andExpect(status().isForbidden());
    }
}
