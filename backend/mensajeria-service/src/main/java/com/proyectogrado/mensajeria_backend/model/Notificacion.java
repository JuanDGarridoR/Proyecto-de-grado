package com.proyectogrado.mensajeria_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Copia de cada SMS enviado con éxito por /api/mensajes/enviar
 * (emergencias, recordatorios de medicamentos y de actividades), para
 * mostrarlo en la campanita del panel. Los códigos OTP no se guardan.
 *
 * Se asocia por celular porque es el dato que reciben los servicios que
 * envían, y el celular de un usuario no se puede editar (es su login).
 */
@Entity
@Table(name = "notificacion", indexes = @Index(name = "idx_notificacion_celular", columnList = "celular"))
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notificacion")
    private Long idNotificacion;

    @Column(name = "celular", nullable = false)
    private String celular;

    @Column(name = "mensaje", nullable = false, length = 1000)
    private String mensaje;

    @Column(name = "fecha_envio", nullable = false)
    private Instant fechaEnvio;

    @Column(name = "leida", nullable = false)
    private boolean leida;

    /** Lo exige JPA. */
    protected Notificacion() {
    }

    public Notificacion(String celular, String mensaje) {
        this.celular = celular;
        this.mensaje = mensaje;
        this.fechaEnvio = Instant.now();
        this.leida = false;
    }

    public Long getIdNotificacion() {
        return idNotificacion;
    }

    public String getCelular() {
        return celular;
    }

    public String getMensaje() {
        return mensaje;
    }

    public Instant getFechaEnvio() {
        return fechaEnvio;
    }

    public boolean isLeida() {
        return leida;
    }
}
