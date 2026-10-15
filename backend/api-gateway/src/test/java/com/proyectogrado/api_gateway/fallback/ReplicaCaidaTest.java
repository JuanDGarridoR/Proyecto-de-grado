package com.proyectogrado.api_gateway.fallback;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Balanceo con una réplica caída: auth-service tiene dos instancias, una
 * apagada (puerto 1, rechaza la conexión) y otra que responde. Ver
 * default-filters y spring.cloud.loadbalancer en application.yml.
 */
@SpringBootTest(properties = "JWT_SECRET=clave_de_pruebas_de_vita_mas_con_al_menos_32_bytes")
class ReplicaCaidaTest {

    /** Peticiones que recibió la réplica sana a /api/auth/falla. */
    private static final AtomicInteger LLAMADAS_CON_ERROR = new AtomicInteger();

    /** Réplica sana: responde "ok", salvo /api/auth/falla, que siempre da 500. */
    private static final HttpServer REPLICA_SANA = iniciarReplicaSana();

    private static HttpServer iniciarReplicaSana() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/", intercambio -> {
                boolean falla = intercambio.getRequestURI().getPath().equals("/api/auth/falla");
                if (falla) {
                    LLAMADAS_CON_ERROR.incrementAndGet();
                }
                byte[] cuerpo = (falla ? "error" : "ok").getBytes(StandardCharsets.UTF_8);
                intercambio.sendResponseHeaders(falla ? 500 : 200, cuerpo.length);
                intercambio.getResponseBody().write(cuerpo);
                intercambio.close();
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void dosReplicas(DynamicPropertyRegistry registro) {
        registro.add("spring.cloud.discovery.client.simple.instances.auth-service[0].uri",
                () -> "http://localhost:1");
        registro.add("spring.cloud.discovery.client.simple.instances.auth-service[1].uri",
                () -> "http://localhost:" + REPLICA_SANA.getAddress().getPort());
    }

    @AfterAll
    static void apagar() {
        REPLICA_SANA.stop(0);
    }

    @Autowired
    private ApplicationContext contexto;

    private WebTestClient cliente;

    @BeforeEach
    void setUp() {
        cliente = WebTestClient.bindToApplicationContext(contexto).build();
        LLAMADAS_CON_ERROR.set(0);
    }

    @Test
    void losGetLleganALaReplicaViva() {
        for (int i = 0; i < 6; i++) {
            cliente.get().uri("/api/auth/informacion")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class).isEqualTo("ok");
        }
    }

    @Test
    void losPostTambienLleganALaReplicaViva() {
        // Como el login: si la réplica a la que le tocaba está apagada, la
        // petición nunca llegó y se repite en la otra.
        for (int i = 0; i < 6; i++) {
            cliente.post().uri("/api/auth/login")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class).isEqualTo("ok");
        }
    }

    @Test
    void unPostQueElServicioRecibioNoSeRepite() {
        // El servicio respondió 500: ya procesó la petición, repetirla podría
        // duplicar lo que hizo (dos alertas de emergencia, dos registros...).
        for (int i = 0; i < 4; i++) {
            cliente.post().uri("/api/auth/falla")
                    .exchange()
                    .expectStatus().isEqualTo(500);
        }

        assertEquals(4, LLAMADAS_CON_ERROR.get());
    }
}
