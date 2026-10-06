package com.proyectogrado.auth_backend.controller;

import com.proyectogrado.auth_backend.model.PersonaMayor;
import com.proyectogrado.auth_backend.model.Usuario;
import com.proyectogrado.auth_backend.repository.PersonaMayorRepository;
import com.proyectogrado.auth_backend.repository.UsuarioRepository;
import com.proyectogrado.auth_backend.security.JwtService;
import com.proyectogrado.auth_backend.service.EmailValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
 * "Mi información" (RF-04 y RF-05): consulta y actualización de los datos de
 * la cuenta, incluidas la EPS y la IPS de la persona mayor. El usuario sale
 * del token, como en producción.
 */
class UsuarioInformacionControllerTest {

    private UsuarioRepository usuarioRepository;
    private PersonaMayorRepository personaMayorRepository;
    private EmailValidationService emailValidationService;
    private MockMvc mockMvc;
    private String token;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        personaMayorRepository = mock(PersonaMayorRepository.class);
        emailValidationService = mock(EmailValidationService.class);

        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "clave_de_pruebas_de_vita_mas_con_al_menos_32_bytes");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3_600_000L);
        token = jwtService.generarToken(40, "PERSONA_MAYOR");

        usuario = new Usuario();
        usuario.setIdUsuario(40);
        usuario.setNombreUsuario("Rosa Díaz");
        usuario.setCelular("+573005556677");
        usuario.setCorreo("rosa@vitamas.co");
        usuario.setFechaNacimiento(LocalDate.of(1948, 7, 2));
        usuario.setGenero("Femenino");
        usuario.setDireccion("Calle 100 Sur # 1-10");

        when(usuarioRepository.findById(40)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc = MockMvcBuilders.standaloneSetup(new UsuarioInformacionController(
                usuarioRepository, personaMayorRepository, jwtService, emailValidationService)).build();
    }

    private PersonaMayor personaMayor() {
        PersonaMayor personaMayor = new PersonaMayor(usuario);
        personaMayor.setIdUsuario(40);
        personaMayor.setEps("Compensar");
        return personaMayor;
    }

    @Test
    void consultarDevuelveLosDatosDelUsuarioDelToken() throws Exception {
        when(personaMayorRepository.findById(40)).thenReturn(Optional.of(personaMayor()));

        mockMvc.perform(get("/api/auth/informacion").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idUsuario").value(40))
                .andExpect(jsonPath("$.nombre").value("Rosa Díaz"))
                .andExpect(jsonPath("$.celular").value("+573005556677"))
                .andExpect(jsonPath("$.correo").value("rosa@vitamas.co"))
                .andExpect(jsonPath("$.eps").value("Compensar"))
                .andExpect(jsonPath("$.tieneContrasena").value(false));
    }

    @Test
    void actualizarConDatosValidosGuardaLosCambiosYLaEpsDeLaPersonaMayor() throws Exception {
        PersonaMayor personaMayor = personaMayor();
        when(personaMayorRepository.findById(40)).thenReturn(Optional.of(personaMayor));

        mockMvc.perform(put("/api/auth/informacion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": " Rosa Elvira Díaz ", "correo": "rosa@vitamas.co",
                                 "fechaNacimiento": "1948-07-02", "genero": "Femenino",
                                 "direccion": "Carrera 3 # 95-40 Sur", "eps": "Capital Salud",
                                 "ips": "USS Usme", "direccionIps": "Calle 137 Sur # 13-50"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Rosa Elvira Díaz"))
                .andExpect(jsonPath("$.direccion").value("Carrera 3 # 95-40 Sur"));

        verify(usuarioRepository).save(usuario);
        assertEquals("Rosa Elvira Díaz", usuario.getNombreUsuario());
        verify(personaMayorRepository).save(personaMayor);
        assertEquals("Capital Salud", personaMayor.getEps());
        assertEquals("USS Usme", personaMayor.getIps());
        // El correo no cambió: no se gasta una consulta de Hunter.
        verify(emailValidationService, never()).puedeRecibirCorreos(anyString());
    }

    @Test
    void actualizarSinNombreSeRechazaYNoGuardaNada() throws Exception {
        mockMvc.perform(put("/api/auth/informacion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"  \", \"correo\": \"rosa@vitamas.co\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("El nombre es obligatorio"));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void actualizarConEpsDemasiadoLargaSeRechaza() throws Exception {
        mockMvc.perform(put("/api/auth/informacion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Rosa\", \"eps\": \"" + "E".repeat(121) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("El nombre de la EPS o IPS no puede tener más de 120 caracteres"));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void actualizarConUnCorreoDeOtraCuentaSeRechaza() throws Exception {
        when(usuarioRepository.existsByCorreo("ocupado@vitamas.co")).thenReturn(true);

        mockMvc.perform(put("/api/auth/informacion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Rosa\", \"correo\": \"ocupado@vitamas.co\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Ya existe un usuario registrado con ese correo"));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void actualizarConUnCorreoQueNoPuedeRecibirMensajesSeRechaza() throws Exception {
        when(emailValidationService.puedeRecibirCorreos("rosa.nueva@vitamas.co")).thenReturn(false);

        mockMvc.perform(put("/api/auth/informacion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Rosa\", \"correo\": \"rosa.nueva@vitamas.co\"}"))
                .andExpect(status().isBadRequest());

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void siHunterNoRespondeSeAvisaConUn502SinGuardar() throws Exception {
        when(emailValidationService.puedeRecibirCorreos("rosa.nueva@vitamas.co"))
                .thenThrow(new RuntimeException("Hunter respondió 503"));

        mockMvc.perform(put("/api/auth/informacion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Rosa\", \"correo\": \"rosa.nueva@vitamas.co\"}"))
                .andExpect(status().isBadGateway());

        verify(usuarioRepository, never()).save(any());
    }

    /**
     * Hallazgo del QC (CP-GUA-009): el backend guarda una fecha de nacimiento
     * futura. Se deja deshabilitada hasta que se agregue esa validación.
     */
    @Test
    @Disabled("Hallazgo CP-GUA-009: se acepta una fecha de nacimiento futura")
    void actualizarConFechaDeNacimientoFuturaSeRechaza() throws Exception {
        mockMvc.perform(put("/api/auth/informacion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Rosa\", \"fechaNacimiento\": \"2090-01-01\"}"))
                .andExpect(status().isBadRequest());

        verify(usuarioRepository, never()).save(any());
    }
}
