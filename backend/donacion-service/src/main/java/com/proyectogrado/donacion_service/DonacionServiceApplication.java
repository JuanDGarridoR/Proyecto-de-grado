package com.proyectogrado.donacion_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de donacion-service (puerto 8091): donaciones que los
 * acompañantes y voluntarios programan a sus organizaciones, y el
 * inventario de cada organización.
 */
@SpringBootApplication
public class DonacionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DonacionServiceApplication.class, args);
    }
}
