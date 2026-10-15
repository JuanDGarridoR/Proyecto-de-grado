package com.proyectogrado.organizacion_service.dto;

/**
 * Cambios del perfil de la organización: la dirección. Su ubicación en el
 * mapa no se edita aquí: analitica-service la saca de la dirección.
 */
public class ActualizarOrganizacionRequest {

    private String direccion;

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }
}
