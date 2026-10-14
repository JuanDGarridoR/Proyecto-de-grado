package com.proyectogrado.donacion_service.repository;

import com.proyectogrado.donacion_service.model.InventarioMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Acceso a la tabla inventario_movimiento (ver InventarioMovimiento).
 */
public interface InventarioMovimientoRepository extends JpaRepository<InventarioMovimiento, Integer> {

    /** Historial de un artículo, lo más reciente primero. */
    List<InventarioMovimiento> findTop100ByIdInventarioOrderByFechaDesc(Integer idInventario);

    /** Al eliminar un artículo se borra también su historial. */
    @Transactional
    void deleteByIdInventario(Integer idInventario);
}
