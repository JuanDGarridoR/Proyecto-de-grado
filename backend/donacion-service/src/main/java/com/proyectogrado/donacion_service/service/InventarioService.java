package com.proyectogrado.donacion_service.service;

import com.proyectogrado.donacion_service.model.Catalogo;
import com.proyectogrado.donacion_service.model.InventarioArticulo;
import com.proyectogrado.donacion_service.model.InventarioMovimiento;
import com.proyectogrado.donacion_service.repository.InventarioArticuloRepository;
import com.proyectogrado.donacion_service.repository.InventarioMovimientoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Único lugar donde cambia la cantidad de un artículo del inventario: cada
 * cambio queda registrado como un movimiento con su motivo.
 */
@Service
public class InventarioService {

    private final InventarioArticuloRepository articuloRepository;
    private final InventarioMovimientoRepository movimientoRepository;

    public InventarioService(
            InventarioArticuloRepository articuloRepository,
            InventarioMovimientoRepository movimientoRepository
    ) {
        this.articuloRepository = articuloRepository;
        this.movimientoRepository = movimientoRepository;
    }

    /**
     * Entrada de una donación recibida: se suma al artículo de la
     * organización con el mismo nombre, categoría y unidad, o se crea uno.
     */
    public InventarioArticulo entradaPorDonacion(
            Integer idOrganizacion, String nombre, String categoria, String unidad,
            BigDecimal cantidad, LocalDate vencimiento, String detalle, Integer idDonacion, Integer idUsuario
    ) {
        InventarioArticulo articulo = articuloRepository
                .findFirstByIdOrganizacionAndCategoriaAndNombreIgnoreCaseAndUnidadIgnoreCase(
                        idOrganizacion, categoria, nombre, unidad)
                .orElseGet(() -> new InventarioArticulo(idOrganizacion, nombre, categoria, unidad));

        return entrada(articulo, cantidad, vencimiento, Catalogo.MOTIVO_DONACION, detalle, idDonacion, idUsuario);
    }

    /** Suma la cantidad y guarda el movimiento de entrada. */
    public InventarioArticulo entrada(
            InventarioArticulo articulo, BigDecimal cantidad, LocalDate vencimiento,
            String motivo, String detalle, Integer idDonacion, Integer idUsuario
    ) {
        articulo.sumar(cantidad, vencimiento);
        InventarioArticulo guardado = articuloRepository.save(articulo);

        movimientoRepository.save(new InventarioMovimiento(
                guardado.getIdInventario(), Catalogo.ENTRADA, cantidad, motivo, detalle, idDonacion, idUsuario));

        return guardado;
    }

    /** Resta la cantidad y guarda el movimiento de salida. Quien llama revisa que alcance. */
    public InventarioArticulo salida(
            InventarioArticulo articulo, BigDecimal cantidad, String motivo, String detalle, Integer idUsuario
    ) {
        articulo.restar(cantidad);
        InventarioArticulo guardado = articuloRepository.save(articulo);

        movimientoRepository.save(new InventarioMovimiento(
                guardado.getIdInventario(), Catalogo.SALIDA, cantidad, motivo, detalle, null, idUsuario));

        return guardado;
    }
}
