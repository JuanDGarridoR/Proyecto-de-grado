package com.proyectogrado.messaging_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Si un usuario quiere o no recibir un tipo de notificación. Si no hay
 * fila para un tipo, el tipo está activo: solo se guarda lo que el usuario
 * cambia desde su perfil.
 */
@Entity
@Table(name = "preferencia_notificacion",
        uniqueConstraints = @UniqueConstraint(columnNames = {"id_usuario", "tipo"}))
public class PreferenciaNotificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_preferencia")
    private Long idPreferencia;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40)
    private TipoNotificacion tipo;

    @Column(name = "activa", nullable = false)
    private boolean activa;

    /** Lo exige JPA. */
    protected PreferenciaNotificacion() {
    }

    public PreferenciaNotificacion(Integer idUsuario, TipoNotificacion tipo, boolean activa) {
        this.idUsuario = idUsuario;
        this.tipo = tipo;
        this.activa = activa;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public TipoNotificacion getTipo() {
        return tipo;
    }

    public boolean isActiva() {
        return activa;
    }

    public void setActiva(boolean activa) {
        this.activa = activa;
    }
}
