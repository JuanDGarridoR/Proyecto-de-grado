package com.proyectogrado.donacion_service.repository;

import com.proyectogrado.donacion_service.model.OrganizacionLookup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Lectura de la tabla organizacion y de los vínculos que deciden a qué
 * organizaciones puede donar cada quien. Las tablas de vínculos son de
 * voluntario-service, acompanante-service y persona-mayor-service; aquí solo
 * se leen.
 */
public interface OrganizacionLookupRepository extends JpaRepository<OrganizacionLookup, Integer> {

    /** Organizaciones donde el voluntario está vinculado (vínculo ACEPTADA). */
    @Query(value = """
            select vo.id_organizacion from voluntario_organizacion vo
            where vo.id_voluntario = :idVoluntario and vo.estado = 'ACEPTADA'
            """, nativeQuery = true)
    List<Integer> idsVinculadasAVoluntario(@Param("idVoluntario") Integer idVoluntario);

    /**
     * Organizaciones de las personas mayores que el acompañante acompaña:
     * ambos vínculos (acompañante-persona mayor y persona mayor-organización)
     * deben estar ACEPTADA.
     */
    @Query(value = """
            select distinct po.id_organizacion from persona_mayor_acompanante pa
            join persona_mayor_organizacion po on po.id_persona_mayor = pa.id_persona_mayor
            where pa.id_acompanante = :idAcompanante
              and pa.estado = 'ACEPTADA' and po.estado = 'ACEPTADA'
            """, nativeQuery = true)
    List<Integer> idsVinculadasAAcompanante(@Param("idAcompanante") Integer idAcompanante);
}
