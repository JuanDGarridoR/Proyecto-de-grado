package com.proyectogrado.voluntario_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Llave compuesta de voluntario_gusto: voluntario más gusto.
 */
@Embeddable
public class VoluntarioGustoId implements Serializable {

    @Column(name = "id_voluntario")
    private Integer idVoluntario;

    @Column(name = "id_gusto")
    private Integer idGusto;

    public VoluntarioGustoId() {
    }

    public VoluntarioGustoId(Integer idVoluntario, Integer idGusto) {
        this.idVoluntario = idVoluntario;
        this.idGusto = idGusto;
    }

    public Integer getIdVoluntario() {
        return idVoluntario;
    }

    public Integer getIdGusto() {
        return idGusto;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VoluntarioGustoId that)) return false;
        return Objects.equals(idVoluntario, that.idVoluntario)
                && Objects.equals(idGusto, that.idGusto);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idVoluntario, idGusto);
    }
}
