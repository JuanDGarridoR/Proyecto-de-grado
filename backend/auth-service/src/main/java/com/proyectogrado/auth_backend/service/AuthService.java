package com.proyectogrado.auth_backend.service;

import com.proyectogrado.auth_backend.client.MensajeriaClient;
import com.proyectogrado.auth_backend.dto.LoginOtpRequest;
import com.proyectogrado.auth_backend.dto.LoginRequest;
import com.proyectogrado.auth_backend.dto.LoginResponse;
import com.proyectogrado.auth_backend.dto.RegistroRequest;
import com.proyectogrado.auth_backend.dto.RestablecerContrasenaRequest;
import com.proyectogrado.auth_backend.model.Acompanante;
import com.proyectogrado.auth_backend.model.Organizacion;
import com.proyectogrado.auth_backend.model.PersonaMayor;
import com.proyectogrado.auth_backend.model.Rol;
import com.proyectogrado.auth_backend.model.Usuario;
import com.proyectogrado.auth_backend.model.UsuarioRol;
import com.proyectogrado.auth_backend.model.Voluntario;
import com.proyectogrado.auth_backend.repository.AcompananteRepository;
import com.proyectogrado.auth_backend.repository.OrganizacionRepository;
import com.proyectogrado.auth_backend.repository.PersonaMayorRepository;
import com.proyectogrado.auth_backend.repository.RolRepository;
import com.proyectogrado.auth_backend.repository.UsuarioRepository;
import com.proyectogrado.auth_backend.repository.UsuarioRolRepository;
import com.proyectogrado.auth_backend.repository.VoluntarioRepository;
import com.proyectogrado.auth_backend.security.JwtService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/**
 * Registro, inicio de sesión y recuperación de contraseña.
 *
 * Hay dos formas de entrar: con correo y contraseña, o con el celular y un
 * código OTP que envía y verifica mensajeria-service. El celular siempre es
 * obligatorio; el correo y la contraseña son opcionales, pero van juntos.
 */
@Service
public class AuthService {

    /** Largo mínimo de una contraseña nueva. Las que ya existían con menos siguen sirviendo para entrar. */
    public static final int LARGO_MINIMO_CONTRASENA = 8;

    public static final String MENSAJE_CONTRASENA_CORTA =
            "La contraseña debe tener mínimo " + LARGO_MINIMO_CONTRASENA + " caracteres";

    /** Contraseñas incorrectas seguidas que bloquean la cuenta. */
    public static final int MAXIMO_INTENTOS_FALLIDOS = 5;

    /** Cuánto dura el bloqueo. */
    public static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final PersonaMayorRepository personaMayorRepository;
    private final AcompananteRepository acompananteRepository;
    private final OrganizacionRepository organizacionRepository;
    private final VoluntarioRepository voluntarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MensajeriaClient mensajeriaClient;
    private final ValidacionCorreoService validacionCorreoService;
    private final SesionService sesionService;
    private final TerminosService terminosService;

    public AuthService(
            UsuarioRepository usuarioRepository,
            RolRepository rolRepository,
            UsuarioRolRepository usuarioRolRepository,
            PersonaMayorRepository personaMayorRepository,
            AcompananteRepository acompananteRepository,
            OrganizacionRepository organizacionRepository,
            VoluntarioRepository voluntarioRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            MensajeriaClient mensajeriaClient,
            ValidacionCorreoService validacionCorreoService,
            TerminosService terminosService,
            SesionService sesionService
    ) {

        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.usuarioRolRepository = usuarioRolRepository;
        this.personaMayorRepository = personaMayorRepository;
        this.acompananteRepository = acompananteRepository;
        this.organizacionRepository = organizacionRepository;
        this.voluntarioRepository = voluntarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mensajeriaClient = mensajeriaClient;
        this.validacionCorreoService = validacionCorreoService;
        this.terminosService = terminosService;
        this.sesionService = sesionService;
    }

