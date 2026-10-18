package com.proyectogrado.auth_backend.service;

/**
 * El token de renovación no corresponde a una sesión abierta: se cerró, se
 * revocó (cambio de contraseña, cuenta eliminada) o venció. Hay que volver
 * a iniciar sesión. El controlador la responde con 401.
 */
public class SesionInvalidaException extends RuntimeException {

    public SesionInvalidaException() {
        super("Tu sesión terminó. Vuelve a iniciar sesión.");
    }
}
