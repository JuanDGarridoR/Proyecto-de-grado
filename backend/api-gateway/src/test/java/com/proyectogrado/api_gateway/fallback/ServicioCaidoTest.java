package com.proyectogrado.api_gateway.fallback;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Con un servicio caído, el gateway no deja la petición colgada ni devuelve
 * un error técnico: responde 503 con un mensaje para la persona (ver
 * FallbackController y default-filters en application.yml).
 *
 * auth-service apunta a un puerto donde no hay nada escuchando; se usa
 * /api/auth porque es una ruta pública (no hace falta token).
 */
@SpringBootTest(properties = {
        "JWT_SECRET=clave_de_pruebas_de_vita_mas_con_al_menos_32_bytes",
        "AUTH_SERVICE_URL=http://localhost:1"
})
class ServicioCaidoTest {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void siElServicioNoRespondeElGatewayDevuelve503ConUnMensajeAmable() {
        WebTestClient cliente = WebTestClient.bindToApplicationContext(contexto).build();

        cliente.get().uri("/api/auth/informacion")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(String.class).isEqualTo(FallbackController.MENSAJE);
    }
}
