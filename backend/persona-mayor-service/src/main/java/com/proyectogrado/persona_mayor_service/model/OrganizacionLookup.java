package com.proyectogrado.persona_mayor_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Vista de solo lectura de la tabla organizacion, que pertenece a
 * auth-service. Sirve para listarle a la persona mayor las organizaciones a
 * las que puede pedir unirse.
 */
@Entity
@Table(name = "organizacion")
public class OrganizacionLookup {

    @Id
    @Column(name = "id_organizacion")
    private Integer idOrganizacion;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "direccion")
    private String direccion;

    /** Lo exige JPA. */
    protected OrganizacionLookup() {
    }

    public Integer getIdOrganizacion() {
        return idOrganizacion;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDireccion() {
        return direccion;
    }
}
