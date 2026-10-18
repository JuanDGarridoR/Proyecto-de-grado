package com.proyectogrado.organizacion_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Vista de solo lectura de la tabla usuario, que pertenece a auth-service.
 * Sirve para buscar a una persona mayor por celular al vincularla y para
 * saber a qué organización pertenece el usuario autenticado (id_organizacion
 * solo tiene valor en cuentas de organización).
 */
@Entity
@Table(name = "usuario")
public class UsuarioLookup {

    @Id
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "nombre_usuario")
    private String nombreUsuario;

    @Column(name = "Celular")
    private String celular;

    @Column(name = "correo")
    private String correo;

    /** Ruta de la foto de perfil (se muestra con GET /api/auth/fotos/{ruta}) (la sube la persona mayor), o null. */
    @Column(name = "foto_perfil")
    private String fotoPerfil;

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

    public String getCorreo() {
        return correo;
    }

    public String getFotoPerfil() {
        return fotoPerfil;
    }

    public Integer getIdOrganizacion() {
        return idOrganizacion;
    }
}
