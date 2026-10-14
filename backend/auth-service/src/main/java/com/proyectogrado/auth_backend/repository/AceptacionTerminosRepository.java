package com.proyectogrado.auth_backend.repository;

import com.proyectogrado.auth_backend.model.AceptacionTerminos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Acceso a la tabla aceptacion_terminos (ver AceptacionTerminos).
 */
public interface AceptacionTerminosRepository extends JpaRepository<AceptacionTerminos, Integer> {

    /**
     * Crea la constancia REGISTRO_ANTERIOR de las cuentas que aún no tienen
     * una, con la fecha de creación de la cuenta. Devuelve cuántas creó.
     */
    @Modifying
    @Query(value = """
            INSERT INTO aceptacion_terminos (id_usuario, acepto, fecha_aceptacion, version_terminos, origen)
            SELECT u.id_usuario, TRUE, COALESCE(u.fecha_creacion, CURRENT_TIMESTAMP), NULL, 'REGISTRO_ANTERIOR'
              FROM usuario u
             WHERE NOT EXISTS (SELECT 1 FROM aceptacion_terminos a WHERE a.id_usuario = u.id_usuario)
            """, nativeQuery = true)
    int registrarCuentasAnteriores();
}
