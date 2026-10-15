package com.proyectogrado.acompanante_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Vista de solo lectura de la tabla acompanante, que pertenece a
 * auth-service. Sirve para saber si un usuario es acompañante y para leer el
 * parentesco de los vínculos creados antes de que se guardara en
 * persona_mayor_acompanante (ver PersonaMayorAcompanante.relacion).
 */
@Entity
@Table(name = "acompanante")
public class AcompananteInfoLookup {

    @Id
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "relacion")
    private String relacion;

    /** Lo exige JPA. */

    protected AcompananteInfoLookup() {
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public String getRelacion() {
        return relacion;
    }
}
