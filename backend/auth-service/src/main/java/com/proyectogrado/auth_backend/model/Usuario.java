package com.proyectogrado.auth_backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * Cuenta de un usuario de la plataforma, sea cual sea su rol. Es la tabla
 * central del modelo: los demás servicios la consultan con sus propias
 * entidades (UsuarioLookup) para obtener nombres y celulares.
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "nombre_usuario", nullable = false)
    private String nombreUsuario;

    /** Hash BCrypt. Es null si la cuenta se creó solo con celular y OTP. */
    @Column(name = "contrasena_hash")
    private String contrasenaHash;

    @Column(name = "correo", unique = true)
    private String correo;

    /** Con indicativo del país, por ejemplo +573001234567. */
    @Column(name = "Celular", unique = true)
    private String celular;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "genero")
    private String genero;

    @Column(name = "direccion")
    private String direccion;

    /** Solo en cuentas de organización: la organización a la que pertenece la cuenta. */
    @Column(name = "id_organizacion")
    private Integer idOrganizacion;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    /** Una cuenta inactiva no puede iniciar sesión. */
    @Column(name = "activo")
    private Boolean activo;

    /**
     * URL pública de la foto de perfil en Supabase Storage (bucket
     * fotos-perfil), o null si no tiene. La sube cualquier rol.
     */
    @Column(name = "foto_perfil")
    private String fotoPerfil;

    /**
     * Último rol con el que se usó la cuenta, para quien es persona mayor y
     * acompañante a la vez (RF-76): al iniciar sesión entra con ese. Null si
     * nunca ha cambiado de rol.
     */
    @Column(name = "ultimo_rol", length = 30)
    private String ultimoRol;

    /**
     * Contraseñas incorrectas seguidas desde el último inicio de sesión
     * exitoso. Al llegar al máximo, la cuenta se bloquea un rato y el
     * contador vuelve a 0 (ver AuthService.login).
     */
    @Column(name = "intentos_fallidos")
    private Integer intentosFallidos;

    /** Hasta cuándo (hora de Colombia) no se puede entrar con contraseña. Null si no está bloqueada. */
    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    public Usuario() {
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

    public String getContrasenaHash() {
        return contrasenaHash;
    }

    public void setContrasenaHash(String contrasenaHash) {
        this.contrasenaHash = contrasenaHash;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getCelular() {
        return celular;
    }

    public void setCelular(String celular) {
        this.celular = celular;
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

    public Integer getIdOrganizacion() {
        return idOrganizacion;
    }

    public void setIdOrganizacion(Integer idOrganizacion) {
        this.idOrganizacion = idOrganizacion;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    public String getFotoPerfil() {
        return fotoPerfil;
    }

    public String getUltimoRol() {
        return ultimoRol;
    }

    public void setUltimoRol(String ultimoRol) {
        this.ultimoRol = ultimoRol;
    }

    public void setFotoPerfil(String fotoPerfil) {
        this.fotoPerfil = fotoPerfil;
    }

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();

        if (this.activo == null) {
            this.activo = true;
        }
    }

    public Integer getIntentosFallidos() {
        return intentosFallidos;
    }

    public void setIntentosFallidos(Integer intentosFallidos) {
        this.intentosFallidos = intentosFallidos;
    }

    public LocalDateTime getBloqueadoHasta() {
        return bloqueadoHasta;
    }

    public void setBloqueadoHasta(LocalDateTime bloqueadoHasta) {
        this.bloqueadoHasta = bloqueadoHasta;
    }
}
