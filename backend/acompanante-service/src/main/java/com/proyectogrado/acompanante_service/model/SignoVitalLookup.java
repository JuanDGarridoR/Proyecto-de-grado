package com.proyectogrado.acompanante_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Vista de solo lectura de la tabla signo_vital, que pertenece a
 * salud-service. Permite que el acompañante vea los signos vitales de la
 * persona mayor.
 */
@Entity
@Table(name = "signo_vital")
public class SignoVitalLookup {

    // Igual que en salud-service: si este servicio arranca primero en una
    // base nueva y crea la tabla, el id debe quedar autogenerado.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_signo_vital")
    private Integer idSignoVital;

    @Column(name = "id_persona_mayor")
    private Integer idPersonaMayor;

    @Column(name = "fecha_hora")
    private LocalDateTime fechaHora;

    @Column(name = "presion_sistolica")
    private Integer presionSistolica;

    @Column(name = "presion_diastolica")
    private Integer presionDiastolica;

    @Column(name = "frecuencia_cardiaca")
    private Integer frecuenciaCardiaca;

    @Column(name = "temperatura")
    private Double temperatura;

    @Column(name = "saturacion_oxigeno")
    private Integer saturacionOxigeno;

    @Column(name = "frecuencia_respiratoria")
    private Integer frecuenciaRespiratoria;

    @Column(name = "peso")
    private Double peso;

    @Column(name = "estatura")
    private Double estatura;

    @Column(name = "observaciones")
    private String observaciones;

    /** Lo exige JPA. */

    protected SignoVitalLookup() {
    }

    public Integer getIdSignoVital() {
        return idSignoVital;
    }

    public Integer getIdPersonaMayor() {
        return idPersonaMayor;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public Integer getPresionSistolica() {
        return presionSistolica;
    }

    public Integer getPresionDiastolica() {
        return presionDiastolica;
    }

    public Integer getFrecuenciaCardiaca() {
        return frecuenciaCardiaca;
    }

    public Double getTemperatura() {
        return temperatura;
    }

    public Integer getSaturacionOxigeno() {
        return saturacionOxigeno;
    }

    public Integer getFrecuenciaRespiratoria() {
        return frecuenciaRespiratoria;
    }

    public Double getPeso() {
        return peso;
    }

    public Double getEstatura() {
        return estatura;
    }

    public String getObservaciones() {
        return observaciones;
    }
}
