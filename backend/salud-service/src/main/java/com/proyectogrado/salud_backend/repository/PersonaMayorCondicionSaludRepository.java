package com.proyectogrado.salud_backend.repository;

import com.proyectogrado.salud_backend.model.PersonaMayorCondicionSalud;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acceso a las enfermedades, alergias y discapacidades de cada persona mayor.
 */
public interface PersonaMayorCondicionSaludRepository
        extends JpaRepository<PersonaMayorCondicionSalud, Integer> {

    List<PersonaMayorCondicionSalud> findByIdPersonaMayor(Integer idPersonaMayor);

    /** Para no registrar dos veces la misma condición del catálogo. */
    boolean existsByIdPersonaMayorAndCondicion_IdCondicionSalud(Integer idPersonaMayor, Integer idCondicionSalud);
}
