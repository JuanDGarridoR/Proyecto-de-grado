package com.proyectogrado.persona_mayor_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de persona-mayor-service (puerto 8085): perfil, gustos,
 * acompañantes, organizaciones y botón de emergencia de la persona mayor.
 * También envía los mensajes de cumpleaños (ver CumpleanosScheduler y
 * TareasProgramadasConfig).
 */
@SpringBootApplication
public class PersonaMayorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonaMayorServiceApplication.class, args);
    }
}
