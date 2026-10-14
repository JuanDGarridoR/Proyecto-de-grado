package com.proyectogrado.salud_backend.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Cita médica de una persona mayor, con el estado de sus recordatorios
 * (ver CitaMedicaRecordatorioScheduler). idPersonaMayor es un Integer simple y
 * no una relación JPA, porque la tabla persona_mayor es de auth-service.
 */
@Entity
@Table(name = "cita_medica")
public class CitaMedica {

    /** La cita no necesita acompañamiento. */
    public static final String SIN_ACOMPANAMIENTO = "NO";
    /** La cita lo requiere (por ejemplo, un procedimiento con sedación). */
    public static final String ACOMPANAMIENTO_REQUERIDO = "REQUERIDO";
    /** No lo requiere, pero la persona mayor quiere ir acompañada. */
    public static final String ACOMPANAMIENTO_PREFERIDO = "PREFERIDO";

    public static final int MAX_PUNTO_ENCUENTRO = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cita")
    private Integer idCita;

    @Column(name = "id_persona_mayor", nullable = false)
    private Integer idPersonaMayor;

    @Column(name = "titulo", nullable = false)
    private String titulo;

    @Column(name = "lugar", nullable = false)
    private String lugar;

    /** Opcional: consultorio, piso o sala dentro del lugar. */
    @Column(name = "consultorio")
    private String consultorio;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora", nullable = false)
    private LocalTime hora;

    @Column(name = "observaciones")
    private String observaciones;

    /**
     * Inicio de la cita (fecha + hora) para el que ya se envió el aviso del
     * día antes. Si la cita cambia de fecha u hora deja de coincidir y se
     * vuelve a avisar (ver CitaMedicaRepository).
     */
    @Column(name = "recordatorio_dia_enviado_para")
    private LocalDateTime recordatorioDiaEnviadoPara;

    /** Lo mismo, para el aviso de una hora antes. */
    @Column(name = "recordatorio_hora_enviado_para")
    private LocalDateTime recordatorioHoraEnviadoPara;

    /**
     * Si necesita acompañamiento: NO, REQUERIDO o PREFERIDO (ver las
     * constantes). null en las citas viejas, que cuenta como NO.
     */
    @Column(name = "acompanamiento", length = 20)
    private String acompanamiento;

    /** Acompañante que aceptó ir a la cita, o null si nadie lo ha hecho. */
    @Column(name = "id_acompanante_cita")
    private Integer idAcompananteCita;

    /** Dónde se encuentran para salir a la cita; lo escribe el acompañante al aceptar. */
    @Column(name = "punto_encuentro", length = MAX_PUNTO_ENCUENTRO)
    private String puntoEncuentro;

    @Column(name = "fecha_acompanamiento_aceptado")
    private LocalDateTime fechaAcompanamientoAceptado;

    public CitaMedica() {
    }

    public Integer getIdCita() {
        return idCita;
    }

    public void setIdCita(Integer idCita) {
        this.idCita = idCita;
    }

    public Integer getIdPersonaMayor() {
        return idPersonaMayor;
    }

    public void setIdPersonaMayor(Integer idPersonaMayor) {
        this.idPersonaMayor = idPersonaMayor;
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

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public LocalTime getHora() {
        return hora;
    }

    public void setHora(LocalTime hora) {
        this.hora = hora;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public LocalDateTime getRecordatorioDiaEnviadoPara() {
        return recordatorioDiaEnviadoPara;
    }

    public void setRecordatorioDiaEnviadoPara(LocalDateTime recordatorioDiaEnviadoPara) {
        this.recordatorioDiaEnviadoPara = recordatorioDiaEnviadoPara;
    }

    public LocalDateTime getRecordatorioHoraEnviadoPara() {
        return recordatorioHoraEnviadoPara;
    }

    public void setRecordatorioHoraEnviadoPara(LocalDateTime recordatorioHoraEnviadoPara) {
        this.recordatorioHoraEnviadoPara = recordatorioHoraEnviadoPara;
    }

    /** NO, REQUERIDO o PREFERIDO; las citas viejas (null) cuentan como NO. */
    public String getAcompanamiento() {
        return acompanamiento != null ? acompanamiento : SIN_ACOMPANAMIENTO;
    }

    /** Al quitar el acompañamiento, se libera al acompañante que había aceptado. */
    public void setAcompanamiento(String acompanamiento) {
        this.acompanamiento = acompanamiento;
        if (!necesitaAcompanamiento()) {
            liberarAcompanante();
        }
    }

    public boolean necesitaAcompanamiento() {
        return ACOMPANAMIENTO_REQUERIDO.equals(acompanamiento) || ACOMPANAMIENTO_PREFERIDO.equals(acompanamiento);
    }

    public Integer getIdAcompananteCita() {
        return idAcompananteCita;
    }

    public String getPuntoEncuentro() {
        return puntoEncuentro;
    }

    public LocalDateTime getFechaAcompanamientoAceptado() {
        return fechaAcompanamientoAceptado;
    }

    /** El acompañante acepta ir a la cita y dice dónde se encuentran. */
    public void asignarAcompanante(Integer idAcompanante, String puntoEncuentro, LocalDateTime ahora) {
        this.idAcompananteCita = idAcompanante;
        this.puntoEncuentro = puntoEncuentro;
        this.fechaAcompanamientoAceptado = ahora;
    }

    public void liberarAcompanante() {
        this.idAcompananteCita = null;
        this.puntoEncuentro = null;
        this.fechaAcompanamientoAceptado = null;
    }

    /** Fecha y hora de la cita, o null si falta alguna. */
    public LocalDateTime getInicio() {
        return fecha != null && hora != null ? fecha.atTime(hora) : null;
    }
}