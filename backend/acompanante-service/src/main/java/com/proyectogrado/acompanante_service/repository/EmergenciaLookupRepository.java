package com.proyectogrado.acompanante_service.repository;

import com.proyectogrado.acompanante_service.model.EmergenciaLookup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Lectura de las emergencias que activaron las personas mayores.
 */
public interface EmergenciaLookupRepository extends JpaRepository<EmergenciaLookup, Integer> {

    /** Emergencias de esas personas desde la fecha indicada, de la más reciente a la más antigua. */
    List<EmergenciaLookup> findByIdPersonaMayorInAndFechaHoraAfterOrderByFechaHoraDesc(
            Collection<Integer> idsPersonasMayores, LocalDateTime desde);
}
