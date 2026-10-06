package com.proyectogrado.salud_backend.controller;

import com.proyectogrado.salud_backend.config.ZonaHoraria;
import com.proyectogrado.salud_backend.model.CitaMedica;
import com.proyectogrado.salud_backend.repository.CitaMedicaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Citas médicas de la persona mayor: se registran solo citas futuras con
 * motivo, lugar, fecha y hora; las pasadas no se editan, y nadie toca las
 * citas de otra persona.
 */
class CitaMedicaControllerTest {

    private static final int PERSONA_MAYOR = 10;

    private CitaMedicaRepository citaMedicaRepository;
    private MockMvc mockMvc;
    private final LocalDate manana = ZonaHoraria.hoy().plusDays(1);

    @BeforeEach
    void setUp() {
        citaMedicaRepository = mock(CitaMedicaRepository.class);
        when(citaMedicaRepository.save(any(CitaMedica.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc = MockMvcBuilders.standaloneSetup(new CitaMedicaController(citaMedicaRepository)).build();
    }

    private String cita(String titulo, String lugar, String fecha, String hora) {
        return """
                {"titulo": "%s", "lugar": "%s", "consultorio": " ", "fecha": "%s", "hora": "%s",
                 "observaciones": "Llevar exámenes"}
                """.formatted(titulo, lugar, fecha, hora);
    }

    private CitaMedica citaGuardada(int idPersonaMayor, LocalDate fecha) {
        CitaMedica cita = new CitaMedica();
        cita.setIdCita(4);
        cita.setIdPersonaMayor(idPersonaMayor);
        cita.setTitulo("Control de tensión");
        cita.setLugar("USS Usme");
        cita.setFecha(fecha);
        cita.setHora(LocalTime.of(7, 30));
        return cita;
    }

    @Test
    void registrarUnaCitaFuturaLaGuarda() throws Exception {
        mockMvc.perform(post("/api/persona-mayor/citas-medicas")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cita(" Control de tensión ", "USS Usme", manana.toString(), "07:30")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value("Control de tensión"))
                .andExpect(jsonPath("$.fecha").value(manana.toString()))
                .andExpect(jsonPath("$.hora").value("07:30"))
                // Un consultorio en blanco se guarda vacío.
                .andExpect(jsonPath("$.consultorio").doesNotExist());
    }

    @Test
    void unaCitaSinMotivoOSinLugarSeRechaza() throws Exception {
        mockMvc.perform(post("/api/persona-mayor/citas-medicas")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cita("", "USS Usme", manana.toString(), "07:30")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Escribe el motivo, el lugar, la fecha y la hora de la cita"));

        mockMvc.perform(post("/api/persona-mayor/citas-medicas")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cita("Control", " ", manana.toString(), "07:30")))
                .andExpect(status().isBadRequest());

        verify(citaMedicaRepository, never()).save(any());
    }

    @Test
    void unaFechaMalEscritaSeRechazaConUnMensajeClaro() throws Exception {
        mockMvc.perform(post("/api/persona-mayor/citas-medicas")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cita("Control", "USS Usme", "20/10/2026", "07:30")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("La fecha o la hora de la cita no son válidas"));
    }

    @Test
    void unaCitaEnElPasadoSeRechaza() throws Exception {
        mockMvc.perform(post("/api/persona-mayor/citas-medicas")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cita("Control", "USS Usme", ZonaHoraria.hoy().minusDays(1).toString(), "07:30")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("La fecha y la hora de la cita deben ser posteriores a este momento"));
    }

    @Test
    void editarUnaCitaFuturaPropiaGuardaLosCambios() throws Exception {
        CitaMedica guardada = citaGuardada(PERSONA_MAYOR, manana);
        when(citaMedicaRepository.findById(4)).thenReturn(Optional.of(guardada));

        mockMvc.perform(put("/api/persona-mayor/citas-medicas/4")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cita("Control de tensión", "Hospital de Usme", manana.toString(), "10:00")))
                .andExpect(status().isOk());

        assertEquals("Hospital de Usme", guardada.getLugar());
        assertEquals(LocalTime.of(10, 0), guardada.getHora());
        assertNull(guardada.getConsultorio());
    }

    @Test
    void unaCitaQueYaPasoNoSeEdita() throws Exception {
        when(citaMedicaRepository.findById(4))
                .thenReturn(Optional.of(citaGuardada(PERSONA_MAYOR, ZonaHoraria.hoy().minusDays(2))));

        mockMvc.perform(put("/api/persona-mayor/citas-medicas/4")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cita("Control", "USS Usme", manana.toString(), "07:30")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Esta cita ya pasó y no se puede editar"));

        verify(citaMedicaRepository, never()).save(any());
    }

    @Test
    void nadiePuedeEditarNiBorrarLasCitasDeOtraPersona() throws Exception {
        when(citaMedicaRepository.findById(4)).thenReturn(Optional.of(citaGuardada(99, manana)));

        mockMvc.perform(put("/api/persona-mayor/citas-medicas/4")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cita("Control", "USS Usme", manana.toString(), "07:30")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/persona-mayor/citas-medicas/4").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isForbidden());

        verify(citaMedicaRepository, never()).delete(any());
    }

    @Test
    void borrarUnaCitaPropiaLaElimina() throws Exception {
        CitaMedica guardada = citaGuardada(PERSONA_MAYOR, ZonaHoraria.hoy().minusDays(2));
        when(citaMedicaRepository.findById(4)).thenReturn(Optional.of(guardada));

        // Las citas pasadas no se editan, pero sí se pueden borrar.
        mockMvc.perform(delete("/api/persona-mayor/citas-medicas/4").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isNoContent());

        verify(citaMedicaRepository).delete(guardada);
    }
}
