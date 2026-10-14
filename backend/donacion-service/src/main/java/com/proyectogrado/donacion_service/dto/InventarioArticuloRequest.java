package com.proyectogrado.donacion_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos de un artículo del inventario. Al crearlo, cantidad es la
 * existencia inicial (queda como entrada con motivoEntrada); al editarlo,
 * cantidad y motivoEntrada se ignoran: la cantidad solo cambia con
 * movimientos.
 */
public record InventarioArticuloRequest(
        String nombre,
        String categoria,
        String unidad,
        BigDecimal cantidad,
        BigDecimal cantidadMinima,
        LocalDate fechaVencimiento,
        String motivoEntrada
) {
}
