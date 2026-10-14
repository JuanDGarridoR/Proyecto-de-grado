package com.proyectogrado.donacion_service.dto;

/** Organización a la que el acompañante o voluntario puede donar. */
public record OrganizacionDonableResponse(
        Integer idOrganizacion,
        String nombre,
        String direccion,
        String celular,
        /** URL pública de la foto de perfil, o null si no tiene. */
        String fotoPerfil
) {
}
