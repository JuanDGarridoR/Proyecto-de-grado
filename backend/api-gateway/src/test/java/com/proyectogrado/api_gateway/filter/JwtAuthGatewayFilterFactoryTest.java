package com.proyectogrado.api_gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Filtro JwtAuth del gateway (RNF-07 a RNF-10): sin un token válido la
 * petición no llega al servicio; con uno válido llega con X-User-Id y
 * X-User-Rol tomados del token, nunca de lo que mande el cliente.
 */
class JwtAuthGatewayFilterFactoryTest {

    private static final String CLAVE = "clave_de_pruebas_de_vita_mas_con_al_menos_32_bytes";

    private final GatewayFilter filtro =
            new JwtAuthGatewayFilterFactory(CLAVE).apply(new JwtAuthGatewayFilterFactory.Config());

    /** Petición que llegó al servicio, o null si el filtro la detuvo. */
    private final AtomicReference<ServerWebExchange> recibida = new AtomicReference<>();

    private String token(String clave, Integer idUsuario, String rol, long vigenciaMs) {
        Map<String, Object> claims = new HashMap<>();
        if (idUsuario != null) {
            claims.put("idUsuario", idUsuario);
        }
        claims.put("rol", rol);

        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + vigenciaMs))
                .signWith(Keys.hmacShaKeyFor(clave.getBytes()))
                .compact();
    }

    private MockServerWebExchange ejecutar(MockServerHttpRequest peticion) {
        MockServerWebExchange exchange = MockServerWebExchange.from(peticion);
        filtro.filter(exchange, siguiente -> {
            recibida.set(siguiente);
            return Mono.empty();
        }).block();
        return exchange;
    }

    @Test
    void sinTokenResponde401YLaPeticionNoLlegaAlServicio() {
        MockServerWebExchange exchange = ejecutar(MockServerHttpRequest.get("/api/persona-mayor/medicamentos").build());

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertNull(recibida.get());
    }

    @Test
    void unEncabezadoSinBearerSeRechaza() {
        MockServerWebExchange exchange = ejecutar(MockServerHttpRequest.get("/api/actividades")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXN1YXJpbzpjbGF2ZQ==").build());

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertNull(recibida.get());
    }

    @Test
    void unTokenFirmadoConOtraClaveSeRechaza() {
        String falso = token("otra_clave_que_no_es_la_de_vita_mas_1234567890", 9, "ORGANIZACION", 60_000);

        MockServerWebExchange exchange = ejecutar(MockServerHttpRequest.get("/api/analitica/salud")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + falso).build());

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertNull(recibida.get());
    }

    @Test
    void unTokenVencidoSeRechaza() {
        String vencido = token(CLAVE, 9, "ORGANIZACION", -60_000);

        MockServerWebExchange exchange = ejecutar(MockServerHttpRequest.get("/api/analitica/salud")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + vencido).build());

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertNull(recibida.get());
    }

    @Test
    void unTokenSinIdDeUsuarioSeRechaza() {
        String sinId = token(CLAVE, null, "ORGANIZACION", 60_000);

        MockServerWebExchange exchange = ejecutar(MockServerHttpRequest.get("/api/actividades")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sinId).build());

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertNull(recibida.get());
    }

    @Test
    void conTokenValidoLaPeticionLlegaConElIdYElRolDelToken() {
        MockServerWebExchange exchange = ejecutar(MockServerHttpRequest.get("/api/actividades")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CLAVE, 9, "ORGANIZACION", 60_000)).build());

        assertNull(exchange.getResponse().getStatusCode());
        assertNotNull(recibida.get());
        HttpHeaders encabezados = recibida.get().getRequest().getHeaders();
        assertEquals("9", encabezados.getFirst("X-User-Id"));
        assertEquals("ORGANIZACION", encabezados.getFirst("X-User-Rol"));
    }

    @Test
    void unXUserIdEnviadoPorElClienteSeReemplazaPorElDelToken() {
        // Un usuario intenta hacerse pasar por el usuario 1.
        ejecutar(MockServerHttpRequest.get("/api/persona-mayor/medicamentos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CLAVE, 9, "PERSONA_MAYOR", 60_000))
                .header("X-User-Id", "1")
                .header("X-User-Rol", "ORGANIZACION")
                .build());

        HttpHeaders encabezados = recibida.get().getRequest().getHeaders();
        assertEquals("9", encabezados.getFirst("X-User-Id"));
        assertEquals(1, encabezados.get("X-User-Id").size());
        assertEquals("PERSONA_MAYOR", encabezados.getFirst("X-User-Rol"));
    }
}
