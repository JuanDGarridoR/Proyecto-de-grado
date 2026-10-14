package com.proyectogrado.donacion_service.repository;

import com.proyectogrado.donacion_service.model.UsuarioLookup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Lectura de la tabla usuario (ver UsuarioLookup).
 */
public interface UsuarioLookupRepository extends JpaRepository<UsuarioLookup, Integer> {

    /** true si el usuario tiene el rol indicado (ACOMPANANTE, VOLUNTARIO...). */
    @Query(value = """
            select count(*) > 0 from usuario_rol ur
            join rol r on r.id_rol = ur.id_rol
            where ur.id_usuario = :idUsuario and r.nombre = :rol
            """, nativeQuery = true)
    boolean tieneRol(@Param("idUsuario") Integer idUsuario, @Param("rol") String rol);

    /**
     * Cuentas con rol ORGANIZACION de una organización: de la primera salen
     * su celular y su foto.
     */
    @Query(value = """
            select u.* from usuario u
            join usuario_rol ur on ur.id_usuario = u.id_usuario
            join rol r on r.id_rol = ur.id_rol
            where u.id_organizacion = :idOrganizacion and r.nombre = 'ORGANIZACION'
            order by u.id_usuario
            """, nativeQuery = true)
    List<UsuarioLookup> findCuentasOrganizacion(@Param("idOrganizacion") Integer idOrganizacion);
}
