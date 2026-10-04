package com.proyectogrado.actividad_service.dto;

import java.time.LocalDate;

/**
 * Actividad propuesta por un voluntario o una persona mayor, con su estado
 * (PENDIENTE, ACEPTADA o RECHAZADA). Quien la propuso ve a qué organización
 * la presentó y la organización ve quién la propuso: idVoluntario o
 * idPersonaMayor (el otro queda en null).
 */
public record PropuestaActividadResponse(
        Integer idActividad,
        Integer idOrganizacion,
        String nombreOrganizacion,
        Integer idVoluntario,
        String nombreVoluntario,
        Integer idPersonaMayor,
        String nombrePersonaMayor,
        String estado,
        String nombre,
        String descripcion,
        LocalDate fecha,
        String hora,
        String lugar,
        String tipo,
        Integer cupos,
        String responsable
) {
}
