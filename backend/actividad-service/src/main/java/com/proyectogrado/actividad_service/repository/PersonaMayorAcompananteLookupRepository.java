package com.proyectogrado.actividad_service.repository;

import com.proyectogrado.actividad_service.model.PersonaMayorAcompananteId;
import com.proyectogrado.actividad_service.model.PersonaMayorAcompananteLookup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Lectura de los vínculos entre personas mayores y acompañantes.
 */
public interface PersonaMayorAcompananteLookupRepository
        extends JpaRepository<PersonaMayorAcompananteLookup, PersonaMayorAcompananteId> {

    List<PersonaMayorAcompananteLookup> findById_IdAcompananteAndEstado(Integer idAcompanante, String estado);

    boolean existsById_IdPersonaMayorAndId_IdAcompananteAndEstado(
            Integer idPersonaMayor, Integer idAcompanante, String estado);
}
