package com.proyectogrado.auth_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Antes el parentesco se guardaba en acompanante.relacion, uno por
 * acompañante. Pero depende del vínculo y no del acompañante (puede ser hijo
 * de una persona mayor y sobrino de otra), así que ahora va solo en
 * persona_mayor_acompanante.relacion.
 *
 * Al arrancar, si la columna vieja todavía existe, copia su valor a los
 * vínculos que no tienen parentesco y la borra. Después de la primera vez no
 * hace nada. Hibernate (ddl-auto=update) no borra columnas, por eso se hace
 * aquí.
 */
@Component
public class ParentescoAcompananteMigracion {

    private static final Logger log = LoggerFactory.getLogger(ParentescoAcompananteMigracion.class);

    private final JdbcTemplate jdbc;

    public ParentescoAcompananteMigracion(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void migrar() {
        if (!existeColumna("acompanante", "relacion")) {
            return;
        }

        int copiados = 0;
        if (existeColumna("persona_mayor_acompanante", "relacion")) {
            copiados = jdbc.update("""
                    UPDATE persona_mayor_acompanante
                       SET relacion = (SELECT a.relacion FROM acompanante a
                                        WHERE a.id_usuario = persona_mayor_acompanante.id_acompanante)
                     WHERE relacion IS NULL
                       AND id_acompanante IN (SELECT id_usuario FROM acompanante
                                               WHERE relacion IS NOT NULL)
                    """);
        }

        // IF EXISTS: con varias réplicas, otra puede haberla borrado ya.
        jdbc.execute("ALTER TABLE acompanante DROP COLUMN IF EXISTS relacion");
        log.info("Parentesco movido de acompanante a {} vínculos; columna acompanante.relacion borrada", copiados);
    }

    private boolean existeColumna(String tabla, String columna) {
        Integer cantidad = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                 WHERE table_schema = current_schema()
                   AND table_name = ? AND column_name = ?
                """, Integer.class, tabla, columna);
        return cantidad != null && cantidad > 0;
    }
}
