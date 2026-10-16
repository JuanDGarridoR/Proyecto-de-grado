package com.proyectogrado.analitica_service.controller;

import com.proyectogrado.analitica_service.red.RedApoyo;
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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Red de apoyo de la organización para el grafo y el mapa de su panel, con
 * las consultas reales sobre H2 en modo PostgreSQL y los datos de
 * red/datos.sql: quién entra en la red (solo vínculos aceptados, cuentas
 * activas y la propia organización), y que de cada persona mayor solo
 * salga el barrio.
 */
class RedControllerTest {

    private static final int CUENTA_ORGANIZACION = 30;
    private static final int ROSA = 10;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:red-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        ResourceDatabasePopulator scripts = new ResourceDatabasePopulator(
                new ClassPathResource("red/esquema.sql"),
                new ClassPathResource("red/datos.sql"));
        scripts.setSqlScriptEncoding("UTF-8");
        scripts.execute(dataSource);

        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new RedController(new OrganizacionActual(jdbc), new RedApoyo(jdbc))).build();
    }

    @Test
    void laRedTraeSoloLasPersonasMayoresConVinculoAceptadoYCuentaActiva() throws Exception {
        mockMvc.perform(get("/api/analitica/red").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizacion.nombre").value("Fundación Entrenubes"))
                .andExpect(jsonPath("$.organizacion.latitud").value(4.4805))
                // Sin la cuenta inactiva, el vínculo pendiente ni la de otra organización
                .andExpect(jsonPath("$.personasMayores[*].nombre",
                        contains("Ana Sin Barrio", "Pedro Gómez", "Rosa Díaz")));
    }

    @Test
    void deCadaPersonaSoloSaleElBarrioNuncaSuDireccion() throws Exception {
        mockMvc.perform(get("/api/analitica/red").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(jsonPath("$.personasMayores[2].barrio").value("Yomasa"))
                .andExpect(jsonPath("$.personasMayores[1].barrio").value("La Flora"))
                .andExpect(jsonPath("$.personasMayores[0].barrio").doesNotExist())
                .andExpect(jsonPath("$.personasMayores[2].direccion").doesNotExist())
                .andExpect(jsonPath("$.barrios[*].nombre", containsInAnyOrder("Yomasa", "La Flora")));
    }

    @Test
    void losAcompanantesSonLosDeVinculoAceptadoConSuRelacion() throws Exception {
        mockMvc.perform(get("/api/analitica/red").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(jsonPath("$.acompanantes[*].nombre", contains("Carmen Díaz")))
                .andExpect(jsonPath("$.vinculos[0].idPersonaMayor").value(ROSA))
                .andExpect(jsonPath("$.vinculos[0].idAcompanante").value(50))
                .andExpect(jsonPath("$.vinculos[0].relacion").value("Hija"));
    }

    @Test
    void losVoluntariosYLosGustosSonSoloLosDeLaRed() throws Exception {
        mockMvc.perform(get("/api/analitica/red").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(jsonPath("$.voluntarios[*].nombre", contains("Laura Voluntaria")))
                .andExpect(jsonPath("$.voluntarios[0].gustos", contains(2)))
                .andExpect(jsonPath("$.personasMayores[2].gustos", containsInAnyOrder(1, 2)))
                // Ajedrez solo lo tienen alguien de otra organización y un voluntario pendiente
                .andExpect(jsonPath("$.gustos[*].nombre", containsInAnyOrder("Boleros", "Tejido")));
    }

    @Test
    void lasActividadesSonLasRecientesYProximasDeLaOrganizacionConSusInscritos() throws Exception {
        mockMvc.perform(get("/api/analitica/red").header("X-User-Id", CUENTA_ORGANIZACION))
                // Sin la de hace un año, la de otra organización ni la propuesta pendiente
                .andExpect(jsonPath("$.actividades[*].nombre", contains("Tejido", "Caminata")))
                .andExpect(jsonPath("$.actividades[1].latitud").value(4.528))
                .andExpect(jsonPath("$.personasMayores[2].actividades", containsInAnyOrder(100, 104)))
                .andExpect(jsonPath("$.personasMayores[2].actividades", not(hasItem(101))));
    }

    @Test
    void soloUnaOrganizacionVeSuRed() throws Exception {
        mockMvc.perform(get("/api/analitica/red").header("X-User-Id", ROSA))
                .andExpect(status().isForbidden());
    }
}
