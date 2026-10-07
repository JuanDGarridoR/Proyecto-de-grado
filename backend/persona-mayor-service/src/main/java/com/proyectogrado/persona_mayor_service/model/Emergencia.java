package com.proyectogrado.persona_mayor_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Cada vez que una persona mayor activa el botón de emergencia. Se guarda
 * aunque el SMS no salga, para que sus acompañantes también lo vean en la
 * aplicación (acompanante-service lee esta tabla).
 */
@Entity
@Table(name = "emergencia", indexes = @Index(name = "idx_emergencia_persona", columnList = "id_persona_mayor"))
public class Emergencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_emergencia")
    private Integer idEmergencia;

    @Column(name = "id_persona_mayor", nullable = false)
    private Integer idPersonaMayor;

    /** Hora de Colombia. */
    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    public Emergencia() {
    }

    public Emergencia(Integer idPersonaMayor, LocalDateTime fechaHora) {
        this.idPersonaMayor = idPersonaMayor;
        this.fechaHora = fechaHora;
    }

    public Integer getIdEmergencia() {
        return idEmergencia;
    }

    public Integer getIdPersonaMayor() {
        return idPersonaMayor;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }
}
