package com.proyectogrado.persona_mayor_service.repository;

import com.proyectogrado.persona_mayor_service.model.OrganizacionLookup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Lectura de las organizaciones registradas.
 */
public interface OrganizacionLookupRepository extends JpaRepository<OrganizacionLookup, Integer> {

    List<OrganizacionLookup> findAllByOrderByNombreAsc();
}
