package com.proyectogrado.analitica_service.lugares;

/**
 * Lugar que encontró la búsqueda: su nombre ("Parque La Flora"), la
 * dirección completa y dónde queda en el mapa (grados WGS84).
 */
public record LugarEncontrado(
        String nombre,
        String direccion,
        double latitud,
        double longitud
) {
}
