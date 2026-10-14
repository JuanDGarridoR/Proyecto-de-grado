package com.proyectogrado.auth_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Calificación que un usuario le da a la plataforma desde su perfil: de 0 a
 * 5 estrellas y, si quiere, una observación u oportunidad de mejora. Cada
 * usuario tiene una sola: si vuelve a calificar, se actualiza.
 */
@Entity
@Table(name = "calificacion_plataforma")
public class CalificacionPlataforma {

    public static final int MAX_ESTRELLAS = 5;
    public static final int MAX_COMENTARIO = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_calificacion")
    private Integer idCalificacion;

    @Column(name = "id_usuario", nullable = false, unique = true)
    private Integer idUsuario;

    @Column(name = "estrellas", nullable = false)
    private Integer estrellas;

    @Column(name = "comentario", length = MAX_COMENTARIO)
    private String comentario;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    /** Lo exige JPA. */
    protected CalificacionPlataforma() {
    }

    public CalificacionPlataforma(Integer idUsuario, LocalDateTime ahora) {
        this.idUsuario = idUsuario;
        this.fechaCreacion = ahora;
        this.fechaActualizacion = ahora;
    }

    /** Cambia la calificación y guarda cuándo. */
    public void calificar(Integer estrellas, String comentario, LocalDateTime ahora) {
        this.estrellas = estrellas;
        this.comentario = comentario;
        this.fechaActualizacion = ahora;
    }

    public Integer getIdCalificacion() {
        return idCalificacion;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public Integer getEstrellas() {
        return estrellas;
    }

    public String getComentario() {
        return comentario;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}
