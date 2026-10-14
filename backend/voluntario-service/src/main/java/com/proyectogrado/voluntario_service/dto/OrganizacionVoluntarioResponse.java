package com.proyectogrado.voluntario_service.dto;

/**
 * Organización vista por el voluntario. "estado" es el de su vínculo con
 * ella: PENDIENTE, ACEPTADA, RECHAZADA o null si nunca ha solicitado.
 */
public record OrganizacionVoluntarioResponse(
        Integer idOrganizacion,
        String nombre,
        String direccion,
        String celular,
        String correo,
        String estado,
        /** URL pública de la foto de perfil, o null si no tiene. */
        String fotoPerfil
) {
}
