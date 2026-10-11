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

import java.time.LocalDate;
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
            ValidacionCorreoService validacionCorreoService
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

        boolean contrasenaCorrecta = passwordEncoder.matches(
                request.getContrasena(),
                usuario.getContrasenaHash()
        );

        if (!contrasenaCorrecta) {
            throw new RuntimeException("Correo o contraseña incorrectos");
        }

        return generarRespuestaLogin(usuario, "Inicio de sesión exitoso");
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

    if (request.getContrasena().length() < 6) {

        throw new RuntimeException(
                "La contraseña debe tener mínimo 6 caracteres"
        );
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

        usuarioRepository.save(usuario);
    }

    /** Lo usa el login para no enviar un código por SMS a un celular sin cuenta. */
    public boolean existeCelular(String celular) {
    if (celular == null || celular.isBlank()) {
        return false;
    }

        return usuarioRepository.existsByCelular(celular);
    }

    /** Respuesta de login con un token nuevo. Si el usuario tiene varios roles, se usa el primero. */
    private LoginResponse generarRespuestaLogin(Usuario usuario, String mensaje) {

        List<UsuarioRol> usuariosRoles =
                usuarioRolRepository.findByUsuario_IdUsuario(
                        usuario.getIdUsuario()
                );

        String rol = usuariosRoles.stream()
                .filter(usuarioRol -> usuarioRol.getRol() != null)
                .map(usuarioRol -> usuarioRol.getRol().getNombre())
                .findFirst()
                .orElse("SIN_ROL");

        String token = jwtService.generarToken(
                usuario.getIdUsuario(),
                rol
        );

        return new LoginResponse(
                token,
                rol,
                mensaje,
                usuario.getIdUsuario(),
                usuario.getNombreUsuario()
        );
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

        String token = jwtService.generarToken(
                usuarioGuardado.getIdUsuario(),
                rol.getNombre()
        );

        return new LoginResponse(
                token,
                rol.getNombre(),
                "Cuenta creada exitosamente",
                usuarioGuardado.getIdUsuario(),
                usuarioGuardado.getNombreUsuario()
        );
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

        if (tieneContrasena && request.getContrasena().trim().length() < 6) {
            throw new RuntimeException(
                    "La contraseña debe tener mínimo 6 caracteres"
            );
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
    }
}
