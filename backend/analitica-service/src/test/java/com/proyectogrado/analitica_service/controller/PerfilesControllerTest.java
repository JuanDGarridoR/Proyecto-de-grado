package com.proyectogrado.analitica_service.controller;

import com.proyectogrado.analitica_service.perfiles.DatosPerfiles;
import com.proyectogrado.analitica_service.perfiles.SegmentacionPerfiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perfiles con K-means sobre H2 en modo PostgreSQL, con las consultas
 * reales. Hay dos poblaciones bien distintas de 8 personas cada una:
 *
 * - "Autónomas" (ids 100-107): 66-69 años, acompañadas, sin enfermedades
 *   ni medicamentos y con signos vitales normales.
 * - "Frágiles" (ids 200-207): 86-89 años, viven solas, sin acompañantes,
 *   con 3 enfermedades, 5 medicamentos, signos fuera de rango, citas que
 *   piden acompañamiento y emergencias.
 *
 * Más una persona sin datos (excluida), una de otra organización y una
 * con el vínculo pendiente (no entran).
 */
class PerfilesControllerTest {

    private static final int CUENTA_ORGANIZACION = 30;
    private static final int ORGANIZACION = 1;
    private static final int ACOMPANANTE_1 = 500;
    private static final int ACOMPANANTE_2 = 501;

    private JdbcTemplate jdbc;
    private MockMvc mockMvc;
    private LocalDate hoy;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:perfiles-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        ResourceDatabasePopulator scripts = new ResourceDatabasePopulator(new ClassPathResource("perfiles/esquema.sql"));
        scripts.setSqlScriptEncoding("UTF-8");
        scripts.execute(dataSource);

        jdbc = new JdbcTemplate(dataSource);
        hoy = LocalDate.now();
        cargarDatos();

