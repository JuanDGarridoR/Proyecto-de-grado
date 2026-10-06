package com.proyectogrado.salud_backend.controller;

import com.proyectogrado.salud_backend.model.PersonaMayorOrganizacion;
import com.proyectogrado.salud_backend.model.SignoVital;
import com.proyectogrado.salud_backend.model.UsuarioLookup;
import com.proyectogrado.salud_backend.repository.PersonaMayorOrganizacionRepository;
import com.proyectogrado.salud_backend.repository.SignoVitalRepository;
import com.proyectogrado.salud_backend.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Signos vitales (RF-13 y RF-16): la persona mayor registra y consulta su
 * historial, y la organización registra y consulta los de las personas
 * vinculadas a ella. Ambos usan la misma validación.
 */
class SignoVitalControllerTest {

    private static final int PERSONA_MAYOR = 10;
    private static final int CUENTA_ORGANIZACION = 30;
    private static final int ORGANIZACION = 5;

    private SignoVitalRepository signoVitalRepository;
    private PersonaMayorOrganizacionRepository personaMayorOrganizacionRepository;
    private UsuarioLookupRepository usuarioLookupRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        signoVitalRepository = mock(SignoVitalRepository.class);
        personaMayorOrganizacionRepository = mock(PersonaMayorOrganizacionRepository.class);
        usuarioLookupRepository = mock(UsuarioLookupRepository.class);

        when(signoVitalRepository.save(any(SignoVital.class))).thenAnswer(inv -> inv.getArgument(0));
        when(signoVitalRepository.saveAndFlush(any(SignoVital.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioLookup cuentaOrganizacion = mock(UsuarioLookup.class);
        when(cuentaOrganizacion.getIdOrganizacion()).thenReturn(ORGANIZACION);
        when(usuarioLookupRepository.findById(CUENTA_ORGANIZACION)).thenReturn(Optional.of(cuentaOrganizacion));

        mockMvc = MockMvcBuilders.standaloneSetup(
                new SignoVitalController(signoVitalRepository),
                new SignoVitalOrganizacionController(
                        signoVitalRepository, personaMayorOrganizacionRepository, usuarioLookupRepository)).build();
    }

    private SignoVital medicion(int presionSistolica, LocalDateTime fechaHora) {
        SignoVital signoVital = new SignoVital();
        signoVital.setIdSignoVital(presionSistolica);
        signoVital.setIdPersonaMayor(PERSONA_MAYOR);
        signoVital.setFechaHora(fechaHora);
        signoVital.setPresionSistolica(presionSistolica);
        signoVital.setPresionDiastolica(80);
        return signoVital;
    }

    private void vinculoAceptado(boolean existe) {
        when(personaMayorOrganizacionRepository.findById_IdPersonaMayorAndId_IdOrganizacionAndEstado(
                PERSONA_MAYOR, ORGANIZACION, "ACEPTADA"))
                .thenReturn(existe ? Optional.of(new PersonaMayorOrganizacion(PERSONA_MAYOR, ORGANIZACION)) : Optional.empty());
    }

    @Test
    void laPersonaMayorRegistraUnaMedicionPropiaConLaFechaActual() throws Exception {
        mockMvc.perform(post("/api/persona-mayor/signos-vitales")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"presionSistolica\": 128, \"presionDiastolica\": 82, \"frecuenciaCardiaca\": 70}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.presionSistolica").value(128))
                .andExpect(jsonPath("$.fechaHora").exists());

        ArgumentCaptor<SignoVital> guardada = ArgumentCaptor.forClass(SignoVital.class);
        verify(signoVitalRepository).save(guardada.capture());
        assertEquals(PERSONA_MAYOR, guardada.getValue().getIdPersonaMayor());
        assertNotNull(guardada.getValue().getFechaHora());
    }

    @Test
    void unaMedicionImposibleNoSeGuarda() throws Exception {
        mockMvc.perform(post("/api/persona-mayor/signos-vitales")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saturacionOxigeno\": 120}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(
                        "Saturación de oxígeno: 120 % no es un valor posible (debe estar entre 50 y 100)."));

        verify(signoVitalRepository, never()).save(any());
    }

    @Test
    void elHistorialVaDelRegistroMasRecienteAlMasAntiguo() throws Exception {
        when(signoVitalRepository.findByIdPersonaMayorOrderByFechaHoraDesc(PERSONA_MAYOR)).thenReturn(List.of(
                medicion(140, LocalDateTime.of(2026, 10, 3, 9, 0)),
                medicion(125, LocalDateTime.of(2026, 9, 28, 8, 15))));

        mockMvc.perform(get("/api/persona-mayor/signos-vitales").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fechaHora").value("2026-10-03T09:00"))
                .andExpect(jsonPath("$[1].fechaHora").value("2026-09-28T08:15"));
    }

    @Test
    void laOrganizacionRegistraSignosDeUnaPersonaVinculada() throws Exception {
        vinculoAceptado(true);

        mockMvc.perform(post("/api/organizacion/signos-vitales/" + PERSONA_MAYOR)
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temperatura\": 36.8, \"peso\": 61.2, \"observaciones\": \"Jornada de salud\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.temperatura").value(36.8))
                .andExpect(jsonPath("$.observaciones").value("Jornada de salud"));
    }

    @Test
    void laOrganizacionNoVeNiRegistraSignosDeUnaPersonaNoVinculada() throws Exception {
        vinculoAceptado(false);

        mockMvc.perform(get("/api/organizacion/signos-vitales/" + PERSONA_MAYOR).header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isForbidden())
                .andExpect(content().string("La persona mayor no está asociada a esta organización"));

        mockMvc.perform(post("/api/organizacion/signos-vitales/" + PERSONA_MAYOR)
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temperatura\": 36.8}"))
                .andExpect(status().isForbidden());

        verify(signoVitalRepository, never()).saveAndFlush(any());
        verify(signoVitalRepository, never()).findTop10ByIdPersonaMayorOrderByFechaHoraDesc(any());
    }

    @Test
    void unUsuarioQueNoEsOrganizacionNoEntra() throws Exception {
        when(usuarioLookupRepository.findById(PERSONA_MAYOR)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/organizacion/signos-vitales/" + PERSONA_MAYOR).header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isNotFound())
                .andExpect(content().string("El usuario no tiene una organización asociada"));
    }

    @Test
    void laOrganizacionVeLasUltimasDiezMedicionesDeUnaPersonaVinculada() throws Exception {
        vinculoAceptado(true);
        when(signoVitalRepository.findTop10ByIdPersonaMayorOrderByFechaHoraDesc(PERSONA_MAYOR))
                .thenReturn(List.of(medicion(150, LocalDateTime.of(2026, 10, 4, 7, 45))));

        mockMvc.perform(get("/api/organizacion/signos-vitales/" + PERSONA_MAYOR).header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].presionSistolica").value(150));
    }
}
