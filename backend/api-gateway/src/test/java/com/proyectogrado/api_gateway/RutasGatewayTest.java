package com.proyectogrado.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rutas reales de application.yml (RF-49, RF-51 y RNF-10): cada ruta llega al
 * servicio que le corresponde, las protegidas exigen token y las que no se
 * deben exponer no tienen ruta. Arranca el contexto del gateway sin abrir el
 * puerto 8080 y sin llamar a ningún servicio.
 */
@SpringBootTest
class RutasGatewayTest {

    @Autowired
    private RouteLocator routeLocator;

    /** Primera ruta que coincide con la petición, igual que en el gateway; null si ninguna. */
    private Route rutaPara(HttpMethod metodo, String ruta) {
        List<Route> rutas = routeLocator.getRoutes().collectList().block();
        for (Route candidata : rutas) {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(metodo, ruta).build());
            if (Boolean.TRUE.equals(Mono.from(candidata.getPredicate().apply(exchange)).block())) {
                return candidata;
            }
        }
        return null;
    }

    private int puerto(HttpMethod metodo, String ruta) {
        return rutaPara(metodo, ruta).getUri().getPort();
    }

    /** true si los filtros de la ruta dejan pasar una petición sin token. */
    private boolean pasaSinToken(String ruta) {
        Route destino = rutaPara(HttpMethod.GET, ruta);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(ruta).build());
        AtomicBoolean llego = new AtomicBoolean(false);

        GatewayFilterChain alServicio = e -> {
            llego.set(true);
            return Mono.empty();
        };

        GatewayFilterChain cadena = alServicio;
        List<GatewayFilter> filtros = destino.getFilters();
        for (int i = filtros.size() - 1; i >= 0; i--) {
            GatewayFilter filtro = filtros.get(i);
            GatewayFilterChain resto = cadena;
            cadena = e -> filtro.filter(e, resto);
        }
        cadena.filter(exchange).block();

        if (!llego.get()) {
            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        }
        return llego.get();
    }

    @Test
    void cadaRutaLlegaAlServicioQueLeCorresponde() {
        assertEquals(8081, puerto(HttpMethod.POST, "/api/auth/login"));
        assertEquals(8082, puerto(HttpMethod.POST, "/api/otp/send"));
        assertEquals(8082, puerto(HttpMethod.GET, "/api/notificaciones"));
        assertEquals(8084, puerto(HttpMethod.GET, "/api/persona-mayor/medicamentos"));
        assertEquals(8084, puerto(HttpMethod.PUT, "/api/persona-mayor/citas-medicas/3"));
        assertEquals(8085, puerto(HttpMethod.POST, "/api/persona-mayor/emergencia"));
        assertEquals(8085, puerto(HttpMethod.GET, "/api/persona-mayor/acompanantes"));
        assertEquals(8086, puerto(HttpMethod.GET, "/api/acompanante/seguimiento/7/medicamentos"));
        assertEquals(8087, puerto(HttpMethod.GET, "/api/voluntario/organizaciones"));
        assertEquals(8088, puerto(HttpMethod.GET, "/api/organizacion/personas-mayores"));
        assertEquals(8089, puerto(HttpMethod.GET, "/api/actividades/mias"));
        assertEquals(8090, puerto(HttpMethod.GET, "/api/analitica/actividades"));
    }

    @Test
    void lasRutasEspecificasDeLaOrganizacionVanAntesQueLaGeneral() {
        assertEquals(8085, puerto(HttpMethod.GET, "/api/organizacion/personas-mayores/7/acompanantes"));
        assertEquals(8084, puerto(HttpMethod.POST, "/api/organizacion/signos-vitales/7"));
        assertEquals(8087, puerto(HttpMethod.GET, "/api/organizacion/voluntarios/solicitudes"));
    }

    @Test
    void lasRutasQueNoSeDebenExponerNoTienenRuta() {
        // La verificación de OTP solo la llama auth-service por dentro.
        assertNull(rutaPara(HttpMethod.POST, "/api/otp/verify"));
        // El envío de SMS libre es solo entre servicios.
        assertNull(rutaPara(HttpMethod.POST, "/api/mensajes/enviar"));
        // Los signos vitales de la persona mayor no se editan ni se borran.
        assertNull(rutaPara(HttpMethod.PUT, "/api/persona-mayor/signos-vitales"));
        assertNull(rutaPara(HttpMethod.DELETE, "/api/persona-mayor/signos-vitales"));
    }

    @Test
    void lasRutasProtegidasExigenToken() {
        assertFalse(pasaSinToken("/api/persona-mayor/medicamentos"));
        assertFalse(pasaSinToken("/api/acompanante/personas-mayores"));
        assertFalse(pasaSinToken("/api/organizacion/personas-mayores"));
        assertFalse(pasaSinToken("/api/actividades"));
        assertFalse(pasaSinToken("/api/analitica/salud"));
        assertFalse(pasaSinToken("/api/notificaciones"));
    }

    @Test
    void elLoginYElRegistroSonPublicos() {
        // auth-service valida el token por su cuenta en sus rutas privadas.
        assertTrue(pasaSinToken("/api/auth/login"));
        assertTrue(pasaSinToken("/api/auth/registro"));
    }
}
