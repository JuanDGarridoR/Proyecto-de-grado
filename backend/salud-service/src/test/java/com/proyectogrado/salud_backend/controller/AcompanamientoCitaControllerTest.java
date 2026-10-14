package com.proyectogrado.salud_backend.controller;

import com.proyectogrado.salud_backend.client.MensajeriaClient;
import com.proyectogrado.salud_backend.config.ZonaHoraria;
import com.proyectogrado.salud_backend.model.CitaMedica;
import com.proyectogrado.salud_backend.model.UsuarioLookup;
import com.proyectogrado.salud_backend.repository.CitaMedicaRepository;
import com.proyectogrado.salud_backend.repository.RelacionAcompananteLookupRepository;
import com.proyectogrado.salud_backend.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acompañamiento a citas médicas (RF-75): un acompañante se ofrece a ir con
 * la persona mayor, dice dónde se encuentran y ella recibe un SMS.
 */
class AcompanamientoCitaControllerTest {

    private static final int PERSONA_MAYOR = 10;
    private static final int LAURA = 20;
    private static final int PEDRO = 21;
    private static final String URL = "/api/acompanante/personas-mayores/10/citas-medicas/5/acompanamiento";

    private CitaMedicaRepository citaMedicaRepository;
    private MensajeriaClient mensajeriaClient;
    private MockMvc mockMvc;
    private CitaMedica cita;

    @BeforeEach
    void setUp() {
        citaMedicaRepository = mock(CitaMedicaRepository.class);
        mensajeriaClient = mock(MensajeriaClient.class);
        RelacionAcompananteLookupRepository relaciones = mock(RelacionAcompananteLookupRepository.class);
        UsuarioLookupRepository usuarios = mock(UsuarioLookupRepository.class);

        when(relaciones.existsById_IdPersonaMayorAndId_IdAcompananteAndEstado(PERSONA_MAYOR, LAURA, "ACEPTADA"))
                .thenReturn(true);
        when(relaciones.existsById_IdPersonaMayorAndId_IdAcompananteAndEstado(PERSONA_MAYOR, PEDRO, "ACEPTADA"))
                .thenReturn(true);
        usuario(usuarios, PERSONA_MAYOR, "Rosa Díaz", "+573001110000");
        usuario(usuarios, LAURA, "Laura Pérez", "+573002220000");
        usuario(usuarios, PEDRO, "Pedro León", "+573003330000");

        cita = new CitaMedica();
        cita.setIdCita(5);
        cita.setIdPersonaMayor(PERSONA_MAYOR);
        cita.setTitulo("Control de presión");
        cita.setLugar("USS Usme");
        cita.setFecha(ZonaHoraria.hoy().plusDays(3));
        cita.setHora(LocalTime.of(9, 30));
        cita.setAcompanamiento(CitaMedica.ACOMPANAMIENTO_REQUERIDO);
        when(citaMedicaRepository.findById(5)).thenReturn(Optional.of(cita));
        when(citaMedicaRepository.save(any(CitaMedica.class))).thenAnswer(inv -> inv.getArgument(0));

        CitaMedicaController citas = new CitaMedicaController(citaMedicaRepository, usuarios);
        mockMvc = MockMvcBuilders.standaloneSetup(new AcompanamientoCitaController(
                citaMedicaRepository, relaciones, usuarios, mensajeriaClient, citas)).build();
    }

    private static void usuario(UsuarioLookupRepository usuarios, int id, String nombre, String celular) {
        UsuarioLookup usuario = mock(UsuarioLookup.class);
        when(usuario.getNombreUsuario()).thenReturn(nombre);
        when(usuario.getCelular()).thenReturn(celular);
        when(usuarios.findById(id)).thenReturn(Optional.of(usuario));
    }

