package com.proyectogrado.analitica_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Servicio de analítica: reportes de la organización (actividades, salud y
 * perfil de la población) y su descarga en PDF. Lee la base de datos
 * compartida; lo único que escribe es la ubicación en el mapa de las
 * organizaciones y las actividades (UbicadorAutomatico, que EnableScheduling
 * corre cada minuto).
 */
@SpringBootApplication
@EnableScheduling
public class AnaliticaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnaliticaServiceApplication.class, args);
    }
}
