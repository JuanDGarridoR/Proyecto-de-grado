package com.proyectogrado.auth_backend.controller;

import com.proyectogrado.auth_backend.dto.EliminarCuentaRequest;
import com.proyectogrado.auth_backend.model.RetiroCuenta;
import com.proyectogrado.auth_backend.security.JwtService;
import com.proyectogrado.auth_backend.service.CuentaService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Eliminación, inactivación y reactivación de la cuenta del usuario
 * autenticado, para cualquier rol. El id sale del token, así que cada
 * usuario solo puede cambiar la suya.
 */
@RestController
@RequestMapping("/api/auth/cuenta")
public class CuentaController {

    private final CuentaService cuentaService;
    private final JwtService jwtService;

    public CuentaController(CuentaService cuentaService, JwtService jwtService) {
        this.cuentaService = cuentaService;
        this.jwtService = jwtService;
    }

    /**
     * Borra la cuenta y todo lo relacionado con ella (ver CuentaService).
     * Pide la razón del retiro, que queda guardada para las organizaciones.
     */
    @DeleteMapping
    public ResponseEntity<String> eliminarCuenta(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody(required = false) EliminarCuentaRequest request
    ) {
        String token = authorizationHeader.substring(7);
        Integer idUsuario = jwtService.extraerIdUsuario(token);

        // Las organizaciones tienen su propia lista de razones.
        String razon = request != null && request.razon() != null ? request.razon().trim() : null;
        if (razon == null || !RetiroCuenta.esRazonValida(jwtService.extraerRol(token), razon)) {
            return ResponseEntity.badRequest().body("Selecciona la razón por la que eliminas tu cuenta");
        }

        String comentario = request.comentario() == null || request.comentario().isBlank()
                ? null
                : request.comentario().trim();
        if (comentario != null && comentario.length() > RetiroCuenta.MAX_COMENTARIO) {
            return ResponseEntity.badRequest()
                    .body("El comentario no puede tener más de " + RetiroCuenta.MAX_COMENTARIO + " caracteres");
        }

        try {
            cuentaService.eliminarCuenta(idUsuario, razon, comentario);
        } catch (RuntimeException e) {
            System.err.println("Error al eliminar la cuenta " + idUsuario + ": " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("No se pudo eliminar la cuenta");
        }

        return ResponseEntity.ok("Cuenta eliminada correctamente");
    }

    /** La cuenta deja de verse para sus organizaciones, acompañantes y demás vínculos. */
    @PutMapping("/inactivar")
    public ResponseEntity<String> inactivarCuenta(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Integer idUsuario = jwtService.extraerIdUsuario(authorizationHeader.substring(7));

        try {
            cuentaService.inactivarCuenta(idUsuario);
        } catch (RuntimeException e) {
            System.err.println("Error al inactivar la cuenta " + idUsuario + ": " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("No se pudo inactivar la cuenta");
        }

        return ResponseEntity.ok("Tu cuenta quedó inactiva. Puedes reactivarla cuando quieras desde tu perfil.");
    }

    /** Vuelve a mostrar la cuenta en sus vínculos (los que tengan la otra parte activa). */
    @PutMapping("/reactivar")
    public ResponseEntity<String> reactivarCuenta(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Integer idUsuario = jwtService.extraerIdUsuario(authorizationHeader.substring(7));

        try {
            cuentaService.reactivarCuenta(idUsuario);
        } catch (RuntimeException e) {
            System.err.println("Error al reactivar la cuenta " + idUsuario + ": " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("No se pudo reactivar la cuenta");
        }

        return ResponseEntity.ok("Tu cuenta está activa de nuevo.");
    }
}
