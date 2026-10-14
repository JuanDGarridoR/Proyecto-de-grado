package com.proyectogrado.donacion_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entrada o salida de un artículo del inventario, con su motivo: una
 * donación recibida, una compra, lo que se entregó a las personas mayores,
 * lo que se venció... Es el historial: nunca se edita.
 */
@Entity
@Table(name = "inventario_movimiento")
public class InventarioMovimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    private Integer idMovimiento;

    @Column(name = "id_inventario", nullable = false)
    private Integer idInventario;

    /** Catalogo.ENTRADA o Catalogo.SALIDA. */
    @Column(name = "tipo", nullable = false)
    private String tipo;

    /** Siempre positiva; el tipo dice si suma o resta. */
    @Column(name = "cantidad", nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidad;

    @Column(name = "motivo", nullable = false)
    private String motivo;

    @Column(name = "detalle", length = 300)
    private String detalle;

    /** La donación de la que vino, si el motivo es DONACION. */
    @Column(name = "id_donacion")
    private Integer idDonacion;

    /** Cuenta de la organización que registró el movimiento. */
    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    /** Lo exige JPA. */
    protected InventarioMovimiento() {
    }

    public InventarioMovimiento(Integer idInventario, String tipo, BigDecimal cantidad, String motivo,
                                String detalle, Integer idDonacion, Integer idUsuario) {
        this.idInventario = idInventario;
        this.tipo = tipo;
        this.cantidad = cantidad;
        this.motivo = motivo;
        this.detalle = detalle;
        this.idDonacion = idDonacion;
        this.idUsuario = idUsuario;
        this.fecha = Catalogo.ahora();
    }

    public Integer getIdMovimiento() {
        return idMovimiento;
    }

    public Integer getIdInventario() {
        return idInventario;
    }

    public String getTipo() {
        return tipo;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getDetalle() {
        return detalle;
    }

    public Integer getIdDonacion() {
        return idDonacion;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }
}
