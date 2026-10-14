package com.proyectogrado.donacion_service.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Donación tal como la ven el donante y la organización. horaEntrega va en
 * formato HH:mm, o null.
 */
public record DonacionResponse(
        Integer idDonacion,
        String estado,
        LocalDate fechaEntrega,
        String horaEntrega,
        String observaciones,
        String notaOrganizacion,
        LocalDateTime fechaRegistro,
        LocalDateTime fechaRespuesta,
        Integer idOrganizacion,
        String nombreOrganizacion,
        String direccionOrganizacion,
        String fotoOrganizacion,
        Integer idDonante,
        String nombreDonante,
        String celularDonante,
        String fotoDonante,
        String rolDonante,
        List<ArticuloResponse> articulos
) {
}
