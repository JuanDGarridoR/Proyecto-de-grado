package com.proyectogrado.persona_mayor_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Vista de solo lectura de la tabla persona_mayor (la crea auth-service
 * durante el registro). Aquí solo sirve para saber quién es persona mayor.
 *
 * Solo se mapea el id a propósito: la fecha de nacimiento, el género y la
 * dirección están en la tabla usuario, y con ddl-auto=update cualquier
 * columna que se mapee aquí se crearía en persona_mayor.
 */
@Entity
@Table(name = "persona_mayor")
public class PersonaMayorLookup {

    @Id
    @Column(name = "id_usuario")
    private Integer idUsuario;

    /** Lo exige JPA. */
    protected PersonaMayorLookup() {
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }
}
