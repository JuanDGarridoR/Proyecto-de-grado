package com.proyectogrado.auth_backend.controller;

import com.proyectogrado.auth_backend.model.Usuario;
import com.proyectogrado.auth_backend.repository.UsuarioRepository;
import com.proyectogrado.auth_backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cambio de contraseña desde "Mi información": exige la contraseña actual a
 * quien ya tenía una y deja crearla sin ella a quien se registró solo con OTP.
 */
class ContrasenaControllerTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UsuarioRepository usuarioRepository;
    private MockMvc mockMvc;
    private String token;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);

        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "clave_de_pruebas_de_vita_mas_con_al_menos_32_bytes");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3_600_000L);
        token = jwtService.generarToken(12, "ACOMPANANTE");

        usuario = new Usuario();
        usuario.setIdUsuario(12);
        usuario.setContrasenaHash(passwordEncoder.encode("actual-123"));
        when(usuarioRepository.findById(12)).thenReturn(Optional.of(usuario));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ContrasenaController(usuarioRepository, jwtService, passwordEncoder))
                .build();
    }

    private String cuerpo(String actual, String nueva) {
        return "{\"contrasenaActual\": " + (actual == null ? "null" : "\"" + actual + "\"")
                + ", \"nuevaContrasena\": \"" + nueva + "\"}";
    }

    @Test
    void conLaContrasenaActualCorrectaGuardaLaNueva() throws Exception {
        mockMvc.perform(put("/api/auth/contrasena")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("actual-123", "nueva-456")))
                .andExpect(status().isOk())
                .andExpect(content().string("Contraseña guardada correctamente"));

        verify(usuarioRepository).save(usuario);
        assertTrue(passwordEncoder.matches("nueva-456", usuario.getContrasenaHash()));
    }

    @Test
    void conLaContrasenaActualIncorrectaResponde401() throws Exception {
        mockMvc.perform(put("/api/auth/contrasena")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("otra", "nueva-456")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("La contraseña actual es incorrecta"));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void unaContrasenaDeMenosDeSeisCaracteresSeRechaza() throws Exception {
        mockMvc.perform(put("/api/auth/contrasena")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("actual-123", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("La contraseña debe tener mínimo 6 caracteres"));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void quienSeRegistroSoloConOtpPuedeCrearSuContrasenaSinLaActual() throws Exception {
        usuario.setContrasenaHash(null);

        mockMvc.perform(put("/api/auth/contrasena")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(null, "primera-789")))
                .andExpect(status().isOk());

        assertTrue(passwordEncoder.matches("primera-789", usuario.getContrasenaHash()));
    }
}
