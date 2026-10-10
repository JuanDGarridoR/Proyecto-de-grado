package com.proyectogrado.actividad_service.repository;

import com.proyectogrado.actividad_service.model.Actividad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Actividades periódicas cuyo día ya pasó y que ven las personas mayores
     * (no las propuestas pendientes ni rechazadas): les toca crear la siguiente.
     */
    @Query("""
            SELECT a FROM Actividad a
             WHERE a.frecuenciaDias IS NOT NULL
               AND a.fecha < :hoy
               AND (a.estado IS NULL OR a.estado = 'ACEPTADA')
            """)
    List<Actividad> findPeriodicasVencidas(@Param("hoy") LocalDate hoy);

    /**
     * Le quita la periodicidad a una ocurrencia para pasársela a la
     * siguiente. Solo afecta la fila si todavía la tenía; así, si hay dos
     * instancias del scheduler, solo una crea la siguiente ocurrencia.
     */
    @Modifying
    @Query("""
            UPDATE Actividad a
               SET a.frecuenciaDias = NULL
             WHERE a.idActividad = :idActividad
               AND a.frecuenciaDias IS NOT NULL
            """)
    int soltarPeriodicidad(@Param("idActividad") Integer idActividad);
}
