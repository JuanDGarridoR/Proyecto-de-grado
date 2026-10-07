package com.proyectogrado.persona_mayor_service.repository;

import com.proyectogrado.persona_mayor_service.model.Emergencia;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Emergencias activadas por las personas mayores.
 */
public interface EmergenciaRepository extends JpaRepository<Emergencia, Integer> {
}