    private org.springframework.test.web.servlet.ResultActions aceptar(int idAcompanante, String punto) throws Exception {
        return mockMvc.perform(put(URL)
                .header("X-User-Id", idAcompanante)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"puntoEncuentro\": \"" + punto + "\"}"));
    }

    @Test
    void elAcompananteAceptaYLaPersonaMayorRecibeElMensajeConElPuntoDeEncuentro() throws Exception {
        aceptar(LAURA, " Portería del conjunto, 8:45 a. m. ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idAcompananteCita").value(LAURA))
                .andExpect(jsonPath("$.nombreAcompananteCita").value("Laura Pérez"))
                .andExpect(jsonPath("$.puntoEncuentro").value("Portería del conjunto, 8:45 a. m."));

        ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
        verify(mensajeriaClient).enviarMensaje(eq("+573001110000"), mensaje.capture(), eq("CITA_MEDICA"));
        assertTrue(mensaje.getValue().contains("Laura Pérez te acompañará a tu cita de Control de presión"));
        assertTrue(mensaje.getValue().contains("Se encontrarán en: Portería del conjunto, 8:45 a. m."));
    }

    @Test
    void siOtroAcompananteYaAceptoResponde409() throws Exception {
        cita.asignarAcompanante(LAURA, "Portería", ZonaHoraria.ahora());

        aceptar(PEDRO, "Mi casa")
                .andExpect(status().isConflict())
                .andExpect(content().string("Laura Pérez ya se ofreció a acompañar esta cita"));

        assertEquals(LAURA, cita.getIdAcompananteCita());
        verify(mensajeriaClient, never()).enviarMensaje(anyString(), anyString(), anyString());
    }

    @Test
    void quienAceptoPuedeCambiarElPuntoDeEncuentro() throws Exception {
        cita.asignarAcompanante(LAURA, "Portería", ZonaHoraria.ahora());

        aceptar(LAURA, "Paradero de la 91 Sur").andExpect(status().isOk());

        assertEquals("Paradero de la 91 Sur", cita.getPuntoEncuentro());
        ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
        verify(mensajeriaClient).enviarMensaje(eq("+573001110000"), mensaje.capture(), eq("CITA_MEDICA"));
        assertTrue(mensaje.getValue().contains("cambió el punto de encuentro"));
    }

    @Test
    void sinPuntoDeEncuentroNoSeAcepta() throws Exception {
        aceptar(LAURA, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Escribe dónde se van a encontrar para salir a la cita"));

        assertNull(cita.getIdAcompananteCita());
    }

    @Test
    void unaCitaQueNoNecesitaAcompanamientoNoSePuedeAceptar() throws Exception {
        cita.setAcompanamiento(CitaMedica.SIN_ACOMPANAMIENTO);

        aceptar(LAURA, "Portería").andExpect(status().isBadRequest());
    }

    @Test
    void quienAceptoPuedeCancelarYLaCitaQuedaLibre() throws Exception {
        cita.asignarAcompanante(LAURA, "Portería", ZonaHoraria.ahora());

        mockMvc.perform(delete(URL).header("X-User-Id", LAURA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idAcompananteCita").doesNotExist());

        assertNull(cita.getIdAcompananteCita());
        assertNull(cita.getPuntoEncuentro());
        ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
        verify(mensajeriaClient).enviarMensaje(eq("+573001110000"), mensaje.capture(), eq("CITA_MEDICA"));
        assertTrue(mensaje.getValue().contains("Laura Pérez ya no podrá acompañarte"));
    }

    @Test
    void otroAcompananteNoPuedeCancelarLoQueAceptoLaura() throws Exception {
        cita.asignarAcompanante(LAURA, "Portería", ZonaHoraria.ahora());

        mockMvc.perform(delete(URL).header("X-User-Id", PEDRO)).andExpect(status().isNotFound());

        assertEquals(LAURA, cita.getIdAcompananteCita());
    }

    @Test
    void quitarElAcompanamientoDeLaCitaLiberaAlAcompanante() {
        cita.asignarAcompanante(LAURA, "Portería", ZonaHoraria.ahora());

        cita.setAcompanamiento(CitaMedica.SIN_ACOMPANAMIENTO);

        assertNull(cita.getIdAcompananteCita());
    }

    @Test
    void quienNoAcompanaALaPersonaNoPuedeAceptar() throws Exception {
        aceptar(99, "Portería").andExpect(status().isForbidden());
    }
}
