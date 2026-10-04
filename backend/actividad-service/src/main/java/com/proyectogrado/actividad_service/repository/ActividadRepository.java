package com.proyectogrado.actividad_service.repository;

import com.proyectogrado.actividad_service.model.Actividad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Acceso a la tabla actividad.
 */
public interface ActividadRepository extends JpaRepository<Actividad, Integer> {

    List<Actividad> findByIdOrganizacion(Integer idOrganizacion);

    /** Propuestas de un voluntario, en cualquier estado. */
    List<Actividad> findByIdVoluntario(Integer idVoluntario);

    /** Propuestas de una persona mayor, en cualquier estado. */
    List<Actividad> findByIdPersonaMayorProponente(Integer idPersonaMayor);

    /** Propuestas (de voluntarios o personas mayores) a una organización con un estado dado. */
    List<Actividad> findByIdOrganizacionAndEstado(Integer idOrganizacion, String estado);

    /** Actividades entre dos fechas, ambas incluidas. La usa el scheduler de recordatorios. */
    List<Actividad> findByFechaBetween(LocalDate desde, LocalDate hasta);
}
