package com.proyectogrado.auth_backend.controller;

import com.proyectogrado.auth_backend.model.Acompanante;
import com.proyectogrado.auth_backend.model.PersonaMayor;
import com.proyectogrado.auth_backend.model.Rol;
import com.proyectogrado.auth_backend.model.Usuario;
import com.proyectogrado.auth_backend.model.UsuarioRol;
import com.proyectogrado.auth_backend.repository.AcompananteRepository;
import com.proyectogrado.auth_backend.repository.PersonaMayorRepository;
import com.proyectogrado.auth_backend.repository.RolRepository;
import com.proyectogrado.auth_backend.repository.UsuarioRepository;
import com.proyectogrado.auth_backend.repository.UsuarioRolRepository;
import com.proyectogrado.auth_backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cambio de rol entre persona mayor y acompañante (RF-76): la misma cuenta,
 * con su misma información, se usa con uno u otro rol.
 */
class RolControllerTest {

    private static final int USUARIO = 15;

    private UsuarioRolRepository usuarioRolRepository;
    private RolRepository rolRepository;
    private PersonaMayorRepository personaMayorRepository;
    private AcompananteRepository acompananteRepository;
    private UsuarioRepository usuarioRepository;
    private JwtService jwtService;
    private MockMvc mockMvc;
    private String token;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        usuarioRolRepository = mock(UsuarioRolRepository.class);
        rolRepository = mock(RolRepository.class);
        personaMayorRepository = mock(PersonaMayorRepository.class);
        acompananteRepository = mock(AcompananteRepository.class);

        usuario = new Usuario();
        usuario.setIdUsuario(USUARIO);
        usuario.setNombreUsuario("Rosa Díaz");
        when(usuarioRepository.findById(USUARIO)).thenReturn(Optional.of(usuario));

        when(rolRepository.findByNombre("PERSONA_MAYOR")).thenReturn(Optional.of(rol(1, "PERSONA_MAYOR")));
        when(rolRepository.findByNombre("ACOMPANANTE")).thenReturn(Optional.of(rol(2, "ACOMPANANTE")));

        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "clave_de_pruebas_de_vita_mas_con_al_menos_32_bytes");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3_600_000L);
        token = jwtService.generarToken(USUARIO, "PERSONA_MAYOR");

        mockMvc = MockMvcBuilders.standaloneSetup(new RolController(usuarioRepository, rolRepository,
                usuarioRolRepository, personaMayorRepository, acompananteRepository, jwtService)).build();
    }

    private static Rol rol(int id, String nombre) {
        Rol rol = new Rol(nombre);
        rol.setIdRol(id);
        return rol;
    }

    private void conRoles(String... roles) {
        List<UsuarioRol> filas = Arrays.stream(roles)
                .map(nombre -> new UsuarioRol(usuario, rolRepository.findByNombre(nombre).orElse(rol(9, nombre))))
                .toList();
        when(usuarioRolRepository.findByUsuario_IdUsuario(USUARIO)).thenReturn(filas);
    }

    private MvcResult cambiarA(String rol, int estadoEsperado) throws Exception {
        return mockMvc.perform(post("/api/auth/roles/cambiar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\": \"" + rol + "\"}"))
                .andExpect(status().is(estadoEsperado))
                .andReturn();
    }

    @Test
    void listaLosRolesDeLaCuenta() throws Exception {
        conRoles("PERSONA_MAYOR", "ACOMPANANTE");

        mockMvc.perform(get("/api/auth/roles").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles[1]").value("ACOMPANANTE"));
    }

    @Test
    void conLosDosRolesDevuelveUnTokenDelOtroRolSinCrearNada() throws Exception {
        conRoles("PERSONA_MAYOR", "ACOMPANANTE");

        MvcResult resultado = cambiarA("acompanante", 200);

        String cuerpo = resultado.getResponse().getContentAsString();
        String nuevoToken = cuerpo.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        assertEquals("ACOMPANANTE", jwtService.extraerRol(nuevoToken));
        assertEquals(USUARIO, jwtService.extraerIdUsuario(nuevoToken));
        verify(usuarioRolRepository, never()).save(any());
        verify(acompananteRepository, never()).save(any());

        // Al volver a iniciar sesión, entra como acompañante.
        assertEquals("ACOMPANANTE", usuario.getUltimoRol());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void siAunNoEsAcompananteSeLeActivaElRolConSuMismaCuenta() throws Exception {
        conRoles("PERSONA_MAYOR");

        mockMvc.perform(post("/api/auth/roles/cambiar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\": \"ACOMPANANTE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ACOMPANANTE"))
                .andExpect(jsonPath("$.idUsuario").value(USUARIO))
                .andExpect(jsonPath("$.nombreUsuario").value("Rosa Díaz"));

        ArgumentCaptor<UsuarioRol> fila = ArgumentCaptor.forClass(UsuarioRol.class);
        verify(usuarioRolRepository).save(fila.capture());
        assertEquals(USUARIO, fila.getValue().getId().getIdUsuario());
        assertEquals(2, fila.getValue().getId().getIdRol());

        ArgumentCaptor<Acompanante> perfil = ArgumentCaptor.forClass(Acompanante.class);
        verify(acompananteRepository).save(perfil.capture());
        assertEquals(usuario, perfil.getValue().getUsuario());
    }

    @Test
    void unAcompananteTambienPuedeActivarElRolDePersonaMayor() throws Exception {
        token = jwtService.generarToken(USUARIO, "ACOMPANANTE");
        conRoles("ACOMPANANTE");

        cambiarA("PERSONA_MAYOR", 200);

        verify(personaMayorRepository).save(any(PersonaMayor.class));
    }

    @Test
    void soloSeCambiaEntrePersonaMayorYAcompanante() throws Exception {
        conRoles("PERSONA_MAYOR");
        cambiarA("ORGANIZACION", 400);

        // Una organización o un voluntario no pueden volverse persona mayor desde aquí.
        conRoles("VOLUNTARIO");
        cambiarA("PERSONA_MAYOR", 403);

        verify(usuarioRolRepository, never()).save(any());
    }
}
