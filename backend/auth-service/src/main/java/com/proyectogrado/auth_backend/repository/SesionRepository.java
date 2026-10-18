package com.proyectogrado.auth_backend.repository;

import com.proyectogrado.auth_backend.model.Sesion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Acceso a la tabla sesion (ver Sesion y SesionService).
 */
public interface SesionRepository extends JpaRepository<Sesion, Long> {

    Optional<Sesion> findByHashToken(String hashToken);

    long deleteByHashToken(String hashToken);

    long deleteByIdUsuario(Integer idUsuario);

    long deleteByIdUsuarioAndHashTokenNot(Integer idUsuario, String hashToken);

    long deleteByIdUsuarioAndExpiraBefore(Integer idUsuario, LocalDateTime fecha);
}
