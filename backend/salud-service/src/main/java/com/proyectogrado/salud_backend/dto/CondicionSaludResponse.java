package com.proyectogrado.salud_backend.dto;

/**
 * Una opción del catálogo de enfermedades, alergias y discapacidades.
 */
public class CondicionSaludResponse {

    private Integer idCondicionSalud;
    private String tipo;        // ENFERMEDAD, ALERGIA o DISCAPACIDAD
    private String nombre;
    private String categoria;
    private Boolean esOtra;

    public CondicionSaludResponse(Integer idCondicionSalud, String tipo, String nombre,
                                  String categoria, Boolean esOtra) {
        this.idCondicionSalud = idCondicionSalud;
        this.tipo = tipo;
        this.nombre = nombre;
        this.categoria = categoria;
        this.esOtra = esOtra;
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
}
