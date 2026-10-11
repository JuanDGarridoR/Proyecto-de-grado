package com.proyectogrado.auth_backend.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Por qué alguien eliminó su cuenta. Se guarda justo antes de borrarla.
 *
 * Si era una persona mayor, queda una fila por cada organización a la que
 * pertenecía (vínculo ACEPTADA o CUENTA_INACTIVA), con su nombre, para que
 * esa organización sepa por qué ya no está; organizacion-service las lee en
 * /api/organizacion/retiros. Para los demás roles (o una persona mayor sin
 * organizaciones) queda una sola fila sin nombre ni organización, que solo
 * sirve para estadísticas.
 */
@Entity
@Table(name = "retiro_cuenta")
public class RetiroCuenta {

    /**
     * Códigos de las razones de personas mayores, acompañantes y
     * voluntarios; el frontend muestra el texto de cada una.
     */
    public static final Set<String> RAZONES = Set.of(
            "FALLECIMIENTO",
            "SALUD",
            "HOGAR_CUIDADO",
            "CAMBIO_RESIDENCIA",
            "DIFICULTAD_USO",
            "NO_LA_NECESITA",
            "PRIVACIDAD",
            "OTRA"
    );

    /** Las organizaciones se retiran por otras razones. */
    public static final Set<String> RAZONES_ORGANIZACION = Set.of(
            "CIERRE_ORGANIZACION",
            "CAMBIO_ADMINISTRACION",
            "OTRA_HERRAMIENTA",
            "SIN_PERSONAL",
            "NO_SE_AJUSTA",
            "DIFICULTAD_USO",
            "PRIVACIDAD",
            "OTRA"
    );

    /** Si la razón es una de las que puede elegir ese rol. */
    public static boolean esRazonValida(String rol, String razon) {
        return ("ORGANIZACION".equals(rol) ? RAZONES_ORGANIZACION : RAZONES).contains(razon);
    }

    public static final int MAX_COMENTARIO = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_retiro")
    private Integer idRetiro;

    /** Organización a la que pertenecía la persona mayor; null en los demás casos. */
    @Column(name = "id_organizacion")
    private Integer idOrganizacion;

    /** Nombre de la persona mayor, solo cuando hay organización. */
    @Column(name = "nombre")
    private String nombre;

    @Column(name = "rol")
    private String rol;

    @Column(name = "razon", nullable = false)
    private String razon;

    @Column(name = "comentario", length = MAX_COMENTARIO)
    private String comentario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    public RetiroCuenta() {
    }

    public RetiroCuenta(Integer idOrganizacion, String nombre, String rol, String razon,
                        String comentario, LocalDateTime fecha) {
        this.idOrganizacion = idOrganizacion;
        this.nombre = nombre;
        this.rol = rol;
        this.razon = razon;
        this.comentario = comentario;
        this.fecha = fecha;
    }

    public Integer getIdRetiro() {
        return idRetiro;
    }

    public Integer getIdOrganizacion() {
        return idOrganizacion;
    }

    public String getNombre() {
        return nombre;
    }

    public String getRol() {
        return rol;
    }

    public String getRazon() {
        return razon;
    }

    public String getComentario() {
        return comentario;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }
}
