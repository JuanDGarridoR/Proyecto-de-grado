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
 * Límite de peticiones por IP en las rutas públicas que se pueden abusar:
 * donde se prueban contraseñas o códigos, y donde se envían SMS (cada uno
 * se paga).
 *
 * Complementa los límites de los servicios: el bloqueo de la cuenta tras 5
 * contraseñas incorrectas (auth-service) y el tope de códigos por celular
 * (mensajeria-service). Esos frenan a quien ataca una cuenta o un celular;
 * este, a quien hace muchas peticiones desde la misma IP.
 *
 * Una ruta puede tener varias reglas (por ejemplo, por minutos y por día);
 * cada una cuenta aparte, y si alguna pasa su máximo, el gateway responde
 * 429 sin llegar al servicio hasta que termina la ventana de esa regla.
 *
 * Los contadores viven en memoria: basta porque hay un solo gateway (ver
 * ARQUITECTURA_DISPONIBILIDAD.md, punto 13). Con varios gateways habría
 * que pasarlos a Redis (filtro RequestRateLimiter).
 */
@Component
public class LimiteIntentosGlobalFilter implements GlobalFilter, Ordered {

    /** Método y ruta, cuántas peticiones se permiten por IP y en cuánto tiempo. */
    record Regla(HttpMethod metodo, String ruta, int maximo, Duration ventana) {
    }

    private static final Duration MINUTO = Duration.ofMinutes(1);
    private static final Duration DIA = Duration.ofDays(1);

    static final List<Regla> REGLAS = List.of(
            // Probar contraseñas o códigos
            new Regla(HttpMethod.POST, "/api/auth/login", 10, MINUTO),
            new Regla(HttpMethod.POST, "/api/auth/login-otp", 10, MINUTO),
            new Regla(HttpMethod.POST, "/api/auth/restablecer-contrasena", 10, MINUTO),

            // Cada código es un SMS pagado. Para entrar o recuperar la
            // contraseña alcanzan de sobra unos pocos al día.
            new Regla(HttpMethod.POST, "/api/otp/send", 5, Duration.ofMinutes(10)),
            new Regla(HttpMethod.POST, "/api/otp/send", 20, DIA)

    );

    /** Con más contadores que estos, se borran los de ventanas ya terminadas. */
    private static final int LIMPIAR_DESDE = 10_000;

    /** Peticiones de una IP a una regla desde que empezó su ventana, y cuánto dura esa ventana. */
    private record Ventana(Instant inicio, Duration duracion, int peticiones) {
        boolean terminada(Instant ahora) {
            return !ahora.isBefore(inicio.plus(duracion));
        }
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
        List<Regla> reglas = reglasDe(request.getMethod(), request.getPath().value());
        if (reglas.isEmpty()) {
            return chain.filter(exchange);
        }

        Instant ahora = reloj.instant();
        limpiarSiHaceFalta(ahora);
        String ip = ipDe(request);

        // Primero se revisan todas las reglas de la ruta. Si alguna ya llegó a
        // su máximo, se responde 429 sin contar la petición en ninguna: una
        // petición rechazada no gasta cupo. Se espera a que termine la ventana
        // más lejana de las que se pasaron.
        long segundosDeEspera = 0;
        for (Regla regla : reglas) {
            Ventana ventana = ventanas.get(clave(ip, regla));
            if (ventana != null && !ventana.terminada(ahora) && ventana.peticiones() >= regla.maximo()) {
                long segundos = Duration.between(ahora, ventana.inicio().plus(regla.ventana())).getSeconds();
                segundosDeEspera = Math.max(segundosDeEspera, Math.max(1, segundos));
            }
        }

        if (segundosDeEspera > 0) {
            return responderDemasiadasPeticiones(exchange.getResponse(), segundosDeEspera);
        }

        for (Regla regla : reglas) {
            ventanas.compute(clave(ip, regla), (clave, actual) -> actual == null || actual.terminada(ahora)
                    ? new Ventana(ahora, regla.ventana(), 1)
                    : new Ventana(actual.inicio(), regla.ventana(), actual.peticiones() + 1));
        }
        return chain.filter(exchange);
    }

    private static String clave(String ip, Regla regla) {
        return ip + " " + regla.metodo() + " " + regla.ruta() + " " + regla.ventana();
    }

    private static List<Regla> reglasDe(HttpMethod metodo, String ruta) {
        return REGLAS.stream()
                .filter(regla -> regla.metodo().equals(metodo) && regla.ruta().equals(ruta))
                .toList();
    }

    /** Mensaje para la persona según cuánto le falta esperar. */
    static String mensaje(long segundos) {
        long minutos = (segundos + 59) / 60;
        if (minutos <= 1) {
            return "Hiciste demasiados intentos seguidos. Espera un minuto e intenta de nuevo.";
        }
        if (minutos <= 60) {
            return "Hiciste demasiados intentos seguidos. Intenta de nuevo en " + minutos + " minutos.";
        }
        return "Hiciste demasiados intentos por hoy desde esta conexión. Intenta de nuevo más tarde.";
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
        ventanas.values().removeIf(ventana -> ventana.terminada(ahora));
    }

    /** 429 con el mismo formato de mensaje que auth-service ({"mensaje": ...}) y el encabezado Retry-After. */
    private static Mono<Void> responderDemasiadasPeticiones(ServerHttpResponse response, long segundos) {
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(segundos));

        byte[] cuerpo = ("{\"mensaje\": \"" + mensaje(segundos) + "\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(cuerpo);
        return response.writeWith(Mono.just(buffer));
    }

    /** Antes que todo lo demás: una petición rechazada no debe llegar a ningún servicio. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
