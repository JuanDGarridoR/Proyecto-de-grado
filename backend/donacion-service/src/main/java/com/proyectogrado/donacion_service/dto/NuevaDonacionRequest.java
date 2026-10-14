package com.proyectogrado.donacion_service.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Donación que programa un acompañante o voluntario. horaEntrega es
 * opcional, en formato HH:mm.
 */
public record NuevaDonacionRequest(
        Integer idOrganizacion,
        LocalDate fechaEntrega,
        String horaEntrega,
        String observaciones,
        List<ArticuloRequest> articulos
) {
}
