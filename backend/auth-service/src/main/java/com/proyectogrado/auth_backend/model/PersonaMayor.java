package com.proyectogrado.auth_backend.model;

import jakarta.persistence.*;

/**
 * Datos propios de la persona mayor. Comparte la llave con usuario
 * (id_usuario): cada persona mayor es también un usuario.
 */
@Entity
@Table(name = "persona_mayor")
public class PersonaMayor {

    @Id
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    /** Entidad promotora de salud a la que está afiliada. */
    @Column(name = "eps")
    private String eps;

    /** Institución prestadora de salud donde la atienden. */
    @Column(name = "ips")
    private String ips;

    /** Dirección de la IPS. */
    @Column(name = "direccion_ips")
    private String direccionIps;

    /** Si vive sola: true sí, false no, null si no lo ha indicado. */
    @Column(name = "vive_solo")
    private Boolean viveSolo;

    public PersonaMayor() {
    }

    public Boolean getViveSolo() {
        return viveSolo;
    }

    public void setViveSolo(Boolean viveSolo) {
        this.viveSolo = viveSolo;
    }

    public PersonaMayor(Usuario usuario) {
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

    public String getEps() {
        return eps;
    }

    public void setEps(String eps) {
        this.eps = eps;
    }

    public String getIps() {
        return ips;
    }

    public void setIps(String ips) {
        this.ips = ips;
    }

    public String getDireccionIps() {
        return direccionIps;
    }

    public void setDireccionIps(String direccionIps) {
        this.direccionIps = direccionIps;
    }
}