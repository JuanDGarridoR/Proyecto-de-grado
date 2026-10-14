package com.proyectogrado.organizacion_service.controller;

import com.proyectogrado.organizacion_service.model.NotaPersonaMayor;
import com.proyectogrado.organizacion_service.model.PersonaMayorOrganizacion;
import com.proyectogrado.organizacion_service.model.PersonaMayorOrganizacionId;
import com.proyectogrado.organizacion_service.model.UsuarioLookup;
import com.proyectogrado.organizacion_service.repository.NotaPersonaMayorRepository;
import com.proyectogrado.organizacion_service.repository.PersonaMayorOrganizacionRepository;
import com.proyectogrado.organizacion_service.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notas de la organización sobre sus personas mayores (RF-74): solo de
 * personas vinculadas, privadas de cada organización y editables.
 */
class NotaPersonaMayorControllerTest {

    private static final int CUENTA_ORGANIZACION = 30;
    private static final int ORGANIZACION = 5;
    private static final int PERSONA_MAYOR = 10;
    private static final String URL = "/api/organizacion/personas-mayores/10/notas";

    private NotaPersonaMayorRepository notaRepository;
    private PersonaMayorOrganizacionRepository relacionRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        notaRepository = mock(NotaPersonaMayorRepository.class);
        relacionRepository = mock(PersonaMayorOrganizacionRepository.class);
        UsuarioLookupRepository usuarioLookupRepository = mock(UsuarioLookupRepository.class);

        UsuarioLookup cuenta = mock(UsuarioLookup.class);
        when(cuenta.getIdOrganizacion()).thenReturn(ORGANIZACION);
        when(cuenta.getNombreUsuario()).thenReturn("Fundación Entrenubes");
        when(usuarioLookupRepository.findById(CUENTA_ORGANIZACION)).thenReturn(Optional.of(cuenta));

        vinculo("ACEPTADA");
        when(notaRepository.save(any(NotaPersonaMayor.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc = MockMvcBuilders.standaloneSetup(new NotaPersonaMayorController(
                notaRepository, relacionRepository, usuarioLookupRepository)).build();
    }

    private void vinculo(String estado) {
        PersonaMayorOrganizacion relacion = new PersonaMayorOrganizacion(PERSONA_MAYOR, ORGANIZACION);
        relacion.setEstado(estado);
        when(relacionRepository.findById(new PersonaMayorOrganizacionId(PERSONA_MAYOR, ORGANIZACION)))
                .thenReturn(Optional.of(relacion));
    }

    private NotaPersonaMayor nota(int id, int idOrganizacion, String texto) {
        NotaPersonaMayor nota = new NotaPersonaMayor(
                idOrganizacion, PERSONA_MAYOR, texto, CUENTA_ORGANIZACION, LocalDateTime.of(2026, 10, 1, 9, 0));
        ReflectionTestUtils.setField(nota, "idNota", id);
        return nota;
    }

    @Test
    void creaLaNotaConElTextoLimpioYQuienLaEscribio() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"texto": "  Visita en casa: está bien de ánimo  "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.texto").value("Visita en casa: está bien de ánimo"))
                .andExpect(jsonPath("$.autor").value("Fundación Entrenubes"))
                .andExpect(jsonPath("$.editada").value(false));

        ArgumentCaptor<NotaPersonaMayor> guardada = ArgumentCaptor.forClass(NotaPersonaMayor.class);
        verify(notaRepository).save(guardada.capture());
        assertEquals(ORGANIZACION, guardada.getValue().getIdOrganizacion());
        assertEquals(PERSONA_MAYOR, guardada.getValue().getIdPersonaMayor());
    }

    @Test
    void listaLasNotasDeLaPersonaMasRecientePrimero() throws Exception {
        when(notaRepository.findByIdOrganizacionAndIdPersonaMayorOrderByFechaCreacionDesc(ORGANIZACION, PERSONA_MAYOR))
                .thenReturn(List.of(nota(2, ORGANIZACION, "Segunda"), nota(1, ORGANIZACION, "Primera")));

        mockMvc.perform(get(URL).header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].texto").value(org.hamcrest.Matchers.contains("Segunda", "Primera")));
    }

    @Test
    void editarCambiaElTextoYQuedaMarcadaComoEditada() throws Exception {
        NotaPersonaMayor existente = nota(7, ORGANIZACION, "Texto viejo");
        when(notaRepository.findById(7)).thenReturn(Optional.of(existente));

        mockMvc.perform(put(URL + "/7")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"texto": "Texto nuevo"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("Texto nuevo"))
                .andExpect(jsonPath("$.editada").value(true));

        assertTrue(existente.fueEditada());
    }

    @Test
    void unaOrganizacionNoPuedeTocarLaNotaDeOtra() throws Exception {
        when(notaRepository.findById(8)).thenReturn(Optional.of(nota(8, 6, "De otra organización")));

        mockMvc.perform(delete(URL + "/8").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isNotFound());

        verify(notaRepository, never()).delete(any());
    }

    @Test
    void soloSeTomanNotasDePersonasVinculadas() throws Exception {
        vinculo("PENDIENTE");

        mockMvc.perform(post(URL)
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"texto": "Hola"}
                                """))
                .andExpect(status().isForbidden());

        verify(notaRepository, never()).save(any());
    }

    @Test
    void unaNotaVaciaSeRechaza() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"texto": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Escribe la nota"));
    }

    @Test
    void eliminarBorraLaNotaDeLaOrganizacion() throws Exception {
        NotaPersonaMayor existente = nota(9, ORGANIZACION, "Para borrar");
        when(notaRepository.findById(9)).thenReturn(Optional.of(existente));

        mockMvc.perform(delete(URL + "/9").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isOk());

        verify(notaRepository).delete(existente);
    }
}
