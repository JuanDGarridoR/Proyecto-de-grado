package com.proyectogrado.api_gateway.eventos;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Detecta cada escritura exitosa (POST/PUT/DELETE/PATCH con respuesta 2xx)
 * que pasa por el gateway y avisa qué recurso cambió, para que los demás
 * usuarios vean el cambio sin recargar la página.
 *
 * Como todo el tráfico del frontend pasa por aquí, los servicios no tienen
 * que publicar nada. Si se agrega un endpoint nuevo que modifica datos
 * compartidos, basta con agregar su ruta en RECURSOS_POR_RUTA.
 */
@Component
public class PublicarCambiosGlobalFilter implements GlobalFilter, Ordered {

    private static final Set<HttpMethod> METODOS_DE_ESCRITURA =
            Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH);

    /**
     * Ruta del gateway y recurso que cambia cuando se escribe en ella. Gana
     * la primera coincidencia. Las rutas que no aparecen (login, OTP,
     * contraseña) no cambian datos que vean otros usuarios. "*" significa
     * que cambia todo: al borrar una cuenta se borran también sus relaciones.
     */
    private static final Map<String, String> RECURSOS_POR_RUTA = new LinkedHashMap<>();

    static {
        RECURSOS_POR_RUTA.put("/api/auth/cuenta", "*");
        RECURSOS_POR_RUTA.put("/api/auth/registro", "usuarios");
        RECURSOS_POR_RUTA.put("/api/auth/informacion", "usuarios");
        RECURSOS_POR_RUTA.put("/api/persona-mayor/perfil", "usuarios");
        RECURSOS_POR_RUTA.put("/api/organizacion/informacion", "usuarios");

        // Tienen que ir antes de /api/voluntario/**, que es el perfil del voluntario.
        RECURSOS_POR_RUTA.put("/api/voluntario/organizaciones/**", "voluntarios");
        RECURSOS_POR_RUTA.put("/api/organizacion/voluntarios/**", "voluntarios");

        RECURSOS_POR_RUTA.put("/api/voluntario/**", "usuarios");

        RECURSOS_POR_RUTA.put("/api/actividades/**", "actividades");

        RECURSOS_POR_RUTA.put("/api/persona-mayor/medicamentos/**", "medicamentos");

        RECURSOS_POR_RUTA.put("/api/persona-mayor/citas-medicas/**", "citas-medicas");

        RECURSOS_POR_RUTA.put("/api/persona-mayor/condiciones-salud/**", "condiciones-salud");

        RECURSOS_POR_RUTA.put("/api/persona-mayor/signos-vitales/**", "signos-vitales");
        RECURSOS_POR_RUTA.put("/api/organizacion/signos-vitales/**", "signos-vitales");

        RECURSOS_POR_RUTA.put("/api/persona-mayor/acompanantes/**", "acompanamientos");
        RECURSOS_POR_RUTA.put("/api/acompanante/**", "acompanamientos");

        RECURSOS_POR_RUTA.put("/api/persona-mayor/organizaciones/**", "organizaciones");
        RECURSOS_POR_RUTA.put("/api/organizacion/personas-mayores/**", "organizaciones");

        RECURSOS_POR_RUTA.put("/api/gustos/**", "gustos");
        RECURSOS_POR_RUTA.put("/api/persona-mayor/*/gustos", "gustos");

        // La emergencia envía SMS a los acompañantes: se avisa para que su
        // campanita se actualice al instante. Los recordatorios los envían
        // tareas programadas (no pasan por aquí); el frontend los consulta
        // cada minuto.
        RECURSOS_POR_RUTA.put("/api/persona-mayor/emergencia", "notificaciones");
        RECURSOS_POR_RUTA.put("/api/notificaciones/**", "notificaciones");
    }

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final CambiosPublisher publisher;

    public PublicarCambiosGlobalFilter(CambiosPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!METODOS_DE_ESCRITURA.contains(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        String recurso = resolverRecurso(exchange.getRequest().getPath().value());
        if (recurso == null) {
            return chain.filter(exchange);
        }

        // Se avisa después de que el servicio respondió (el cambio ya
        // quedó guardado) y solo si la operación fue exitosa.
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpStatusCode estado = exchange.getResponse().getStatusCode();
            if (estado != null && estado.is2xxSuccessful()) {
                publisher.publicar(recurso);
            }
        }));
    }

    /** Recurso de la primera regla que coincide con la ruta, o null si la ruta no está en la lista. */
    private String resolverRecurso(String ruta) {
        for (Map.Entry<String, String> regla : RECURSOS_POR_RUTA.entrySet()) {
            if (matcher.match(regla.getKey(), ruta)) {
                return regla.getValue();
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
