package com.proyectogrado.analitica_service.dto;

import java.util.List;

/**
 * Datos crudos de los reportes de donaciones e inventario de la
 * organización. Como en los demás reportes, los indicadores y las gráficas
 * se calculan en el frontend (y allí se filtra por período).
 */
public final class RecursosDtos {

    private RecursosDtos() {
    }

    /** Un artículo de una donación; cantidadRecibida es null hasta que se recibe. */
    public record ArticuloDonado(
            String categoria,
            String descripcion,
            double cantidad,
            String unidad,
            Double cantidadRecibida
    ) {
    }

    /** Una donación: cuándo se entrega, en qué estado está, quién la hace y qué trae. */
    public record DonacionAnalitica(
            Integer id,
            String fechaEntrega,     // YYYY-MM-DD
            String estado,           // PROGRAMADA, RECIBIDA, NO_ENTREGADA o CANCELADA
            String rolDonante,       // ACOMPANANTE o VOLUNTARIO
            String donante,
            List<ArticuloDonado> articulos
    ) {
    }

    public record DonacionesAnalitica(List<DonacionAnalitica> donaciones) {
    }

    /** Un artículo del inventario tal como está hoy. */
    public record ArticuloInventario(
            Integer id,
            String nombre,
            String categoria,
            String unidad,
            double cantidad,
            Double cantidadMinima,
            String fechaVencimiento  // YYYY-MM-DD o null
    ) {
    }

    /** Una entrada o salida del inventario, con su motivo. */
    public record MovimientoInventario(
            Integer idArticulo,
            String tipo,             // ENTRADA o SALIDA
            double cantidad,
            String motivo,
            String fecha             // YYYY-MM-DDTHH:mm
    ) {
    }

    public record InventarioAnalitica(
            List<ArticuloInventario> articulos,
            List<MovimientoInventario> movimientos
    ) {
    }
}
