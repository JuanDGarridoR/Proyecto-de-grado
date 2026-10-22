package com.proyectogrado.analitica_service.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reportes de donaciones e inventario de la organización, con las consultas
 * reales sobre H2 en modo PostgreSQL y los datos de recursos/datos.sql:
 * cada organización ve solo lo suyo, con los artículos de cada donación y
 * los movimientos de su inventario.
 */
class RecursosControllerTest {

    private static final int CUENTA_ORGANIZACION = 30;
    private static final int CARMEN_ACOMPANANTE = 50;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:recursos-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        ResourceDatabasePopulator scripts = new ResourceDatabasePopulator(
                new ClassPathResource("recursos/esquema.sql"),
                new ClassPathResource("recursos/datos.sql"));
        scripts.setSqlScriptEncoding("UTF-8");
        scripts.execute(dataSource);

        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new RecursosController(jdbc, new OrganizacionActual(jdbc))).build();
    }

    @Test
    void lasDonacionesSonSoloLasDeLaOrganizacionDeLaMasRecienteALaMasAntigua() throws Exception {
        mockMvc.perform(get("/api/analitica/donaciones").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.donaciones[*].id", contains(3, 2, 1)))
                .andExpect(jsonPath("$.donaciones[*].estado", contains("PROGRAMADA", "NO_ENTREGADA", "RECIBIDA")))
                .andExpect(jsonPath("$.donaciones[2].fechaEntrega").value("2026-10-01"))
                .andExpect(jsonPath("$.donaciones[2].rolDonante").value("ACOMPANANTE"))
                .andExpect(jsonPath("$.donaciones[2].donante").value("Carmen Díaz"));
    }

    @Test
    void cadaDonacionTraeSusArticulosConLoQueSeRecibio() throws Exception {
        mockMvc.perform(get("/api/analitica/donaciones").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(jsonPath("$.donaciones[2].articulos[*].categoria", contains("ALIMENTOS", "DINERO")))
                .andExpect(jsonPath("$.donaciones[2].articulos[0].cantidad").value(10.0))
                .andExpect(jsonPath("$.donaciones[2].articulos[0].cantidadRecibida").value(8.0))
                // La que no llegó no tiene cantidad recibida
                .andExpect(jsonPath("$.donaciones[1].articulos[0].cantidadRecibida").doesNotExist());
    }

    @Test
    void elInventarioTraeLosArticulosDeHoyYSusMovimientos() throws Exception {
        mockMvc.perform(get("/api/analitica/inventario").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isOk())
                // Sin la panela de la otra organización
                .andExpect(jsonPath("$.articulos[*].nombre", contains("Arroz", "Dinero")))
                .andExpect(jsonPath("$.articulos[0].cantidad").value(2.0))
                .andExpect(jsonPath("$.articulos[0].cantidadMinima").value(5.0))
                .andExpect(jsonPath("$.articulos[0].fechaVencimiento").value("2027-01-10"))
                .andExpect(jsonPath("$.articulos[1].cantidadMinima").doesNotExist())
                .andExpect(jsonPath("$.movimientos[*].motivo", contains("DONACION", "DONACION", "ENTREGA")))
                .andExpect(jsonPath("$.movimientos[2].tipo").value("SALIDA"))
                .andExpect(jsonPath("$.movimientos[2].fecha").value("2026-10-03T15:30"));
    }

    @Test
    void soloUnaOrganizacionVeEstosReportes() throws Exception {
        mockMvc.perform(get("/api/analitica/donaciones").header("X-User-Id", CARMEN_ACOMPANANTE))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/analitica/inventario").header("X-User-Id", CARMEN_ACOMPANANTE))
                .andExpect(status().isForbidden());
    }
}
