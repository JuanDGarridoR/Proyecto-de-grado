package com.proyectogrado.auth_backend.repository;

import com.proyectogrado.auth_backend.model.RetiroCuenta;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a la tabla retiro_cuenta (razones por las que se eliminan cuentas).
 */
public interface RetiroCuentaRepository extends JpaRepository<RetiroCuenta, Integer> {
}
