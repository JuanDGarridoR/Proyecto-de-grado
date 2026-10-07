package com.proyectogrado.salud_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Una enfermedad, alergia o discapacidad del catálogo (por ejemplo,
 * "Hipertensión arterial"). La persona mayor elige del catálogo en lugar de
 * escribir texto libre, para que la analítica pueda agrupar y contar sin
 * depender de cómo cada quien escribe el nombre.
 *
 * El catálogo lo llena CatalogoCondicionSaludInicializador al arrancar.
 */
@Entity
@Table(
        name = "condicion_salud",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tipo", "nombre"})
)
public class CondicionSalud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_condicion_salud")
    private Integer idCondicionSalud;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoCondicionSalud tipo;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    /**
     * Grupo dentro del tipo, para agrupar en la analítica: "Cardiovascular"
     * en enfermedades, "Medicamentos" en alergias, "Visual" en discapacidades.
     */
    @Column(name = "categoria", nullable = false, length = 60)
    private String categoria;

    /**
     * Opción "Otra" de cada tipo: la persona mayor escribe en el detalle cuál
     * es, y la puede registrar varias veces.
     */
    @Column(name = "es_otra", nullable = false)
    private Boolean esOtra;

    public CondicionSalud() {
    }

    public CondicionSalud(TipoCondicionSalud tipo, String nombre, String categoria, boolean esOtra) {
        this.tipo = tipo;
        this.nombre = nombre;
        this.categoria = categoria;
        this.esOtra = esOtra;
    }

    public Integer getIdCondicionSalud() {
        return idCondicionSalud;
    }

    public void setIdCondicionSalud(Integer idCondicionSalud) {
        this.idCondicionSalud = idCondicionSalud;
    }

    public TipoCondicionSalud getTipo() {
        return tipo;
    }

    public void setTipo(TipoCondicionSalud tipo) {
        this.tipo = tipo;
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

    public Boolean getEsOtra() {
        return esOtra;
    }

    public void setEsOtra(Boolean esOtra) {
        this.esOtra = esOtra;
    }
}
