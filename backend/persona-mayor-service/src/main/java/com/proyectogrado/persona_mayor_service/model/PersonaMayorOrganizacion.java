package com.proyectogrado.persona_mayor_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Vínculo entre una persona mayor y una organización.
 */
@Entity
@Table(name = "persona_mayor_organizacion")
public class PersonaMayorOrganizacion {

    public static final String ORGANIZACION = "ORGANIZACION";
    public static final String PERSONA_MAYOR = "PERSONA_MAYOR";

    public static final String INACTIVA = "INACTIVA";
    /** La puso auth-service porque la cuenta de la otra parte está inactiva. */
    public static final String CUENTA_INACTIVA = "CUENTA_INACTIVA";

    @EmbeddedId
    private PersonaMayorOrganizacionId id;

    /**
     * PENDIENTE hasta que el otro lado responde; luego ACEPTADA o RECHAZADA.
     * La persona mayor puede pausar un vínculo aceptado (INACTIVA): mientras
     * tanto la organización no la ve y ella no ve lo de la organización.
     * CUENTA_INACTIVA y PENDIENTE_CUENTA_INACTIVA los pone auth-service
     * cuando una de las dos cuentas se inactiva.
     */
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
