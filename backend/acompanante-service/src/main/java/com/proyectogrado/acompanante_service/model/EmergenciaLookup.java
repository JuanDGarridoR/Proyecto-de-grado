package com.proyectogrado.acompanante_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Vista de solo lectura de la tabla emergencia, que pertenece a
 * persona-mayor-service: cada vez que una persona mayor activó el botón de
 * emergencia.
 */
@Entity
@Table(name = "emergencia")
public class EmergenciaLookup {

    // Igual que en persona-mayor-service: si este servicio arranca primero en
    // una base nueva y crea la tabla, el id debe quedar autogenerado.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_emergencia")
    private Integer idEmergencia;

    @Column(name = "id_persona_mayor", nullable = false)
    private Integer idPersonaMayor;

    /** Hora de Colombia. */
    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

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
