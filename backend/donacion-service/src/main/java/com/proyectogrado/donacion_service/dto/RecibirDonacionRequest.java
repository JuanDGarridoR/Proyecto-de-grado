package com.proyectogrado.donacion_service.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que de verdad llegó de cada artículo. Si no se envía (o falta un
 * artículo), se toma la cantidad que anunció el donante. 0 = no llegó.
 */
public record RecibirDonacionRequest(List<CantidadRecibida> articulos) {

    public record CantidadRecibida(Integer idArticulo, BigDecimal cantidad) {
    }
}
