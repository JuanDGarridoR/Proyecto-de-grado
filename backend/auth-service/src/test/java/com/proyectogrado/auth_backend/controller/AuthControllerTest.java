package com.proyectogrado.auth_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyectogrado.auth_backend.client.MensajeriaClient;
import com.proyectogrado.auth_backend.model.AceptacionTerminos;
import com.proyectogrado.auth_backend.model.Acompanante;
import com.proyectogrado.auth_backend.model.Organizacion;
import com.proyectogrado.auth_backend.model.PersonaMayor;
import com.proyectogrado.auth_backend.model.Rol;
import com.proyectogrado.auth_backend.model.Usuario;
import com.proyectogrado.auth_backend.model.UsuarioRol;
import com.proyectogrado.auth_backend.model.Voluntario;
import com.proyectogrado.auth_backend.repository.AceptacionTerminosRepository;
import com.proyectogrado.auth_backend.repository.AcompananteRepository;
import com.proyectogrado.auth_backend.repository.OrganizacionRepository;
import com.proyectogrado.auth_backend.repository.PersonaMayorRepository;
import com.proyectogrado.auth_backend.repository.RolRepository;
import com.proyectogrado.auth_backend.repository.UsuarioRepository;
import com.proyectogrado.auth_backend.repository.UsuarioRolRepository;
import com.proyectogrado.auth_backend.repository.VoluntarioRepository;
import com.proyectogrado.auth_backend.security.JwtService;
import com.proyectogrado.auth_backend.service.AuthService;
import com.proyectogrado.auth_backend.service.SesionService;
import com.proyectogrado.auth_backend.service.TerminosService;
import com.proyectogrado.auth_backend.service.ValidacionCorreoService;
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

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
 * contraseña (RF-01 y RF-03 a RF-06). Usa el AuthService real, con BCrypt y
 * JwtService reales; solo se simulan los repositorios, mensajeria-service y
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
    private AcompananteRepository acompananteRepository;
    private OrganizacionRepository organizacionRepository;
    private VoluntarioRepository voluntarioRepository;
    private MensajeriaClient mensajeriaClient;
    private ValidacionCorreoService validacionCorreoService;
    private JwtService jwtService;
    private AceptacionTerminosRepository aceptacionTerminosRepository;
    private SesionService sesionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        rolRepository = mock(RolRepository.class);
        usuarioRolRepository = mock(UsuarioRolRepository.class);
        personaMayorRepository = mock(PersonaMayorRepository.class);
        acompananteRepository = mock(AcompananteRepository.class);
        organizacionRepository = mock(OrganizacionRepository.class);
        voluntarioRepository = mock(VoluntarioRepository.class);
        mensajeriaClient = mock(MensajeriaClient.class);
        validacionCorreoService = mock(ValidacionCorreoService.class);
        aceptacionTerminosRepository = mock(AceptacionTerminosRepository.class);
        sesionService = mock(SesionService.class);
        when(sesionService.abrir(any())).thenReturn("token-de-renovacion");

        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "clave_de_pruebas_de_vita_mas_con_al_menos_32_bytes");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3_600_000L);

        AuthService authService = new AuthService(
                usuarioRepository,
                rolRepository,
                usuarioRolRepository,
                personaMayorRepository,
                acompananteRepository,
                organizacionRepository,
                voluntarioRepository,
                passwordEncoder,
                jwtService,
                mensajeriaClient,
                validacionCorreoService,
                new TerminosService(aceptacionTerminosRepository, "2026-10"),
                sesionService
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService), new SesionController(authService, sesionService))
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
        when(validacionCorreoService.validarCorreo(correo))
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
                  "celular": "%s",
                  "aceptaTerminos": true
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
                                 "direccion": "Carrera 5 # 80-10 Sur", "celular": "+573004445566",
                                 "aceptaTerminos": true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("ORGANIZACION"));

        ArgumentCaptor<Usuario> cuenta = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(cuenta.capture());
        assertEquals(7, cuenta.getValue().getIdOrganizacion());
    }

    /** Registro sin correo: el acompañante y el voluntario piden los mismos datos que la persona mayor. */
    private String registroSinCorreo(String rol, String celular) {
        return """
                {"nombreUsuario": "Carlos Pérez", "rol": "%s", "fechaNacimiento": "1985-06-20",
                 "genero": "Masculino", "direccion": "Carrera 4 Este # 76-20 Sur", "celular": "%s",
                 "aceptaTerminos": true}
                """.formatted(rol, celular);
    }

    @Test
    void registroDeAcompananteCreaSuCuenta() throws Exception {
        when(rolRepository.findByNombre("ACOMPANANTE")).thenReturn(Optional.of(rol(2, "ACOMPANANTE")));

        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registroSinCorreo("ACOMPANANTE", "+573005556677")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("ACOMPANANTE"));

        // El parentesco no va en la cuenta: lo indica la persona mayor en cada vínculo.
        ArgumentCaptor<Acompanante> acompanante = ArgumentCaptor.forClass(Acompanante.class);
        verify(acompananteRepository).save(acompanante.capture());
        assertEquals("+573005556677", acompanante.getValue().getUsuario().getCelular());
        // Sin correo no hay nada que validar con Hunter.
        verify(validacionCorreoService, never()).validarCorreo(anyString());
    }

    @Test
    void registroDeVoluntarioCreaSuCuenta() throws Exception {
        when(rolRepository.findByNombre("VOLUNTARIO")).thenReturn(Optional.of(rol(4, "VOLUNTARIO")));

        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registroSinCorreo("VOLUNTARIO", "+573007778899")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("VOLUNTARIO"));

        verify(voluntarioRepository).save(any(Voluntario.class));
        verify(personaMayorRepository, never()).save(any());
    }

    @Test
    void registroGuardaLaConstanciaDeQueAceptoLosTerminos() throws Exception {
        hunterResponde(CORREO, "valid");
        when(rolRepository.findByNombre("PERSONA_MAYOR")).thenReturn(Optional.of(rol(1, "PERSONA_MAYOR")));

        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registroPersonaMayor(CELULAR, "1950-03-14")))
                .andExpect(status().isCreated());

        ArgumentCaptor<AceptacionTerminos> constancia = ArgumentCaptor.forClass(AceptacionTerminos.class);
        verify(aceptacionTerminosRepository).save(constancia.capture());
        assertEquals(25, constancia.getValue().getIdUsuario());
        assertTrue(constancia.getValue().isAcepto());
        assertEquals("2026-10", constancia.getValue().getVersionTerminos());
        assertEquals(AceptacionTerminos.REGISTRO, constancia.getValue().getOrigen());
        // Fecha y hora de ahora, en hora de Colombia.
        LocalDateTime ahora = LocalDateTime.now(ZoneId.of("America/Bogota"));
        assertTrue(Duration.between(constancia.getValue().getFechaAceptacion(), ahora).abs().toMinutes() < 1);
    }

    @Test
    void registroSinAceptarLosTerminosSeRechazaYNoGuardaNada() throws Exception {
        String sinAceptar = registroPersonaMayor(CELULAR, "1950-03-14").replace("\"aceptaTerminos\": true", "\"aceptaTerminos\": false");

        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sinAceptar))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Debes aceptar los términos y condiciones para crear tu cuenta"));

        verify(usuarioRepository, never()).saveAndFlush(any());
        verify(aceptacionTerminosRepository, never()).save(any());
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
    void elLoginAbreUnaSesionYDevuelveSuTokenDeRenovacion() throws Exception {
        Usuario usuario = usuarioConContrasena("secreta1");
        when(usuarioRepository.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(usuarioRolRepository.findByUsuario_IdUsuario(25))
                .thenReturn(List.of(new UsuarioRol(usuario, rol(1, "PERSONA_MAYOR"))));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\": \"" + CORREO + "\", \"contrasena\": \"secreta1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value("token-de-renovacion"));

        verify(sesionService).abrir(25);
    }

    @Test
    void conUnaSesionAbiertaSeRenuevaElTokenConElRolDeLaPestana() throws Exception {
        Usuario usuario = usuarioConContrasena("secreta1");
        usuario.setUltimoRol("PERSONA_MAYOR");
        when(sesionService.usuarioDe("token-de-renovacion")).thenReturn(Optional.of(25));
        when(usuarioRepository.findById(25)).thenReturn(Optional.of(usuario));
        when(usuarioRolRepository.findByUsuario_IdUsuario(25)).thenReturn(List.of(
                new UsuarioRol(usuario, rol(1, "PERSONA_MAYOR")),
                new UsuarioRol(usuario, rol(2, "ACOMPANANTE"))));

        MvcResult resultado = mockMvc.perform(post("/api/auth/sesion/renovar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"token-de-renovacion\", \"rol\": \"ACOMPANANTE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ACOMPANANTE"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String token = objectMapper.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
        assertEquals(25, jwtService.extraerIdUsuario(token));
        assertEquals("ACOMPANANTE", jwtService.extraerRol(token));
    }

    @Test
    void unRolQueLaCuentaNoTieneNoSePuedePedirAlRenovar() throws Exception {
        Usuario usuario = usuarioConContrasena("secreta1");
        when(sesionService.usuarioDe("token-de-renovacion")).thenReturn(Optional.of(25));
        when(usuarioRepository.findById(25)).thenReturn(Optional.of(usuario));
        when(usuarioRolRepository.findByUsuario_IdUsuario(25))
                .thenReturn(List.of(new UsuarioRol(usuario, rol(1, "PERSONA_MAYOR"))));

        mockMvc.perform(post("/api/auth/sesion/renovar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"token-de-renovacion\", \"rol\": \"ORGANIZACION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("PERSONA_MAYOR"));
    }

    @Test
    void conUnaSesionCerradaORevocadaNoSeRenuevaYHayQueVolverAEntrar() throws Exception {
        when(sesionService.usuarioDe(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/sesion/renovar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"token-revocado\", \"rol\": \"PERSONA_MAYOR\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.mensaje").value("Tu sesión terminó. Vuelve a iniciar sesión."));
    }

    @Test
    void cerrarSesionRevocaElTokenDeRenovacion() throws Exception {
        mockMvc.perform(post("/api/auth/sesion/cerrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"token-de-renovacion\"}"))
                .andExpect(status().isNoContent());

        verify(sesionService).cerrar("token-de-renovacion");
    }

    @Test
    void conLosDosRolesEntraConElUltimoQueUsoYSiNoComoPersonaMayor() throws Exception {
        Usuario usuario = usuarioConContrasena("secreta1");
        when(usuarioRepository.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(usuarioRolRepository.findByUsuario_IdUsuario(25)).thenReturn(List.of(
                new UsuarioRol(usuario, rol(2, "ACOMPANANTE")),
                new UsuarioRol(usuario, rol(1, "PERSONA_MAYOR"))));
        String login = "{\"correo\": \"Ana.Rojas@vitamas.co\", \"contrasena\": \"secreta1\"}";

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(jsonPath("$.rol").value("PERSONA_MAYOR"));

        usuario.setUltimoRol("ACOMPANANTE");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(jsonPath("$.rol").value("ACOMPANANTE"));
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

    /**
     * El repositorio simulado hace lo mismo que la consulta real
     * (UsuarioRepository.registrarIntentoFallido): suma el intento y, al
     * llegar al máximo, bloquea y deja el contador en 0.
     */
    private void contarIntentosFallidos(Usuario usuario) {
        when(usuarioRepository.findById(25)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.registrarIntentoFallido(eq(25), anyInt(), any(LocalDateTime.class))).thenAnswer(inv -> {
            int maximo = inv.getArgument(1);
            int intentos = (usuario.getIntentosFallidos() == null ? 0 : usuario.getIntentosFallidos()) + 1;
            if (intentos >= maximo) {
                usuario.setIntentosFallidos(0);
                usuario.setBloqueadoHasta(inv.getArgument(2));
            } else {
                usuario.setIntentosFallidos(intentos);
            }
            return 1;
        });
    }

    private String loginJson(String contrasena) {
        return "{\"correo\": \"" + CORREO + "\", \"contrasena\": \"" + contrasena + "\"}";
    }

    @Test
    void laQuintaContrasenaIncorrectaSeguidaBloqueaLaCuentaQuinceMinutos() throws Exception {
        Usuario usuario = usuarioConContrasena("secreta1");
        when(usuarioRepository.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        contarIntentosFallidos(usuario);

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson("otra-clave")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson("otra-clave")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value(
                        "Por seguridad, tu cuenta está bloqueada por varios intentos fallidos. "
                                + "Intenta de nuevo en 15 minutos, o entra con tu celular y el código por SMS."))
                .andExpect(jsonPath("$.token").doesNotExist());

        verify(usuarioRepository, times(5)).registrarIntentoFallido(eq(25), eq(5), any(LocalDateTime.class));
        long minutos = Duration.between(LocalDateTime.now(ZoneId.of("America/Bogota")), usuario.getBloqueadoHasta()).toMinutes();
        assertTrue(minutos >= 14 && minutos <= 15);
    }

    @Test
    void conLaCuentaBloqueadaNiLaContrasenaCorrectaSirveNiSeCuentanMasIntentos() throws Exception {
        Usuario usuario = usuarioConContrasena("secreta1");
        usuario.setBloqueadoHasta(LocalDateTime.now(ZoneId.of("America/Bogota")).plusMinutes(3));
        when(usuarioRepository.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson("secreta1")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("en 3 minutos")))
                .andExpect(jsonPath("$.token").doesNotExist());

        verify(usuarioRepository, never()).registrarIntentoFallido(any(), anyInt(), any());
    }

    @Test
    void cuandoElBloqueoVencePuedeEntrarYSeBorranLosIntentos() throws Exception {
        Usuario usuario = usuarioConContrasena("secreta1");
        usuario.setBloqueadoHasta(LocalDateTime.now(ZoneId.of("America/Bogota")).minusMinutes(1));
        when(usuarioRepository.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(usuarioRolRepository.findByUsuario_IdUsuario(25))
                .thenReturn(List.of(new UsuarioRol(usuario, rol(1, "PERSONA_MAYOR"))));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson("secreta1")))
                .andExpect(status().isOk());

        verify(usuarioRepository).reiniciarIntentosFallidos(25);
    }

    @Test
    void unInicioDeSesionExitosoReiniciaLosIntentosFallidos() throws Exception {
        Usuario usuario = usuarioConContrasena("secreta1");
        usuario.setIntentosFallidos(3);
        when(usuarioRepository.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(usuarioRolRepository.findByUsuario_IdUsuario(25))
                .thenReturn(List.of(new UsuarioRol(usuario, rol(1, "PERSONA_MAYOR"))));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson("secreta1")))
                .andExpect(status().isOk());

        verify(usuarioRepository).reiniciarIntentosFallidos(25);
    }

    @Test
    void sinIntentosFallidosElLoginNoEscribeEnLaBase() throws Exception {
        Usuario usuario = usuarioConContrasena("secreta1");
        when(usuarioRepository.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(usuarioRolRepository.findByUsuario_IdUsuario(25))
                .thenReturn(List.of(new UsuarioRol(usuario, rol(1, "PERSONA_MAYOR"))));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson("secreta1")))
                .andExpect(status().isOk());

        verify(usuarioRepository, never()).reiniciarIntentosFallidos(any());
    }

    @Test
    void restablecerLaContrasenaCierraTodasLasSesionesAbiertas() throws Exception {
        Usuario usuario = usuarioConContrasena("olvidada1");
        when(usuarioRepository.findByCelular(CELULAR)).thenReturn(Optional.of(usuario));
        when(mensajeriaClient.verificarOtp(CELULAR, "551177")).thenReturn(true);

        mockMvc.perform(post("/api/auth/restablecer-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"celular": "%s", "codigo": "551177",
                                 "contrasena": "nueva-clave", "confirmarContrasena": "nueva-clave"}
                                """.formatted(CELULAR)))
                .andExpect(status().isOk());

        verify(sesionService).cerrarTodas(25);
    }

    @Test
    void restablecerLaContrasenaConElCodigoDesbloqueaLaCuenta() throws Exception {
        Usuario usuario = usuarioConContrasena("olvidada1");
        usuario.setIntentosFallidos(2);
        usuario.setBloqueadoHasta(LocalDateTime.now(ZoneId.of("America/Bogota")).plusMinutes(10));
        when(usuarioRepository.findByCelular(CELULAR)).thenReturn(Optional.of(usuario));
        when(mensajeriaClient.verificarOtp(CELULAR, "551177")).thenReturn(true);

        mockMvc.perform(post("/api/auth/restablecer-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"celular": "%s", "codigo": "551177",
                                 "contrasena": "nueva-clave", "confirmarContrasena": "nueva-clave"}
                                """.formatted(CELULAR)))
                .andExpect(status().isOk());

        assertEquals(0, usuario.getIntentosFallidos());
        assertNull(usuario.getBloqueadoHasta());
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
        when(mensajeriaClient.verificarOtp(CELULAR, "482913")).thenReturn(true);
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
        when(mensajeriaClient.verificarOtp(CELULAR, "000000")).thenReturn(false);

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
        when(mensajeriaClient.verificarOtp(CELULAR, "551177")).thenReturn(true);

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

        verify(mensajeriaClient, never()).verificarOtp(anyString(), anyString());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void restablecerContrasenaConCodigoIncorrectoNoCambiaLaContrasena() throws Exception {
        Usuario usuario = usuarioConContrasena("olvidada1");
        String hashAnterior = usuario.getContrasenaHash();
        when(usuarioRepository.findByCelular(CELULAR)).thenReturn(Optional.of(usuario));
        when(mensajeriaClient.verificarOtp(CELULAR, "123123")).thenReturn(false);

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
