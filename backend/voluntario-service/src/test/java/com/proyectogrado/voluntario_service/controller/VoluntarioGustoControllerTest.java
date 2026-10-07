package com.proyectogrado.voluntario_service.controller;

import com.proyectogrado.voluntario_service.model.GustoLookup;
import com.proyectogrado.voluntario_service.model.VoluntarioGusto;
import com.proyectogrado.voluntario_service.repository.GustoLookupRepository;
import com.proyectogrado.voluntario_service.repository.UsuarioLookupRepository;
import com.proyectogrado.voluntario_service.repository.VoluntarioGustoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gustos del voluntario: los marca del mismo catálogo que las personas
 * mayores y con ellos se le recomiendan organizaciones.
 */
class VoluntarioGustoControllerTest {

    private static final int VOLUNTARIO = 40;

    private VoluntarioGustoRepository voluntarioGustoRepository;
    private GustoLookupRepository gustoLookupRepository;
    private UsuarioLookupRepository usuarioLookupRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        voluntarioGustoRepository = mock(VoluntarioGustoRepository.class);
        gustoLookupRepository = mock(GustoLookupRepository.class);
        usuarioLookupRepository = mock(UsuarioLookupRepository.class);

        when(usuarioLookupRepository.tieneRol(VOLUNTARIO, "VOLUNTARIO")).thenReturn(true);

        mockMvc = MockMvcBuilders.standaloneSetup(new VoluntarioGustoController(
                voluntarioGustoRepository, gustoLookupRepository, usuarioLookupRepository)).build();
    }

    private GustoLookup gusto(int id, String nombre, String categoria) {
        GustoLookup gusto = mock(GustoLookup.class);
        when(gusto.getIdGusto()).thenReturn(id);
        when(gusto.getNombre()).thenReturn(nombre);
        when(gusto.getCategoria()).thenReturn(categoria);
        return gusto;
    }

    @Test
    void elVoluntarioVeSusGustosOrdenadosPorNombre() throws Exception {
        GustoLookup leer = gusto(1, "Leer", "GUSTO");
        GustoLookup baile = gusto(2, "Baile", "TALENTO");
        when(voluntarioGustoRepository.findById_IdVoluntario(VOLUNTARIO))
                .thenReturn(List.of(new VoluntarioGusto(VOLUNTARIO, 1), new VoluntarioGusto(VOLUNTARIO, 2)));
        when(gustoLookupRepository.findAllById(List.of(1, 2))).thenReturn(List.of(leer, baile));

        mockMvc.perform(get("/api/voluntario/gustos").header("X-User-Id", VOLUNTARIO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Baile"))
                .andExpect(jsonPath("$[0].categoria").value("TALENTO"))
                .andExpect(jsonPath("$[1].idGusto").value(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void guardarReemplazaLosGustosEIgnoraLosQueNoEstanEnElCatalogo() throws Exception {
        GustoLookup leer = gusto(1, "Leer", "GUSTO");
        // El 99 no existe en el catálogo.
        when(gustoLookupRepository.findAllById(List.of(1, 99))).thenReturn(List.of(leer));
        when(voluntarioGustoRepository.findById_IdVoluntario(VOLUNTARIO)).thenReturn(List.of());

        mockMvc.perform(put("/api/voluntario/gustos")
                        .header("X-User-Id", VOLUNTARIO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idsGustos\": [1, 99]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<List<VoluntarioGusto>> guardados = ArgumentCaptor.forClass(List.class);
        verify(voluntarioGustoRepository).saveAll(guardados.capture());
        assertEquals(1, guardados.getValue().size());
        assertEquals(1, guardados.getValue().get(0).getId().getIdGusto());
    }

    @Test
    void quienNoEsVoluntarioNoTieneGustosAqui() throws Exception {
        mockMvc.perform(get("/api/voluntario/gustos").header("X-User-Id", 10))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/voluntario/gustos")
                        .header("X-User-Id", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idsGustos\": [1]}"))
                .andExpect(status().isForbidden());

        verify(voluntarioGustoRepository, never()).saveAll(anyList());
        verify(voluntarioGustoRepository, never()).deleteAll(any());
    }
}
