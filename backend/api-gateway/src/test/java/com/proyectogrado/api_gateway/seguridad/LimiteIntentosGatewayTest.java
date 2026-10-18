package com.proyectogrado.api_gateway.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * El límite de intentos con el gateway completo: el 429 lleva las
 * cabeceras CORS, así el navegador deja que el frontend lea el mensaje en
 * vez de verlo como un error de red.
 *
 * auth-service apunta a un puerto donde no hay nada escuchando: los
 * primeros 10 intentos terminan en el 503 del circuit breaker, y el
 * undécimo ni siquiera se intenta.
 */
@SpringBootTest(properties = {
        "JWT_SECRET=clave_de_pruebas_de_vita_mas_con_al_menos_32_bytes",
        "AUTH_SERVICE_URL=http://localhost:1"
})
class LimiteIntentosGatewayTest {

    private static final String ORIGEN = "http://localhost:4200";

    @Autowired
    private ApplicationContext contexto;

    @Test
    void elUndecimoIntentoRecibe429ConCorsYElMensaje() {
        WebTestClient cliente = WebTestClient.bindToApplicationContext(contexto).build();

        for (int i = 0; i < 10; i++) {
            cliente.post().uri("http://localhost:8080/api/auth/login")
                    .header("Origin", ORIGEN)
                    .header("X-Forwarded-For", "200.1.1.1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{}")
                    .exchange()
                    .expectStatus().isEqualTo(503);
        }

        cliente.post().uri("http://localhost:8080/api/auth/login")
                .header("Origin", ORIGEN)
                .header("X-Forwarded-For", "200.1.1.1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("Access-Control-Allow-Origin", ORIGEN)
                .expectBody().jsonPath("$.mensaje").isEqualTo(LimiteIntentosGlobalFilter.mensaje(60));
    }
}
