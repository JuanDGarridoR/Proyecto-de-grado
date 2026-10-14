package com.proyectogrado.donacion_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Un artículo de una donación; cantidadRecibida es null hasta que la organización la recibe. */
public record ArticuloResponse(
        Integer idArticulo,
        String categoria,
        String descripcion,
        BigDecimal cantidad,
        String unidad,
        LocalDate fechaVencimiento,
        BigDecimal cantidadRecibida
) {
}
