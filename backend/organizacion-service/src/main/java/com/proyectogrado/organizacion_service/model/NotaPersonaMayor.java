package com.proyectogrado.organizacion_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Nota que una organización toma sobre una de sus personas mayores (una
 * visita, una conversación, algo a tener en cuenta). Son privadas de la
 * organización: ni la persona mayor ni otras organizaciones las ven. Se
 * pueden editar y eliminar.
 */
@Entity
@Table(name = "nota_persona_mayor")
public class NotaPersonaMayor {

    public static final int MAX_TEXTO = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_nota")
    private Integer idNota;

    @Column(name = "id_organizacion", nullable = false)
    private Integer idOrganizacion;

    @Column(name = "id_persona_mayor", nullable = false)
    private Integer idPersonaMayor;

    @Column(name = "texto", nullable = false, length = MAX_TEXTO)
    private String texto;

    /** Cuenta de la organización que la escribió (o la editó por última vez). */
    @Column(name = "id_autor", nullable = false)
    private Integer idAutor;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    /** Lo exige JPA. */
    protected NotaPersonaMayor() {
    }

    public NotaPersonaMayor(Integer idOrganizacion, Integer idPersonaMayor, String texto, Integer idAutor,
                            LocalDateTime ahora) {
        this.idOrganizacion = idOrganizacion;
        this.idPersonaMayor = idPersonaMayor;
        this.texto = texto;
        this.idAutor = idAutor;
        this.fechaCreacion = ahora;
        this.fechaActualizacion = ahora;
    }

    /** Cambia el texto y guarda quién y cuándo. */
    public void editar(String texto, Integer idAutor, LocalDateTime ahora) {
        this.texto = texto;
        this.idAutor = idAutor;
        this.fechaActualizacion = ahora;
    }

    public boolean fueEditada() {
        return fechaActualizacion.isAfter(fechaCreacion);
    }

    public Integer getIdNota() {
        return idNota;
    }

    public Integer getIdOrganizacion() {
        return idOrganizacion;
    }

    public Integer getIdPersonaMayor() {
        return idPersonaMayor;
    }

    public String getTexto() {
        return texto;
    }

    public Integer getIdAutor() {
        return idAutor;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}
