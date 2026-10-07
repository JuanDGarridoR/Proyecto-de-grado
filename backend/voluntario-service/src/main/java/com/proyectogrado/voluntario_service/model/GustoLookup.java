package com.proyectogrado.voluntario_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Vista de solo lectura del catálogo de gustos (tabla gusto), que pertenece
 * a persona-mayor-service. Voluntarios y personas mayores marcan sus gustos
 * del mismo catálogo.
 */
@Entity
@Table(name = "gusto")
public class GustoLookup {

    // Igual que en persona-mayor-service: si este servicio arranca primero en
    // una base nueva y crea la tabla, el id debe quedar autogenerado.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_gusto")
    private Integer idGusto;

    @Column(name = "nombre", nullable = false, unique = true)
    private String nombre;

    /** GUSTO, TALENTO o HOBBY. */
    @Column(name = "categoria", length = 20)
    private String categoria;

    public Integer getIdGusto() {
        return idGusto;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCategoria() {
        return categoria;
    }
}
