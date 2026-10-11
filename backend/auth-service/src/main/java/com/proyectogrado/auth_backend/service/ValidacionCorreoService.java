package com.proyectogrado.auth_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Validación de correos con la Email Verifier API de Hunter: comprueba que
 * el correo exista y pueda recibir mensajes. Se usa en el registro y al
 * cambiar el correo en "Mi información".
 */
@Service
public class ValidacionCorreoService {

    private static final Logger log = LoggerFactory.getLogger(ValidacionCorreoService.class);

    private final String apiKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Si hunter.api-key no está definida, el servicio no arranca: es mejor
     * fallar al inicio que en silencio en cada registro.
     */
    public ValidacionCorreoService(
            @Value("${hunter.api-key:}") String apiKey
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "La propiedad 'hunter.api-key' no se resolvió. "
                            + "Revisa application.properties y el perfil activo."
            );
        }
        this.apiKey = apiKey;
        log.info("Hunter API key cargada, longitud={}, prefijo={}",
                apiKey.length(), apiKey.substring(0, Math.min(4, apiKey.length())));
    }

    /**
     * Misma regla del registro: el correo solo se acepta si Hunter responde
     * con estado "valid" (existe y puede recibir mensajes).
     */
    public boolean puedeRecibirCorreos(String correo) {
        return "valid".equalsIgnoreCase(
                validarCorreo(correo).path("data").path("status").asText()
        );
    }

    /** Consulta a Hunter y devuelve su respuesta tal cual. Si Hunter falla, lanza RuntimeException. */
    public JsonNode validarCorreo(String correo) {

        try {
            String url = "https://api.hunter.io/v2/email-verifier"
                    + "?email=" + java.net.URLEncoder.encode(
                            correo,
                            java.nio.charset.StandardCharsets.UTF_8
                    )
                    + "&api_key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.debug("HUNTER STATUS: {}", response.statusCode());
            log.debug("HUNTER RESPONSE: {}", response.body());

            if (response.statusCode() >= 300) {
                throw new RuntimeException(
                        "Hunter respondió " + response.statusCode() + ": " + response.body()
                );
            }

            return objectMapper.readTree(response.body());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("La validación del correo fue interrumpida", e);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo validar el correo con Hunter: " + e.getMessage(), e);
        }
    }
}