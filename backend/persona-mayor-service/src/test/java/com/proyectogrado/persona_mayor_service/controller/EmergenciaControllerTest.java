package com.proyectogrado.persona_mayor_service.controller;

import com.proyectogrado.persona_mayor_service.client.MessagingClient;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorAcompanante;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorOrganizacion;
import com.proyectogrado.persona_mayor_service.model.UsuarioLookup;
import com.proyectogrado.persona_mayor_service.model.Emergencia;
import com.proyectogrado.persona_mayor_service.repository.EmergenciaRepository;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorAcompananteRepository;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorOrganizacionRepository;
import com.proyectogrado.persona_mayor_service.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Botón de emergencia (RF-17 y RF-23): la alerta, con el nombre de la persona
 * mayor, llega por SMS a todos sus acompañantes y organizaciones aceptados.
 * messaging-service está simulado, así que no sale ningún SMS.
 */
class EmergenciaControllerTest {

    private static final int PERSONA_MAYOR = 10;
    private static final String MENSAJE = "ALERTA DE EMERGENCIA: Rosa Díaz ha activado una alerta desde VITA+. "
            + "Por favor, verifica que se encuentre bien.";

    private PersonaMayorAcompananteRepository relacionAcompananteRepository;
    private PersonaMayorOrganizacionRepository relacionOrganizacionRepository;
    private UsuarioLookupRepository usuarioLookupRepository;
    private MessagingClient messagingClient;
    private EmergenciaRepository emergenciaRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        relacionAcompananteRepository = mock(PersonaMayorAcompananteRepository.class);
        relacionOrganizacionRepository = mock(PersonaMayorOrganizacionRepository.class);
        usuarioLookupRepository = mock(UsuarioLookupRepository.class);
        messagingClient = mock(MessagingClient.class);
        emergenciaRepository = mock(EmergenciaRepository.class);

        UsuarioLookup personaMayor = usuario(PERSONA_MAYOR, "Rosa Díaz", "+573001110000");
        when(usuarioLookupRepository.findById(PERSONA_MAYOR)).thenReturn(Optional.of(personaMayor));
        when(messagingClient.enviarMensaje(anyString(), anyString(), anyString())).thenReturn(true);

