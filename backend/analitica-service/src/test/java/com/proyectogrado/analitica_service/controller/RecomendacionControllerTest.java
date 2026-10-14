package com.proyectogrado.analitica_service.controller;

import com.proyectogrado.analitica_service.recomendacion.RecomendadorOrganizaciones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recomendación de organizaciones a la persona mayor y al voluntario (RF-49),
 * con las consultas reales sobre H2 en modo PostgreSQL y los datos ficticios
 * de recomendaciones/datos.sql: qué organizaciones entran, en qué orden y con
 * qué razones.
 */
class RecomendacionControllerTest {

    private static final int ROSA = 10;
    private static final int LAURA_VOLUNTARIA = 40;
    private static final int PEDRO_ACOMPANANTE = 50;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:recomendaciones-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        ResourceDatabasePopulator scripts = new ResourceDatabasePopulator(
                new ClassPathResource("recomendaciones/esquema.sql"),
                new ClassPathResource("recomendaciones/datos.sql"));
        scripts.setSqlScriptEncoding("UTF-8");
        scripts.execute(dataSource);

        mockMvc = MockMvcBuilders.standaloneSetup(new RecomendacionController(
                new RecomendadorOrganizaciones(new NamedParameterJdbcTemplate(dataSource)))).build();
    }

    @Test
    void laPersonaMayorRecibeOrganizacionesALasQueNoPerteneceConSusRazones() throws Exception {
        mockMvc.perform(get("/api/analitica/recomendaciones/organizaciones").header("X-User-Id", ROSA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conGustos").value(true))
                .andExpect(jsonPath("$.direccionReconocida").value(true))
                // Sin el Comedor (ya pertenece) ni el Hogar Cerrado (cuenta inactiva)
                .andExpect(jsonPath("$.recomendaciones[*].nombre",
                        contains("Centro Día Renacer", "Fundación Entrenubes")))
                // Cercanía 1, actividades 1 de 2 gustos y comunidad 1: 35 + 20 + 25
                .andExpect(jsonPath("$.recomendaciones[0].puntaje").value(80))
                .andExpect(jsonPath("$.recomendaciones[0].barrio").value("La Flora"))
                .andExpect(jsonPath("$.recomendaciones[0].gustosCoincidentes", contains("Baile")))
                .andExpect(jsonPath("$.recomendaciones[0].razones",
                        hasItem("Queda muy cerca de tu casa (a menos de 500 m, en La Flora)")))
                .andExpect(jsonPath("$.recomendaciones[0].razones",
                        hasItem("Tiene actividades de Baile: Noche de música y baile")))
                .andExpect(jsonPath("$.recomendaciones[0].razones",
                        hasItem("1 persona de esta organización comparte tus gustos (Tejido)")))
                // La propuesta pendiente no cuenta como actividad
                .andExpect(jsonPath("$.recomendaciones[0].actividadesCoincidentes", not(hasItem("Tejido en grupo"))));
    }

    @Test
    void elVoluntarioNoRecibeLaOrganizacionALaQueYaAyuda() throws Exception {
        mockMvc.perform(get("/api/analitica/recomendaciones/organizaciones").header("X-User-Id", LAURA_VOLUNTARIA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recomendaciones[*].nombre", not(hasItem("Fundación Entrenubes"))))
                .andExpect(jsonPath("$.recomendaciones[*].nombre", hasItem("Centro Día Renacer")))
                .andExpect(jsonPath("$.recomendaciones[?(@.nombre == 'Centro Día Renacer')].razones[*]",
                        hasItem("1 persona mayor de esta organización comparte tus gustos (Tejido)")));
    }

    @Test
    void laProfesionSumaComoOficioYNoComoGusto() throws Exception {
        // RF-71: Laura fue docente, el Centro Día Renacer tiene un grupo de
        // lectura y Luis, una de sus personas mayores, también fue docente.
        mockMvc.perform(get("/api/analitica/recomendaciones/organizaciones").header("X-User-Id", LAURA_VOLUNTARIA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recomendaciones[?(@.nombre == 'Centro Día Renacer')].razones[*]",
                        hasItem("Tiene actividades relacionadas con tu oficio (Docencia): Grupo de lectura")))
                .andExpect(jsonPath("$.recomendaciones[?(@.nombre == 'Centro Día Renacer')].razones[*]",
                        hasItem("1 persona mayor de esta organización también se dedicó a Docencia")))
                // La profesión no se mezcla con los gustos
                .andExpect(jsonPath("$.recomendaciones[?(@.nombre == 'Centro Día Renacer')].gustosCoincidentes[*]",
                        not(hasItem("Docencia"))))
                .andExpect(jsonPath("$.recomendaciones[?(@.nombre == 'Centro Día Renacer')].razones[*]",
                        hasItem("1 persona mayor de esta organización comparte tus gustos (Tejido)")));
    }

    @Test
    void quienNoEsPersonaMayorNiVoluntarioNoRecibeRecomendaciones() throws Exception {
        mockMvc.perform(get("/api/analitica/recomendaciones/organizaciones").header("X-User-Id", PEDRO_ACOMPANANTE))
                .andExpect(status().isForbidden());
    }
}
