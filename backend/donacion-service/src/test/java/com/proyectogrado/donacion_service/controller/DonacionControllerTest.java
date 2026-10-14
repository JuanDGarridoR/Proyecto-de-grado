package com.proyectogrado.donacion_service.controller;

import com.proyectogrado.donacion_service.model.Catalogo;
import com.proyectogrado.donacion_service.model.Donacion;
import com.proyectogrado.donacion_service.repository.DonacionRepository;
import com.proyectogrado.donacion_service.repository.OrganizacionLookupRepository;
import com.proyectogrado.donacion_service.repository.UsuarioLookupRepository;
import com.proyectogrado.donacion_service.service.DonacionMapper;
import com.proyectogrado.donacion_service.service.VinculosService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Donaciones del lado del donante (RF-62 y RF-63): solo acompañantes y voluntarios, solo a
 * organizaciones vinculadas, con fecha válida y al menos un artículo.
 */
class DonacionControllerTest {

    private static final int VOLUNTARIO = 40;
    private static final int ORGANIZACION = 5;

    private DonacionRepository donacionRepository;
    private VinculosService vinculosService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        donacionRepository = mock(DonacionRepository.class);
        vinculosService = mock(VinculosService.class);
        UsuarioLookupRepository usuarioLookupRepository = mock(UsuarioLookupRepository.class);
        OrganizacionLookupRepository organizacionLookupRepository = mock(OrganizacionLookupRepository.class);

        when(vinculosService.rolDonante(VOLUNTARIO)).thenReturn(VinculosService.VOLUNTARIO);
        when(vinculosService.organizacionesVinculadas(VOLUNTARIO, VinculosService.VOLUNTARIO))
                .thenReturn(List.of(ORGANIZACION));
        when(donacionRepository.save(any(Donacion.class))).thenAnswer(inv -> inv.getArgument(0));

        DonacionMapper mapper = new DonacionMapper(
                usuarioLookupRepository, organizacionLookupRepository, vinculosService);

        mockMvc = MockMvcBuilders.standaloneSetup(new DonacionController(
                donacionRepository, organizacionLookupRepository, vinculosService, mapper)).build();
    }

    private static String manana() {
        return Catalogo.hoy().plusDays(1).toString();
    }

    @Test
    void programarGuardaLaDonacionConSusArticulosYElDineroEnPesos() throws Exception {
        mockMvc.perform(post("/api/donaciones")
                        .header("X-User-Id", VOLUNTARIO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idOrganizacion": 5, "fechaEntrega": "%s", "horaEntrega": "10:30",
                                 "observaciones": " Lo llevo en la mañana ",
                                 "articulos": [
                                   {"categoria": "ALIMENTOS", "descripcion": " Arroz ", "cantidad": 10, "unidad": "kg"},
                                   {"categoria": "DINERO", "cantidad": 50000, "unidad": "dólares"}
                                 ]}
                                """.formatted(manana())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PROGRAMADA"))
                .andExpect(jsonPath("$.horaEntrega").value("10:30"))
                .andExpect(jsonPath("$.rolDonante").value("VOLUNTARIO"));

        ArgumentCaptor<Donacion> captor = ArgumentCaptor.forClass(Donacion.class);
        verify(donacionRepository).save(captor.capture());
        Donacion donacion = captor.getValue();

        assertEquals(ORGANIZACION, donacion.getIdOrganizacion());
        assertEquals("Lo llevo en la mañana", donacion.getObservaciones());
        assertEquals(2, donacion.getArticulos().size());
        assertEquals("Arroz", donacion.getArticulos().get(0).getDescripcion());
        // El dinero siempre queda en COP y con una descripción por defecto.
        assertEquals("COP", donacion.getArticulos().get(1).getUnidad());
        assertEquals("Dinero", donacion.getArticulos().get(1).getDescripcion());
    }

    @Test
    void noSePuedeDonarAUnaOrganizacionNoVinculada() throws Exception {
        mockMvc.perform(post("/api/donaciones")
                        .header("X-User-Id", VOLUNTARIO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idOrganizacion": 99, "fechaEntrega": "%s",
                                 "articulos": [{"categoria": "ROPA", "descripcion": "Chaquetas", "cantidad": 3, "unidad": "unidades"}]}
                                """.formatted(manana())))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Solo puedes donar a organizaciones con las que estás vinculado"));

        verify(donacionRepository, never()).save(any());
    }

    @Test
    void laFechaDeEntregaNoPuedeSerPasada() throws Exception {
        mockMvc.perform(post("/api/donaciones")
                        .header("X-User-Id", VOLUNTARIO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idOrganizacion": 5, "fechaEntrega": "%s",
                                 "articulos": [{"categoria": "ROPA", "descripcion": "Chaquetas", "cantidad": 3, "unidad": "unidades"}]}
                                """.formatted(Catalogo.hoy().minusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("La fecha de entrega no puede ser anterior a hoy"));
    }

    @Test
    void cadaArticuloNecesitaCantidadPositiva() throws Exception {
        mockMvc.perform(post("/api/donaciones")
                        .header("X-User-Id", VOLUNTARIO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idOrganizacion": 5, "fechaEntrega": "%s",
                                 "articulos": [{"categoria": "ALIMENTOS", "descripcion": "Leche", "cantidad": 0, "unidad": "litros"}]}
                                """.formatted(manana())))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("La cantidad de Leche debe ser mayor que cero"));
    }

    @Test
    void unaPersonaMayorNoPuedeDonar() throws Exception {
        mockMvc.perform(post("/api/donaciones")
                        .header("X-User-Id", 77)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void elDonantePuedeCancelarSuDonacionProgramada() throws Exception {
        Donacion donacion = new Donacion(VOLUNTARIO, VinculosService.VOLUNTARIO, ORGANIZACION, LocalDate.now());
        when(donacionRepository.findById(8)).thenReturn(Optional.of(donacion));

        mockMvc.perform(put("/api/donaciones/8/cancelar").header("X-User-Id", VOLUNTARIO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));

        assertEquals(Donacion.CANCELADA, donacion.getEstado());
    }

    @Test
    void nadieMasPuedeCancelarUnaDonacionAjena() throws Exception {
        Donacion donacion = new Donacion(VOLUNTARIO, VinculosService.VOLUNTARIO, ORGANIZACION, LocalDate.now());
        when(donacionRepository.findById(8)).thenReturn(Optional.of(donacion));

        mockMvc.perform(put("/api/donaciones/8/cancelar").header("X-User-Id", 41))
                .andExpect(status().isNotFound());

        assertEquals(Donacion.PROGRAMADA, donacion.getEstado());
        assertNull(donacion.getFechaRespuesta());
    }

    @Test
    void validarCantidadConMasDeDosDecimales() throws Exception {
        mockMvc.perform(post("/api/donaciones")
                        .header("X-User-Id", VOLUNTARIO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idOrganizacion": 5, "fechaEntrega": "%s",
                                 "articulos": [{"categoria": "ALIMENTOS", "descripcion": "Azúcar", "cantidad": %s, "unidad": "kg"}]}
                                """.formatted(manana(), new BigDecimal("1.234"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("La cantidad de Azúcar puede tener máximo dos decimales"));
    }
}