        mockMvc = MockMvcBuilders.standaloneSetup(new EmergenciaController(
                relacionAcompananteRepository, relacionOrganizacionRepository,
                usuarioLookupRepository, messagingClient, emergenciaRepository)).build();
    }

    private UsuarioLookup usuario(int id, String nombre, String celular) {
        UsuarioLookup usuario = mock(UsuarioLookup.class);
        when(usuario.getIdUsuario()).thenReturn(id);
        when(usuario.getNombreUsuario()).thenReturn(nombre);
        when(usuario.getCelular()).thenReturn(celular);
        return usuario;
    }

    private void acompanantesAceptados(int... ids) {
        when(relacionAcompananteRepository.findById_IdPersonaMayorAndEstado(PERSONA_MAYOR, "ACEPTADA"))
                .thenReturn(java.util.Arrays.stream(ids)
                        .mapToObj(id -> new PersonaMayorAcompanante(PERSONA_MAYOR, id))
                        .toList());
    }

    @Test
    void laAlertaLlegaATodosLosAcompanantesYOrganizacionesAceptados() throws Exception {
        acompanantesAceptados(20, 21);
        UsuarioLookup hijo = usuario(20, "Carlos Díaz", "+573002220000");
        UsuarioLookup vecina = usuario(21, "Marta Gil", "+573002221111");
        when(usuarioLookupRepository.findById(20)).thenReturn(Optional.of(hijo));
        when(usuarioLookupRepository.findById(21)).thenReturn(Optional.of(vecina));

        when(relacionOrganizacionRepository.findById_IdPersonaMayorAndEstado(PERSONA_MAYOR, "ACEPTADA"))
                .thenReturn(List.of(new PersonaMayorOrganizacion(PERSONA_MAYOR, 5)));
        UsuarioLookup fundacion = usuario(30, "Fundación Entrenubes", "+573003330000");
        when(usuarioLookupRepository.findByIdOrganizacion(5)).thenReturn(List.of(fundacion));

        mockMvc.perform(post("/api/persona-mayor/emergencia").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isOk())
                .andExpect(content().string("Alerta de emergencia enviada a 2 acompañante(s) y 1 organización(es)"));

        verify(messagingClient).enviarMensaje("+573002220000", MENSAJE, "EMERGENCIA");
        verify(messagingClient).enviarMensaje("+573002221111", MENSAJE, "EMERGENCIA");
        verify(messagingClient).enviarMensaje("+573003330000", MENSAJE, "EMERGENCIA");
    }

    @Test
    void unAcompananteSinCelularSeOmiteYLosDemasSiReciben() throws Exception {
        acompanantesAceptados(20, 21);
        UsuarioLookup sinCelular = usuario(20, "Carlos Díaz", " ");
        UsuarioLookup vecina = usuario(21, "Marta Gil", "+573002221111");
        when(usuarioLookupRepository.findById(20)).thenReturn(Optional.of(sinCelular));
        when(usuarioLookupRepository.findById(21)).thenReturn(Optional.of(vecina));

        mockMvc.perform(post("/api/persona-mayor/emergencia").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isOk())
                .andExpect(content().string("Alerta de emergencia enviada a 1 acompañante(s) y 0 organización(es)"));

        verify(messagingClient, times(1)).enviarMensaje(anyString(), eq(MENSAJE), eq("EMERGENCIA"));
    }

    @Test
    void soloSeCuentanLosEnviosQueMessagingConfirma() throws Exception {
        acompanantesAceptados(20, 21);
        UsuarioLookup hijo = usuario(20, "Carlos Díaz", "+573002220000");
        UsuarioLookup vecina = usuario(21, "Marta Gil", "+573002221111");
        when(usuarioLookupRepository.findById(20)).thenReturn(Optional.of(hijo));
        when(usuarioLookupRepository.findById(21)).thenReturn(Optional.of(vecina));
        // La vecina desactivó las alertas de emergencia (o el SMS falló).
        when(messagingClient.enviarMensaje("+573002221111", MENSAJE, "EMERGENCIA")).thenReturn(false);

        mockMvc.perform(post("/api/persona-mayor/emergencia").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isOk())
                .andExpect(content().string("Alerta de emergencia enviada a 1 acompañante(s) y 0 organización(es)"));
    }

    @Test
    void sinContactosAceptadosLaAlertaNoSeEnviaYSeAvisa() throws Exception {
        acompanantesAceptados();
        when(relacionOrganizacionRepository.findById_IdPersonaMayorAndEstado(PERSONA_MAYOR, "ACEPTADA"))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/persona-mayor/emergencia").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("No se pudo enviar la alerta a ningún acompañante u organización"));

        verify(messagingClient, never()).enviarMensaje(anyString(), anyString(), anyString());
    }

    @Test
    void laEmergenciaSeGuardaAunqueNoSePuedaAvisarPorSms() throws Exception {
        acompanantesAceptados(20);
        UsuarioLookup hijo = usuario(20, "Carlos Díaz", "+573002220000");
        when(usuarioLookupRepository.findById(20)).thenReturn(Optional.of(hijo));
        when(relacionOrganizacionRepository.findById_IdPersonaMayorAndEstado(PERSONA_MAYOR, "ACEPTADA"))
                .thenReturn(List.of());
        when(messagingClient.enviarMensaje(anyString(), anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/persona-mayor/emergencia").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isBadRequest());

        // Así el acompañante la ve en su inicio aunque el SMS no haya salido.
        ArgumentCaptor<Emergencia> guardada = ArgumentCaptor.forClass(Emergencia.class);
        verify(emergenciaRepository).save(guardada.capture());
        assertEquals(PERSONA_MAYOR, guardada.getValue().getIdPersonaMayor());
        assertNotNull(guardada.getValue().getFechaHora());
    }
}
