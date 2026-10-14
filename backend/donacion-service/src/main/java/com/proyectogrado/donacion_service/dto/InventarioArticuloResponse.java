package com.proyectogrado.donacion_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Artículo del inventario con su existencia actual. */
public record InventarioArticuloResponse(
        Integer idInventario,
        String nombre,
        String categoria,
        String unidad,
        BigDecimal cantidad,
        BigDecimal cantidadMinima,
        LocalDate fechaVencimiento,
        LocalDateTime fechaActualizacion
) {
}
