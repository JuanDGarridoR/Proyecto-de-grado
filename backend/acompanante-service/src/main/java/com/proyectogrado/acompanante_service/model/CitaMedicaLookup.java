package com.proyectogrado.acompanante_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Vista de solo lectura de la tabla cita_medica, que pertenece a
 * salud-service. Permite que el acompañante vea las citas médicas de la
 * persona mayor.
 */
@Entity
@Table(name = "cita_medica")
public class CitaMedicaLookup {

    // Igual que en salud-service: si este servicio arranca primero en una
    // base nueva y crea la tabla, el id debe quedar autogenerado.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cita")
    private Integer idCita;

    @Column(name = "id_persona_mayor")
    private Integer idPersonaMayor;

    @Column(name = "titulo")
    private String titulo;

    @Column(name = "lugar")
    private String lugar;

    @Column(name = "consultorio")
    private String consultorio;

    @Column(name = "fecha")
    private LocalDate fecha;

    @Column(name = "hora")
    private LocalTime hora;

    @Column(name = "observaciones")
    private String observaciones;

    /** Lo exige JPA. */
    protected CitaMedicaLookup() {
    }

    public Integer getIdCita() {
        return idCita;
    }

    public Integer getIdPersonaMayor() {
        return idPersonaMayor;
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

    public LocalDate getFecha() {
        return fecha;
    }

    public LocalTime getHora() {
        return hora;
    }

    public String getObservaciones() {
        return observaciones;
    }
}
