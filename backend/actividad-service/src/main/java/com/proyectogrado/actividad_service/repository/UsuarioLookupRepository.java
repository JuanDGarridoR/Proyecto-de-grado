package com.proyectogrado.actividad_service.repository;

import com.proyectogrado.actividad_service.model.UsuarioLookup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Lectura de la tabla usuario (ver UsuarioLookup).
 */
public interface UsuarioLookupRepository extends JpaRepository<UsuarioLookup, Integer> {

    /** true si el usuario tiene el rol indicado (VOLUNTARIO, ORGANIZACION...). */
    @Query(value = """
            select count(*) > 0 from usuario_rol ur
            join rol r on r.id_rol = ur.id_rol
            where ur.id_usuario = :idUsuario and r.nombre = :rol
            """, nativeQuery = true)
    boolean tieneRol(@Param("idUsuario") Integer idUsuario, @Param("rol") String rol);

    /**
     * true si el voluntario está vinculado (ACEPTADA) a la organización. La
     * tabla voluntario_organizacion es de voluntario-service.
     */
    @Query(value = """
            select count(*) > 0 from voluntario_organizacion
            where id_voluntario = :idVoluntario and id_organizacion = :idOrganizacion
              and estado = 'ACEPTADA'
            """, nativeQuery = true)
    boolean voluntarioVinculado(@Param("idVoluntario") Integer idVoluntario,
                                @Param("idOrganizacion") Integer idOrganizacion);

    /** Organizaciones a las que el voluntario está vinculado (ACEPTADA). */
    @Query(value = """
            select id_organizacion from voluntario_organizacion
            where id_voluntario = :idVoluntario and estado = 'ACEPTADA'
            """, nativeQuery = true)
    List<Integer> organizacionesDeVoluntario(@Param("idVoluntario") Integer idVoluntario);
}
