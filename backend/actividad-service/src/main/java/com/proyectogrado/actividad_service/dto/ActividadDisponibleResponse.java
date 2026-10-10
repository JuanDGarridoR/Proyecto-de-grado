package com.proyectogrado.actividad_service.dto;

import java.time.LocalDate;

/**
 * Actividad tal como la ve la persona mayor; inscrito indica si ya se inscribió.
 */
public class ActividadDisponibleResponse {

    private Integer idActividad;
    private String nombre;
    private String descripcion;
    private LocalDate fecha;
    private String hora;
    private String lugar;
    private String tipo;
    private Integer cupos;
    private boolean inscrito;

    /** Cada cuántos días se repite; null si no se repite. */
    private Integer frecuenciaDias;

    public ActividadDisponibleResponse() {
    }

    public Integer getFrecuenciaDias() {
        return frecuenciaDias;
    }

    public void setFrecuenciaDias(Integer frecuenciaDias) {
        this.frecuenciaDias = frecuenciaDias;
    }

    public ActividadDisponibleResponse(
            Integer idActividad,
            String nombre,
            String descripcion,
            LocalDate fecha,
            String hora,
            String lugar,
            String tipo,
            Integer cupos,
            boolean inscrito) {

        this.idActividad = idActividad;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.fecha = fecha;
        this.hora = hora;
        this.lugar = lugar;
        this.tipo = tipo;
        this.cupos = cupos;
        this.inscrito = inscrito;
    }

    public Integer getIdActividad() {
        return idActividad;
    }

    public void setIdActividad(Integer idActividad) {
        this.idActividad = idActividad;
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

    public String getLugar() {
        return lugar;
    }

    public void setLugar(String lugar) {
        this.lugar = lugar;
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

    public boolean isInscrito() {
        return inscrito;
    }

    public void setInscrito(boolean inscrito) {
        this.inscrito = inscrito;
    }
}
