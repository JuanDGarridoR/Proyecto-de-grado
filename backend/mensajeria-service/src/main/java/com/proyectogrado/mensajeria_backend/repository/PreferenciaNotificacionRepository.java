package com.proyectogrado.mensajeria_backend.repository;

import com.proyectogrado.mensajeria_backend.model.PreferenciaNotificacion;
import com.proyectogrado.mensajeria_backend.model.TipoNotificacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Preferencias de notificación de cada usuario (tabla preferencia_notificacion).
 */
public interface PreferenciaNotificacionRepository extends JpaRepository<PreferenciaNotificacion, Long> {

    List<PreferenciaNotificacion> findByIdUsuario(Integer idUsuario);

    Optional<PreferenciaNotificacion> findByIdUsuarioAndTipo(Integer idUsuario, TipoNotificacion tipo);
}
