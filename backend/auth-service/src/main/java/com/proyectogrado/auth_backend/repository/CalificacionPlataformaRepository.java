package com.proyectogrado.auth_backend.repository;

import com.proyectogrado.auth_backend.model.CalificacionPlataforma;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Acceso a la tabla calificacion_plataforma (ver CalificacionPlataforma).
 */
public interface CalificacionPlataformaRepository extends JpaRepository<CalificacionPlataforma, Integer> {

    Optional<CalificacionPlataforma> findByIdUsuario(Integer idUsuario);
}
