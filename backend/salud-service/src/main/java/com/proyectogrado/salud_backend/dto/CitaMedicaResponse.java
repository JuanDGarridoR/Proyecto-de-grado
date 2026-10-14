package com.proyectogrado.salud_backend.dto;

/**
 * Cita médica tal como la devuelve la API.
 */
public class CitaMedicaResponse {

    private Integer idCita;
    private String titulo;
    private String lugar;
    private String consultorio;
    private String fecha;   // "yyyy-MM-dd"
    private String hora;    // "HH:mm"
    private String observaciones;
    /** NO, REQUERIDO o PREFERIDO. */
    private String acompanamiento;
    /** Quién aceptó acompañarla y dónde se encuentran; null si nadie ha aceptado. */
    private Integer idAcompananteCita;
    private String nombreAcompananteCita;
    private String puntoEncuentro;

    public CitaMedicaResponse(
            Integer idCita,
            String titulo,
            String lugar,
            String consultorio,
            String fecha,
            String hora,
            String observaciones,
            String acompanamiento,
            Integer idAcompananteCita,
            String nombreAcompananteCita,
            String puntoEncuentro
    ) {
        this.acompanamiento = acompanamiento;
        this.idAcompananteCita = idAcompananteCita;
        this.nombreAcompananteCita = nombreAcompananteCita;
        this.puntoEncuentro = puntoEncuentro;
        this.idCita = idCita;
        this.titulo = titulo;
        this.lugar = lugar;
        this.consultorio = consultorio;
        this.fecha = fecha;
        this.hora = hora;
        this.observaciones = observaciones;
    }

    public Integer getIdCita() {
        return idCita;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getLugar() {
        return lugar;
    }

    public String getConsultorio() {
        return consultorio;
    }

    public String getFecha() {
        return fecha;
    }

    public String getHora() {
        return hora;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public String getAcompanamiento() {
        return acompanamiento;
    }

    public Integer getIdAcompananteCita() {
        return idAcompananteCita;
    }

    public String getNombreAcompananteCita() {
        return nombreAcompananteCita;
    }

    public String getPuntoEncuentro() {
        return puntoEncuentro;
    }
}
