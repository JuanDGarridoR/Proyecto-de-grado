package com.proyectogrado.acompanante_service.dto;

/**
 * Datos básicos de una persona mayor para las listas del acompañante.
 */
public record PersonaMayorResponse(
        Integer idUsuario,
        String nombre,
        String celular,
        String correo,
        /** Ruta de la foto de perfil (se muestra con GET /api/auth/fotos/{ruta}), o null si no tiene. */
        String fotoPerfil,
        /** Parentesco del acompañante con ella (por ejemplo, "Hijo"); null si no se indicó. */
        String relacion
) {
}
