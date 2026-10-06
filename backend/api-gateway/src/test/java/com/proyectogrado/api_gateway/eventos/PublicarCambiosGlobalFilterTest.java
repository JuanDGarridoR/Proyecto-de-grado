package com.proyectogrado.api_gateway.eventos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Avisos de cambio (RF-51): cada escritura exitosa que pasa por el gateway
 * avisa qué recurso cambió para que los demás usuarios lo vean sin recargar.
 */
class PublicarCambiosGlobalFilterTest {

    private CambiosPublisher publisher;
    private PublicarCambiosGlobalFilter filtro;

    @BeforeEach
    void setUp() {
        publisher = mock(CambiosPublisher.class);
        filtro = new PublicarCambiosGlobalFilter(publisher);
    }

    /** Pasa la petición por el filtro; el servicio de destino responde con estadoServicio. */
    private void ejecutar(HttpMethod metodo, String ruta, HttpStatus estadoServicio) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(metodo, ruta).build());
        filtro.filter(exchange, siguiente -> {
            siguiente.getResponse().setStatusCode(estadoServicio);
            return Mono.empty();
        }).block();
    }

    @Test
    void unaEscrituraExitosaAvisaElRecursoQueCambio() {
        ejecutar(HttpMethod.POST, "/api/actividades/5/inscribirse", HttpStatus.CREATED);

        verify(publisher).publicar("actividades");
    }

    @Test
    void unaEscrituraQueFallaNoAvisaNada() {
        ejecutar(HttpMethod.POST, "/api/persona-mayor/medicamentos", HttpStatus.BAD_REQUEST);

        verify(publisher, never()).publicar(anyString());
    }

    @Test
    void unaConsultaNoAvisaNada() {
        ejecutar(HttpMethod.GET, "/api/persona-mayor/medicamentos", HttpStatus.OK);

        verify(publisher, never()).publicar(anyString());
    }

    @Test
    void unaRutaEspecificaGanaSobreLaGeneral() {
        // /api/voluntario/** también coincide, pero va después en la lista.
        ejecutar(HttpMethod.POST, "/api/voluntario/organizaciones/3/solicitud", HttpStatus.OK);

        verify(publisher).publicar("voluntarios");
        verify(publisher, never()).publicar("usuarios");
    }

    @Test
    void alBorrarUnaCuentaSeAvisaQueCambioTodo() {
        ejecutar(HttpMethod.DELETE, "/api/auth/cuenta", HttpStatus.OK);

        verify(publisher).publicar("*");
    }

    @Test
    void elBotonDeEmergenciaActualizaLasNotificaciones() {
        ejecutar(HttpMethod.POST, "/api/persona-mayor/emergencia", HttpStatus.OK);

        verify(publisher).publicar("notificaciones");
    }

    @Test
    void elInicioDeSesionNoAvisaNada() {
        ejecutar(HttpMethod.POST, "/api/auth/login", HttpStatus.OK);

        verify(publisher, never()).publicar(anyString());
    }
}
