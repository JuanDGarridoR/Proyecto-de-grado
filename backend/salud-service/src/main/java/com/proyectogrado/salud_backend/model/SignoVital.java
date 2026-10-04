package com.proyectogrado.salud_backend.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Medición de signos vitales de una persona mayor. Cada valor es opcional.
 * Unidades: presión en mmHg, frecuencias por minuto, temperatura en °C,
 * saturación en %, peso en kg y estatura en cm.
 */
@Entity
@Table(name = "signo_vital")
public class SignoVital {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_signo_vital")
    private Integer idSignoVital;

    @Column(name = "id_persona_mayor", nullable = false)
    private Integer idPersonaMayor;

    @Column(name = "fecha_hora", nullable = false)
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

    public SignoVital() {
    }

    public Integer getIdSignoVital() {
        return idSignoVital;
    }

    public void setIdSignoVital(Integer idSignoVital) {
        this.idSignoVital = idSignoVital;
    }

    public Integer getIdPersonaMayor() {
        return idPersonaMayor;
    }

    public void setIdPersonaMayor(Integer idPersonaMayor) {
        this.idPersonaMayor = idPersonaMayor;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public Integer getPresionSistolica() {
        return presionSistolica;
    }

    public void setPresionSistolica(Integer presionSistolica) {
        this.presionSistolica = presionSistolica;
    }

    public Integer getPresionDiastolica() {
        return presionDiastolica;
    }

    public void setPresionDiastolica(Integer presionDiastolica) {
        this.presionDiastolica = presionDiastolica;
    }

    public Integer getFrecuenciaCardiaca() {
        return frecuenciaCardiaca;
    }

    public void setFrecuenciaCardiaca(Integer frecuenciaCardiaca) {
        this.frecuenciaCardiaca = frecuenciaCardiaca;
    }

    public Double getTemperatura() {
        return temperatura;
    }

    public void setTemperatura(Double temperatura) {
        this.temperatura = temperatura;
    }

    public Integer getSaturacionOxigeno() {
        return saturacionOxigeno;
    }

    public void setSaturacionOxigeno(Integer saturacionOxigeno) {
        this.saturacionOxigeno = saturacionOxigeno;
    }

    public Integer getFrecuenciaRespiratoria() {
        return frecuenciaRespiratoria;
    }

    public void setFrecuenciaRespiratoria(Integer frecuenciaRespiratoria) {
        this.frecuenciaRespiratoria = frecuenciaRespiratoria;
    }

    public Double getPeso() {
        return peso;
    }

    public void setPeso(Double peso) {
        this.peso = peso;
    }

    public Double getEstatura() {
        return estatura;
    }

    public void setEstatura(Double estatura) {
        this.estatura = estatura;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}