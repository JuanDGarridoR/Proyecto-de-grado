package com.proyectogrado.auth_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Una sesión abierta: la que se crea al iniciar sesión o registrarse, y con
 * la que el navegador pide tokens de acceso nuevos (ver SesionService).
 *
 * Se guarda solo el hash SHA-256 del token de renovación, nunca el token:
 * quien lea la tabla no puede usarlo. Borrar la fila cierra la sesión.
 */
@Entity
@Table(name = "sesion", indexes = @Index(name = "idx_sesion_usuario", columnList = "id_usuario"))
public class Sesion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sesion")
    private Long idSesion;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    /** SHA-256 del token de renovación, en hexadecimal. */
    @Column(name = "hash_token", nullable = false, unique = true, length = 64)
    private String hashToken;

    /** Hora de Colombia. */
    @Column(name = "creada", nullable = false)
    private LocalDateTime creada;

    /** Se corre cada vez que la sesión se usa: vence tras un tiempo sin usarla. */
    @Column(name = "expira", nullable = false)
    private LocalDateTime expira;

    protected Sesion() {
    }

    public Sesion(Integer idUsuario, String hashToken, LocalDateTime creada, LocalDateTime expira) {
        this.idUsuario = idUsuario;
        this.hashToken = hashToken;
        this.creada = creada;
        this.expira = expira;
    }

    public Long getIdSesion() {
        return idSesion;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public String getHashToken() {
        return hashToken;
    }

    public LocalDateTime getCreada() {
        return creada;
    }

    public LocalDateTime getExpira() {
        return expira;
    }

    public void setExpira(LocalDateTime expira) {
        this.expira = expira;
    }
}
