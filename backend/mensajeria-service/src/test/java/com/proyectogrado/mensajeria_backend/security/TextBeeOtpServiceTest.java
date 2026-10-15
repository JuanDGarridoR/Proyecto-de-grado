package com.proyectogrado.mensajeria_backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Códigos OTP y envío de SMS con TextBee (RF-05, RF-06, RNF-03 y RNF-09).
 * El cliente HTTP es simulado: se revisa la petición que se enviaría a
 * TextBee sin mandar ningún SMS real. El generador de códigos se fija en
 * 123456 para poder verificarlo.
 */
class TextBeeOtpServiceTest {

    private static final String CELULAR = "+573001112233";
    private static final String CODIGO = "123456";

    private TextBeeOtpService servicio;
    private HttpClient httpClient;
    private HttpResponse<String> respuestaTextBee;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        servicio = new TextBeeOtpService();
        ReflectionTestUtils.setField(servicio, "apiKey", "clave-de-pruebas");
        ReflectionTestUtils.setField(servicio, "deviceId", "dispositivo-7");

        // 100000 + 23456 = 123456
        ReflectionTestUtils.setField(servicio, "random", new SecureRandom() {
            @Override
            public int nextInt(int limite) {
                return 23456;
            }
        });

        httpClient = mock(HttpClient.class);
        respuestaTextBee = mock(HttpResponse.class);
        when(respuestaTextBee.statusCode()).thenReturn(201);
        doReturn(respuestaTextBee).when(httpClient).send(any(HttpRequest.class), any());
        ReflectionTestUtils.setField(servicio, "httpClient", httpClient);
    }

    /** Cuerpo JSON de la petición que se le hizo a TextBee. */
    private static String cuerpo(HttpRequest peticion) throws Exception {
        CompletableFuture<String> resultado = new CompletableFuture<>();
        peticion.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            @Override
            public void onSubscribe(Flow.Subscription suscripcion) {
                suscripcion.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer parte) {
                byte[] datos = new byte[parte.remaining()];
                parte.get(datos);
                bytes.write(datos, 0, datos.length);
            }

            @Override
            public void onError(Throwable error) {
                resultado.completeExceptionally(error);
            }

            @Override
            public void onComplete() {
                resultado.complete(bytes.toString(StandardCharsets.UTF_8));
            }
        });
        return resultado.get(1, TimeUnit.SECONDS);
    }

    @Test
    void elSmsSeEnviaATextBeeConLaClaveElDispositivoYElCodigo() throws Exception {
        servicio.enviarCodigo(CELULAR);

        ArgumentCaptor<HttpRequest> peticion = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(peticion.capture(), any());

        HttpRequest enviada = peticion.getValue();
        assertEquals("POST", enviada.method());
        assertEquals(URI.create("https://api.textbee.dev/api/v1/gateway/devices/dispositivo-7/send-sms"),
                enviada.uri());
        assertEquals("clave-de-pruebas", enviada.headers().firstValue("x-api-key").orElseThrow());
        assertEquals("application/json", enviada.headers().firstValue("Content-Type").orElseThrow());

        String json = cuerpo(enviada);
        assertTrue(json.contains("\"recipients\":[\"" + CELULAR + "\"]"), json);
        assertTrue(json.contains("Tu código de verificación de Vita+ es: " + CODIGO), json);
    }

    @Test
    void elCodigoCorrectoSirveUnaSolaVez() {
        servicio.enviarCodigo(CELULAR);

        assertTrue(servicio.verificarCodigo(CELULAR, CODIGO));
        assertFalse(servicio.verificarCodigo(CELULAR, CODIGO));
    }

    /** Código guardado para CELULAR (un CodigoOtp, record privado del servicio). */
    private Object codigoGuardado() {
        return codigosPendientes().get(CELULAR);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> codigosPendientes() {
        return (Map<String, Object>) ReflectionTestUtils.getField(servicio, "codigosPendientes");
    }

    private static Object componente(Object registro, String nombre) throws Exception {
        for (RecordComponent componente : registro.getClass().getRecordComponents()) {
            if (componente.getName().equals(nombre)) {
                Method accesor = componente.getAccessor();
                accesor.setAccessible(true);
                return accesor.invoke(registro);
            }
        }
        throw new IllegalArgumentException("CodigoOtp no tiene " + nombre);
    }

    @Test
    void elCodigoSeGuardaComoHmacYVenceALosDiezMinutos() throws Exception {
        Instant antes = Instant.now();
        servicio.enviarCodigo(CELULAR);
        Instant despues = Instant.now();

        Object guardado = codigoGuardado();
        byte[] hash = (byte[]) componente(guardado, "hash");
        assertEquals(32, hash.length);
        assertFalse(new String(hash, StandardCharsets.UTF_8).contains(CODIGO));

        Instant expiracion = (Instant) componente(guardado, "expiracion");
        assertFalse(expiracion.isBefore(antes.plus(Duration.ofMinutes(10))));
        assertFalse(expiracion.isAfter(despues.plus(Duration.ofMinutes(10))));
    }

    @Test
    void unCodigoVencidoNoSirveYSeBorra() throws Exception {
        servicio.enviarCodigo(CELULAR);

        // Como si ya hubieran pasado los diez minutos
        Object guardado = codigoGuardado();
        Constructor<?> constructor = guardado.getClass()
                .getDeclaredConstructor(byte[].class, Instant.class, Instant.class, int.class);
        constructor.setAccessible(true);
        codigosPendientes().put(CELULAR, constructor.newInstance(
                componente(guardado, "hash"), componente(guardado, "enviado"), Instant.now().minusSeconds(1), 0));

        assertFalse(servicio.verificarCodigo(CELULAR, CODIGO));
        assertFalse(codigosPendientes().containsKey(CELULAR));
    }

    @Test
    void unCodigoIncorrectoNoSirve() {
        servicio.enviarCodigo(CELULAR);

        assertFalse(servicio.verificarCodigo(CELULAR, "654321"));
        // El código correcto sigue sirviendo después de un fallo.
        assertTrue(servicio.verificarCodigo(CELULAR, CODIGO));
    }

    @Test
    void trasCincoIntentosFallidosElCodigoSeAnula() {
        servicio.enviarCodigo(CELULAR);

        for (int intento = 0; intento < 5; intento++) {
            assertFalse(servicio.verificarCodigo(CELULAR, "000000"));
        }

        // Ni siquiera el código correcto sirve ya: hay que pedir otro.
        assertFalse(servicio.verificarCodigo(CELULAR, CODIGO));
    }

    @Test
    void elCodigoDeUnCelularNoSirveParaOtro() {
        servicio.enviarCodigo(CELULAR);

        assertFalse(servicio.verificarCodigo("+573009998877", CODIGO));
    }

    @Test
    void sinCodigoPedidoNoSeVerificaNada() {
        assertFalse(servicio.verificarCodigo(CELULAR, CODIGO));
    }

    @Test
    void unCelularEscritoConEspaciosEsElMismoCelular() {
        servicio.enviarCodigo("+57 300 111 2233");

        assertTrue(servicio.verificarCodigo(CELULAR, CODIGO));
    }

    @Test
    void noSePuedePedirOtroCodigoAntesDeTreintaSegundos() {
        servicio.enviarCodigo(CELULAR);

        assertThrows(IllegalStateException.class, () -> servicio.enviarCodigo(CELULAR));
    }

    @Test
    void siTextBeeRespondeConErrorElEnvioFalla() {
        when(respuestaTextBee.statusCode()).thenReturn(500);
        when(respuestaTextBee.body()).thenReturn("{\"error\":\"dispositivo desconectado\"}");

        assertThrows(RuntimeException.class, () -> servicio.enviarMensaje(CELULAR, "Prueba de VITA+"));
    }

    @Test
    void aLosCelularesFicticiosNoSeLesEnviaNadaYNoGastanCupo() throws Exception {
        // Los tres prefijos del script de datos ficticios
        servicio.enviarMensaje("+570000100001", "Recordatorio de medicamento");
        servicio.enviarMensaje("+570000200001", "Alerta de emergencia");
        servicio.enviarCodigo("+570000300001");

        verify(httpClient, never()).send(any(HttpRequest.class), any());
    }

    @Test
    void soloSonFicticiosLosCelularesQueEmpiezanPor000() {
        assertTrue(TextBeeOtpService.esCelularFicticio("+570000100001"));
        assertTrue(TextBeeOtpService.esCelularFicticio("+57 000 020 0001"));
        assertTrue(TextBeeOtpService.esCelularFicticio("0001234567"));

        assertFalse(TextBeeOtpService.esCelularFicticio(CELULAR));
        assertFalse(TextBeeOtpService.esCelularFicticio("3001112233"));
        assertFalse(TextBeeOtpService.esCelularFicticio("+57 300 000 1234"));
        assertFalse(TextBeeOtpService.esCelularFicticio(null));
    }

    @Test
    void unMensajeVacioSeRechazaSinLlamarATextBee() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> servicio.enviarMensaje(CELULAR, "  "));
        assertThrows(IllegalArgumentException.class, () -> servicio.enviarMensaje(" ", "Hola"));

        verify(httpClient, never()).send(any(HttpRequest.class), any());
    }
}
