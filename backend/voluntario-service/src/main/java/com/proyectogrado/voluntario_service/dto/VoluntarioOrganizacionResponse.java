package com.proyectogrado.voluntario_service.dto;

/** Voluntario visto por la organización (vinculado o con solicitud pendiente). */
public record VoluntarioOrganizacionResponse(
        Integer idUsuario,
        String nombre,
        String celular,
        String correo,
        /** URL pública de la foto de perfil, o null si no tiene. */
        String fotoPerfil
) {
}
