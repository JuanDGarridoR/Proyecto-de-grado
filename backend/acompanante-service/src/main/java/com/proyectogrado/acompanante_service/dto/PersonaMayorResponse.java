package com.proyectogrado.acompanante_service.dto;

/**
 * Datos básicos de una persona mayor para las listas del acompañante.
 */
public record PersonaMayorResponse(
        Integer idUsuario,
        String nombre,
        String celular,
        String correo,
        /** URL pública de la foto de perfil, o null si no tiene. */
        String fotoPerfil
) {
}
