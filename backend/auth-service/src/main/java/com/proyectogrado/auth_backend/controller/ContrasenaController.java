package com.proyectogrado.auth_backend.controller;

import com.proyectogrado.auth_backend.dto.CambiarContrasenaRequest;
import com.proyectogrado.auth_backend.model.Usuario;
import com.proyectogrado.auth_backend.repository.UsuarioRepository;
import com.proyectogrado.auth_backend.security.JwtService;
import com.proyectogrado.auth_backend.service.AuthService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cambio de contraseña desde "Mi información", para cualquier rol. Está en
 * auth-service porque la contraseña es del usuario, no de un rol.
 */
@RestController
@RequestMapping("/api/auth/contrasena")
public class ContrasenaController {

    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public ContrasenaController(
            UsuarioRepository usuarioRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder
    ) {
        this.usuarioRepository = usuarioRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Guarda la nueva contraseña. Si el usuario ya tenía una, primero se
     * comprueba la actual; si se registró solo con OTP, se crea sin pedirla.
     */
    @PutMapping
    public ResponseEntity<String> cambiarContrasena(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody CambiarContrasenaRequest request
    ) {
        // En /api/auth el gateway no agrega X-User-Id: el id se saca del token.
        String token = authorizationHeader.substring(7);
        Integer idUsuario = jwtService.extraerIdUsuario(token);

        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String nuevaContrasena = request.getNuevaContrasena();

        if (nuevaContrasena == null || nuevaContrasena.isBlank()) {
            return ResponseEntity.badRequest().body("La nueva contraseña es obligatoria");
        }

        if (nuevaContrasena.length() < AuthService.LARGO_MINIMO_CONTRASENA) {
            return ResponseEntity.badRequest().body(AuthService.MENSAJE_CONTRASENA_CORTA);
        }

        boolean tieneContrasena = usuario.getContrasenaHash() != null
                && !usuario.getContrasenaHash().isBlank();

        if (tieneContrasena) {

            String contrasenaActual = request.getContrasenaActual();

            if (contrasenaActual == null || contrasenaActual.isBlank()) {
                return ResponseEntity.badRequest().body("Debes ingresar tu contraseña actual");
            }

            if (!passwordEncoder.matches(contrasenaActual, usuario.getContrasenaHash())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("La contraseña actual es incorrecta");
            }
        }

        usuario.setContrasenaHash(passwordEncoder.encode(nuevaContrasena));
        usuarioRepository.save(usuario);

        return ResponseEntity.ok("Contraseña guardada correctamente");
    }
}