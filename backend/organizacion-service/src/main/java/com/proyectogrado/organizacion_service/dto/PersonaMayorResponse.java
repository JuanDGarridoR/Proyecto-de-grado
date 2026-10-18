package com.proyectogrado.organizacion_service.dto;

/**
 * Datos básicos de una persona mayor vinculada a la organización.
 */
public record PersonaMayorResponse(
        Integer idUsuario,
        String nombre,
        String celular,
        String correo,
        /** Ruta de la foto de perfil (se muestra con GET /api/auth/fotos/{ruta}), o null si no tiene. */
        String fotoPerfil
) {
}
