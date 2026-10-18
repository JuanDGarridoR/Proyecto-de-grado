package com.proyectogrado.auth_backend.controller;

import com.proyectogrado.auth_backend.dto.LoginResponse;
import com.proyectogrado.auth_backend.service.AuthService;
import com.proyectogrado.auth_backend.service.SesionInvalidaException;
import com.proyectogrado.auth_backend.service.SesionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Renovar y cerrar la sesión con el token de renovación (ver SesionService).
 * Son públicas porque se llaman justo cuando el token de acceso venció.
 */
@RestController
@RequestMapping("/api/auth/sesion")
public class SesionController {

    /** Token de renovación y, al renovar, el rol que está usando la pestaña. */
    public record SesionRequest(String refreshToken, String rol) {
    }

    private final AuthService authService;
    private final SesionService sesionService;

    public SesionController(AuthService authService, SesionService sesionService) {
        this.authService = authService;
        this.sesionService = sesionService;
    }

    /** Token de acceso nuevo. Si la sesión ya no está abierta, 401 y hay que volver a entrar. */
    @PostMapping("/renovar")
    public ResponseEntity<LoginResponse> renovar(@RequestBody SesionRequest request) {
        try {
            return ResponseEntity.ok(authService.renovarSesion(request.refreshToken(), request.rol()));
        } catch (SesionInvalidaException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(null, null, e.getMessage(), null, null));
        }
    }

    /** Cierra la sesión. Responde 204 aunque ya estuviera cerrada. */
    @PostMapping("/cerrar")
    public ResponseEntity<Void> cerrar(@RequestBody SesionRequest request) {
        sesionService.cerrar(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
