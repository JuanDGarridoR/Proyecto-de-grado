package com.proyectogrado.auth_backend.dto;

/**
 * Datos del formulario de registro. Las reglas de qué campos son
 * obligatorios están en AuthService.validarRegistro.
 */
public class RegistroRequest {

    private String nombreUsuario;

    // Opcionales, pero van juntos.
    private String correo;
    private String contrasena;

    /** PERSONA_MAYOR, ACOMPANANTE, ORGANIZACION o VOLUNTARIO. */
    private String rol;

    // Obligatorios para persona mayor, acompañante y voluntario. La
    // dirección también lo es para la organización.
    private String fechaNacimiento; // "yyyy-MM-dd"
    private String genero;
    private String direccion;

    /** Obligatorio para todos: +57 seguido de 10 dígitos. */
    private String celular;

    /** Debe ser true: sin aceptar los términos y condiciones no se crea la cuenta. */
    private Boolean aceptaTerminos;

    public RegistroRequest() {
    }

    public Boolean getAceptaTerminos() {
        return aceptaTerminos;
    }

    public void setAceptaTerminos(Boolean aceptaTerminos) {
        this.aceptaTerminos = aceptaTerminos;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public String getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(String fechaNacimiento) {
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

    public String getCelular() {
        return celular;
    }

    public void setCelular(String celular) {
        this.celular = celular;
    }
}