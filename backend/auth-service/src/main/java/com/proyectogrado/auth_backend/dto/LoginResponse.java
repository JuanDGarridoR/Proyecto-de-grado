package com.proyectogrado.auth_backend.dto;

/**
 * Respuesta del login y del registro. Si algo falla, solo viene el mensaje
 * con el motivo.
 */
public class LoginResponse {

    private String token;

    /** Con el rol, el frontend decide a qué panel enviar al usuario. */
    private String rol;

    private String mensaje;
    private Integer idUsuario;
    private String nombreUsuario;

    /**
     * Token de renovación de la sesión (ver SesionService). Solo viene al
     * iniciar sesión o registrarse; al renovar o cambiar de rol se sigue
     * usando el mismo.
     */
    private String refreshToken;

    public LoginResponse() {
    }

    public LoginResponse(String token, String rol, String mensaje) {
        this.token = token;
        this.rol = rol;
        this.mensaje = mensaje;
    }

    public LoginResponse(String token, String rol, String mensaje, Integer idUsuario, String nombreUsuario) {
        this.token = token;
        this.rol = rol;
        this.mensaje = mensaje;
        this.idUsuario = idUsuario;
        this.nombreUsuario = nombreUsuario;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Integer idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}