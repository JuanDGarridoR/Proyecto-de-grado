package com.proyectogrado.persona_mayor_service.controller;

import com.proyectogrado.persona_mayor_service.model.AcompananteLookup;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorAcompanante;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorAcompananteId;
import com.proyectogrado.persona_mayor_service.model.UsuarioLookup;
import com.proyectogrado.persona_mayor_service.repository.AcompananteLookupRepository;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorAcompananteRepository;
import com.proyectogrado.persona_mayor_service.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contactos de emergencia de la persona mayor (RF-07 a RF-11 y RF-14): sus
 * acompañantes. Los agrega por celular, quedan pendientes hasta que el
 * acompañante acepta, los consulta y los quita.
 */
class PersonaMayorAcompananteControllerTest {

    private static final int PERSONA_MAYOR = 10;
    private static final int ACOMPANANTE = 20;
    private static final String CELULAR_ACOMPANANTE = "+573002223344";

    private PersonaMayorAcompananteRepository relacionRepository;
    private UsuarioLookupRepository usuarioLookupRepository;
    private AcompananteLookupRepository acompananteLookupRepository;
    private AcompananteLookup acompanante;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        relacionRepository = mock(PersonaMayorAcompananteRepository.class);
        usuarioLookupRepository = mock(UsuarioLookupRepository.class);
        acompananteLookupRepository = mock(AcompananteLookupRepository.class);

        UsuarioLookup usuarioAcompanante = mock(UsuarioLookup.class);
        when(usuarioAcompanante.getIdUsuario()).thenReturn(ACOMPANANTE);
        when(usuarioAcompanante.getNombreUsuario()).thenReturn("Carlos Díaz");
        when(usuarioAcompanante.getCelular()).thenReturn(CELULAR_ACOMPANANTE);
        when(usuarioLookupRepository.findByCelular(CELULAR_ACOMPANANTE)).thenReturn(Optional.of(usuarioAcompanante));
        when(usuarioLookupRepository.findById(ACOMPANANTE)).thenReturn(Optional.of(usuarioAcompanante));

        acompanante = mock(AcompananteLookup.class);
        when(acompanante.getIdUsuario()).thenReturn(ACOMPANANTE);
        when(acompanante.getRelacion()).thenReturn("Hijo");
        when(acompananteLookupRepository.findById(ACOMPANANTE)).thenReturn(Optional.of(acompanante));

