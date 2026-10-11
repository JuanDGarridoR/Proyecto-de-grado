package com.proyectogrado.auth_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia mensajeria-service, que es el único que envía y valida
 * códigos OTP. La URL sale de mensajeria.service.url.
 */
@Configuration
public class MensajeriaClientConfig {

    @Bean
    public RestClient mensajeriaRestClient(
            @Value("${mensajeria.service.url}") String mensajeriaServiceUrl
    ) {
        return RestClient.builder()
                .baseUrl(mensajeriaServiceUrl)
                .build();
    }
}
