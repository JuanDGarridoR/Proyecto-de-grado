package com.proyectogrado.persona_mayor_service.repository;

import com.proyectogrado.persona_mayor_service.model.PersonaMayorLookup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Acceso a la tabla persona_mayor (ver PersonaMayorLookup).
 */
public interface PersonaMayorLookupRepository extends JpaRepository<PersonaMayorLookup, Integer> {

    /** Personas mayores que nacieron en ese mes y alguno de esos días (fecha de la tabla usuario). */
    @Query(value = """
            select pm.* from persona_mayor pm
            join usuario u on u.id_usuario = pm.id_usuario
            where u.fecha_nacimiento is not null
              and extract(month from u.fecha_nacimiento) = :mes
              and extract(day from u.fecha_nacimiento) in (:dias)
            """, nativeQuery = true)
    List<PersonaMayorLookup> findCumpleanos(@Param("mes") int mes, @Param("dias") Collection<Integer> dias);
}
