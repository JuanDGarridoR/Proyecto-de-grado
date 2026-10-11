package com.proyectogrado.auth_backend.dto;

/**
 * Razón por la que se elimina la cuenta (uno de los códigos de
 * RetiroCuenta.RAZONES) y un comentario opcional.
 */
public record EliminarCuentaRequest(String razon, String comentario) {
}
