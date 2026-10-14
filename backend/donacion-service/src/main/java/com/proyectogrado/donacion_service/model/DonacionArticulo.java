package com.proyectogrado.donacion_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una cosa que se dona: por ejemplo "Arroz, 10 kg" o "Dinero, 50000 COP".
 * Una donación puede traer varias.
 */
@Entity
@Table(name = "donacion_articulo")
public class DonacionArticulo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_articulo")
    private Integer idArticulo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_donacion", nullable = false)
    private Donacion donacion;

    /** Una de Catalogo.CATEGORIAS. */
    @Column(name = "categoria", nullable = false)
    private String categoria;

    @Column(name = "descripcion", nullable = false, length = 120)
    private String descripcion;

    @Column(name = "cantidad", nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidad;

    /** kg, unidades, litros, paquetes... COP si es dinero. */
    @Column(name = "unidad", nullable = false, length = 30)
    private String unidad;

    /** Para alimentos y medicamentos; null si no aplica. */
    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    /** Lo que de verdad llegó, que anota la organización al recibirla. */
    @Column(name = "cantidad_recibida", precision = 12, scale = 2)
    private BigDecimal cantidadRecibida;

    /** Lo exige JPA. */
    protected DonacionArticulo() {
    }

    public DonacionArticulo(String categoria, String descripcion, BigDecimal cantidad, String unidad,
                            LocalDate fechaVencimiento) {
        this.categoria = categoria;
        this.descripcion = descripcion;
        this.cantidad = cantidad;
        this.unidad = unidad;
        this.fechaVencimiento = fechaVencimiento;
    }

    public Integer getIdArticulo() {
        return idArticulo;
    }

    void setDonacion(Donacion donacion) {
        this.donacion = donacion;
    }

    public String getCategoria() {
        return categoria;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public String getUnidad() {
        return unidad;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public BigDecimal getCantidadRecibida() {
        return cantidadRecibida;
    }

    public void setCantidadRecibida(BigDecimal cantidadRecibida) {
        this.cantidadRecibida = cantidadRecibida;
    }
}
