package com.proyectogrado.auth_backend.model;

import jakarta.persistence.*;

import java.util.Set;

/**
 * Datos propios de la persona mayor. Comparte la llave con usuario
 * (id_usuario): cada persona mayor es también un usuario.
 */
@Entity
@Table(name = "persona_mayor")
public class PersonaMayor {

    /**
     * Nivel de uso del celular, de menor a mayor: no lo usa, bajo (solo
     * llamadas), medio (llamadas y WhatsApp) o alto (varias aplicaciones).
     */
    public static final Set<String> USOS_CELULAR = Set.of("NO_USA", "BASICO", "INTERMEDIO", "AVANZADO");

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

    /** Uno de USOS_CELULAR, o null si no lo ha indicado. */
    @Column(name = "uso_celular", length = 20)
    private String usoCelular;

    public PersonaMayor() {
    }

    public String getUsoCelular() {
        return usoCelular;
    }

    public void setUsoCelular(String usoCelular) {
        this.usoCelular = usoCelular;
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