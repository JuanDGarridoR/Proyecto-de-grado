package com.proyectogrado.voluntario_service.repository;

import com.proyectogrado.voluntario_service.model.VoluntarioGusto;
import com.proyectogrado.voluntario_service.model.VoluntarioGustoId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Gustos marcados por los voluntarios.
 */
public interface VoluntarioGustoRepository extends JpaRepository<VoluntarioGusto, VoluntarioGustoId> {

    List<VoluntarioGusto> findById_IdVoluntario(Integer idVoluntario);
}