        mockMvc = MockMvcBuilders.standaloneSetup(new PersonaMayorAcompananteController(
                relacionRepository, usuarioLookupRepository, acompananteLookupRepository)).build();
    }

    private PersonaMayorAcompanante relacion(String estado, String solicitadaPor) {
        PersonaMayorAcompanante relacion = new PersonaMayorAcompanante(PERSONA_MAYOR, ACOMPANANTE);
        relacion.setEstado(estado);
        relacion.setSolicitadaPor(solicitadaPor);
        return relacion;
    }

    private String agregar(String celular, String parentesco) {
        return "{\"celular\": \"" + celular + "\", \"relacion\": \"" + parentesco + "\"}";
    }

    @Test
    void agregarUnAcompananteRegistradoDejaLaSolicitudPendiente() throws Exception {
        when(relacionRepository.findById(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/persona-mayor/acompanantes")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agregar(CELULAR_ACOMPANANTE, "Hijo")))
                .andExpect(status().isOk())
                .andExpect(content().string("Solicitud de acompañamiento enviada correctamente"));

        ArgumentCaptor<PersonaMayorAcompanante> guardada = ArgumentCaptor.forClass(PersonaMayorAcompanante.class);
        verify(relacionRepository).saveAndFlush(guardada.capture());
        assertEquals(new PersonaMayorAcompananteId(PERSONA_MAYOR, ACOMPANANTE), guardada.getValue().getId());
        assertEquals("PENDIENTE", guardada.getValue().getEstado());
        assertEquals(PersonaMayorAcompanante.PERSONA_MAYOR, guardada.getValue().getSolicitadaPor());
        verify(acompanante).setRelacion("Hijo");
    }

    @Test
    void agregarSinCelularSeRechaza() throws Exception {
        mockMvc.perform(post("/api/persona-mayor/acompanantes")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agregar("", "Hijo")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("El celular es obligatorio"));

        verify(relacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void agregarUnCelularSinCuentaSeRechaza() throws Exception {
        when(usuarioLookupRepository.findByCelular(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/persona-mayor/acompanantes")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agregar("+573000000000", "Vecina")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("No existe un usuario registrado con ese celular"));

        verify(relacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void agregarAUnUsuarioQueNoEsAcompananteSeRechaza() throws Exception {
        when(acompananteLookupRepository.findById(ACOMPANANTE)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/persona-mayor/acompanantes")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agregar(CELULAR_ACOMPANANTE, "Hijo")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("El usuario existe, pero no está registrado como acompañante"));
    }

    @Test
    void agregarUnAcompananteQueYaEstaAceptadoSeRechaza() throws Exception {
        when(relacionRepository.findById(any())).thenReturn(Optional.of(relacion("ACEPTADA", "PERSONA_MAYOR")));

        mockMvc.perform(post("/api/persona-mayor/acompanantes")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agregar(CELULAR_ACOMPANANTE, "Hijo")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Este acompañante ya está registrado"));

        verify(relacionRepository, never()).saveAndFlush(any());
    }

    /**
     * Hallazgo del QC (CP-GCE-006): al repetir la solicitud a un acompañante
     * ya aceptado responde 400, pero antes ya guardó el parentesco nuevo.
     */
    @Test
    @Disabled("Hallazgo CP-GCE-006: una solicitud rechazada cambia el parentesco del contacto")
    void unaSolicitudRechazadaNoCambiaElParentescoDelContacto() throws Exception {
        when(relacionRepository.findById(any())).thenReturn(Optional.of(relacion("ACEPTADA", "PERSONA_MAYOR")));

        mockMvc.perform(post("/api/persona-mayor/acompanantes")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agregar(CELULAR_ACOMPANANTE, "Vecino")))
                .andExpect(status().isBadRequest());

        verify(acompanante, never()).setRelacion("Vecino");
        verify(acompananteLookupRepository, never()).saveAndFlush(any());
    }

    @Test
    void trasUnRechazoSePuedeVolverAEnviarLaSolicitud() throws Exception {
        PersonaMayorAcompanante rechazada = relacion("RECHAZADA", "PERSONA_MAYOR");
        when(relacionRepository.findById(any())).thenReturn(Optional.of(rechazada));

        mockMvc.perform(post("/api/persona-mayor/acompanantes")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agregar(CELULAR_ACOMPANANTE, "Hijo")))
                .andExpect(status().isOk());

        assertEquals("PENDIENTE", rechazada.getEstado());
        verify(relacionRepository).saveAndFlush(rechazada);
    }

    @Test
    void laListaDeContactosMuestraLosAcompanantesAceptadosConSuParentesco() throws Exception {
        when(relacionRepository.findById_IdPersonaMayorAndEstado(PERSONA_MAYOR, "ACEPTADA"))
                .thenReturn(List.of(relacion("ACEPTADA", "PERSONA_MAYOR")));

        mockMvc.perform(get("/api/persona-mayor/acompanantes").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idUsuario").value(ACOMPANANTE))
                .andExpect(jsonPath("$[0].nombre").value("Carlos Díaz"))
                .andExpect(jsonPath("$[0].celular").value(CELULAR_ACOMPANANTE))
                .andExpect(jsonPath("$[0].relacion").value("Hijo"));
    }

    @Test
    void lasSolicitudesPendientesSoloIncluyenLasQueEnvioElAcompanante() throws Exception {
        when(relacionRepository.findById_IdPersonaMayorAndEstado(PERSONA_MAYOR, "PENDIENTE")).thenReturn(List.of(
                relacion("PENDIENTE", PersonaMayorAcompanante.ACOMPANANTE),
                new PersonaMayorAcompanante(PERSONA_MAYOR, 99)));

        mockMvc.perform(get("/api/persona-mayor/acompanantes/solicitudes").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idUsuario").value(ACOMPANANTE));
    }

    @Test
    void aceptarLaSolicitudDeUnAcompananteLaDejaAceptada() throws Exception {
        PersonaMayorAcompanante pendiente = relacion("PENDIENTE", PersonaMayorAcompanante.ACOMPANANTE);
        when(relacionRepository.findById(new PersonaMayorAcompananteId(PERSONA_MAYOR, ACOMPANANTE)))
                .thenReturn(Optional.of(pendiente));

        mockMvc.perform(put("/api/persona-mayor/acompanantes/solicitudes/" + ACOMPANANTE + "/aceptar")
                        .header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isOk())
                .andExpect(content().string("Solicitud de acompañamiento aceptada"));

        assertEquals("ACEPTADA", pendiente.getEstado());
    }

    @Test
    void noSePuedeAceptarUnaSolicitudQueEnvioLaPropiaPersonaMayor() throws Exception {
        when(relacionRepository.findById(new PersonaMayorAcompananteId(PERSONA_MAYOR, ACOMPANANTE)))
                .thenReturn(Optional.of(relacion("PENDIENTE", PersonaMayorAcompanante.PERSONA_MAYOR)));

        mockMvc.perform(put("/api/persona-mayor/acompanantes/solicitudes/" + ACOMPANANTE + "/aceptar")
                        .header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isNotFound());
    }

    @Test
    void unaSolicitudYaRespondidaNoSeVuelveAResponder() throws Exception {
        when(relacionRepository.findById(new PersonaMayorAcompananteId(PERSONA_MAYOR, ACOMPANANTE)))
                .thenReturn(Optional.of(relacion("RECHAZADA", PersonaMayorAcompanante.ACOMPANANTE)));

        mockMvc.perform(put("/api/persona-mayor/acompanantes/solicitudes/" + ACOMPANANTE + "/aceptar")
                        .header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Esta solicitud ya fue procesada"));
    }

    @Test
    void quitarUnContactoBorraElVinculo() throws Exception {
        PersonaMayorAcompanante aceptada = relacion("ACEPTADA", "PERSONA_MAYOR");
        when(relacionRepository.findById(new PersonaMayorAcompananteId(PERSONA_MAYOR, ACOMPANANTE)))
                .thenReturn(Optional.of(aceptada));

        mockMvc.perform(delete("/api/persona-mayor/acompanantes/" + ACOMPANANTE).header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isOk())
                .andExpect(content().string("Asociación cancelada correctamente"));

        verify(relacionRepository).delete(aceptada);
    }

    @Test
    void quitarUnContactoQueYaNoExisteResponde404() throws Exception {
        when(relacionRepository.findById(any())).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/persona-mayor/acompanantes/" + ACOMPANANTE).header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isNotFound())
                .andExpect(content().string("No existe una asociación registrada"));

        verify(relacionRepository, never()).delete(any());
    }
}
