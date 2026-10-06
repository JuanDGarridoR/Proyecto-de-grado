package com.proyectogrado.messaging_backend.controller;

import com.proyectogrado.messaging_backend.model.Notificacion;
import com.proyectogrado.messaging_backend.model.PreferenciaNotificacion;
import com.proyectogrado.messaging_backend.model.TipoNotificacion;
import com.proyectogrado.messaging_backend.model.UsuarioLookup;
import com.proyectogrado.messaging_backend.repository.NotificacionRepository;
import com.proyectogrado.messaging_backend.repository.PreferenciaNotificacionRepository;
import com.proyectogrado.messaging_backend.repository.UsuarioLookupRepository;
import com.proyectogrado.messaging_backend.security.TextBeeOtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Envío de SMS que piden los demás servicios (alertas de emergencia y
 * recordatorios, RF-17 y RF-23): respeta las preferencias del usuario y deja
 * una copia para la campanita. TextBee está simulado.
 */
class MensajeControllerTest {

    private static final String CELULAR = "+573001112233";

    private TextBeeOtpService textBeeOtpService;
    private NotificacionRepository notificacionRepository;
    private PreferenciaNotificacionRepository preferenciaRepository;
    private UsuarioLookupRepository usuarioLookupRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        textBeeOtpService = mock(TextBeeOtpService.class);
        notificacionRepository = mock(NotificacionRepository.class);
        preferenciaRepository = mock(PreferenciaNotificacionRepository.class);
        usuarioLookupRepository = mock(UsuarioLookupRepository.class);

        UsuarioLookup usuario = mock(UsuarioLookup.class);
        when(usuario.getIdUsuario()).thenReturn(8);
        when(usuarioLookupRepository.findFirstByCelular(CELULAR)).thenReturn(Optional.of(usuario));

        mockMvc = MockMvcBuilders.standaloneSetup(new MensajeController(
                textBeeOtpService, notificacionRepository, preferenciaRepository, usuarioLookupRepository)).build();
    }

    private String cuerpo(String tipo) {
        return "{\"celular\": \"" + CELULAR + "\", \"mensaje\": \"ALERTA DE EMERGENCIA: Rosa Díaz ha activado una alerta\""
                + (tipo == null ? "" : ", \"tipo\": \"" + tipo + "\"") + "}";
    }

    @Test
    void elSmsSeEnviaYQuedaGuardadoParaLaCampanita() throws Exception {
        mockMvc.perform(post("/api/mensajes/enviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("EMERGENCIA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(textBeeOtpService).enviarMensaje(CELULAR, "ALERTA DE EMERGENCIA: Rosa Díaz ha activado una alerta");

        ArgumentCaptor<Notificacion> guardada = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository).save(guardada.capture());
        assertEquals(CELULAR, guardada.getValue().getCelular());
        assertEquals("ALERTA DE EMERGENCIA: Rosa Díaz ha activado una alerta", guardada.getValue().getMensaje());
        assertFalse(guardada.getValue().isLeida());
    }

    @Test
    void siElUsuarioDesactivoEseTipoNoSeEnviaNada() throws Exception {
        when(preferenciaRepository.findByIdUsuarioAndTipo(8, TipoNotificacion.MEDICAMENTO))
                .thenReturn(Optional.of(new PreferenciaNotificacion(8, TipoNotificacion.MEDICAMENTO, false)));

        mockMvc.perform(post("/api/mensajes/enviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("MEDICAMENTO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.omitido").value(true));

        verify(textBeeOtpService, never()).enviarMensaje(anyString(), anyString());
        verify(notificacionRepository, never()).save(any());
    }

    @Test
    void sinTipoElMensajeSiempreSeEnvia() throws Exception {
        mockMvc.perform(post("/api/mensajes/enviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(textBeeOtpService).enviarMensaje(anyString(), anyString());
    }

    @Test
    void siTextBeeFallaRespondeErrorYNoGuardaLaNotificacion() throws Exception {
        doThrow(new RuntimeException("TextBee respondió 500"))
                .when(textBeeOtpService).enviarMensaje(anyString(), anyString());

        mockMvc.perform(post("/api/mensajes/enviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("EMERGENCIA")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));

        verify(notificacionRepository, never()).save(any());
    }

    @Test
    void unMensajeVacioSeRechazaCon400() throws Exception {
        doThrow(new IllegalArgumentException("El mensaje no puede estar vacío"))
                .when(textBeeOtpService).enviarMensaje(CELULAR, "");

        mockMvc.perform(post("/api/mensajes/enviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"celular\": \"" + CELULAR + "\", \"mensaje\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El mensaje no puede estar vacío"));
    }
}
