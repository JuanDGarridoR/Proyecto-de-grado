package com.proyectogrado.persona_mayor_service.dto;

/**
 * Organización vinculada a la persona mayor (o que le envió una solicitud),
 * con los datos de contacto de su cuenta.
 */
public record OrganizacionSolicitudResponse(
        Integer idOrganizacion,
        String nombre,
        String celular,
        String correo,
        /** URL pública de la foto de perfil, o null si no tiene. */
        String fotoPerfil
) {
}
