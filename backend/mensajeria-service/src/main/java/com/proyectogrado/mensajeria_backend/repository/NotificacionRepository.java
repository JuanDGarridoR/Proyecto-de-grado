package com.proyectogrado.mensajeria_backend.repository;

import com.proyectogrado.mensajeria_backend.model.Notificacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Acceso al historial de notificaciones (tabla notificacion).
 */
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    List<Notificacion> findTop20ByCelularOrderByFechaEnvioDesc(String celular);

    long countByCelularAndLeidaFalse(String celular);

    /** Marca como leídas todas las notificaciones pendientes de ese celular. */
    @Transactional
    @Modifying
    @Query("update Notificacion n set n.leida = true where n.celular = :celular and n.leida = false")
    int marcarLeidas(@Param("celular") String celular);
}
