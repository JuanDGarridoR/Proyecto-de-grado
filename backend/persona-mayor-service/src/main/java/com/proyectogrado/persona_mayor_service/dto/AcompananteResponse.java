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
        /** Ruta de la foto de perfil (se muestra con GET /api/auth/fotos/{ruta}), o null si no tiene. */
        String fotoPerfil
) {
}
