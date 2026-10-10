package com.proyectogrado.analitica_service.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Datos de los reportes de analítica (RF-18 a RF-22 y RF-33). Las consultas
 * SQL reales corren sobre una base H2 en memoria, en modo PostgreSQL, con
 * los datos ficticios de analitica/datos.sql: se comprueba qué personas y
 * actividades entran en cada reporte, los conteos y los filtros por fecha.
 */
class AnaliticaControllerTest {

    private static final int CUENTA_ORGANIZACION = 30;
    private static final int CUENTA_OTRA_ORGANIZACION = 31;

    private JdbcTemplate jdbc;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Una base nueva por prueba: lo que cambia una no afecta a las demás.
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:analitica-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        ResourceDatabasePopulator scripts = new ResourceDatabasePopulator(
                new ClassPathResource("analitica/esquema.sql"), new ClassPathResource("analitica/datos.sql"));
        scripts.setSqlScriptEncoding("UTF-8");
        scripts.execute(dataSource);

        jdbc = new JdbcTemplate(dataSource);
        NamedParameterJdbcTemplate consultas = new NamedParameterJdbcTemplate(dataSource);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AnaliticaController(consultas, new OrganizacionActual(consultas))).build();
    }

    @Test
    void elReporteDePoblacionIncluyeSoloLasPersonasVinculadas() throws Exception {
        mockMvc.perform(get("/api/analitica/poblacion").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isOk())
                // Sin Marta (pendiente), Ana (rechazó) ni Pedro (otra organización); por nombre.
                .andExpect(jsonPath("$.personas[*].nombre", contains("Luis Gómez", "Rosa Díaz")))
                .andExpect(jsonPath("$.personas[0].fechaNacimiento").value("1944-07-01"))
                // EPS en blanco: no se reporta.
                .andExpect(jsonPath("$.personas[0].eps").doesNotExist())
                .andExpect(jsonPath("$.personas[1].genero").value("Femenino"))
                .andExpect(jsonPath("$.personas[1].eps").value("Capital Salud"))
                .andExpect(jsonPath("$.intereses[*].nombre", contains("Boleros", "Tejer")))
                .andExpect(jsonPath("$.intereses[0].personas").value(2))
                .andExpect(jsonPath("$.personasConIntereses").value(2))
                // Acompañantes distintos y aceptados de sus personas (20 y 21).
                .andExpect(jsonPath("$.acompanantesActivos").value(2));
    }

    @Test
    void sinFiltrosElReporteDeActividadesTraeTodasLasVisiblesConFecha() throws Exception {
        mockMvc.perform(get("/api/analitica/actividades").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isOk())
                // Sin propuestas pendientes o rechazadas, sin la que no tiene fecha ni la de otra organización.
                .andExpect(jsonPath("$[*].nombre", contains("Yoga en el parque", "Taller de memoria")))
                .andExpect(jsonPath("$[0].fecha").value("2026-09-10"))
                .andExpect(jsonPath("$[0].cupos").value(20));
    }

    @Test
    void losIndicadoresDeParticipacionSeCalculanBien() throws Exception {
        mockMvc.perform(get("/api/analitica/actividades").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(jsonPath("$[0].inscritos").value(3))
                .andExpect(jsonPath("$[0].asistentes").value(1))
                // A Marta no le han tomado asistencia.
                .andExpect(jsonPath("$[0].conRegistro").value(2))
                .andExpect(jsonPath("$[1].inscritos").value(1))
                .andExpect(jsonPath("$[1].asistentes").value(1));
    }

    @Test
    void elFiltroPorFechasDejaSoloLasActividadesDelPeriodo() throws Exception {
        mockMvc.perform(get("/api/analitica/actividades")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .param("desde", "2026-09-20")
                        .param("hasta", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", contains("Taller de memoria")));

        // Al quitar el filtro vuelven a aparecer todas.
        mockMvc.perform(get("/api/analitica/actividades").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void losIndicadoresReflejanLaAsistenciaRecienRegistrada() throws Exception {
        // Lo que hace actividad-service al registrar la asistencia de Luis y una nueva inscripción.
        jdbc.update("UPDATE participacion SET asistio = TRUE WHERE id_persona_mayor = 11 AND id_actividad = 100");
        jdbc.update("INSERT INTO participacion VALUES (11, 101, NULL)");

        mockMvc.perform(get("/api/analitica/actividades").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(jsonPath("$[0].asistentes").value(2))
                .andExpect(jsonPath("$[1].inscritos").value(2))
                .andExpect(jsonPath("$[1].conRegistro").value(1));
    }

    @Test
    void elReporteDeSaludTraeLasMedicionesDeLasPersonasVinculadasEnOrden() throws Exception {
        mockMvc.perform(get("/api/analitica/salud").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personas[*].nombre", contains("Luis Gómez", "Rosa Díaz")))
                .andExpect(jsonPath("$.mediciones[*].fechaHora",
                        contains("2026-09-01T08:00", "2026-09-15T09:30", "2026-09-20T08:00")))
                .andExpect(jsonPath("$.mediciones[2].presionSistolica").value(150))
                .andExpect(jsonPath("$.mediciones[1].saturacionOxigeno").value(92))
                // Lo que no se midió llega vacío, no en cero.
                .andExpect(jsonPath("$.mediciones[1].presionSistolica").doesNotExist());
    }

    @Test
    void unaNuevaMedicionApareceEnElReporteDeSalud() throws Exception {
        jdbc.update("INSERT INTO signo_vital (id_signo_vital, id_persona_mayor, fecha_hora, saturacion_oxigeno)"
                + " VALUES (6, 11, '2026-10-01 07:00:00', 95)");

        mockMvc.perform(get("/api/analitica/salud").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(jsonPath("$.mediciones.length()").value(4))
                .andExpect(jsonPath("$.mediciones[3].fechaHora").value("2026-10-01T07:00"));
    }

    @Test
    void cadaOrganizacionVeSoloSusDatos() throws Exception {
        mockMvc.perform(get("/api/analitica/actividades").header("X-User-Id", CUENTA_OTRA_ORGANIZACION))
                .andExpect(jsonPath("$[*].nombre", contains("Cine al aire libre")));

        mockMvc.perform(get("/api/analitica/poblacion").header("X-User-Id", CUENTA_OTRA_ORGANIZACION))
                .andExpect(jsonPath("$.personas[*].nombre", contains("Pedro León")))
                .andExpect(jsonPath("$.intereses[*].nombre", contains("Caminar")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"actividades", "salud", "poblacion"})
    void soloUnaOrganizacionVeLaAnalitica(String reporte) throws Exception {
        // La cuenta 10 es de una persona mayor.
        mockMvc.perform(get("/api/analitica/" + reporte).header("X-User-Id", 10))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Solo una organización puede ver la analítica"));
    }
}
