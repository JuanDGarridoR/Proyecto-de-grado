package com.proyectogrado.acompanante_service.controller;

import com.proyectogrado.acompanante_service.model.EmergenciaLookup;
import com.proyectogrado.acompanante_service.model.PersonaMayorAcompanante;
import com.proyectogrado.acompanante_service.model.UsuarioLookup;
import com.proyectogrado.acompanante_service.repository.EmergenciaLookupRepository;
import com.proyectogrado.acompanante_service.repository.PersonaMayorAcompananteRepository;
import com.proyectogrado.acompanante_service.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Emergencias en el inicio del acompañante (RF-17): ve las que activaron en
 * las últimas 24 horas las personas mayores con las que tiene un vínculo
 * aceptado.
 */
class AcompananteEmergenciaControllerTest {

    private static final int ACOMPANANTE = 20;
    private static final int PERSONA_MAYOR = 10;

    private PersonaMayorAcompananteRepository relacionRepository;
    private EmergenciaLookupRepository emergenciaRepository;
    private UsuarioLookupRepository usuarioLookupRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        relacionRepository = mock(PersonaMayorAcompananteRepository.class);
        emergenciaRepository = mock(EmergenciaLookupRepository.class);
        usuarioLookupRepository = mock(UsuarioLookupRepository.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new AcompananteEmergenciaController(
                relacionRepository, emergenciaRepository, usuarioLookupRepository)).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void veLasEmergenciasRecientesDeSusPersonasMayores() throws Exception {
        PersonaMayorAcompanante vinculo = new PersonaMayorAcompanante(PERSONA_MAYOR, ACOMPANANTE);
        vinculo.setEstado("ACEPTADA");
        when(relacionRepository.findById_IdAcompananteAndEstado(ACOMPANANTE, "ACEPTADA")).thenReturn(List.of(vinculo));

        EmergenciaLookup emergencia = mock(EmergenciaLookup.class);
        when(emergencia.getIdEmergencia()).thenReturn(7);
        when(emergencia.getIdPersonaMayor()).thenReturn(PERSONA_MAYOR);
        when(emergencia.getFechaHora()).thenReturn(LocalDateTime.of(2026, 10, 7, 10, 30));
        when(emergenciaRepository.findByIdPersonaMayorInAndFechaHoraAfterOrderByFechaHoraDesc(any(), any()))
                .thenReturn(List.of(emergencia));

        UsuarioLookup rosa = mock(UsuarioLookup.class);
        when(rosa.getNombreUsuario()).thenReturn("Rosa Díaz");
        when(usuarioLookupRepository.findById(PERSONA_MAYOR)).thenReturn(Optional.of(rosa));

        mockMvc.perform(get("/api/acompanante/emergencias").header("X-User-Id", ACOMPANANTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idPersonaMayor").value(PERSONA_MAYOR))
                .andExpect(jsonPath("$[0].nombre").value("Rosa Díaz"))
                .andExpect(jsonPath("$[0].fechaHora").value("2026-10-07T10:30"));

        // Solo se consultan las personas que acompaña.
        ArgumentCaptor<Collection<Integer>> personas = ArgumentCaptor.forClass(Collection.class);
        verify(emergenciaRepository).findByIdPersonaMayorInAndFechaHoraAfterOrderByFechaHoraDesc(personas.capture(), any());
        assertEquals(List.of(PERSONA_MAYOR), List.copyOf(personas.getValue()));
    }

    @Test
    void sinPersonasAcompanadasNoHayEmergencias() throws Exception {
        when(relacionRepository.findById_IdAcompananteAndEstado(ACOMPANANTE, "ACEPTADA")).thenReturn(List.of());

        mockMvc.perform(get("/api/acompanante/emergencias").header("X-User-Id", ACOMPANANTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(emergenciaRepository, never()).findByIdPersonaMayorInAndFechaHoraAfterOrderByFechaHoraDesc(any(), any());
    }
}
