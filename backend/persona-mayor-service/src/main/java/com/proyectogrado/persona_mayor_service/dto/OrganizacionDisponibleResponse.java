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
        String correo
) {
}
