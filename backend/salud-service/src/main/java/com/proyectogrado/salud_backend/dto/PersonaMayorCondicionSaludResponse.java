package com.proyectogrado.salud_backend.dto;

/**
 * Enfermedad, alergia o discapacidad de la persona mayor tal como la recibe
 * el frontend, con los datos del catálogo ya incluidos.
 */
public class PersonaMayorCondicionSaludResponse {

    private Integer idPersonaMayorCondicionSalud;
    private Integer idCondicionSalud;
    private String tipo;           // ENFERMEDAD, ALERGIA o DISCAPACIDAD
    private String nombre;
    private String categoria;
    private Boolean esOtra;
    private String severidad;      // LEVE, MODERADA, SEVERA o null
    private String detalle;
    private String fechaRegistro;  // "yyyy-MM-dd"

    public PersonaMayorCondicionSaludResponse(Integer idPersonaMayorCondicionSalud, Integer idCondicionSalud,
                                              String tipo, String nombre, String categoria, Boolean esOtra,
                                              String severidad, String detalle, String fechaRegistro) {
        this.idPersonaMayorCondicionSalud = idPersonaMayorCondicionSalud;
        this.idCondicionSalud = idCondicionSalud;
        this.tipo = tipo;
        this.nombre = nombre;
        this.categoria = categoria;
        this.esOtra = esOtra;
        this.severidad = severidad;
        this.detalle = detalle;
        this.fechaRegistro = fechaRegistro;
    }

    public Integer getIdPersonaMayorCondicionSalud() {
        return idPersonaMayorCondicionSalud;
    }

    public Integer getIdCondicionSalud() {
        return idCondicionSalud;
    }

    public String getTipo() {
        return tipo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCategoria() {
        return categoria;
    }

    public Boolean getEsOtra() {
        return esOtra;
    }

    public String getSeveridad() {
        return severidad;
    }

    public String getDetalle() {
        return detalle;
    }

    public String getFechaRegistro() {
        return fechaRegistro;
    }
}
