package com.proyectogrado.auth_backend.service;

/**
 * La cuenta está bloqueada por demasiadas contraseñas incorrectas seguidas
 * (ver AuthService.login). El controlador la responde con 429.
 */
public class CuentaBloqueadaException extends RuntimeException {

    public CuentaBloqueadaException(long minutosRestantes) {
        super("Por seguridad, tu cuenta está bloqueada por varios intentos fallidos. "
                + "Intenta de nuevo en " + minutosRestantes
                + (minutosRestantes == 1 ? " minuto" : " minutos")
                + ", o entra con tu celular y el código por SMS.");
    }
}
