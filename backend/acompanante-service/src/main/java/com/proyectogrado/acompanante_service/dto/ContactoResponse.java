package com.proyectogrado.acompanante_service.dto;

/**
 * Un acompañante de la persona mayor, con su parentesco, tal como lo ve otro
 * acompañante en la sección de contactos.
 */
public class ContactoResponse {

    private Integer idUsuario;
    private String nombre;
    private String celular;
    private String relacion;

    /** URL pública de la foto de perfil, o null si no tiene. */
    private String fotoPerfil;

    public ContactoResponse(Integer idUsuario, String nombre, String celular, String relacion, String fotoPerfil) {
        this.idUsuario = idUsuario;
        this.nombre = nombre;
        this.celular = celular;
        this.relacion = relacion;
        this.fotoPerfil = fotoPerfil;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCelular() {
        return celular;
    }

    public String getRelacion() {
        return relacion;
    }

    public String getFotoPerfil() {
        return fotoPerfil;
    }
}