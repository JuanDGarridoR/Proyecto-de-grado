package com.proyectogrado.mensajeria_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Vista de solo lectura de la tabla usuario, que pertenece a auth-service.
 * Solo se usa para saber el celular del usuario autenticado y así
 * encontrar sus notificaciones.
 */
@Entity
@Table(name = "usuario")
public class UsuarioLookup {

    @Id
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "Celular")
    private String celular;

    /** Lo exige JPA. */
    protected UsuarioLookup() {
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public String getCelular() {
        return celular;
    }
}
