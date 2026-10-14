package com.proyectogrado.donacion_service.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Donación que un acompañante o voluntario programa para una organización
 * con la que está vinculado. Empieza PROGRAMADA; cuando la lleva, la
 * organización la marca RECIBIDA (sus artículos entran al inventario) o
 * NO_ENTREGADA. Mientras esté PROGRAMADA, el donante puede cancelarla.
 */
@Entity
@Table(name = "donacion")
public class Donacion {

    public static final String PROGRAMADA = "PROGRAMADA";
    public static final String RECIBIDA = "RECIBIDA";
    public static final String NO_ENTREGADA = "NO_ENTREGADA";
    public static final String CANCELADA = "CANCELADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_donacion")
    private Integer idDonacion;

    @Column(name = "id_donante", nullable = false)
    private Integer idDonante;

    /** ACOMPANANTE o VOLUNTARIO: el rol con el que donó. */
    @Column(name = "rol_donante", nullable = false)
    private String rolDonante;

    @Column(name = "id_organizacion", nullable = false)
    private Integer idOrganizacion;

    /** Día en que el donante llevará la donación a la organización. */
    @Column(name = "fecha_entrega", nullable = false)
    private LocalDate fechaEntrega;

    /** Hora aproximada; null si no la indicó. */
    @Column(name = "hora_entrega")
    private LocalTime horaEntrega;

    @Column(name = "observaciones", length = 500)
    private String observaciones;

    @Column(name = "estado", nullable = false)
    private String estado = PROGRAMADA;

    /** Lo que anota la organización al marcarla no entregada. */
    @Column(name = "nota_organizacion", length = 300)
    private String notaOrganizacion;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    /** Cuándo la organización la marcó recibida o no entregada, o el donante la canceló. */
    @Column(name = "fecha_respuesta")
    private LocalDateTime fechaRespuesta;

    @OneToMany(mappedBy = "donacion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("idArticulo")
    private List<DonacionArticulo> articulos = new ArrayList<>();

    /** Lo exige JPA. */
    protected Donacion() {
    }

    public Donacion(Integer idDonante, String rolDonante, Integer idOrganizacion, LocalDate fechaEntrega) {
        this.idDonante = idDonante;
        this.rolDonante = rolDonante;
        this.idOrganizacion = idOrganizacion;
        this.fechaEntrega = fechaEntrega;
        this.fechaRegistro = Catalogo.ahora();
    }

    public void agregarArticulo(DonacionArticulo articulo) {
        articulo.setDonacion(this);
        articulos.add(articulo);
    }

    /** Cambia el estado y guarda cuándo pasó. */
    public void responder(String nuevoEstado) {
        this.estado = nuevoEstado;
        this.fechaRespuesta = Catalogo.ahora();
    }

    public boolean estaProgramada() {
        return PROGRAMADA.equals(estado);
    }

    public Integer getIdDonacion() {
        return idDonacion;
    }

    public Integer getIdDonante() {
        return idDonante;
    }

    public String getRolDonante() {
        return rolDonante;
    }

    public Integer getIdOrganizacion() {
        return idOrganizacion;
    }

    public LocalDate getFechaEntrega() {
        return fechaEntrega;
    }

    public LocalTime getHoraEntrega() {
        return horaEntrega;
    }

    public void setHoraEntrega(LocalTime horaEntrega) {
        this.horaEntrega = horaEntrega;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getEstado() {
        return estado;
    }

    public String getNotaOrganizacion() {
        return notaOrganizacion;
    }

    public void setNotaOrganizacion(String notaOrganizacion) {
        this.notaOrganizacion = notaOrganizacion;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public LocalDateTime getFechaRespuesta() {
        return fechaRespuesta;
    }

    public List<DonacionArticulo> getArticulos() {
        return articulos;
    }
}
