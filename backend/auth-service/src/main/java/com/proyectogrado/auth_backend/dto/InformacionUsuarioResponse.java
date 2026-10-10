package com.proyectogrado.auth_backend.dto;

import java.time.LocalDate;

/**
 * Datos que muestra "Mi información" para cualquier rol.
 */
public class InformacionUsuarioResponse {

    private Integer idUsuario;
    private String nombre;
    private String celular;
    private String correo;
    private LocalDate fechaNacimiento;
    private String genero;
    private String direccion;

    // Solo para personas mayores; null en los demás roles.
    private String eps;
    private String ips;
    private String direccionIps;
    /** Si vive sola; null si no lo ha indicado. */
    private Boolean viveSolo;

    /** Si es false, el usuario entra solo con OTP y puede crear una contraseña sin dar la actual. */
    private boolean tieneContrasena;

    public InformacionUsuarioResponse(
            Integer idUsuario,
            String nombre,
            String celular,
            String correo,
            LocalDate fechaNacimiento,
            String genero,
            String direccion,
            String eps,
            String ips,
            String direccionIps,
            Boolean viveSolo,
            boolean tieneContrasena
    ) {
        this.viveSolo = viveSolo;
        this.idUsuario = idUsuario;
        this.nombre = nombre;
        this.celular = celular;
        this.correo = correo;
        this.fechaNacimiento = fechaNacimiento;
        this.genero = genero;
        this.direccion = direccion;
        this.eps = eps;
        this.ips = ips;
        this.direccionIps = direccionIps;
        this.tieneContrasena = tieneContrasena;
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

    public String getCorreo() {
        return correo;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public String getGenero() {
        return genero;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getEps() {
        return eps;
    }

    public String getIps() {
        return ips;
    }

    public String getDireccionIps() {
        return direccionIps;
    }

    public Boolean getViveSolo() {
        return viveSolo;
    }

    public boolean isTieneContrasena() {
        return tieneContrasena;
    }
}