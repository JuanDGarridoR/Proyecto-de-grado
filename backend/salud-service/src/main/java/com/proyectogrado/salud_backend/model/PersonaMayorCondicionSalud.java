package com.proyectogrado.salud_backend.model;

import com.proyectogrado.salud_backend.config.ZonaHoraria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Enfermedad, alergia o discapacidad que registró una persona mayor. Una
 * persona puede tener cero o varias de cada tipo; ninguna es obligatoria.
 *
 * idPersonaMayor es un Integer simple y no una relación JPA, porque la
 * tabla persona_mayor pertenece a auth-service. La condición sí es de este
 * servicio y se referencia como entidad.
 */
@Entity
@Table(
        name = "persona_mayor_condicion_salud",
        indexes = @Index(name = "idx_pm_condicion_salud_persona", columnList = "id_persona_mayor")
)
public class PersonaMayorCondicionSalud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_persona_mayor_condicion_salud")
    private Integer idPersonaMayorCondicionSalud;

    @Column(name = "id_persona_mayor", nullable = false)
    private Integer idPersonaMayor;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_condicion_salud", nullable = false)
    private CondicionSalud condicion;

    /** Opcional: la persona mayor puede no saberlo. */
    @Enumerated(EnumType.STRING)
    @Column(name = "severidad", length = 20)
    private SeveridadCondicion severidad;

    /**
     * Texto libre opcional, por ejemplo "Me da urticaria". En la opción
     * "Otra" es obligatorio y dice cuál es la condición.
     */
    @Column(name = "detalle", length = 500)
    private String detalle;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDate fechaRegistro;

    public PersonaMayorCondicionSalud() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.fechaRegistro == null) {
            this.fechaRegistro = ZonaHoraria.hoy();
        }
    }

    public Integer getIdPersonaMayorCondicionSalud() {
        return idPersonaMayorCondicionSalud;
    }

    public void setIdPersonaMayorCondicionSalud(Integer idPersonaMayorCondicionSalud) {
        this.idPersonaMayorCondicionSalud = idPersonaMayorCondicionSalud;
    }

    public Integer getIdPersonaMayor() {
        return idPersonaMayor;
    }

    public void setIdPersonaMayor(Integer idPersonaMayor) {
        this.idPersonaMayor = idPersonaMayor;
    }

    public CondicionSalud getCondicion() {
        return condicion;
    }

    public void setCondicion(CondicionSalud condicion) {
        this.condicion = condicion;
    }

    public SeveridadCondicion getSeveridad() {
        return severidad;
    }

    public void setSeveridad(SeveridadCondicion severidad) {
        this.severidad = severidad;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }

    public LocalDate getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDate fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }
}
