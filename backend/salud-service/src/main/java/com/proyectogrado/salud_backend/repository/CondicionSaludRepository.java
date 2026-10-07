package com.proyectogrado.salud_backend.repository;

import com.proyectogrado.salud_backend.model.CondicionSalud;
import com.proyectogrado.salud_backend.model.TipoCondicionSalud;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acceso al catálogo de enfermedades, alergias y discapacidades.
 */
public interface CondicionSaludRepository extends JpaRepository<CondicionSalud, Integer> {

    /** Catálogo en el orden en que se muestra: tipo, categoría y nombre. */
    List<CondicionSalud> findAllByOrderByTipoAscCategoriaAscNombreAsc();

    boolean existsByTipoAndNombre(TipoCondicionSalud tipo, String nombre);
}
