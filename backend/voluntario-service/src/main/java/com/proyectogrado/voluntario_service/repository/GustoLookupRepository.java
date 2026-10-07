package com.proyectogrado.voluntario_service.repository;

import com.proyectogrado.voluntario_service.model.GustoLookup;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Lectura del catálogo de gustos.
 */
public interface GustoLookupRepository extends JpaRepository<GustoLookup, Integer> {
}
