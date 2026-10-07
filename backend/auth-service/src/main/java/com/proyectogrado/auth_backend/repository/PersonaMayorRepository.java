package com.proyectogrado.auth_backend.repository;

import com.proyectogrado.auth_backend.model.PersonaMayor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a la tabla persona_mayor.
 */
public interface PersonaMayorRepository extends JpaRepository<PersonaMayor, Integer> {

    /**
     * Si el acompañante tiene un vínculo aceptado con la persona mayor. La
     * tabla persona_mayor_acompanante es de persona-mayor-service; aquí solo
     * se consulta con SQL nativo.
     */
    @Query(value = """
            SELECT COUNT(*) > 0
              FROM persona_mayor_acompanante
             WHERE id_persona_mayor = :idPersonaMayor
               AND id_acompanante = :idAcompanante
               AND estado = 'ACEPTADA'
            """, nativeQuery = true)
    boolean esAcompananteAceptado(@Param("idPersonaMayor") Integer idPersonaMayor,
                                  @Param("idAcompanante") Integer idAcompanante);
}
