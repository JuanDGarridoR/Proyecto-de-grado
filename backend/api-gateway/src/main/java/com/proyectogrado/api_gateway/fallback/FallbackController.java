package com.proyectogrado.api_gateway.fallback;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

/**
 * Respuesta cuando el servicio de una ruta no está disponible: su circuit
 * breaker está abierto, no contestó a tiempo o no tiene ninguna instancia
 * sana (ver default-filters en application.yml).
 *
 * Responde 503 con un texto para la persona, en vez de un error técnico: el
 * frontend muestra el cuerpo de los errores tal cual (mensajeDeError). Acepta
 * cualquier método porque el gateway reenvía aquí la petición original.
 *
 * No necesita @CrossOrigin: se llega por forward desde una ruta, que ya
 * aplicó el CORS de application.yml.
 */
@RestController
public class FallbackController {

    static final String MENSAJE =
            "Esta parte de Vita+ no está disponible en este momento. Intenta de nuevo en unos minutos.";

    @RequestMapping("/fallback/servicio-no-disponible")
    public Mono<ResponseEntity<String>> servicioNoDisponible() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(new MediaType(MediaType.TEXT_PLAIN, java.nio.charset.StandardCharsets.UTF_8))
                .body(MENSAJE));
    }
}
