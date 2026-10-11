package com.proyectogrado.mensajeria_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de mensajeria-service (puerto 8082): envío de SMS con
 * TextBee, códigos OTP y notificaciones del panel.
 */
@SpringBootApplication
public class MensajeriaBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(MensajeriaBackendApplication.class, args);
	}

}
