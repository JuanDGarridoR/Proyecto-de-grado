package com.proyectogrado.persona_mayor_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Vista de solo lectura de la tabla acompanante, que crea auth-service
 * durante el registro. Sirve para saber si un usuario es acompañante; el
 * parentesco va en cada vínculo (ver PersonaMayorAcompanante.relacion).
 */
@Entity
@Table(name = "acompanante")
public class AcompananteLookup {

    @Id
    @Column(name = "id_usuario")
    private Integer idUsuario;

    /** Lo exige JPA. */
    protected AcompananteLookup() {
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }
}
