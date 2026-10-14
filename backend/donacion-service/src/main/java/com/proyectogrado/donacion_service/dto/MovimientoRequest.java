package com.proyectogrado.donacion_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entrada o salida manual. fechaVencimiento solo aplica a entradas (por
 * ejemplo, una compra de alimentos).
 */
public record MovimientoRequest(
        String tipo,
        BigDecimal cantidad,
        String motivo,
        String detalle,
        LocalDate fechaVencimiento
) {
}
