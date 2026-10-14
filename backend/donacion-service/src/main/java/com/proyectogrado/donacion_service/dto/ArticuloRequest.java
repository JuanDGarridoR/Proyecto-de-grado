package com.proyectogrado.donacion_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una cosa que se va a donar. Si la categoría es DINERO, la unidad siempre queda en COP. */
public record ArticuloRequest(
        String categoria,
        String descripcion,
        BigDecimal cantidad,
        String unidad,
        LocalDate fechaVencimiento
) {
}