    /** Inicio de sesión con correo y contraseña. */
    public LoginResponse login(LoginRequest request) {

        if (request == null
                || request.getCorreo() == null
                || request.getCorreo().isBlank()
                || request.getContrasena() == null
                || request.getContrasena().isBlank()) {

            throw new RuntimeException(
                    "El correo y la contraseña son obligatorios"
            );
        }

        String correo = request.getCorreo().trim().toLowerCase();

        // El mensaje es el mismo si el correo no existe o si la contraseña no
        // coincide, para no revelar qué correos tienen cuenta.
        Usuario usuario = usuarioRepository
                .findByCorreo(correo)
                .orElseThrow(() -> new RuntimeException(
                        "Correo o contraseña incorrectos"
                ));

        // Una cuenta inactiva sí puede entrar: desde su perfil la reactiva
        // (ver CuentaService). Mientras tanto nadie más la ve.

        // Bloqueada: ni se revisa la contraseña, para que seguir probando no sirva.
        LocalDateTime ahora = LocalDateTime.now(ZONA);
        verificarQueNoEsteBloqueada(usuario, ahora);

        boolean contrasenaCorrecta = passwordEncoder.matches(
                request.getContrasena(),
                usuario.getContrasenaHash()
        );

        if (!contrasenaCorrecta) {
            // El contador vive en la base (y no en memoria) porque auth-service
            // corre en dos réplicas: cada intento puede caer en una distinta.
            usuarioRepository.registrarIntentoFallido(
                    usuario.getIdUsuario(),
                    MAXIMO_INTENTOS_FALLIDOS,
                    ahora.plus(DURACION_BLOQUEO)
            );
            // Si este fue el intento que la bloqueó, se avisa de una vez.
            usuarioRepository.findById(usuario.getIdUsuario())
                    .ifPresent(actualizado -> verificarQueNoEsteBloqueada(actualizado, ahora));

            throw new RuntimeException("Correo o contraseña incorrectos");
        }

        if (tieneIntentosFallidos(usuario)) {
            usuarioRepository.reiniciarIntentosFallidos(usuario.getIdUsuario());
        }

        return generarRespuestaLogin(usuario, "Inicio de sesión exitoso");
    }

    /** Lanza CuentaBloqueadaException si la cuenta sigue bloqueada a esta hora. */
    private void verificarQueNoEsteBloqueada(Usuario usuario, LocalDateTime ahora) {
        LocalDateTime hasta = usuario.getBloqueadoHasta();
        if (hasta != null && ahora.isBefore(hasta)) {
            long segundos = Duration.between(ahora, hasta).getSeconds();
            throw new CuentaBloqueadaException(Math.max(1, (segundos + 59) / 60));
        }
    }

    private boolean tieneIntentosFallidos(Usuario usuario) {
        return (usuario.getIntentosFallidos() != null && usuario.getIntentosFallidos() > 0)
                || usuario.getBloqueadoHasta() != null;
    }

    /** Inicio de sesión con el celular y el código OTP que llegó por SMS. */
    public LoginResponse loginConOtp(LoginOtpRequest request) {

        if (request == null
                || request.getCelular() == null
                || request.getCelular().isBlank()
                || request.getCodigo() == null
                || request.getCodigo().isBlank()) {

            throw new RuntimeException(
                    "El celular y el código son obligatorios"
            );
        }

        String celular = request.getCelular().trim();

        boolean codigoValido = mensajeriaClient.verificarOtp(
                celular,
                request.getCodigo().trim()
        );

        if (!codigoValido) {
            throw new RuntimeException("Código incorrecto o expirado");
        }

        Usuario usuario = usuarioRepository
                .findByCelular(celular)
                .orElseThrow(() -> new RuntimeException(
                        "No existe una cuenta con ese celular"
                ));

        return generarRespuestaLogin(usuario, "Inicio de sesión exitoso");
    }

