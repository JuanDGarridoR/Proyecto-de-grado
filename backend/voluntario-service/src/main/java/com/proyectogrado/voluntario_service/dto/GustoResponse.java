package com.proyectogrado.voluntario_service.dto;

/**
 * Gusto del catálogo, con la misma forma que devuelve persona-mayor-service
 * para las personas mayores.
 */
public record GustoResponse(Integer idGusto, String nombre, String categoria) {
}
