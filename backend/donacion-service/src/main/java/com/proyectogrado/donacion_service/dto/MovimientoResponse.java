package com.proyectogrado.donacion_service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Un movimiento del historial de un artículo, con quién lo registró. */
public record MovimientoResponse(
        Integer idMovimiento,
        String tipo,
        BigDecimal cantidad,
        String motivo,
        String detalle,
        Integer idDonacion,
        String registradoPor,
        LocalDateTime fecha
) {
}
