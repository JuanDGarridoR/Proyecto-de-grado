package com.proyectogrado.acompanante_service.dto;

/**
 * Emergencia reciente de una persona mayor que acompaña el usuario.
 * fechaHora va en "yyyy-MM-ddTHH:mm" (hora de Colombia).
 */
public record EmergenciaResponse(
        Integer idEmergencia,
        Integer idPersonaMayor,
        String nombre,
        String fechaHora
) {
}
