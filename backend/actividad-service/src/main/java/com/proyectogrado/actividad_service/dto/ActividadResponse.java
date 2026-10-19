package com.proyectogrado.actividad_service.dto;

import java.time.LocalDate;

/**
 * Actividad tal como la recibe el frontend.
 */
public class ActividadResponse {

    private Integer idActividad;
    private Integer idOrganizacion;
    private String nombre;
    private String descripcion;
    private LocalDate fecha;
    private String hora;
    private String horaFin;
    private String lugar;

    /** Punto de encuentro en el mapa; null si la actividad no tiene. */
    private Double latitud;
    private Double longitud;

    private String tipo;
    private Integer cupos;
    private String responsable;

    /** Cada cuántos días se repite; null si no se repite. */
    private Integer frecuenciaDias;

    public ActividadResponse() {
    }

    public Integer getFrecuenciaDias() {
        return frecuenciaDias;
    }

    public void setFrecuenciaDias(Integer frecuenciaDias) {
        this.frecuenciaDias = frecuenciaDias;
    }

    public ActividadResponse(Integer idActividad, Integer idOrganizacion, String nombre,
                              String descripcion, LocalDate fecha, String hora,
                              String lugar, String tipo, Integer cupos,
                              String responsable) {
        this.idActividad = idActividad;
        this.idOrganizacion = idOrganizacion;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.fecha = fecha;
        this.hora = hora;
        this.lugar = lugar;
        this.tipo = tipo;
        this.cupos = cupos;
        this.responsable = responsable;
    }

    public Integer getIdActividad() {
        return idActividad;
    }

    public void setIdActividad(Integer idActividad) {
        this.idActividad = idActividad;
    }

    public Integer getIdOrganizacion() {
        return idOrganizacion;
    }

    public void setIdOrganizacion(Integer idOrganizacion) {
        this.idOrganizacion = idOrganizacion;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getHora() {
        return hora;
    }

    public void setHora(String hora) {
        this.hora = hora;
    }

    public String getHoraFin() {
        return horaFin;
    }

    public void setHoraFin(String horaFin) {
        this.horaFin = horaFin;
    }

    public String getLugar() {
        return lugar;
    }

    public void setLugar(String lugar) {
        this.lugar = lugar;
    }

    public Double getLatitud() {
        return latitud;
    }

    public void setLatitud(Double latitud) {
        this.latitud = latitud;
    }

    public Double getLongitud() {
        return longitud;
    }

    public void setLongitud(Double longitud) {
        this.longitud = longitud;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Integer getCupos() {
        return cupos;
    }

    public void setCupos(Integer cupos) {
        this.cupos = cupos;
    }

    public String getResponsable() {
        return responsable;
    }

    public void setResponsable(String responsable) {
        this.responsable = responsable;
    }
}