    /**
     * Cambia la contraseña de quien la olvidó. La persona se identifica con
     * su celular y el código OTP que recibió.
     */
public void restablecerContrasena(
        RestablecerContrasenaRequest request
) {

    if (request == null
            || request.getCelular() == null
            || request.getCelular().isBlank()
            || request.getCodigo() == null
            || request.getCodigo().isBlank()
            || request.getContrasena() == null
            || request.getContrasena().isBlank()
            || request.getConfirmarContrasena() == null
            || request.getConfirmarContrasena().isBlank()) {

        throw new RuntimeException(
                "Todos los campos son obligatorios"
        );
    }

    if (!request.getContrasena().equals(
            request.getConfirmarContrasena())) {

        throw new RuntimeException(
                "Las contraseñas no coinciden"
        );
    }

    if (request.getContrasena().length() < LARGO_MINIMO_CONTRASENA) {

        throw new RuntimeException(MENSAJE_CONTRASENA_CORTA);
    }

    String celular = request.getCelular().trim();

    Usuario usuario = usuarioRepository
            .findByCelular(celular)
            .orElseThrow(() -> new RuntimeException(
                    "No existe una cuenta con ese celular"
            ));

    boolean codigoValido =
            mensajeriaClient.verificarOtp(
                    celular,
                    request.getCodigo().trim()
            );

    if (!codigoValido) {

        throw new RuntimeException(
                "Código incorrecto o expirado"
        );
    }

    usuario.setContrasenaHash(
            passwordEncoder.encode(
                    request.getContrasena()
            )
    );

        // Quien demostró con el código que el celular es suyo puede volver a
        // entrar de una vez, aunque la cuenta estuviera bloqueada.
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);

        usuarioRepository.save(usuario);

