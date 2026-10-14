package com.proyectogrado.auth_backend.controller;

import com.proyectogrado.auth_backend.model.CalificacionPlataforma;
import com.proyectogrado.auth_backend.repository.CalificacionPlataformaRepository;
import com.proyectogrado.auth_backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Calificación de la plataforma desde el perfil (RF-70): de 0 a 5 estrellas
 * y una observación opcional; cada usuario tiene una sola, que actualiza.
 */
class CalificacionControllerTest {

    private CalificacionPlataformaRepository repository;
    private MockMvc mockMvc;
    private String token;

    @BeforeEach
    void setUp() {
        repository = mock(CalificacionPlataformaRepository.class);
        when(repository.save(any(CalificacionPlataforma.class))).thenAnswer(inv -> inv.getArgument(0));

        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "clave_de_pruebas_de_vita_mas_con_al_menos_32_bytes");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3_600_000L);
        token = jwtService.generarToken(40, "VOLUNTARIO");

        mockMvc = MockMvcBuilders.standaloneSetup(new CalificacionController(repository, jwtService)).build();
    }

    @Test
    void sinCalificacionResponde204() throws Exception {
        when(repository.findByIdUsuario(40)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/calificacion").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void laPrimeraCalificacionSeGuardaConElUsuarioDelToken() throws Exception {
        when(repository.findByIdUsuario(40)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/auth/calificacion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"estrellas": 4, "comentario": "  Me gustaría un modo con letra más grande  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estrellas").value(4))
                .andExpect(jsonPath("$.comentario").value("Me gustaría un modo con letra más grande"));

        ArgumentCaptor<CalificacionPlataforma> guardada = ArgumentCaptor.forClass(CalificacionPlataforma.class);
        verify(repository).save(guardada.capture());
        assertEquals(40, guardada.getValue().getIdUsuario());
    }

    @Test
    void volverACalificarActualizaLaMismaYAceptaCeroEstrellas() throws Exception {
        LocalDateTime antes = LocalDateTime.of(2026, 10, 1, 9, 0);
        CalificacionPlataforma existente = new CalificacionPlataforma(40, antes);
        existente.calificar(5, "Excelente", antes);
        when(repository.findByIdUsuario(40)).thenReturn(Optional.of(existente));

        mockMvc.perform(put("/api/auth/calificacion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"estrellas": 0, "comentario": ""}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estrellas").value(0));

        assertEquals(0, existente.getEstrellas());
        assertNull(existente.getComentario());
        assertEquals(antes, existente.getFechaCreacion());
    }

    @Test
    void masDeCincoEstrellasSeRechaza() throws Exception {
        mockMvc.perform(put("/api/auth/calificacion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"estrellas": 6}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Elige una calificación de 0 a 5 estrellas"));

        verify(repository, never()).save(any());
    }

    @Test
    void unaObservacionDemasiadoLargaSeRechaza() throws Exception {
        String larga = "a".repeat(CalificacionPlataforma.MAX_COMENTARIO + 1);

        mockMvc.perform(put("/api/auth/calificacion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estrellas\": 3, \"comentario\": \"" + larga + "\"}"))
                .andExpect(status().isBadRequest());

        verify(repository, never()).save(any());
    }
}
