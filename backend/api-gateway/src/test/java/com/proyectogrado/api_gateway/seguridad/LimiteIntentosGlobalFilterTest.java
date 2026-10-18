package com.proyectogrado.api_gateway.seguridad;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Límite de peticiones por IP en las rutas públicas que se pueden abusar
 * (login y envío de códigos por SMS):
 * pasado el máximo, el gateway responde 429 sin llegar al servicio.
 */
class LimiteIntentosGlobalFilterTest {

    /** Reloj que se puede adelantar a mano. */
    private static final class RelojDePrueba extends Clock {
        private Instant ahora = Instant.parse("2026-10-17T15:00:00Z");

        void adelantar(Duration tiempo) {
            ahora = ahora.plus(tiempo);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }
    }

    private RelojDePrueba reloj;
    private LimiteIntentosGlobalFilter filtro;
    private int llegaronAlServicio;

    @BeforeEach
    void setUp() {
        reloj = new RelojDePrueba();
        filtro = new LimiteIntentosGlobalFilter(reloj);
        llegaronAlServicio = 0;
    }

    /** Pasa la petición por el filtro y devuelve el estado de la respuesta. */
    private MockServerWebExchange enviar(HttpMethod metodo, String ruta, String ip) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(metodo, ruta).header("X-Forwarded-For", ip).build());
        filtro.filter(exchange, siguiente -> {
            llegaronAlServicio++;
            siguiente.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return Mono.empty();
        }).block();
        return exchange;
    }

    private HttpStatusCode estado(MockServerWebExchange exchange) {
        return exchange.getResponse().getStatusCode();
    }

    @Test
    void despuesDeDiezIntentosEnUnMinutoLaIpRecibe429SinLlegarAlServicio() {
        for (int i = 0; i < 10; i++) {
            assertEquals(HttpStatus.UNAUTHORIZED, estado(enviar(HttpMethod.POST, "/api/auth/login", "200.1.1.1")));
        }

        MockServerWebExchange bloqueada = enviar(HttpMethod.POST, "/api/auth/login", "200.1.1.1");

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, estado(bloqueada));
        assertEquals(10, llegaronAlServicio);
        assertEquals("60", bloqueada.getResponse().getHeaders().getFirst("Retry-After"));
        assertTrue(bloqueada.getResponse().getBodyAsString().block().contains("Espera un minuto"));
    }

    @Test
    void alTerminarLaVentanaLaIpPuedeVolverAIntentar() {
        for (int i = 0; i < 11; i++) {
            enviar(HttpMethod.POST, "/api/auth/login", "200.1.1.1");
        }

        reloj.adelantar(Duration.ofSeconds(60));

        assertEquals(HttpStatus.UNAUTHORIZED, estado(enviar(HttpMethod.POST, "/api/auth/login", "200.1.1.1")));
    }

    @Test
    void cadaIpYCadaRutaTienenSuPropioContador() {
        for (int i = 0; i < 11; i++) {
            enviar(HttpMethod.POST, "/api/auth/login", "200.1.1.1");
        }

        assertEquals(HttpStatus.UNAUTHORIZED, estado(enviar(HttpMethod.POST, "/api/auth/login", "200.2.2.2")));
        assertEquals(HttpStatus.UNAUTHORIZED, estado(enviar(HttpMethod.POST, "/api/auth/login-otp", "200.1.1.1")));
    }

    @Test
    void lasRutasSinReglaYLasConsultasNoSeLimitan() {
        for (int i = 0; i < 30; i++) {
            enviar(HttpMethod.POST, "/api/persona-mayor/medicamentos", "200.1.1.1");
            enviar(HttpMethod.GET, "/api/auth/login", "200.1.1.1");
        }

        assertEquals(60, llegaronAlServicio);
    }

    @Test
    void laIpEsLaUltimaDeXForwardedForQueEsLaQuePusoCaddy() {
        // Aunque el navegador mande una IP distinta en cada intento, cuenta la que agregó Caddy.
        for (int i = 0; i < 10; i++) {
            enviar(HttpMethod.POST, "/api/auth/login", "10.0.0." + i + ", 200.1.1.1");
        }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                estado(enviar(HttpMethod.POST, "/api/auth/login", "1.2.3.4, 200.1.1.1")));
    }

    @Test
    void losCodigosPorSmsTienenUnLimiteCadaDiezMinutosYOtroPorDia() {
        for (int i = 0; i < 5; i++) {
            enviar(HttpMethod.POST, "/api/otp/send", "200.1.1.1");
        }
        MockServerWebExchange sexto = enviar(HttpMethod.POST, "/api/otp/send", "200.1.1.1");
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, estado(sexto));
        assertEquals("600", sexto.getResponse().getHeaders().getFirst("Retry-After"));

        // Cada diez minutos se pueden 5 más, hasta 20 en el día.
        for (int bloque = 0; bloque < 3; bloque++) {
            reloj.adelantar(Duration.ofMinutes(10));
            for (int i = 0; i < 5; i++) {
                assertEquals(HttpStatus.UNAUTHORIZED, estado(enviar(HttpMethod.POST, "/api/otp/send", "200.1.1.1")));
            }
        }
        reloj.adelantar(Duration.ofMinutes(10));

        MockServerWebExchange vigesimoPrimero = enviar(HttpMethod.POST, "/api/otp/send", "200.1.1.1");
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, estado(vigesimoPrimero));
        assertTrue(vigesimoPrimero.getResponse().getBodyAsString().block().contains("por hoy"));
        assertEquals(20, llegaronAlServicio);
    }
}
