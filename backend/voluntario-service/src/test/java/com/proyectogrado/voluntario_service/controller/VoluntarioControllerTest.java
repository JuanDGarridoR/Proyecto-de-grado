package com.proyectogrado.voluntario_service.controller;

import com.proyectogrado.voluntario_service.model.UsuarioLookup;
import com.proyectogrado.voluntario_service.model.VoluntarioLookup;
import com.proyectogrado.voluntario_service.repository.UsuarioLookupRepository;
import com.proyectogrado.voluntario_service.repository.VoluntarioLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perfil del voluntario: los datos de su cuenta, solo si de verdad es voluntario.
 */
class VoluntarioControllerTest {

    private UsuarioLookupRepository usuarioLookupRepository;
    private VoluntarioLookupRepository voluntarioLookupRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        usuarioLookupRepository = mock(UsuarioLookupRepository.class);
        voluntarioLookupRepository = mock(VoluntarioLookupRepository.class);

        UsuarioLookup usuario = mock(UsuarioLookup.class);
        when(usuario.getNombreUsuario()).thenReturn("Andrea López");
        when(usuario.getCelular()).thenReturn("+573004440000");
        when(usuario.getCorreo()).thenReturn("andrea@vitamas.co");
        when(usuarioLookupRepository.findById(40)).thenReturn(Optional.of(usuario));
        when(usuarioLookupRepository.findById(10)).thenReturn(Optional.of(usuario));
        when(voluntarioLookupRepository.findById(40)).thenReturn(Optional.of(mock(VoluntarioLookup.class)));

        mockMvc = MockMvcBuilders.standaloneSetup(
                new VoluntarioController(usuarioLookupRepository, voluntarioLookupRepository)).build();
    }

    @Test
    void elVoluntarioVeLosDatosDeSuCuenta() throws Exception {
        mockMvc.perform(get("/api/voluntario/perfil").header("X-User-Id", 40))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idUsuario").value(40))
                .andExpect(jsonPath("$.nombre").value("Andrea López"))
                .andExpect(jsonPath("$.correo").value("andrea@vitamas.co"));
    }

    @Test
    void quienNoEsVoluntarioNoTienePerfilDeVoluntario() throws Exception {
        mockMvc.perform(get("/api/voluntario/perfil").header("X-User-Id", 10))
                .andExpect(status().isNotFound());
    }
}