        NamedParameterJdbcTemplate consultas = new NamedParameterJdbcTemplate(dataSource);
        mockMvc = MockMvcBuilders.standaloneSetup(new PerfilesController(new OrganizacionActual(consultas),
                new SegmentacionPerfiles(new DatosPerfiles(consultas)))).build();
    }

    private void cargarDatos() {
        jdbc.update("INSERT INTO usuario (id_usuario, nombre_usuario, id_organizacion) VALUES (?, ?, ?)",
                CUENTA_ORGANIZACION, "Fundación Entrenubes", ORGANIZACION);
        jdbc.update("INSERT INTO usuario (id_usuario, nombre_usuario, activo) VALUES (?, 'Carmen', TRUE), (?, 'Luis', TRUE)",
                ACOMPANANTE_1, ACOMPANANTE_2);
        jdbc.update("""
                INSERT INTO condicion_salud VALUES
                  (1, 'ENFERMEDAD', 'Hipertensión arterial'), (2, 'ENFERMEDAD', 'Diabetes'),
                  (3, 'ENFERMEDAD', 'EPOC'), (4, 'ALERGIA', 'Penicilina')
                """);
        jdbc.update("INSERT INTO gusto VALUES (1, 'Baile', 'PASATIEMPO'), (2, 'Tejido', 'TALENTO'), (3, 'Docente', 'PROFESION')");
        jdbc.update("INSERT INTO actividad VALUES (900, ?)", ORGANIZACION);

        for (int i = 0; i < 8; i++) {
            int id = 100 + i;
            persona(id, "Autónoma " + i, 66 + i % 4, false, ORGANIZACION, "ACEPTADA");
            jdbc.update("INSERT INTO persona_mayor_acompanante VALUES (?, ?, 'ACEPTADA'), (?, ?, 'ACEPTADA')",
                    id, ACOMPANANTE_1, id, ACOMPANANTE_2);
            // Una alergia no cuenta como enfermedad
            jdbc.update("INSERT INTO persona_mayor_condicion_salud (id_persona_mayor, id_condicion_salud) VALUES (?, 4)", id);
            for (int m = 0; m < 3; m++) {
                signos(id, 118 + i % 4, 78, 72, 97);
            }
            cita(id, "NO");
            jdbc.update("INSERT INTO persona_mayor_gusto VALUES (?, 1)", id);
            jdbc.update("INSERT INTO participacion VALUES (?, 900, TRUE)", id);
        }

        for (int i = 0; i < 8; i++) {
            int id = 200 + i;
            persona(id, "Frágil " + i, 86 + i % 4, true, ORGANIZACION, "ACEPTADA");
            for (int c = 1; c <= 3; c++) {
                jdbc.update("INSERT INTO persona_mayor_condicion_salud (id_persona_mayor, id_condicion_salud, severidad) "
                        + "VALUES (?, ?, ?)", id, c, c == 1 ? "SEVERA" : "MODERADA");
            }
            for (int m = 0; m < 5; m++) {
                jdbc.update("INSERT INTO medicamento (id_persona_mayor, activo) VALUES (?, TRUE)", id);
            }
            for (int m = 0; m < 3; m++) {
                signos(id, 165 + i % 4, 95, 104, 90);
            }
            cita(id, "REQUERIDO");
            jdbc.update("INSERT INTO emergencia (id_persona_mayor, fecha_hora) VALUES (?, ?), (?, ?)",
                    id, Timestamp.valueOf(LocalDateTime.now().minusDays(5)),
                    id, Timestamp.valueOf(LocalDateTime.now().minusDays(20)));
            jdbc.update("INSERT INTO persona_mayor_gusto VALUES (?, 2), (?, 3)", id, id);
            jdbc.update("INSERT INTO participacion VALUES (?, 900, FALSE)", id);
        }

        // Datos de prueba: el script de datos ficticios usa correos @vitaplus.test
        jdbc.update("UPDATE usuario SET correo = 'pm100@vitaplus.test' WHERE id_usuario = 100");

        // Sin fecha de nacimiento ni signos vitales: no entra al análisis
        jdbc.update("INSERT INTO usuario (id_usuario, nombre_usuario, activo) VALUES (300, 'Sin Datos', TRUE)");
        jdbc.update("INSERT INTO persona_mayor VALUES (300, NULL)");
        jdbc.update("INSERT INTO persona_mayor_organizacion VALUES (300, ?, 'ACEPTADA')", ORGANIZACION);

        // No entran: otra organización y vínculo pendiente
        persona(400, "De Otra Organización", 70, false, 2, "ACEPTADA");
        signos(400, 120, 80, 70, 97);
        persona(401, "Pendiente", 70, false, ORGANIZACION, "PENDIENTE");
        signos(401, 120, 80, 70, 97);
    }

    private void persona(int id, String nombre, int edad, boolean viveSolo, int organizacion, String estado) {
        jdbc.update("INSERT INTO usuario (id_usuario, nombre_usuario, fecha_nacimiento, activo) VALUES (?, ?, ?, TRUE)",
                id, nombre, Date.valueOf(hoy.minusYears(edad).minusDays(10)));
        jdbc.update("INSERT INTO persona_mayor VALUES (?, ?)", id, viveSolo);
        jdbc.update("INSERT INTO persona_mayor_organizacion VALUES (?, ?, ?)", id, organizacion, estado);
    }

    private void signos(int id, int sistolica, int diastolica, int pulso, int saturacion) {
        jdbc.update("""
                INSERT INTO signo_vital (id_persona_mayor, fecha_hora, presion_sistolica, presion_diastolica,
                                         frecuencia_cardiaca, temperatura, saturacion_oxigeno)
                VALUES (?, ?, ?, ?, ?, 36.6, ?)
                """, id, Timestamp.valueOf(LocalDateTime.now().minusDays(10)), sistolica, diastolica, pulso, saturacion);
    }

    private void cita(int id, String acompanamiento) {
        jdbc.update("INSERT INTO cita_medica (id_persona_mayor, fecha, acompanamiento) VALUES (?, ?, ?)",
                id, Date.valueOf(hoy.minusDays(30)), acompanamiento);
    }

    @Test
    void evaluaLaCalidadDeLosDatosAntesDeEntrenar() throws Exception {
        mockMvc.perform(get("/api/analitica/perfiles").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LISTO"))
                // Sin la de otra organización ni la pendiente
                .andExpect(jsonPath("$.calidad.personasAsociadas").value(17))
                .andExpect(jsonPath("$.calidad.personasIncluidas").value(16))
                .andExpect(jsonPath("$.calidad.registrosDePrueba").value(1))
                .andExpect(jsonPath("$.calidad.nivel").value("LIMITADA"))
                .andExpect(jsonPath("$.excluidas[0].nombre").value("Sin Datos"))
                .andExpect(jsonPath("$.excluidas[0].faltantes", hasItem("Signos vitales de los últimos 180 días")))
                .andExpect(jsonPath("$.advertencias", hasItem(containsString("datos ficticios"))))
                .andExpect(jsonPath("$.advertencias", hasItem(containsString("poco estables"))));
    }

    @Test
    void descartaLasVariablesSinDatosOSinVariacion() throws Exception {
        mockMvc.perform(get("/api/analitica/perfiles").header("X-User-Id", CUENTA_ORGANIZACION))
                // Nadie tiene discapacidades ni peso y estatura
                .andExpect(jsonPath("$.variables[?(@.clave == 'discapacidades')].usada").value(false))
                .andExpect(jsonPath("$.variables[?(@.clave == 'imc')].usada").value(false))
                .andExpect(jsonPath("$.variables[?(@.clave == 'edad')].usada").value(true))
                .andExpect(jsonPath("$.variables[?(@.clave == 'enfermedades')].usada").value(true));
    }

    @Test
    void encuentraLasDosPoblacionesYPoneDePrimeroLaDeMasNecesidad() throws Exception {
        mockMvc.perform(get("/api/analitica/perfiles").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(jsonPath("$.modelo.kSugerido").value(2))
                .andExpect(jsonPath("$.modelo.k").value(2))
                .andExpect(jsonPath("$.modelo.estructura").value("FUERTE"))
                .andExpect(jsonPath("$.modelo.silueta", greaterThan(0.5)))
                .andExpect(jsonPath("$.grupos", hasSize(2)))
                .andExpect(jsonPath("$.grupos[0].personas").value(8))
                .andExpect(jsonPath("$.grupos[0].nombre", containsString("Cuidado de la salud")))
                .andExpect(jsonPath("$.grupos[1].nombre").value("Activos e independientes"))
                .andExpect(jsonPath("$.personas[?(@.grupo == 1)].id",
                        containsInAnyOrder(200, 201, 202, 203, 204, 205, 206, 207)))
                // Intereses y asistencia describen al grupo (sin profesiones)
                .andExpect(jsonPath("$.grupos[0].interesesFrecuentes", containsInAnyOrder("Tejido")))
                .andExpect(jsonPath("$.grupos[0].asistencia").value(0.0))
                .andExpect(jsonPath("$.grupos[1].asistencia").value(100.0));
    }

    @Test
    void laOrganizacionPuedeElegirElNumeroDeGrupos() throws Exception {
        mockMvc.perform(get("/api/analitica/perfiles").param("k", "3").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(jsonPath("$.modelo.k").value(3))
                .andExpect(jsonPath("$.modelo.kElegidoPorUsuario").value(true))
                .andExpect(jsonPath("$.grupos", hasSize(3)));

        // Un k fuera del rango (16 personas: máximo 4) se ignora
        mockMvc.perform(get("/api/analitica/perfiles").param("k", "9").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(jsonPath("$.modelo.kMaximo").value(4))
                .andExpect(jsonPath("$.modelo.k").value(2))
                .andExpect(jsonPath("$.modelo.kElegidoPorUsuario").value(false));
    }

    @Test
    void conPocosDatosNoEntrenaYExplicaPorQue() throws Exception {
        jdbc.update("DELETE FROM persona_mayor_organizacion WHERE id_persona_mayor BETWEEN 200 AND 207");

        mockMvc.perform(get("/api/analitica/perfiles").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(jsonPath("$.estado").value("INSUFICIENTE"))
                .andExpect(jsonPath("$.calidad.nivel").value("INSUFICIENTE"))
                .andExpect(jsonPath("$.modelo").doesNotExist())
                .andExpect(jsonPath("$.grupos", hasSize(0)))
                .andExpect(jsonPath("$.advertencias[0]", containsString("se necesitan al menos 12")));
    }

    @Test
    void soloUnaOrganizacionVeLosPerfiles() throws Exception {
        mockMvc.perform(get("/api/analitica/perfiles").header("X-User-Id", 100))
                .andExpect(status().isForbidden());
    }
}
