package com.proyectogrado.organizacion_service.repository;

import com.proyectogrado.organizacion_service.model.NotaPersonaMayor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acceso a la tabla nota_persona_mayor (ver NotaPersonaMayor).
 */
public interface NotaPersonaMayorRepository extends JpaRepository<NotaPersonaMayor, Integer> {

    /** Notas de la organización sobre una persona mayor, la más reciente primero. */
    List<NotaPersonaMayor> findByIdOrganizacionAndIdPersonaMayorOrderByFechaCreacionDesc(
            Integer idOrganizacion, Integer idPersonaMayor);
}
