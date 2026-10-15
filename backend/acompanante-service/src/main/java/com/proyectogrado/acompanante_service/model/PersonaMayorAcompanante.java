package com.proyectogrado.acompanante_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Vínculo entre una persona mayor y un acompañante.
 *
 * Solo se guardan los ids, porque las tablas persona_mayor y acompanante son
 * de auth-service. Para mostrar nombre y celular se usa UsuarioLookup.
 *
 * persona-mayor-service usa esta misma tabla desde el otro lado (agregar y
 * listar acompañantes); aquí se lee y se cambia el estado al aceptar o
 * rechazar solicitudes.
 */
@Entity
@Table(name = "persona_mayor_acompanante")
public class PersonaMayorAcompanante {

    public static final String PERSONA_MAYOR = "PERSONA_MAYOR";
    public static final String ACOMPANANTE = "ACOMPANANTE";

    @EmbeddedId
    private PersonaMayorAcompananteId id;

    /** PENDIENTE hasta que el otro lado responde; luego ACEPTADA o RECHAZADA. */
    @Column(name = "estado", nullable = false)
    private String estado = "PENDIENTE";

    /**
     * Quién envió la solicitud: PERSONA_MAYOR o ACOMPANANTE. Responde el
     * otro. null en vínculos creados antes de que existiera el campo, que
     * siempre los enviaba la persona mayor.
     */
    @Column(name = "solicitada_por")
    private String solicitadaPor;

    /**
     * Parentesco del acompañante con esta persona mayor (por ejemplo, "Hija").
     * Va en el vínculo porque un acompañante puede ser hija de una persona
     * mayor y sobrina de otra. null en vínculos creados antes de que existiera
     * el campo: para esos vale la columna relacion de la tabla acompanante.
     */
    @Column(name = "relacion")
    private String relacion;

    public PersonaMayorAcompanante() {
    }

    public PersonaMayorAcompanante(Integer idPersonaMayor, Integer idAcompanante) {
        this.id = new PersonaMayorAcompananteId(idPersonaMayor, idAcompanante);
    }

    public PersonaMayorAcompananteId getId() {
        return id;
    }

    public void setId(PersonaMayorAcompananteId id) {
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

    public String getRelacion() {
        return relacion;
    }

    public void setRelacion(String relacion) {
        this.relacion = relacion;
    }

    /** true si la solicitud la envió el acompañante (la responde la persona mayor). */
    public boolean laEnvioElAcompanante() {
        return ACOMPANANTE.equals(solicitadaPor);
    }
}
