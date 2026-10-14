package com.proyectogrado.persona_mayor_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un gusto o interés del catálogo (por ejemplo, "Boleros").
 */
@Entity
@Table(name = "gusto")
public class Gusto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_gusto")
    private Integer idGusto;

    @Column(name = "nombre", nullable = false, unique = true)
    private String nombre;

    /** Grupo del gusto: GUSTO, TALENTO, HOBBY o PROFESION (ver CatalogoProfesiones). */
    @Column(name = "categoria", length = 20)
    private String categoria;

    public Gusto() {
    }

    public Gusto(String nombre, String categoria) {
        this.nombre = nombre;
        this.categoria = categoria;
    }

    public Integer getIdGusto() {
        return idGusto;
    }

    public void setIdGusto(Integer idGusto) {
        this.idGusto = idGusto;
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
}
