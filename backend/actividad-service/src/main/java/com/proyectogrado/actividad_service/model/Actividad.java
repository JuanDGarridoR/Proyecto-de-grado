package com.proyectogrado.actividad_service.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Actividad organizada por una organización. Esta tabla es de
 * actividad-service: aquí sí se crea, edita y borra, a diferencia de las
 * entidades Lookup de este servicio, que solo leen tablas de otros servicios.
 *
 * Un voluntario o una persona mayor también pueden proponer una actividad
 * a una de sus organizaciones: queda con idVoluntario o
 * idPersonaMayorProponente y estado PENDIENTE, y solo la ven las personas
 * mayores cuando la organización la acepta. Las que crea la organización
 * tienen estado null.
 *
 * Una actividad periódica (frecuenciaDias distinto de null) se repite cada
 * tantos días con los mismos datos: cuando pasa su día,
 * ActividadPeriodicaScheduler crea la siguiente y le pasa la periodicidad.
 * Así las personas mayores ven una sola ocurrencia a la vez y cada una
 * guarda sus propias inscripciones y asistencia.
 */
@Entity
@Table(name = "actividad")
public class Actividad {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ACEPTADA = "ACEPTADA";
    public static final String RECHAZADA = "RECHAZADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_actividad")
    private Integer idActividad;

    @Column(name = "id_organizacion", nullable = false)
    private Integer idOrganizacion;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "fecha")
    private LocalDate fecha;

    /** "HH:mm", tal como llega del formulario. */
    @Column(name = "hora")
    private String hora;

    @Column(name = "lugar")
    private String lugar;

    /** Texto libre, por ejemplo "Recreativa". */
    @Column(name = "tipo")
    private String tipo;

    /** Máximo de inscritos; si es null, no hay límite. */
    @Column(name = "cupos")
    private Integer cupos;

    @Column(name = "responsable")
    private String responsable;

    /** Voluntario que propuso la actividad; null si la creó la organización. */
    @Column(name = "id_voluntario")
    private Integer idVoluntario;

    /** Persona mayor que propuso la actividad; null si no la propuso una persona mayor. */
    @Column(name = "id_persona_mayor_proponente")
    private Integer idPersonaMayorProponente;

    /**
     * Cada cuántos días se repite; null si no se repite. Solo la tiene la
     * ocurrencia más reciente de la serie.
     */
    @Column(name = "frecuencia_dias")
    private Integer frecuenciaDias;

    /** Estado de la propuesta; null si la creó la organización. */
    @Column(name = "estado")
    private String estado;

    public Actividad() {
    }

    public Integer getIdActividad() {
        return idActividad;
    }

    public void setIdActividad(Integer idActividad) {
        this.idActividad = idActividad;
    }

    public Integer getIdOrganizacion() {
        return idOrganizacion;
    }

    public void setIdOrganizacion(Integer idOrganizacion) {
        this.idOrganizacion = idOrganizacion;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getHora() {
        return hora;
    }

    public void setHora(String hora) {
        this.hora = hora;
    }

    public String getLugar() {
        return lugar;
    }

    public void setLugar(String lugar) {
        this.lugar = lugar;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Integer getCupos() {
        return cupos;
    }

    public void setCupos(Integer cupos) {
        this.cupos = cupos;
    }

    public String getResponsable() {
        return responsable;
    }

    public void setResponsable(String responsable) {
        this.responsable = responsable;
    }

    public Integer getFrecuenciaDias() {
        return frecuenciaDias;
    }

    public void setFrecuenciaDias(Integer frecuenciaDias) {
        this.frecuenciaDias = frecuenciaDias;
    }

    public Integer getIdVoluntario() {
        return idVoluntario;
    }

    public void setIdVoluntario(Integer idVoluntario) {
        this.idVoluntario = idVoluntario;
    }

    public Integer getIdPersonaMayorProponente() {
        return idPersonaMayorProponente;
    }

    public void setIdPersonaMayorProponente(Integer idPersonaMayorProponente) {
        this.idPersonaMayorProponente = idPersonaMayorProponente;
    }

    /** true si la propuso un voluntario o una persona mayor (no la organización). */
    public boolean esPropuesta() {
        return idVoluntario != null || idPersonaMayorProponente != null;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    /**
     * true si la ven las personas mayores: la creó la organización o es una
     * propuesta aceptada.
     */
    public boolean esVisible() {
        return estado == null || ACEPTADA.equals(estado);
    }
}
