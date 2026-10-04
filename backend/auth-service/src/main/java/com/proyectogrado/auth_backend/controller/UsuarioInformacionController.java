package com.proyectogrado.auth_backend.controller;

import com.proyectogrado.auth_backend.dto.ActualizarInformacionRequest;
import com.proyectogrado.auth_backend.dto.InformacionUsuarioResponse;
import com.proyectogrado.auth_backend.model.PersonaMayor;
import com.proyectogrado.auth_backend.model.Usuario;
import com.proyectogrado.auth_backend.repository.PersonaMayorRepository;
import com.proyectogrado.auth_backend.repository.UsuarioRepository;
import com.proyectogrado.auth_backend.security.JwtService;
import com.proyectogrado.auth_backend.service.EmailValidationService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Mi información": nombre, correo, celular, fecha de nacimiento, género y
 * dirección del usuario, para cualquier rol. Los paneles de todos los roles
 * usan este endpoint en lugar de repetir la lógica en cada servicio. A la
 * persona mayor también se le guardan aquí la EPS, la IPS y la dirección
 * de la IPS.
 *
 * El celular no se edita aquí porque es con el que se inicia sesión por OTP.
 */
@RestController
@RequestMapping("/api/auth/informacion")
public class UsuarioInformacionController {

    /** Largo máximo del nombre de la EPS o la IPS. */
    private static final int MAX_SALUD = 120;

    /** Largo máximo de la dirección de la IPS. */
    private static final int MAX_DIRECCION_IPS = 200;

    private final UsuarioRepository usuarioRepository;
    private final PersonaMayorRepository personaMayorRepository;
    private final JwtService jwtService;
    private final EmailValidationService emailValidationService;

    public UsuarioInformacionController(
            UsuarioRepository usuarioRepository,
            PersonaMayorRepository personaMayorRepository,
            JwtService jwtService,
            EmailValidationService emailValidationService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.personaMayorRepository = personaMayorRepository;
        this.jwtService = jwtService;
        this.emailValidationService = emailValidationService;
    }

    /** Datos del usuario autenticado. */
    @GetMapping
    public ResponseEntity<InformacionUsuarioResponse> obtenerInformacion(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Usuario usuario = usuarioDelToken(authorizationHeader);

        return ResponseEntity.ok(aRespuesta(usuario));
    }

    /** Guarda los cambios. Si el correo cambió, antes se valida con Hunter. */
    @PutMapping
    public ResponseEntity<?> actualizarInformacion(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody ActualizarInformacionRequest request
    ) {
        Usuario usuario = usuarioDelToken(authorizationHeader);

        if (request.getNombre() == null || request.getNombre().isBlank()) {
            return ResponseEntity.badRequest().body("El nombre es obligatorio");
        }

        String eps = textoOpcional(request.getEps());
        String ips = textoOpcional(request.getIps());

        if ((eps != null && eps.length() > MAX_SALUD) || (ips != null && ips.length() > MAX_SALUD)) {
            return ResponseEntity.badRequest()
                    .body("El nombre de la EPS o IPS no puede tener más de " + MAX_SALUD + " caracteres");
        }

        String direccionIps = textoOpcional(request.getDireccionIps());

        if (direccionIps != null && direccionIps.length() > MAX_DIRECCION_IPS) {
            return ResponseEntity.badRequest()
                    .body("La dirección de la IPS no puede tener más de " + MAX_DIRECCION_IPS + " caracteres");
        }

        String correo = request.getCorreo() == null || request.getCorreo().isBlank()
                ? null
                : request.getCorreo().trim().toLowerCase();

        // Solo se verifica si el correo cambió, para no gastar consultas de
        // Hunter cada vez que se edita otro dato.
        if (correo != null && !correo.equalsIgnoreCase(usuario.getCorreo())) {

            if (usuarioRepository.existsByCorreo(correo)) {
                return ResponseEntity.badRequest()
                        .body("Ya existe un usuario registrado con ese correo");
            }

            try {
                if (!emailValidationService.puedeRecibirCorreos(correo)) {
                    return ResponseEntity.badRequest().body(
                            "El correo electrónico no parece ser válido o no puede recibir correos. "
                                    + "Verifica que esté escrito correctamente."
                    );
                }
            } catch (RuntimeException e) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body("No se pudo verificar el correo en este momento. Intenta de nuevo más tarde.");
            }
        }

usuario.setNombreUsuario(request.getNombre().trim());
usuario.setCorreo(correo);
usuario.setFechaNacimiento(request.getFechaNacimiento());
usuario.setGenero(request.getGenero());
usuario.setDireccion(request.getDireccion());

usuario = usuarioRepository.save(usuario);

        // La EPS y la IPS (con su dirección) están en la tabla persona_mayor: solo se guardan si
        // el usuario es persona mayor. Para los demás roles se ignoran.
        personaMayorRepository.findById(usuario.getIdUsuario()).ifPresent(personaMayor -> {
            personaMayor.setEps(eps);
            personaMayor.setIps(ips);
            personaMayor.setDireccionIps(direccionIps);
            personaMayorRepository.save(personaMayor);
        });

        return ResponseEntity.ok(aRespuesta(usuario));
    }

    /** En /api/auth el gateway no agrega X-User-Id, así que el usuario se saca del token. */
    private Usuario usuarioDelToken(String authorizationHeader) {
        String token = authorizationHeader.substring(7);
        Integer idUsuario = jwtService.extraerIdUsuario(token);

        return usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    /**
     * tieneContrasena le indica al frontend si, para cambiar la contraseña,
     * debe pedir la actual.
     */
    private InformacionUsuarioResponse aRespuesta(Usuario usuario) {
        boolean tieneContrasena = usuario.getContrasenaHash() != null
                && !usuario.getContrasenaHash().isBlank();

        PersonaMayor personaMayor =
                personaMayorRepository.findById(usuario.getIdUsuario()).orElse(null);

        return new InformacionUsuarioResponse(
                usuario.getIdUsuario(),
                usuario.getNombreUsuario(),
                usuario.getCelular(),
                usuario.getCorreo(),
                usuario.getFechaNacimiento(),
                usuario.getGenero(),
                usuario.getDireccion(),
                personaMayor != null ? personaMayor.getEps() : null,
                personaMayor != null ? personaMayor.getIps() : null,
                personaMayor != null ? personaMayor.getDireccionIps() : null,
                tieneContrasena
        );
    }

    /** Texto sin espacios sobrantes, o null si viene vacío. */
    private static String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}