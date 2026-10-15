package com.proyectogrado.persona_mayor_service.dto;

/**
 * Organización a la que la persona mayor puede pedir unirse (no tiene con
 * ella un vínculo aceptado ni una solicitud pendiente).
 */
public record OrganizacionDisponibleResponse(
        Integer idOrganizacion,
        String nombre,
        String direccion,
        String celular,
        String correo,
        /** URL pública de la foto de perfil, o null si no tiene. */
        String fotoPerfil,
        /** Ubicación de la sede en el mapa; null si no la ha marcado. */
        Double latitud,
        Double longitud
) {
}
