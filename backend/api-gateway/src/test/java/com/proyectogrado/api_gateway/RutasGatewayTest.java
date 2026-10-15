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
 * Rutas reales de application.yml (RNF-01 y RNF-02): cada ruta llega al
 * servicio que le corresponde, las protegidas exigen token y las que no se
 * deben exponer no tienen ruta. Arranca el contexto del gateway sin abrir el
 * puerto 8080 y sin llamar a ningún servicio.
 *
 * JWT_SECRET va aquí porque application.yml lo toma de secretos.properties o
 * del entorno: sin él, la prueba solo pasaría en el computador de quien tenga
 * ese archivo.
 */
@SpringBootTest(properties = "JWT_SECRET=clave_de_pruebas_de_vita_mas_con_al_menos_32_bytes")
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

    /** Servicio al que apunta la ruta (lb://<servicio>: el balanceador elige la instancia). */
    private String servicio(HttpMethod metodo, String ruta) {
        return rutaPara(metodo, ruta).getUri().getHost();
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
        assertEquals("auth-service", servicio(HttpMethod.POST, "/api/auth/login"));
        assertEquals("mensajeria-service", servicio(HttpMethod.POST, "/api/otp/send"));
        assertEquals("mensajeria-service", servicio(HttpMethod.GET, "/api/notificaciones"));
        assertEquals("salud-service", servicio(HttpMethod.GET, "/api/persona-mayor/medicamentos"));
        assertEquals("salud-service", servicio(HttpMethod.PUT, "/api/persona-mayor/citas-medicas/3"));
        assertEquals("persona-mayor-service", servicio(HttpMethod.POST, "/api/persona-mayor/emergencia"));
        assertEquals("persona-mayor-service", servicio(HttpMethod.GET, "/api/persona-mayor/acompanantes"));
        assertEquals("acompanante-service", servicio(HttpMethod.GET, "/api/acompanante/seguimiento/7/medicamentos"));
        assertEquals("voluntario-service", servicio(HttpMethod.GET, "/api/voluntario/organizaciones"));
        assertEquals("organizacion-service", servicio(HttpMethod.GET, "/api/organizacion/personas-mayores"));
        assertEquals("actividad-service", servicio(HttpMethod.GET, "/api/actividades/mias"));
        assertEquals("analitica-service", servicio(HttpMethod.GET, "/api/analitica/actividades"));
    }

    @Test
    void lasRutasEspecificasDeLaOrganizacionVanAntesQueLaGeneral() {
        assertEquals("persona-mayor-service", servicio(HttpMethod.GET, "/api/organizacion/personas-mayores/7/acompanantes"));
        assertEquals("salud-service", servicio(HttpMethod.POST, "/api/organizacion/signos-vitales/7"));
        assertEquals("voluntario-service", servicio(HttpMethod.GET, "/api/organizacion/voluntarios/solicitudes"));
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
