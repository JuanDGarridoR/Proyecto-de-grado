package com.proyectogrado.auth_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyectogrado.auth_backend.client.MessagingClient;
import com.proyectogrado.auth_backend.model.Organizacion;
import com.proyectogrado.auth_backend.model.PersonaMayor;
import com.proyectogrado.auth_backend.model.Rol;
import com.proyectogrado.auth_backend.model.Usuario;
import com.proyectogrado.auth_backend.model.UsuarioRol;
import com.proyectogrado.auth_backend.repository.AcompananteRepository;
import com.proyectogrado.auth_backend.repository.OrganizacionRepository;
import com.proyectogrado.auth_backend.repository.PersonaMayorRepository;
import com.proyectogrado.auth_backend.repository.RolRepository;
import com.proyectogrado.auth_backend.repository.UsuarioRepository;
import com.proyectogrado.auth_backend.repository.UsuarioRolRepository;
import com.proyectogrado.auth_backend.repository.VoluntarioRepository;
import com.proyectogrado.auth_backend.security.JwtService;
import com.proyectogrado.auth_backend.service.AuthService;
import com.proyectogrado.auth_backend.service.EmailValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registro, inicio de sesión (con contraseña y con OTP) y recuperación de
 * contraseña (RF-01, RF-02 y RF-06). Usa el AuthService real, con BCrypt y
 * JwtService reales; solo se simulan los repositorios, messaging-service y
 * Hunter, así que no se toca la base de datos ni se envían SMS.
 */
class AuthControllerTest {

