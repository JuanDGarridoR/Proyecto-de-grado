package com.proyectogrado.persona_mayor_service.controller;

import com.proyectogrado.persona_mayor_service.model.Gusto;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorGusto;
import com.proyectogrado.persona_mayor_service.repository.GustoRepository;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorGustoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
 * Gustos e intereses (caracterización, RF-12): el catálogo y los que marca
 * cada persona mayor. Solo la propia persona mayor cambia los suyos.
 */
class GustoControllerTest {

    private GustoRepository gustoRepository;
    private PersonaMayorGustoRepository personaMayorGustoRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        gustoRepository = mock(GustoRepository.class);
        personaMayorGustoRepository = mock(PersonaMayorGustoRepository.class);

        mockMvc = MockMvcBuilders.standaloneSetup(
                new GustoController(gustoRepository, personaMayorGustoRepository),
                new PersonaMayorGustoController(gustoRepository, personaMayorGustoRepository)).build();
    }

    private Gusto gusto(int id, String nombre, String categoria) {
        Gusto gusto = new Gusto(nombre, categoria);
        gusto.setIdGusto(id);
        return gusto;
    }

    @Test
    void elCatalogoSePuedeFiltrarPorCategoria() throws Exception {
        when(gustoRepository.findByCategoria("TALENTO")).thenReturn(List.of(gusto(3, "Tejer", "TALENTO")));

        mockMvc.perform(get("/api/gustos").param("categoria", "TALENTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Tejer"));
    }

    @Test
    void unGustoNuevoSeAgregaAlCatalogo() throws Exception {
        when(gustoRepository.save(any(Gusto.class))).thenAnswer(inv -> {
            Gusto gusto = inv.getArgument(0);
            gusto.setIdGusto(9);
            return gusto;
        });

        mockMvc.perform(post("/api/gustos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Boleros\", \"categoria\": \"GUSTO\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idGusto").value(9));
    }

    @Test
    void unGustoSinNombreOConNombreRepetidoSeRechaza() throws Exception {
        mockMvc.perform(post("/api/gustos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"\", \"categoria\": \"GUSTO\"}"))
                .andExpect(status().isBadRequest());

        when(gustoRepository.existsByNombre("Boleros")).thenReturn(true);
        mockMvc.perform(post("/api/gustos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Boleros\", \"categoria\": \"GUSTO\"}"))
                .andExpect(status().isConflict());

        verify(gustoRepository, never()).save(any());
    }

    @Test
    void noSeBorraUnGustoQueTieneMarcadoAlgunaPersona() throws Exception {
        when(gustoRepository.existsById(3)).thenReturn(true);
        when(personaMayorGustoRepository.existsByGusto_IdGusto(3)).thenReturn(true);

        mockMvc.perform(delete("/api/gustos/3"))
                .andExpect(status().isConflict());

        verify(gustoRepository, never()).deleteById(any());
    }

    @Test
    void laPersonaMayorReemplazaSusGustosPorLosDeLaLista() throws Exception {
        when(gustoRepository.findAllById(List.of(3, 4)))
                .thenReturn(List.of(gusto(3, "Tejer", "TALENTO"), gusto(4, "Caminar", "HOBBY")));

        mockMvc.perform(put("/api/persona-mayor/10/gustos")
                        .header("X-User-Id", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idsGustos\": [3, 4]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        verify(personaMayorGustoRepository).deleteById_IdPersonaMayor(10);
        verify(personaMayorGustoRepository, times(2)).save(any(PersonaMayorGusto.class));
    }

    @Test
    void nadieMasPuedeCambiarLosGustosDeUnaPersonaMayor() throws Exception {
        mockMvc.perform(put("/api/persona-mayor/10/gustos")
                        .header("X-User-Id", 11)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idsGustos\": [3]}"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Solo puedes gestionar tus propios gustos"));

        verify(personaMayorGustoRepository, never()).deleteById_IdPersonaMayor(any());
    }

    @Test
    void unGustoQueNoExisteNoSePuedeMarcar() throws Exception {
        when(gustoRepository.findAllById(List.of(3, 404))).thenReturn(List.of(gusto(3, "Tejer", "TALENTO")));

        mockMvc.perform(put("/api/persona-mayor/10/gustos")
                        .header("X-User-Id", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idsGustos\": [3, 404]}"))
                .andExpect(status().isBadRequest());

        verify(personaMayorGustoRepository, never()).deleteById_IdPersonaMayor(any());
    }
}
