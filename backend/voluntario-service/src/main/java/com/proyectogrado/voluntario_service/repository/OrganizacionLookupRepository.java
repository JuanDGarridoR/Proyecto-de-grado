package com.proyectogrado.voluntario_service.repository;

import com.proyectogrado.voluntario_service.model.OrganizacionLookup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Lectura de la tabla organizacion (ver OrganizacionLookup).
 */
public interface OrganizacionLookupRepository extends JpaRepository<OrganizacionLookup, Integer> {

    /** Organizaciones con al menos una cuenta activa (las inactivas no se muestran). */
    @Query("""
            SELECT o FROM OrganizacionLookup o
             WHERE EXISTS (SELECT u FROM UsuarioLookup u
                            WHERE u.idOrganizacion = o.idOrganizacion
                              AND (u.activo IS NULL OR u.activo = TRUE))
            """)
    List<OrganizacionLookup> findActivas();
}