    private static final String CELULAR = "+573001112233";
    private static final String CORREO = "ana.rojas@vitamas.co";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private UsuarioRepository usuarioRepository;
    private RolRepository rolRepository;
    private UsuarioRolRepository usuarioRolRepository;
    private PersonaMayorRepository personaMayorRepository;
    private OrganizacionRepository organizacionRepository;
    private MessagingClient messagingClient;
    private EmailValidationService emailValidationService;
    private JwtService jwtService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        rolRepository = mock(RolRepository.class);
        usuarioRolRepository = mock(UsuarioRolRepository.class);
        personaMayorRepository = mock(PersonaMayorRepository.class);
        organizacionRepository = mock(OrganizacionRepository.class);
        messagingClient = mock(MessagingClient.class);
        emailValidationService = mock(EmailValidationService.class);

        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "clave_de_pruebas_de_vita_mas_con_al_menos_32_bytes");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3_600_000L);

        AuthService authService = new AuthService(
                usuarioRepository,
                rolRepository,
                usuarioRolRepository,
                personaMayorRepository,
                mock(AcompananteRepository.class),
                organizacionRepository,
                mock(VoluntarioRepository.class),
                passwordEncoder,
                jwtService,
                messagingClient,
                emailValidationService
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService, emailValidationService))
                .build();

        // La base asigna el id al guardar.
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> {
            Usuario usuario = inv.getArgument(0);
            usuario.setIdUsuario(25);
            return usuario;
        });
        when(usuarioRolRepository.saveAndFlush(any(UsuarioRol.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Rol rol(int id, String nombre) {
        Rol rol = new Rol(nombre);
        rol.setIdRol(id);
        return rol;
    }

    private Usuario usuarioConContrasena(String contrasena) {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(25);
        usuario.setNombreUsuario("Ana Rojas");
        usuario.setCorreo(CORREO);
        usuario.setCelular(CELULAR);
        usuario.setContrasenaHash(passwordEncoder.encode(contrasena));
        usuario.setActivo(true);
        return usuario;
    }

    private void hunterResponde(String correo, String estado) throws Exception {
        when(emailValidationService.validarCorreo(correo))
                .thenReturn(objectMapper.readTree("{\"data\":{\"status\":\"" + estado + "\"}}"));
    }

    private String registroPersonaMayor(String celular, String fechaNacimiento) {
        return """
                {
                  "nombreUsuario": "Ana Rojas",
                  "correo": "%s",
                  "contrasena": "secreta1",
                  "rol": "PERSONA_MAYOR",
                  "fechaNacimiento": "%s",
                  "genero": "Femenino",
                  "direccion": "Calle 91 Sur # 3-20, Usme",
                  "celular": "%s"
                }
                """.formatted(CORREO, fechaNacimiento, celular);
    }

    @Test
    void registroDePersonaMayorConDatosValidosCreaLaCuentaYDevuelveElToken() throws Exception {
        hunterResponde(CORREO, "valid");
        when(rolRepository.findByNombre("PERSONA_MAYOR")).thenReturn(Optional.of(rol(1, "PERSONA_MAYOR")));

        MvcResult resultado = mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registroPersonaMayor(CELULAR, "1950-03-14")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("PERSONA_MAYOR"))
                .andExpect(jsonPath("$.idUsuario").value(25))
                .andExpect(jsonPath("$.mensaje").value("Cuenta creada exitosamente"))
                .andReturn();

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).saveAndFlush(guardado.capture());
        Usuario usuario = guardado.getValue();

        assertEquals(CELULAR, usuario.getCelular());
        assertEquals(CORREO, usuario.getCorreo());
        assertEquals(LocalDate.of(1950, 3, 14), usuario.getFechaNacimiento());
        // La contraseña se guarda como hash BCrypt, nunca en claro.
        assertNotEquals("secreta1", usuario.getContrasenaHash());
        assertTrue(passwordEncoder.matches("secreta1", usuario.getContrasenaHash()));

        verify(usuarioRolRepository).saveAndFlush(any(UsuarioRol.class));
        verify(personaMayorRepository).save(any(PersonaMayor.class));

        String token = objectMapper.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
        assertEquals(25, jwtService.extraerIdUsuario(token));
        assertEquals("PERSONA_MAYOR", jwtService.extraerRol(token));
    }

    @Test
    void registroDeOrganizacionCreaLaOrganizacionYLaEnlazaALaCuenta() throws Exception {
        when(rolRepository.findByNombre("ORGANIZACION")).thenReturn(Optional.of(rol(3, "ORGANIZACION")));
        when(organizacionRepository.save(any(Organizacion.class))).thenAnswer(inv -> {
            Organizacion organizacion = inv.getArgument(0);
            organizacion.setIdOrganizacion(7);
            return organizacion;
        });

        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombreUsuario": "Fundación Entrenubes", "rol": "ORGANIZACION",
                                 "direccion": "Carrera 5 # 80-10 Sur", "celular": "+573004445566"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("ORGANIZACION"));

        ArgumentCaptor<Usuario> cuenta = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(cuenta.capture());
        assertEquals(7, cuenta.getValue().getIdOrganizacion());
    }

    @Test
    void registroSinCelularSeRechazaYNoGuardaNada() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registroPersonaMayor("", "1950-03-14")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El celular es obligatorio"))
                .andExpect(jsonPath("$.token").doesNotExist());

        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void registroSinNombreSeRechaza() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\": \"PERSONA_MAYOR\", \"celular\": \"" + CELULAR + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El nombre de usuario es obligatorio"));

        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void registroDePersonaMayorSinFechaDeNacimientoSeRechaza() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registroPersonaMayor(CELULAR, "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La fecha de nacimiento es obligatoria"));

        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void registroConCelularSinFormatoColombianoSeRechaza() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registroPersonaMayor("3001112233", "1950-03-14")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El celular debe tener el formato +57 seguido de 10 dígitos"));
    }

    @Test
    void registroConCelularYaRegistradoSeRechaza() throws Exception {
        when(usuarioRepository.existsByCelular(CELULAR)).thenReturn(true);

        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registroPersonaMayor(CELULAR, "1950-03-14")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Ya existe un usuario registrado con ese celular"));

        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void registroConCorreoQueHunterNoValidaSeRechaza() throws Exception {
        hunterResponde(CORREO, "invalid");

        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registroPersonaMayor(CELULAR, "1950-03-14")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(
                        "El correo electrónico no parece ser válido o no puede recibir correos. "
                                + "Verifica que esté escrito correctamente."));

        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void loginConCredencialesValidasDevuelveUnTokenConElIdYElRol() throws Exception {
        Usuario usuario = usuarioConContrasena("secreta1");
        when(usuarioRepository.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(usuarioRolRepository.findByUsuario_IdUsuario(25))
                .thenReturn(List.of(new UsuarioRol(usuario, rol(1, "PERSONA_MAYOR"))));

        MvcResult resultado = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\": \"Ana.Rojas@vitamas.co\", \"contrasena\": \"secreta1\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.rol").value("PERSONA_MAYOR"))
                .andExpect(jsonPath("$.nombreUsuario").value("Ana Rojas"))
                .andReturn();

        String token = objectMapper.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
        assertEquals(25, jwtService.extraerIdUsuario(token));
        assertTrue(jwtService.esTokenValido(token, 25));
    }

    @Test
    void loginConContrasenaIncorrectaResponde401() throws Exception {
        when(usuarioRepository.findByCorreo(CORREO)).thenReturn(Optional.of(usuarioConContrasena("secreta1")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\": \"" + CORREO + "\", \"contrasena\": \"otra-clave\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Correo o contraseña incorrectos"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void loginConCorreoNoRegistradoDaElMismoMensajeQueConContrasenaIncorrecta() throws Exception {
        when(usuarioRepository.findByCorreo(anyString())).thenReturn(Optional.empty());

        // Mismo mensaje en ambos casos: no revela qué correos tienen cuenta.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\": \"nadie@vitamas.co\", \"contrasena\": \"secreta1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Correo o contraseña incorrectos"));
    }

    @Test
    void loginConCamposVaciosResponde401ConMensaje() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\": \"\", \"contrasena\": \"\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("El correo y la contraseña son obligatorios"));
    }

    @Test
    void unaCuentaInactivaPuedeEntrarParaReactivarse() throws Exception {
        Usuario usuario = usuarioConContrasena("secreta1");
        usuario.setActivo(false);
        when(usuarioRepository.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\": \"" + CORREO + "\", \"contrasena\": \"secreta1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void loginConCodigoOtpValidoDevuelveToken() throws Exception {
        Usuario usuario = usuarioConContrasena("secreta1");
        when(messagingClient.verificarOtp(CELULAR, "482913")).thenReturn(true);
        when(usuarioRepository.findByCelular(CELULAR)).thenReturn(Optional.of(usuario));
        when(usuarioRolRepository.findByUsuario_IdUsuario(25))
                .thenReturn(List.of(new UsuarioRol(usuario, rol(1, "PERSONA_MAYOR"))));

        mockMvc.perform(post("/api/auth/login-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"celular\": \"" + CELULAR + "\", \"codigo\": \"482913\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.mensaje").value("Inicio de sesión exitoso"));
    }

    @Test
    void loginConCodigoOtpIncorrectoResponde401() throws Exception {
        when(messagingClient.verificarOtp(CELULAR, "000000")).thenReturn(false);

        mockMvc.perform(post("/api/auth/login-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"celular\": \"" + CELULAR + "\", \"codigo\": \"000000\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Código incorrecto o expirado"));
    }

    @Test
    void restablecerContrasenaConCodigoValidoGuardaLaNuevaYPermiteIniciarSesionConElla() throws Exception {
        Usuario usuario = usuarioConContrasena("olvidada1");
        when(usuarioRepository.findByCelular(CELULAR)).thenReturn(Optional.of(usuario));
        when(messagingClient.verificarOtp(CELULAR, "551177")).thenReturn(true);

        mockMvc.perform(post("/api/auth/restablecer-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"celular": "%s", "codigo": "551177",
                                 "contrasena": "nueva-clave", "confirmarContrasena": "nueva-clave"}
                                """.formatted(CELULAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Contraseña actualizada correctamente"));

        verify(usuarioRepository).save(usuario);
        assertTrue(passwordEncoder.matches("nueva-clave", usuario.getContrasenaHash()));

        // Con la contraseña nueva ya se puede entrar.
        when(usuarioRepository.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(usuarioRolRepository.findByUsuario_IdUsuario(25))
                .thenReturn(List.of(new UsuarioRol(usuario, rol(1, "PERSONA_MAYOR"))));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\": \"" + CORREO + "\", \"contrasena\": \"nueva-clave\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void restablecerContrasenaConCelularNoRegistradoSeRechazaSinGastarElCodigo() throws Exception {
        when(usuarioRepository.findByCelular("+573009998877")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/restablecer-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"celular": "+573009998877", "codigo": "551177",
                                 "contrasena": "nueva-clave", "confirmarContrasena": "nueva-clave"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("No existe una cuenta con ese celular"));

        verify(messagingClient, never()).verificarOtp(anyString(), anyString());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void restablecerContrasenaConCodigoIncorrectoNoCambiaLaContrasena() throws Exception {
        Usuario usuario = usuarioConContrasena("olvidada1");
        String hashAnterior = usuario.getContrasenaHash();
        when(usuarioRepository.findByCelular(CELULAR)).thenReturn(Optional.of(usuario));
        when(messagingClient.verificarOtp(CELULAR, "123123")).thenReturn(false);

        mockMvc.perform(post("/api/auth/restablecer-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"celular": "%s", "codigo": "123123",
                                 "contrasena": "nueva-clave", "confirmarContrasena": "nueva-clave"}
                                """.formatted(CELULAR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Código incorrecto o expirado"));

        verify(usuarioRepository, never()).save(any());
        assertEquals(hashAnterior, usuario.getContrasenaHash());
    }

    @Test
    void restablecerContrasenaConConfirmacionDistintaSeRechaza() throws Exception {
        mockMvc.perform(post("/api/auth/restablecer-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"celular": "%s", "codigo": "551177",
                                 "contrasena": "nueva-clave", "confirmarContrasena": "otra-clave"}
                                """.formatted(CELULAR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Las contraseñas no coinciden"));
    }

    @Test
    void celularExisteIndicaSiHayUnaCuentaConEseCelular() throws Exception {
        when(usuarioRepository.existsByCelular(CELULAR)).thenReturn(true);

        mockMvc.perform(get("/api/auth/celular-existe").param("celular", CELULAR))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        mockMvc.perform(get("/api/auth/celular-existe").param("celular", "+573000000000"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }
}
