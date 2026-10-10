package com.proyectogrado.voluntario_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Vínculo entre un voluntario y una organización. Lo crea el voluntario
 * como solicitud (PENDIENTE) y la organización lo acepta (ACEPTADA) o lo
 * rechaza (RECHAZADA). Un voluntario puede estar vinculado a varias
 * organizaciones, pero tiene a lo sumo un registro por organización.
 *
 * La tabla es de voluntario-service (la crea Hibernate con
 * ddl-auto=update); los endpoints de ambos lados viven en este servicio.
 */
@Entity
@Table(name = "voluntario_organizacion")
public class VoluntarioOrganizacion {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ACEPTADA = "ACEPTADA";
    public static final String RECHAZADA = "RECHAZADA";
    /** El voluntario pausó el vínculo: la organización no lo ve hasta que lo reactive. */
    public static final String INACTIVA = "INACTIVA";
    /**
     * Los pone auth-service cuando una de las dos cuentas se inactiva (en
     * vez de ACEPTADA y PENDIENTE); nadie los lista.
     */
    public static final String CUENTA_INACTIVA = "CUENTA_INACTIVA";
    public static final String PENDIENTE_CUENTA_INACTIVA = "PENDIENTE_CUENTA_INACTIVA";

    @EmbeddedId
    private VoluntarioOrganizacionId id;

    @Column(name = "estado", nullable = false)
    private String estado = PENDIENTE;

    /** Lo exige JPA. */
    protected VoluntarioOrganizacion() {
    }

    public VoluntarioOrganizacion(Integer idVoluntario, Integer idOrganizacion) {
        this.id = new VoluntarioOrganizacionId(idVoluntario, idOrganizacion);
    }

    public VoluntarioOrganizacionId getId() {
        return id;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
