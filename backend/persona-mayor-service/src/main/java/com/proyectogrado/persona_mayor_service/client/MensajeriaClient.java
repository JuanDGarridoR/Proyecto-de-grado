package com.proyectogrado.persona_mayor_service.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Cliente de mensajeria-service para enviar las alertas de emergencia por SMS.
 */
@Component
public class MensajeriaClient {

    private final RestClient restClient;

    public MensajeriaClient(RestClient mensajeriaRestClient) {
        this.restClient = mensajeriaRestClient;
    }

    /**
     * Devuelve true si mensajeria-service confirmó el envío; ante cualquier error, false.
     * tipo es el TipoNotificacion de mensajeria-service (EMERGENCIA,
     * MEDICAMENTO...): si el usuario lo desactivó en su perfil, no se envía
     * y también devuelve false.
     */
    public boolean enviarMensaje(String celular, String mensaje, String tipo) {

        try {
            Map<?, ?> respuesta = restClient.post()
                    .uri("/api/mensajes/enviar")
                    .body(Map.of("celular", celular, "mensaje", mensaje, "tipo", tipo))
                    .retrieve()
                    .body(Map.class);

            return respuesta != null && Boolean.TRUE.equals(respuesta.get("success"));

        } catch (Exception e) {
            return false;
        }
    }
}
