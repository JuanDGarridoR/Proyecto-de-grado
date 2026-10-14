package com.proyectogrado.salud_backend.dto;

/**
 * Datos para crear o editar una cita médica.
 */
public class CitaMedicaRequest {

    private String titulo;
    private String lugar;
    private String consultorio;   // opcional
    private String fecha;   // "yyyy-MM-dd"
    private String hora;    // "HH:mm"
    private String observaciones;
    /** NO, REQUERIDO o PREFERIDO (ver CitaMedica); vacío cuenta como NO. */
    private String acompanamiento;

    public CitaMedicaRequest() {
    }

    public String getAcompanamiento() {
        return acompanamiento;
    }

    public void setAcompanamiento(String acompanamiento) {
        this.acompanamiento = acompanamiento;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getLugar() {
        return lugar;
    }

    public void setLugar(String lugar) {
        this.lugar = lugar;
    }

    public String getConsultorio() {
        return consultorio;
    }

    public void setConsultorio(String consultorio) {
        this.consultorio = consultorio;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public String getHora() {
        return hora;
    }

    public void setHora(String hora) {
        this.hora = hora;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}