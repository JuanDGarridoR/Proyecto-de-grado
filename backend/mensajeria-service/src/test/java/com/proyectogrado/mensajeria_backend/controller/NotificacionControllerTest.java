package com.proyectogrado.mensajeria_backend.controller;

import com.proyectogrado.mensajeria_backend.model.Notificacion;
import com.proyectogrado.mensajeria_backend.model.PreferenciaNotificacion;
import com.proyectogrado.mensajeria_backend.model.TipoNotificacion;
import com.proyectogrado.mensajeria_backend.model.UsuarioLookup;
import com.proyectogrado.mensajeria_backend.repository.NotificacionRepository;
import com.proyectogrado.mensajeria_backend.repository.PreferenciaNotificacionRepository;
import com.proyectogrado.mensajeria_backend.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Campanita del panel (RF-17, RF-24, RF-26 y RF-27): historial de
 * notificaciones del usuario, cuántas faltan por leer, marcarlas como
 * leídas y elegir qué tipos recibir.
 */
class NotificacionControllerTest {

    private static final String CELULAR = "+573001112233";

    private NotificacionRepository notificacionRepository;
    private PreferenciaNotificacionRepository preferenciaRepository;
    private UsuarioLookupRepository usuarioLookupRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        notificacionRepository = mock(NotificacionRepository.class);
        preferenciaRepository = mock(PreferenciaNotificacionRepository.class);
        usuarioLookupRepository = mock(UsuarioLookupRepository.class);

        UsuarioLookup usuario = mock(UsuarioLookup.class);
        when(usuario.getCelular()).thenReturn(CELULAR);
        when(usuarioLookupRepository.findById(8)).thenReturn(Optional.of(usuario));

        mockMvc = MockMvcBuilders.standaloneSetup(new NotificacionController(
                notificacionRepository, usuarioLookupRepository, preferenciaRepository)).build();
    }

    @Test
    void elHistorialTraeLasNotificacionesDelUsuarioYCuantasFaltanPorLeer() throws Exception {
        when(notificacionRepository.findTop20ByCelularOrderByFechaEnvioDesc(CELULAR)).thenReturn(List.of(
                new Notificacion(CELULAR, "ALERTA DE EMERGENCIA: Rosa Díaz ha activado una alerta desde VITA+."),
                new Notificacion(CELULAR, "En 15 minutos, a las 8:00 p. m., te toca tomar Losartán (50mg).")));
        when(notificacionRepository.countByCelularAndLeidaFalse(CELULAR)).thenReturn(2L);

        mockMvc.perform(get("/api/notificaciones").header("X-User-Id", 8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noLeidas").value(2))
                .andExpect(jsonPath("$.notificaciones.length()").value(2))
                .andExpect(jsonPath("$.notificaciones[0].mensaje")
                        .value("ALERTA DE EMERGENCIA: Rosa Díaz ha activado una alerta desde VITA+."))
                .andExpect(jsonPath("$.notificaciones[0].leida").value(false))
                .andExpect(jsonPath("$.notificaciones[0].fechaEnvio").exists());
    }

    @Test
    void unUsuarioSinCelularNoTieneNotificaciones() throws Exception {
        when(usuarioLookupRepository.findById(9)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/notificaciones").header("X-User-Id", 9))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noLeidas").value(0))
                .andExpect(jsonPath("$.notificaciones.length()").value(0));

        verify(notificacionRepository, never()).findTop20ByCelularOrderByFechaEnvioDesc(anyString());
    }

    @Test
    void abrirLaCampanitaMarcaComoLeidasLasDelUsuario() throws Exception {
        mockMvc.perform(put("/api/notificaciones/leidas").header("X-User-Id", 8))
                .andExpect(status().isNoContent());

        verify(notificacionRepository).marcarLeidas(CELULAR);
    }

    @Test
    void sinCambiosTodosLosTiposDeNotificacionEstanActivos() throws Exception {
        when(preferenciaRepository.findByIdUsuario(8)).thenReturn(List.of());

        mockMvc.perform(get("/api/notificaciones/preferencias").header("X-User-Id", 8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.EMERGENCIA").value(true))
                .andExpect(jsonPath("$.MEDICAMENTO").value(true))
                .andExpect(jsonPath("$.CITA_MEDICA").value(true))
                .andExpect(jsonPath("$.ACTIVIDAD").value(true));
    }

    @Test
    void unTipoDesactivadoApareceComoInactivo() throws Exception {
        when(preferenciaRepository.findByIdUsuario(8)).thenReturn(List.of(
                new PreferenciaNotificacion(8, TipoNotificacion.CUMPLEANOS, false)));

        mockMvc.perform(get("/api/notificaciones/preferencias").header("X-User-Id", 8))
                .andExpect(jsonPath("$.CUMPLEANOS").value(false))
                .andExpect(jsonPath("$.EMERGENCIA").value(true));
    }

    @Test
    void desactivarUnTipoGuardaLaPreferencia() throws Exception {
        when(preferenciaRepository.findByIdUsuarioAndTipo(8, TipoNotificacion.ACTIVIDAD)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/notificaciones/preferencias/actividad")
                        .header("X-User-Id", 8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("ACTIVIDAD"))
                .andExpect(jsonPath("$.activa").value(false));

        ArgumentCaptor<PreferenciaNotificacion> guardada = ArgumentCaptor.forClass(PreferenciaNotificacion.class);
        verify(preferenciaRepository).save(guardada.capture());
        assertEquals(TipoNotificacion.ACTIVIDAD, guardada.getValue().getTipo());
        assertFalse(guardada.getValue().isActiva());
    }

    @Test
    void unTipoQueNoExisteSeRechaza() throws Exception {
        mockMvc.perform(put("/api/notificaciones/preferencias/promociones")
                        .header("X-User-Id", 8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\": false}"))
                .andExpect(status().isBadRequest());
    }
}
