package com.proyectogrado.donacion_service.repository;

import com.proyectogrado.donacion_service.model.Donacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acceso a la tabla donacion (ver Donacion).
 */
public interface DonacionRepository extends JpaRepository<Donacion, Integer> {

    /** Donaciones del acompañante o voluntario, las más recientes primero. */
    List<Donacion> findByIdDonanteOrderByFechaRegistroDesc(Integer idDonante);

    /** Donaciones hacia la organización, ordenadas por fecha de entrega. */
    List<Donacion> findByIdOrganizacionOrderByFechaEntregaAsc(Integer idOrganizacion);
}
