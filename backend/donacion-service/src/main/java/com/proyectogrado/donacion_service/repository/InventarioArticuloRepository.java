package com.proyectogrado.donacion_service.repository;

import com.proyectogrado.donacion_service.model.InventarioArticulo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a la tabla inventario_articulo (ver InventarioArticulo).
 */
public interface InventarioArticuloRepository extends JpaRepository<InventarioArticulo, Integer> {

    List<InventarioArticulo> findByIdOrganizacionOrderByCategoriaAscNombreAsc(Integer idOrganizacion);

    /**
     * El artículo de la organización con ese nombre, categoría y unidad (sin
     * distinguir mayúsculas). Así una donación de "arroz" en kg se suma al
     * "Arroz" en kg que ya existe.
     */
    Optional<InventarioArticulo> findFirstByIdOrganizacionAndCategoriaAndNombreIgnoreCaseAndUnidadIgnoreCase(
            Integer idOrganizacion, String categoria, String nombre, String unidad);
}
