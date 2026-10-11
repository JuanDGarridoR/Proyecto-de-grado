package com.proyectogrado.organizacion_service.dto;

import java.time.LocalDateTime;

/**
 * Persona mayor que eliminó su cuenta. razon es un código (FALLECIMIENTO,
 * SALUD...); el frontend muestra su texto.
 */
public record RetiroResponse(
        String nombre,
        String razon,
        String comentario,
        LocalDateTime fecha
) {
}
