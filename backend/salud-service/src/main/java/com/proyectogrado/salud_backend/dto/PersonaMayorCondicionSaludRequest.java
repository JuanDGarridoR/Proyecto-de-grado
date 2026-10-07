package com.proyectogrado.salud_backend.dto;

/**
 * Datos del formulario para registrar o editar una enfermedad, alergia o
 * discapacidad. Al editar, idCondicionSalud no se tiene en cuenta.
 */
public class PersonaMayorCondicionSaludRequest {

    private Integer idCondicionSalud;
    private String severidad;   // LEVE, MODERADA o SEVERA; opcional
    private String detalle;     // obligatorio solo en la opción "Otra"

    public Integer getIdCondicionSalud() {
        return idCondicionSalud;
    }

    public void setIdCondicionSalud(Integer idCondicionSalud) {
        this.idCondicionSalud = idCondicionSalud;
    }

    public String getSeveridad() {
        return severidad;
    }

    public void setSeveridad(String severidad) {
        this.severidad = severidad;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }
}
