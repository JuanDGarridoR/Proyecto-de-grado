package com.proyectogrado.auth_backend.dto;

/**
 * Datos para cambiar la contraseña desde "Mi información".
 */
public class CambiarContrasenaRequest {

    /** Solo se exige si el usuario ya tenía contraseña. */
    private String contrasenaActual;
    private String nuevaContrasena;

    /**
     * Token de renovación de quien cambia la contraseña: su sesión sigue
     * abierta y las demás se cierran (ver SesionService.cerrarTodasMenos).
     */
    private String refreshToken;

    public String getContrasenaActual() {
        return contrasenaActual;
    }

    public void setContrasenaActual(String contrasenaActual) {
        this.contrasenaActual = contrasenaActual;
    }

    public String getNuevaContrasena() {
        return nuevaContrasena;
    }

    public void setNuevaContrasena(String nuevaContrasena) {
        this.nuevaContrasena = nuevaContrasena;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}