package com.proyectogrado.voluntario_service.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Gusto, talento o pasatiempo que marcó un voluntario (tabla
 * voluntario_gusto). Con ellos se le recomiendan organizaciones, igual que
 * a las personas mayores (ver RecomendadorOrganizaciones en
 * analitica-service).
 */
@Entity
@Table(name = "voluntario_gusto")
public class VoluntarioGusto {

    @EmbeddedId
    private VoluntarioGustoId id;

    public VoluntarioGusto() {
    }

    public VoluntarioGusto(Integer idVoluntario, Integer idGusto) {
        this.id = new VoluntarioGustoId(idVoluntario, idGusto);
    }

    public VoluntarioGustoId getId() {
        return id;
    }
}
