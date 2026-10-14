package com.proyectogrado.auth_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Constancia de que un usuario aceptó los términos y condiciones al crear
 * su cuenta: quién, cuándo (hora de Colombia) y qué versión.
 *
 * No tiene llave foránea hacia usuario a propósito: si la persona elimina
 * su cuenta, la constancia se conserva como prueba de que aceptó mientras
 * la tuvo. Nunca se edita ni se borra desde la aplicación.
 */
@Entity
@Table(name = "aceptacion_terminos")
public class AceptacionTerminos {

    /** Se aceptaron al crear la cuenta desde el formulario de registro. */
    public static final String REGISTRO = "REGISTRO";

    /**
     * Cuentas creadas antes de que existiera esta tabla: el formulario ya
     * exigía aceptar los términos, pero no se guardaba cuándo. La fecha es
     * la de creación de la cuenta y no hay versión.
     */
    public static final String REGISTRO_ANTERIOR = "REGISTRO_ANTERIOR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_aceptacion")
    private Integer idAceptacion;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(name = "acepto", nullable = false)
    private boolean acepto;

    @Column(name = "fecha_aceptacion", nullable = false)
    private LocalDateTime fechaAceptacion;

    /** Versión del texto aceptado (ver terminos.version); null en REGISTRO_ANTERIOR. */
    @Column(name = "version_terminos", length = 20)
    private String versionTerminos;

    @Column(name = "origen", nullable = false, length = 20)
    private String origen;

    /** Lo exige JPA. */
    protected AceptacionTerminos() {
    }

    public AceptacionTerminos(Integer idUsuario, LocalDateTime fechaAceptacion, String versionTerminos) {
        this.idUsuario = idUsuario;
        this.acepto = true;
        this.fechaAceptacion = fechaAceptacion;
        this.versionTerminos = versionTerminos;
        this.origen = REGISTRO;
    }

    public Integer getIdAceptacion() {
        return idAceptacion;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public boolean isAcepto() {
        return acepto;
    }

    public LocalDateTime getFechaAceptacion() {
        return fechaAceptacion;
    }

    public String getVersionTerminos() {
        return versionTerminos;
    }

    public String getOrigen() {
        return origen;
    }
}
