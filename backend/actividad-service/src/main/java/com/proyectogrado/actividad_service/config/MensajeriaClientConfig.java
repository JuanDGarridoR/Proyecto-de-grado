package com.proyectogrado.actividad_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Cliente HTTP hacia mensajeria-service. La URL sale de mensajeria.service.url.
 */
@Configuration
public class MensajeriaClientConfig {

    @Bean
    public RestClient mensajeriaRestClient(
            @Value("${mensajeria.service.url}") String mensajeriaServiceUrl
    ) {
        // Sin límite de tiempo, si mensajeria-service se cuelga también se
        // cuelga quien lo llama (p. ej. el botón de emergencia o un
        // recordatorio). Si no conecta en 3 s o no responde en 15 s, el
        // envío se da por fallido.
        JdkClientHttpRequestFactory conexion = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        conexion.setReadTimeout(Duration.ofSeconds(15));

        return RestClient.builder()
                .baseUrl(mensajeriaServiceUrl)
                .requestFactory(conexion)
                .build();
    }
}
