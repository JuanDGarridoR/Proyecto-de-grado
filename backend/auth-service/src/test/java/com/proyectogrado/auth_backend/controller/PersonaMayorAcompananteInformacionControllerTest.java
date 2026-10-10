package com.proyectogrado.auth_backend.controller;

import com.proyectogrado.auth_backend.model.PersonaMayor;
import com.proyectogrado.auth_backend.model.Usuario;
import com.proyectogrado.auth_backend.repository.PersonaMayorRepository;
import com.proyectogrado.auth_backend.repository.UsuarioRepository;
import com.proyectogrado.auth_backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El acompañante consulta y edita el perfil de una persona mayor que
 * acompaña, pero no su correo, y nada de una persona que no acompaña.
 */
class PersonaMayorAcompananteInformacionControllerTest {

    private static final int ACOMPANANTE = 20;
    private static final int PERSONA_MAYOR = 40;

    private UsuarioRepository usuarioRepository;
    private PersonaMayorRepository personaMayorRepository;
    private MockMvc mockMvc;
    private String token;
    private Usuario usuario;
    private PersonaMayor personaMayor;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        personaMayorRepository = mock(PersonaMayorRepository.class);

        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "clave_de_pruebas_de_vita_mas_con_al_menos_32_bytes");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3_600_000L);
        token = jwtService.generarToken(ACOMPANANTE, "ACOMPANANTE");

        usuario = new Usuario();
        usuario.setIdUsuario(PERSONA_MAYOR);
        usuario.setNombreUsuario("Rosa Díaz");
        usuario.setCelular("+573005556677");
        usuario.setCorreo("rosa@vitamas.co");

        personaMayor = new PersonaMayor(usuario);
        personaMayor.setIdUsuario(PERSONA_MAYOR);

        when(usuarioRepository.findById(PERSONA_MAYOR)).thenReturn(Optional.of(usuario));
        when(personaMayorRepository.findById(PERSONA_MAYOR)).thenReturn(Optional.of(personaMayor));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(personaMayorRepository.save(any(PersonaMayor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(personaMayorRepository.esAcompananteAceptado(PERSONA_MAYOR, ACOMPANANTE)).thenReturn(true);

        mockMvc = MockMvcBuilders.standaloneSetup(new PersonaMayorAcompananteInformacionController(
                usuarioRepository, personaMayorRepository, jwtService)).build();
    }

    @Test
    void consultaElPerfilDeLaPersonaQueAcompana() throws Exception {
        mockMvc.perform(get("/api/acompanante/personas-mayores/{id}/informacion", PERSONA_MAYOR)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Rosa Díaz"))
                .andExpect(jsonPath("$.celular").value("+573005556677"));
    }

    @Test
    void editaElPerfilPeroNoElCorreo() throws Exception {
        mockMvc.perform(put("/api/acompanante/personas-mayores/{id}/informacion", PERSONA_MAYOR)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": " Rosa Elvira Díaz ", "correo": "otro@vitamas.co",
                                 "fechaNacimiento": "1948-07-02", "genero": "Femenino",
                                 "direccion": "Carrera 3 # 95-40 Sur", "eps": "Capital Salud",
                                 "ips": "USS Usme", "direccionIps": "", "viveSolo": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Rosa Elvira Díaz"))
                .andExpect(jsonPath("$.eps").value("Capital Salud"))
                .andExpect(jsonPath("$.viveSolo").value(true));

        assertEquals("rosa@vitamas.co", usuario.getCorreo());
        assertEquals(LocalDate.of(1948, 7, 2), usuario.getFechaNacimiento());
        assertEquals("USS Usme", personaMayor.getIps());
        assertEquals(null, personaMayor.getDireccionIps());
        assertEquals(true, personaMayor.getViveSolo());
    }

    @Test
    void elNombreEsObligatorio() throws Exception {
        mockMvc.perform(put("/api/acompanante/personas-mayores/{id}/informacion", PERSONA_MAYOR)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"  \"}"))
                .andExpect(status().isBadRequest());

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void noVeNiEditaAUnaPersonaQueNoAcompana() throws Exception {
        when(personaMayorRepository.esAcompananteAceptado(PERSONA_MAYOR, ACOMPANANTE)).thenReturn(false);

        mockMvc.perform(get("/api/acompanante/personas-mayores/{id}/informacion", PERSONA_MAYOR)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/acompanante/personas-mayores/{id}/informacion", PERSONA_MAYOR)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Otro nombre\"}"))
                .andExpect(status().isForbidden());

        verify(usuarioRepository, never()).save(any());
    }
}
