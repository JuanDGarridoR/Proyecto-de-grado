package com.proyectogrado.analitica_service.lugares;

/**
 * Lugar que encontró la búsqueda: su nombre ("Parque La Flora"), la
 * dirección completa, dónde queda en el mapa (grados WGS84) y el barrio
 * que le asigna Google (null si solo dio la localidad).
 */
public record LugarEncontrado(
        String nombre,
        String direccion,
        double latitud,
        double longitud,
        String barrio
) {

    /** Lugar sin barrio. */
    public LugarEncontrado(String nombre, String direccion, double latitud, double longitud) {
        this(nombre, direccion, latitud, longitud, null);
    }
}