        // Quien tuviera la contraseña vieja, o una sesión abierta en otro
        // dispositivo, queda por fuera.
        sesionService.cerrarTodas(usuario.getIdUsuario());
    }

    /** Lo usa el login para no enviar un código por SMS a un celular sin cuenta. */
    public boolean existeCelular(String celular) {
    if (celular == null || celular.isBlank()) {
        return false;
    }

        return usuarioRepository.existsByCelular(celular);
    }

    /**
     * Respuesta de login con un token nuevo. Si el usuario tiene varios roles
     * (persona mayor y acompañante), entra con el último que usó; si nunca ha
     * cambiado, como persona mayor.
     */
    private LoginResponse generarRespuestaLogin(Usuario usuario, String mensaje) {

        String rol = elegirRol(usuario, null);

        String token = jwtService.generarToken(
                usuario.getIdUsuario(),
                rol
        );

        LoginResponse respuesta = new LoginResponse(
                token,
                rol,
                mensaje,
                usuario.getIdUsuario(),
                usuario.getNombreUsuario()
        );
        respuesta.setRefreshToken(sesionService.abrir(usuario.getIdUsuario()));
        return respuesta;
    }

    /**
     * Token de acceso nuevo para una sesión abierta, con el rol que la
     * pestaña está usando (así, si en otra pestaña la misma cuenta cambió de
     * rol, esta no cambia sola). El token de renovación sigue siendo el mismo.
     */
    public LoginResponse renovarSesion(String refreshToken, String rolPedido) {

        Usuario usuario = sesionService.usuarioDe(refreshToken)
                .flatMap(usuarioRepository::findById)
                .orElseThrow(() -> new SesionInvalidaException());

        String rol = elegirRol(usuario, rolPedido);

        return new LoginResponse(
                jwtService.generarToken(usuario.getIdUsuario(), rol),
                rol,
                "Sesión renovada",
                usuario.getIdUsuario(),
                usuario.getNombreUsuario()
        );
    }

    /**
     * Rol con el que entra: el pedido si la cuenta lo tiene; si no, el
     * último que usó, y si no, persona mayor antes que los demás.
     */
    private String elegirRol(Usuario usuario, String rolPedido) {

        List<String> roles = usuarioRolRepository.findByUsuario_IdUsuario(usuario.getIdUsuario())
                .stream()
                .filter(usuarioRol -> usuarioRol.getRol() != null)
                .map(usuarioRol -> usuarioRol.getRol().getNombre())
                .sorted(Comparator.comparing((String nombre) -> !"PERSONA_MAYOR".equals(nombre)))
                .toList();

        if (rolPedido != null && roles.contains(rolPedido)) {
            return rolPedido;
        }

        return roles.contains(usuario.getUltimoRol())
                ? usuario.getUltimoRol()
                : roles.stream().findFirst().orElse("SIN_ROL");
    }

    /**
     * Crea la cuenta con su rol y devuelve un token para que la persona entre
     * de una vez. Si trae correo, primero se valida con Hunter.
     */
    @Transactional
    public LoginResponse registrar(RegistroRequest request) {

        validarRegistro(request);

        String celular = request.getCelular().trim();

        String correo = null;
        if (request.getCorreo() != null && !request.getCorreo().isBlank()) {
            correo = request.getCorreo().trim().toLowerCase();
        }

        String contrasena = null;
        if (request.getContrasena() != null && !request.getContrasena().isBlank()) {
            contrasena = request.getContrasena().trim();
        }

        boolean registroConContrasena = contrasena != null;

        String rolNombre = request.getRol().trim().toUpperCase();

        if (usuarioRepository.existsByCelular(celular)) {
            throw new RuntimeException(
                    "Ya existe un usuario registrado con ese celular"
            );
        }

        if (correo != null && usuarioRepository.existsByCorreo(correo)) {
        throw new RuntimeException(
                "Ya existe un usuario registrado con ese correo"
        );
}

        // Hunter confirma que el correo existe y puede recibir mensajes.
        if (correo != null) {

        JsonNode resultado =
                validacionCorreoService.validarCorreo(correo);

        String status =
                resultado
                        .path("data")
                        .path("status")
                        .asText();

        if (!"valid".equalsIgnoreCase(status)) {
                throw new RuntimeException(
                        "El correo electrónico no parece ser válido o no puede recibir correos. Verifica que esté escrito correctamente."
                );
        }
        }

        Rol rol = rolRepository
                .findByNombre(rolNombre)
                .orElseThrow(() -> new RuntimeException(
                        "El rol solicitado no existe: " + rolNombre
                ));

        Usuario usuario = new Usuario();
        usuario.setNombreUsuario(request.getNombreUsuario().trim());
        usuario.setCelular(celular);

        if (request.getFechaNacimiento() != null
        && !request.getFechaNacimiento().isBlank()) {
    usuario.setFechaNacimiento(
            LocalDate.parse(request.getFechaNacimiento())
    );
}

usuario.setGenero(request.getGenero());
usuario.setDireccion(request.getDireccion());

        if (correo != null) {
            usuario.setCorreo(correo);
        }

        if (registroConContrasena) {
            usuario.setContrasenaHash(passwordEncoder.encode(contrasena));
        }

        usuario.setActivo(true);

        Usuario usuarioGuardado = usuarioRepository.saveAndFlush(usuario);

        usuarioRolRepository.saveAndFlush(
                new UsuarioRol(usuarioGuardado, rol)
        );

        // Además del usuario, cada rol tiene su propia tabla.
        switch (rolNombre) {

case "PERSONA_MAYOR" -> {
    PersonaMayor personaMayor = new PersonaMayor(usuarioGuardado);
    personaMayorRepository.save(personaMayor);
}

            case "ACOMPANANTE" -> {
                Acompanante acompanante = new Acompanante(usuarioGuardado, null);
                acompananteRepository.save(acompanante);
            }

case "ORGANIZACION" -> {
    Organizacion organizacion = new Organizacion();
    organizacion.setNombre(request.getNombreUsuario());
    organizacion.setDireccion(request.getDireccion());

    organizacion = organizacionRepository.save(organizacion);

    // La cuenta queda enlazada a la organización que acaba de crear.
    usuarioGuardado.setIdOrganizacion(organizacion.getIdOrganizacion());
    usuarioRepository.save(usuarioGuardado);
}

            case "VOLUNTARIO" -> voluntarioRepository.save(
                    new Voluntario(usuarioGuardado)
            );

            default -> throw new RuntimeException(
                    "Rol no soportado para registro: " + rolNombre
            );
        }

        // Constancia de que aceptó los términos: va en la misma transacción,
        // así no puede quedar una cuenta sin su constancia.
        terminosService.registrarAceptacion(usuarioGuardado.getIdUsuario());

        String token = jwtService.generarToken(
                usuarioGuardado.getIdUsuario(),
                rol.getNombre()
        );

        LoginResponse respuesta = new LoginResponse(
                token,
                rol.getNombre(),
                "Cuenta creada exitosamente",
                usuarioGuardado.getIdUsuario(),
                usuarioGuardado.getNombreUsuario()
        );
        respuesta.setRefreshToken(sesionService.abrir(usuarioGuardado.getIdUsuario()));
        return respuesta;
    }

    /**
     * Revisa los datos del formulario antes de tocar la base de datos. Los
     * campos obligatorios cambian según el rol.
     */
    private void validarRegistro(RegistroRequest request) {

        if (request == null) {
            throw new RuntimeException("Los datos del registro son obligatorios");
        }

        if (request.getNombreUsuario() == null || request.getNombreUsuario().isBlank()) {
            throw new RuntimeException("El nombre de usuario es obligatorio");
        }

        if (request.getRol() == null || request.getRol().isBlank()) {
            throw new RuntimeException("El rol es obligatorio");
        }

        if (request.getCelular() == null || request.getCelular().isBlank()) {
            throw new RuntimeException("El celular es obligatorio");
        }

        String celular = request.getCelular().trim();

        if (!celular.matches("\\+57\\d{10}")) {
            throw new RuntimeException(
        "El celular debe tener el formato +57 seguido de 10 dígitos"            );
        }

        boolean tieneCorreo =
                request.getCorreo() != null && !request.getCorreo().isBlank();

        boolean tieneContrasena =
                request.getContrasena() != null && !request.getContrasena().isBlank();

        if (tieneCorreo != tieneContrasena) {
            throw new RuntimeException(
                    "El correo y la contraseña deben registrarse juntos"
            );
        }

        if (tieneCorreo) {
            String correo = request.getCorreo().trim().toLowerCase();

            if (!correo.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                throw new RuntimeException("El correo electrónico no es válido");
            }
        }

        if (tieneContrasena && request.getContrasena().trim().length() < LARGO_MINIMO_CONTRASENA) {
            throw new RuntimeException(MENSAJE_CONTRASENA_CORTA);
        }
        String rol = request.getRol().trim().toUpperCase();

if (rol.equals("PERSONA_MAYOR")
        || rol.equals("ACOMPANANTE")
        || rol.equals("VOLUNTARIO")) {

    if (request.getFechaNacimiento() == null
            || request.getFechaNacimiento().isBlank()) {
        throw new RuntimeException(
                "La fecha de nacimiento es obligatoria"
        );
    }

    if (request.getGenero() == null
            || request.getGenero().isBlank()) {
        throw new RuntimeException(
                "El género es obligatorio"
        );
    }

    if (request.getDireccion() == null
            || request.getDireccion().isBlank()) {
        throw new RuntimeException(
                "La dirección es obligatoria"
        );
    }
}

if (rol.equals("ORGANIZACION")) {

    if (request.getDireccion() == null
            || request.getDireccion().isBlank()) {
        throw new RuntimeException(
                "La dirección de la organización es obligatoria"
        );
    }
}

        // El formulario ya lo exige, pero la constancia solo vale si el
        // backend también lo comprueba.
        if (!Boolean.TRUE.equals(request.getAceptaTerminos())) {
            throw new RuntimeException(
                    "Debes aceptar los términos y condiciones para crear tu cuenta"
            );
        }
    }
}
