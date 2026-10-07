package com.proyectogrado.salud_backend.repository;

import com.proyectogrado.salud_backend.model.RelacionAcompananteLookup;
import com.proyectogrado.salud_backend.model.RelacionAcompananteId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Lectura de los vínculos entre personas mayores y acompañantes (ver
 * RelacionAcompananteLookup).
 */
public interface RelacionAcompananteLookupRepository
        extends JpaRepository<RelacionAcompananteLookup, RelacionAcompananteId> {

    List<RelacionAcompananteLookup> findById_IdPersonaMayorAndEstado(Integer idPersonaMayor, String estado);

    /** Si el acompañante tiene un vínculo con la persona mayor en ese estado. */
    boolean existsById_IdPersonaMayorAndId_IdAcompananteAndEstado(
            Integer idPersonaMayor, Integer idAcompanante, String estado);
}
