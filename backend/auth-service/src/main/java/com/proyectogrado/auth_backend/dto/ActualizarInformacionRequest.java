package com.proyectogrado.auth_backend.dto;

import java.time.LocalDate;

/**
 * Cambios que se envían desde "Mi información". No trae el celular porque
 * no se puede editar.
 */
public class ActualizarInformacionRequest {

    private String nombre;
    private String correo;
    private LocalDate fechaNacimiento;
    private String genero;
    private String direccion;

    // Solo aplican a personas mayores.
    private String eps;
    private String ips;
    private String direccionIps;
    /** null si no lo indica. */
    private Boolean viveSolo;

    public Boolean getViveSolo() {
        return viveSolo;
    }

    public void setViveSolo(Boolean viveSolo) {
        this.viveSolo = viveSolo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }

    public String getGenero() {
        return genero;
    }

    public void setGenero(String genero) {
        this.genero = genero;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getEps() {
        return eps;
    }

    public void setEps(String eps) {
        this.eps = eps;
    }

    public String getIps() {
        return ips;
    }

    public void setIps(String ips) {
        this.ips = ips;
    }

    public String getDireccionIps() {
        return direccionIps;
    }

    public void setDireccionIps(String direccionIps) {
        this.direccionIps = direccionIps;
    }
}