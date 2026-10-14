package com.proyectogrado.donacion_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lo que una organización tiene guardado: nombre, categoría, unidad y
 * cantidad actual. La cantidad solo cambia con movimientos (ver
 * InventarioMovimiento), así queda la historia de cada cambio.
 *
 * Un artículo se identifica por nombre, categoría y unidad dentro de la
 * organización: "Arroz (kg)" y "Arroz (paquetes)" son dos artículos.
 */
@Entity
@Table(name = "inventario_articulo")
public class InventarioArticulo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_inventario")
    private Integer idInventario;

    @Column(name = "id_organizacion", nullable = false)
    private Integer idOrganizacion;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "categoria", nullable = false)
    private String categoria;

    @Column(name = "unidad", nullable = false, length = 30)
    private String unidad;

    @Column(name = "cantidad", nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidad = BigDecimal.ZERO;

    /** Por debajo de esta cantidad la página lo marca como "por agotarse"; null = sin aviso. */
    @Column(name = "cantidad_minima", precision = 12, scale = 2)
    private BigDecimal cantidadMinima;

    /** La fecha de vencimiento más próxima de lo que hay guardado; null si no aplica. */
    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    /** Lo exige JPA. */
    protected InventarioArticulo() {
    }

    public InventarioArticulo(Integer idOrganizacion, String nombre, String categoria, String unidad) {
        this.idOrganizacion = idOrganizacion;
        this.nombre = nombre;
        this.categoria = categoria;
        this.unidad = unidad;
        this.fechaActualizacion = Catalogo.ahora();
    }

    /** Suma una entrada. La fecha de vencimiento que queda es la más próxima. */
    public void sumar(BigDecimal valor, LocalDate vencimiento) {
        this.cantidad = this.cantidad.add(valor);

        if (vencimiento != null && (fechaVencimiento == null || vencimiento.isBefore(fechaVencimiento))) {
            this.fechaVencimiento = vencimiento;
        }

        this.fechaActualizacion = Catalogo.ahora();
    }

    /** Resta una salida. Quien llama revisa antes que haya suficiente. */
    public void restar(BigDecimal valor) {
        this.cantidad = this.cantidad.subtract(valor);
        this.fechaActualizacion = Catalogo.ahora();
    }

    public Integer getIdInventario() {
        return idInventario;
    }

    public Integer getIdOrganizacion() {
        return idOrganizacion;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getUnidad() {
        return unidad;
    }

    public void setUnidad(String unidad) {
        this.unidad = unidad;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public BigDecimal getCantidadMinima() {
        return cantidadMinima;
    }

    public void setCantidadMinima(BigDecimal cantidadMinima) {
        this.cantidadMinima = cantidadMinima;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void marcarActualizado() {
        this.fechaActualizacion = Catalogo.ahora();
    }
}
