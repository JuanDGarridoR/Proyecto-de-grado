package com.proyectogrado.salud_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Vista de solo lectura de la tabla usuario, que pertenece a auth-service.
 * Da el nombre y el celular de la persona mayor y de sus acompañantes para
 * los recordatorios, y la organización a la que pertenece una cuenta.
 */
@Entity
@Table(name = "usuario")
public class UsuarioLookup {

    @Id
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "nombre_usuario")
    private String nombreUsuario;

    @Column(name = "celular")
    private String celular;

    @Column(name = "id_organizacion")
private Integer idOrganizacion;

    /** Lo exige JPA. */
    protected UsuarioLookup() {
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public String getCelular() {
        return celular;
    }

    public Integer getIdOrganizacion() {
    return idOrganizacion;
}
}
