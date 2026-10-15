package com.proyectogrado.organizacion_service.dto;

/**
 * Datos de la organización tal como los recibe el frontend.
 */
public class OrganizacionInformacionResponse {

    private Integer idOrganizacion;
    private String nombre;
    private String correo;
    private String celular;
    private String direccion;
    /** Ubicación en el mapa; null si no la ha marcado. */
    private Double latitud;
    private Double longitud;

    public OrganizacionInformacionResponse(Integer idOrganizacion, String nombre, String correo,
                                            String celular, String direccion,
                                            Double latitud, Double longitud) {
        this.idOrganizacion = idOrganizacion;
        this.nombre = nombre;
        this.correo = correo;
        this.celular = celular;
        this.direccion = direccion;
        this.latitud = latitud;
        this.longitud = longitud;
    }

    public Double getLatitud() {
        return latitud;
    }

    public Double getLongitud() {
        return longitud;
    }

    public Integer getIdOrganizacion() {
        return idOrganizacion;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public String getCelular() {
        return celular;
    }

    public String getDireccion() {
        return direccion;
    }
}