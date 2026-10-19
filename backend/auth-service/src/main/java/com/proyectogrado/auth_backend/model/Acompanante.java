package com.proyectogrado.auth_backend.model;

import jakarta.persistence.*;

/**
 * Datos propios del acompañante (familiar o cuidador de una persona mayor).
 * Comparte la llave con usuario (id_usuario). El parentesco no va aquí
 * sino en cada vínculo (persona_mayor_acompanante.relacion), porque puede
 * ser hijo de una persona mayor y sobrino de otra.
 */
@Entity
@Table(name = "acompanante")
public class Acompanante {

    @Id
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    public Acompanante() {
    }

    public Acompanante(Usuario usuario) {
        this.usuario = usuario;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Integer idUsuario) {
        this.idUsuario = idUsuario;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }
}