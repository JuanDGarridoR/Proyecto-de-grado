package com.proyectogrado.persona_mayor_service.dto;

/**
 * Acompañante tal como lo recibe el frontend. relacion es el parentesco
 * que indicó la persona mayor al agregarlo.
 */
public record AcompananteResponse(
        Integer idUsuario,
        String nombre,
        String celular,
        String relacion,
        /** URL pública de la foto de perfil, o null si no tiene. */
        String fotoPerfil
) {
}
