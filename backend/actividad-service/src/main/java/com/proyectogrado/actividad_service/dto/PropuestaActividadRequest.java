package com.proyectogrado.actividad_service.dto;

/**
 * Formulario de actividad que envía un voluntario o una persona mayor: los
 * mismos campos que crea una organización, más la organización a la que la
 * presenta.
 */
public class PropuestaActividadRequest extends ActividadRequest {

    private Integer idOrganizacion;

    public PropuestaActividadRequest() {
    }

    public Integer getIdOrganizacion() {
        return idOrganizacion;
    }

    public void setIdOrganizacion(Integer idOrganizacion) {
        this.idOrganizacion = idOrganizacion;
    }
}
