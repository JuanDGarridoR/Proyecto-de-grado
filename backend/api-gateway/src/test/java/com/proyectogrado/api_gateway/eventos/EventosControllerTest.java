package com.proyectogrado.api_gateway.eventos;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Stream de avisos de cambio (/api/eventos): exige sesión y entrega a cada
 * usuario conectado el nombre del recurso que cambió.
 */
class EventosControllerTest {

    private static final String CLAVE = "clave_de_pruebas_de_vita_mas_con_al_menos_32_bytes";

    private final CambiosPublisher publisher = new CambiosPublisher();
    private final EventosController controller = new EventosController(publisher, CLAVE);

    private String token(String clave) {
        return Jwts.builder()
                .claims(Map.of("idUsuario", 4, "rol", "ACOMPANANTE"))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(clave.getBytes()))
                .compact();
    }

    @Test
    void sinTokenResponde401() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.eventos(null));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    }

    @Test
    void conUnTokenFalsoResponde401() {
        String falso = token("otra_clave_que_no_es_la_de_vita_mas_1234567890");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.eventos("Bearer " + falso));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    }

    @Test
    void elUsuarioConectadoRecibeElRecursoQueCambio() throws Exception {
        CompletableFuture<ServerSentEvent<String>> primerAviso =
                controller.eventos("Bearer " + token(CLAVE)).next().toFuture();

        publisher.publicar("medicamentos");

        ServerSentEvent<String> aviso = primerAviso.get(5, TimeUnit.SECONDS);
        assertEquals("cambio", aviso.event());
        assertEquals("medicamentos", aviso.data());
    }
}
