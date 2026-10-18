package com.proyectogrado.api_gateway.seguridad;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de intentos por IP en las rutas públicas donde se prueban
 * contraseñas o códigos. Complementa el bloqueo de la cuenta tras 5
 * contraseñas incorrectas (auth-service): ese frena a quien ataca una
 * cuenta; este, a quien prueba muchas cuentas desde la misma IP.
 *
 * Si una IP pasa el máximo de una regla dentro de su ventana, el gateway
 * responde 429 sin llegar al servicio, hasta que la ventana termina.
 *
 * Los contadores viven en memoria: basta porque hay un solo gateway (ver
 * ARQUITECTURA_DISPONIBILIDAD.md, punto 13). Con varios gateways habría
 * que pasarlos a Redis (filtro RequestRateLimiter).
 */
@Component
public class LimiteIntentosGlobalFilter implements GlobalFilter, Ordered {

    /** Ruta (solo POST), cuántas peticiones se permiten por IP y en cuánto tiempo. */
    record Regla(String ruta, int maximo, Duration ventana) {
    }

    static final List<Regla> REGLAS = List.of(
            new Regla("/api/auth/login", 10, Duration.ofMinutes(1)),
            new Regla("/api/auth/login-otp", 10, Duration.ofMinutes(1)),
            new Regla("/api/auth/restablecer-contrasena", 10, Duration.ofMinutes(1))
    );

    static final String MENSAJE =
            "Hiciste demasiados intentos seguidos. Espera un minuto e intenta de nuevo.";

    /** Con más contadores que estos, se borran los de ventanas ya terminadas. */
    private static final int LIMPIAR_DESDE = 10_000;

    /** Peticiones de una IP a una regla desde que empezó su ventana. */
    private record Ventana(Instant inicio, int peticiones) {
    }

    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();
    private final Clock reloj;

    public LimiteIntentosGlobalFilter() {
        this(Clock.systemUTC());
    }

    LimiteIntentosGlobalFilter(Clock reloj) {
        this.reloj = reloj;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!HttpMethod.POST.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        Regla regla = reglaDe(request.getPath().value());
        if (regla == null) {
            return chain.filter(exchange);
        }

        Instant ahora = reloj.instant();
        limpiarSiHaceFalta(ahora);

        Ventana ventana = ventanas.compute(ipDe(request) + " " + regla.ruta(), (clave, actual) ->
                actual == null || !ahora.isBefore(actual.inicio().plus(regla.ventana()))
                        ? new Ventana(ahora, 1)
                        : new Ventana(actual.inicio(), actual.peticiones() + 1));

        if (ventana.peticiones() <= regla.maximo()) {
            return chain.filter(exchange);
        }

        long segundos = Math.max(1, Duration.between(ahora, ventana.inicio().plus(regla.ventana())).getSeconds());
        return responderDemasiadasPeticiones(exchange.getResponse(), segundos);
    }

    private static Regla reglaDe(String ruta) {
        for (Regla regla : REGLAS) {
            if (regla.ruta().equals(ruta)) {
                return regla;
            }
        }
        return null;
    }

    /**
     * IP de quien hace la petición. En el servidor, el gateway solo recibe
     * peticiones de Caddy, que pone en X-Forwarded-For la IP real del
     * navegador (e ignora la que el navegador mande por su cuenta). Se toma
     * la última de la lista, que es la que agregó el proxy más cercano.
     */
    static String ipDe(ServerHttpRequest request) {
        String reenviada = request.getHeaders().getFirst("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            String[] ips = reenviada.split(",");
            return ips[ips.length - 1].trim();
        }
        InetSocketAddress remota = request.getRemoteAddress();
        return remota == null || remota.getAddress() == null ? "desconocida" : remota.getAddress().getHostAddress();
    }

    /** Borra los contadores cuya ventana ya terminó, para que la memoria no crezca sin límite. */
    private void limpiarSiHaceFalta(Instant ahora) {
        if (ventanas.size() < LIMPIAR_DESDE) {
            return;
        }
        Duration ventanaMasLarga = REGLAS.stream().map(Regla::ventana).max(Duration::compareTo).orElseThrow();
        ventanas.values().removeIf(v -> !ahora.isBefore(v.inicio().plus(ventanaMasLarga)));
    }

    /** 429 con el mismo formato de mensaje que auth-service ({"mensaje": ...}) y el encabezado Retry-After. */
    private static Mono<Void> responderDemasiadasPeticiones(ServerHttpResponse response, long segundos) {
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(segundos));

        byte[] cuerpo = ("{\"mensaje\": \"" + MENSAJE + "\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(cuerpo);
        return response.writeWith(Mono.just(buffer));
    }

    /** Antes que todo lo demás: una petición rechazada no debe llegar a ningún servicio. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
