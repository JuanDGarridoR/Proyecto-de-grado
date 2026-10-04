package com.proyectogrado.analitica_service.dto;

import java.util.List;

/**
 * Contenido de un reporte para exportarlo a PDF. El frontend envía lo que
 * se ve en pantalla: los indicadores y, por cada sección, la imagen de la
 * gráfica (PNG en base64, como la genera ECharts) y su tabla de datos.
 */
public record ReportePdfRequest(
        String titulo,              // "Salud de la población"
        String descripcion,
        String periodo,             // "Últimos 90 días" o null si no aplica
        List<Indicador> indicadores,
        List<Seccion> secciones,
        String nota                 // aclaración al final (puede ser null)
) {

    /** Tarjeta de indicador. tono: "neutro", "bueno" o "alerta". */
    public record Indicador(String etiqueta, String valor, String detalle, String tono) {
    }

    /**
     * Una gráfica (o solo una tabla, si imagen es null) con su título.
     * explicacion es un párrafo que la interpreta (puede ser null).
     */
    public record Seccion(String titulo, String descripcion, String imagen, String explicacion, Tabla tabla) {
    }

    public record Tabla(List<String> columnas, List<List<String>> filas) {
    }
}
