package com.proyectogrado.donacion_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Vista de solo lectura de la tabla usuario, que pertenece a auth-service.
 * Sirve para mostrar el nombre, celular y foto de donantes y
 * organizaciones, y para saber a qué organización pertenece la cuenta
 * autenticada (id_organizacion solo tiene valor en cuentas de organización).
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

    /** URL pública de la foto de perfil, o null si no tiene. */
    @Column(name = "foto_perfil")
    private String fotoPerfil;

    @Column(name = "id_organizacion")
    private Integer idOrganizacion;

    /** false si el usuario inactivó su cuenta; null cuenta como activo. */
    @Column(name = "activo")
    private Boolean activo;

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

    public boolean estaActivo() {
        return !Boolean.FALSE.equals(activo);
    }
}
