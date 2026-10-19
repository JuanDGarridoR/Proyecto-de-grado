package com.proyectogrado.auth_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El parentesco viejo (acompanante.relacion) pasa a los vínculos que no
 * tienen y la columna se borra. Usa una base H2 en memoria, aparte de la de
 * las demás pruebas.
 */
class ParentescoAcompananteMigracionTest {

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:parentesco;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE acompanante (id_usuario INTEGER PRIMARY KEY, relacion VARCHAR(255))");
        jdbc.execute("""
                CREATE TABLE persona_mayor_acompanante (
                    id_persona_mayor INTEGER, id_acompanante INTEGER, relacion VARCHAR(255),
                    PRIMARY KEY (id_persona_mayor, id_acompanante))""");
    }

    private List<String> parentescos() {
        return jdbc.queryForList(
                "SELECT relacion FROM persona_mayor_acompanante ORDER BY id_persona_mayor", String.class);
    }

    private int columnasRelacionEnAcompanante() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                 WHERE table_name = 'acompanante' AND column_name = 'relacion'""", Integer.class);
    }

    @Test
    void copiaElParentescoViejoSoloALosVinculosSinParentescoYBorraLaColumna() {
        jdbc.execute("INSERT INTO acompanante VALUES (20, 'Hijo'), (21, NULL)");
        jdbc.execute("""
                INSERT INTO persona_mayor_acompanante VALUES
                    (1, 20, NULL),
                    (2, 20, 'Sobrino'),
                    (3, 21, NULL)""");

        new ParentescoAcompananteMigracion(jdbc).migrar();

        assertEquals(Arrays.asList("Hijo", "Sobrino", null), parentescos());
        assertEquals(0, columnasRelacionEnAcompanante());
    }

    @Test
    void siYaSeMigroNoHaceNada() {
        jdbc.execute("ALTER TABLE acompanante DROP COLUMN relacion");
        jdbc.execute("INSERT INTO persona_mayor_acompanante VALUES (1, 20, NULL)");

        new ParentescoAcompananteMigracion(jdbc).migrar();

        assertEquals(Arrays.asList((String) null), parentescos());
    }
}
