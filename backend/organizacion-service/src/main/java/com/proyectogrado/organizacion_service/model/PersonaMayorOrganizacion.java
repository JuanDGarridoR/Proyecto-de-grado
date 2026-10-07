package com.proyectogrado.organizacion_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Vínculo entre una persona mayor y una organización.
 *
 * No se referencian PersonaMayor ni Organizacion como entidades JPA (esas
 * tablas son de auth-service), solo sus ids. persona-mayor-service usa esta
 * misma tabla desde el otro lado (ver, aceptar o rechazar organizaciones).
 */
@Entity
@Table(name = "persona_mayor_organizacion")
public class PersonaMayorOrganizacion {

    public static final String ORGANIZACION = "ORGANIZACION";
    public static final String PERSONA_MAYOR = "PERSONA_MAYOR";

    @EmbeddedId
    private PersonaMayorOrganizacionId id;

    /** PENDIENTE hasta que el otro lado responde; luego ACEPTADA o RECHAZADA. */
    @Column(name = "estado", nullable = false)
    private String estado = "PENDIENTE";

    /**
     * Quién envió la solicitud: ORGANIZACION o PERSONA_MAYOR. Responde el
     * otro. null en vínculos creados antes de que existiera el campo, que
     * siempre los enviaba la organización.
     */
    @Column(name = "solicitada_por")
    private String solicitadaPor;

    public PersonaMayorOrganizacion() {
    }

    public PersonaMayorOrganizacion(Integer idPersonaMayor, Integer idOrganizacion) {
        this.id = new PersonaMayorOrganizacionId(idPersonaMayor, idOrganizacion);
    }

    public PersonaMayorOrganizacionId getId() {
        return id;
    }

    public void setId(PersonaMayorOrganizacionId id) {
        this.id = id;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getSolicitadaPor() {
        return solicitadaPor;
    }

    public void setSolicitadaPor(String solicitadaPor) {
        this.solicitadaPor = solicitadaPor;
    }

    /** true si la solicitud la envió la persona mayor (la responde la organización). */
    public boolean laEnvioLaPersonaMayor() {
        return PERSONA_MAYOR.equals(solicitadaPor);
    }
}
