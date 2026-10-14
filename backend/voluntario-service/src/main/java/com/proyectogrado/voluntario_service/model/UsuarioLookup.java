package com.proyectogrado.voluntario_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Vista de solo lectura de la tabla usuario, que pertenece a auth-service.
 * Este servicio nunca crea, edita ni borra usuarios: la vista existe para
 * leer nombre, celular, correo y organización de las cuentas sin llamar
 * por HTTP a auth-service. Es válido porque todos los servicios comparten
 * la misma base de datos.
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

    /** Solo tiene valor para las cuentas de una organización. */
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
