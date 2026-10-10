package com.proyectogrado.persona_mayor_service.repository;

import com.proyectogrado.persona_mayor_service.model.OrganizacionLookup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Lectura de las organizaciones registradas.
 */
public interface OrganizacionLookupRepository extends JpaRepository<OrganizacionLookup, Integer> {

    /** Organizaciones con al menos una cuenta activa (las inactivas no se muestran), por nombre. */
    @Query("""
            SELECT o FROM OrganizacionLookup o
             WHERE EXISTS (SELECT u FROM UsuarioLookup u
                            WHERE u.idOrganizacion = o.idOrganizacion
                              AND (u.activo IS NULL OR u.activo = TRUE))
             ORDER BY o.nombre
            """)
    List<OrganizacionLookup> findActivasOrderByNombre();
}
