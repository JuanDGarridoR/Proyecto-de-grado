package com.proyectogrado.messaging_backend.repository;

import com.proyectogrado.messaging_backend.model.UsuarioLookup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Lectura de la tabla usuario (ver UsuarioLookup).
 */
public interface UsuarioLookupRepository extends JpaRepository<UsuarioLookup, Integer> {

    /** Dueño de un celular (es único: es el login del usuario). */
    Optional<UsuarioLookup> findFirstByCelular(String celular);
}
