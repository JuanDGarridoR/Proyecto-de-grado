package com.proyectogrado.persona_mayor_service.controller;

import com.proyectogrado.persona_mayor_service.model.AcompananteLookup;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorAcompanante;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorOrganizacion;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorOrganizacionId;
import com.proyectogrado.persona_mayor_service.model.UsuarioLookup;
import com.proyectogrado.persona_mayor_service.repository.AcompananteLookupRepository;
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
import static org.mockito.ArgumentMatchers.any;
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
 * Vínculo entre la persona mayor y las organizaciones (RF-14 y RF-03): ella
 * acepta o rechaza las invitaciones, también puede pedir unirse a una
 * organización, y la organización solo ve los acompañantes de las personas
 * vinculadas a ella.
 */
class PersonaMayorOrganizacionControllerTest {

    private static final int PERSONA_MAYOR = 10;
    private static final int ORGANIZACION = 5;
    private static final int CUENTA_ORGANIZACION = 30;

    private PersonaMayorOrganizacionRepository relacionRepository;
    private UsuarioLookupRepository usuarioLookupRepository;
    private AcompananteLookupRepository acompananteLookupRepository;
    private PersonaMayorAcompananteRepository personaMayorAcompananteRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        relacionRepository = mock(PersonaMayorOrganizacionRepository.class);
        usuarioLookupRepository = mock(UsuarioLookupRepository.class);
        acompananteLookupRepository = mock(AcompananteLookupRepository.class);
        personaMayorAcompananteRepository = mock(PersonaMayorAcompananteRepository.class);

        UsuarioLookup cuentaOrganizacion = mock(UsuarioLookup.class);
        when(cuentaOrganizacion.getIdOrganizacion()).thenReturn(ORGANIZACION);
        when(cuentaOrganizacion.getNombreUsuario()).thenReturn("Fundación Entrenubes");
        when(cuentaOrganizacion.getCelular()).thenReturn("+573003330000");
        when(usuarioLookupRepository.findById(CUENTA_ORGANIZACION)).thenReturn(Optional.of(cuentaOrganizacion));
        when(usuarioLookupRepository.findByIdOrganizacion(ORGANIZACION)).thenReturn(List.of(cuentaOrganizacion));

        mockMvc = MockMvcBuilders.standaloneSetup(new PersonaMayorOrganizacionController(
                relacionRepository, usuarioLookupRepository,
                acompananteLookupRepository, personaMayorAcompananteRepository)).build();
    }

    private PersonaMayorOrganizacion vinculo(String estado) {
        PersonaMayorOrganizacion relacion = new PersonaMayorOrganizacion(PERSONA_MAYOR, ORGANIZACION);
        relacion.setEstado(estado);
        return relacion;
    }

    private void existeVinculo(String estado) {
        when(relacionRepository.findById(new PersonaMayorOrganizacionId(PERSONA_MAYOR, ORGANIZACION)))
                .thenReturn(Optional.of(vinculo(estado)));
    }

    @Test
    void laPersonaMayorVeLasOrganizacionesConLasQueEstaVinculada() throws Exception {
        when(relacionRepository.findById_IdPersonaMayorAndEstado(PERSONA_MAYOR, "ACEPTADA"))
                .thenReturn(List.of(vinculo("ACEPTADA")));

        mockMvc.perform(get("/api/persona-mayor/organizaciones").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idOrganizacion").value(ORGANIZACION))
                .andExpect(jsonPath("$[0].nombre").value("Fundación Entrenubes"))
                .andExpect(jsonPath("$[0].celular").value("+573003330000"));
    }

    @Test
    void aceptarLaSolicitudDeUnaOrganizacionLaDejaAceptada() throws Exception {
        PersonaMayorOrganizacion pendiente = vinculo("PENDIENTE");
        when(relacionRepository.findById(new PersonaMayorOrganizacionId(PERSONA_MAYOR, ORGANIZACION)))
                .thenReturn(Optional.of(pendiente));

        mockMvc.perform(put("/api/persona-mayor/organizaciones/solicitudes/" + ORGANIZACION + "/aceptar")
                        .header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isOk())
                .andExpect(content().string("Solicitud aceptada correctamente"));

        assertEquals("ACEPTADA", pendiente.getEstado());
        verify(relacionRepository).saveAndFlush(pendiente);
    }

    @Test
    void unaSolicitudYaRespondidaNoSeVuelveAResponder() throws Exception {
        existeVinculo("RECHAZADA");

        mockMvc.perform(put("/api/persona-mayor/organizaciones/solicitudes/" + ORGANIZACION + "/aceptar")
                        .header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Esta solicitud ya fue procesada"));
    }

    @Test
    void laPersonaMayorPuedeDeshacerElVinculo() throws Exception {
        PersonaMayorOrganizacion aceptado = vinculo("ACEPTADA");
        when(relacionRepository.findById(new PersonaMayorOrganizacionId(PERSONA_MAYOR, ORGANIZACION)))
                .thenReturn(Optional.of(aceptado));

        mockMvc.perform(delete("/api/persona-mayor/organizaciones/" + ORGANIZACION).header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isOk());

        verify(relacionRepository).delete(aceptado);
    }

    @Test
    void laPersonaMayorSolicitaUnirseYQuedaPendienteParaLaOrganizacion() throws Exception {
        mockMvc.perform(post("/api/persona-mayor/organizaciones/" + ORGANIZACION + "/solicitud")
                        .header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isOk())
                .andExpect(content().string("Solicitud enviada. La organización te responderá pronto"));

        ArgumentCaptor<PersonaMayorOrganizacion> guardada = ArgumentCaptor.forClass(PersonaMayorOrganizacion.class);
        verify(relacionRepository).saveAndFlush(guardada.capture());
        assertEquals("PENDIENTE", guardada.getValue().getEstado());
        assertEquals(PersonaMayorOrganizacion.PERSONA_MAYOR, guardada.getValue().getSolicitadaPor());
    }

    @Test
    void siLaOrganizacionYaLaHabiaInvitadoPedirUnirseLaVincula() throws Exception {
        PersonaMayorOrganizacion invitacion = vinculo("PENDIENTE");
        when(relacionRepository.findById(new PersonaMayorOrganizacionId(PERSONA_MAYOR, ORGANIZACION)))
                .thenReturn(Optional.of(invitacion));

        mockMvc.perform(post("/api/persona-mayor/organizaciones/" + ORGANIZACION + "/solicitud")
                        .header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isOk());

        assertEquals("ACEPTADA", invitacion.getEstado());
    }

    @Test
    void noSePuedeSolicitarDosVecesNiAUnaOrganizacionQueNoExiste() throws Exception {
        PersonaMayorOrganizacion enviada = vinculo("PENDIENTE");
        enviada.setSolicitadaPor(PersonaMayorOrganizacion.PERSONA_MAYOR);
        when(relacionRepository.findById(new PersonaMayorOrganizacionId(PERSONA_MAYOR, ORGANIZACION)))
                .thenReturn(Optional.of(enviada));

        mockMvc.perform(post("/api/persona-mayor/organizaciones/" + ORGANIZACION + "/solicitud")
                        .header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Ya enviaste una solicitud a esta organización"));

        mockMvc.perform(post("/api/persona-mayor/organizaciones/99/solicitud").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isNotFound());
    }

    @Test
    void lasSolicitudesQueEnvioNoAparecenComoInvitacionesNiLasPuedeResponderElla() throws Exception {
        PersonaMayorOrganizacion enviada = vinculo("PENDIENTE");
        enviada.setSolicitadaPor(PersonaMayorOrganizacion.PERSONA_MAYOR);
        when(relacionRepository.findById_IdPersonaMayorAndEstado(PERSONA_MAYOR, "PENDIENTE"))
                .thenReturn(List.of(enviada));
        when(relacionRepository.findById(new PersonaMayorOrganizacionId(PERSONA_MAYOR, ORGANIZACION)))
                .thenReturn(Optional.of(enviada));

        mockMvc.perform(get("/api/persona-mayor/organizaciones/solicitudes").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/persona-mayor/organizaciones/solicitudes/enviadas").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(jsonPath("$[0].nombre").value("Fundación Entrenubes"));

        mockMvc.perform(put("/api/persona-mayor/organizaciones/solicitudes/" + ORGANIZACION + "/aceptar")
                        .header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Esta solicitud la responde la organización"));
    }

    @Test
    void laOrganizacionVeLosAcompanantesDeUnaPersonaVinculada() throws Exception {
        existeVinculo("ACEPTADA");
        when(personaMayorAcompananteRepository.findById_IdPersonaMayorAndEstado(PERSONA_MAYOR, "ACEPTADA"))
                .thenReturn(List.of(new PersonaMayorAcompanante(PERSONA_MAYOR, 20)));

        UsuarioLookup hijo = mock(UsuarioLookup.class);
        when(hijo.getNombreUsuario()).thenReturn("Carlos Díaz");
        when(hijo.getCelular()).thenReturn("+573002220000");
        when(usuarioLookupRepository.findById(20)).thenReturn(Optional.of(hijo));
        AcompananteLookup acompanante = mock(AcompananteLookup.class);
        when(acompanante.getRelacion()).thenReturn("Hijo");
        when(acompananteLookupRepository.findById(20)).thenReturn(Optional.of(acompanante));

        mockMvc.perform(get("/api/organizacion/personas-mayores/" + PERSONA_MAYOR + "/acompanantes")
                        .header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Carlos Díaz"))
                .andExpect(jsonPath("$[0].relacion").value("Hijo"));
    }

    @Test
    void laOrganizacionNoVeLosAcompanantesDeUnaPersonaQueNoAceptoElVinculo() throws Exception {
        existeVinculo("PENDIENTE");

        mockMvc.perform(get("/api/organizacion/personas-mayores/" + PERSONA_MAYOR + "/acompanantes")
                        .header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isNotFound())
                .andExpect(content().string("La persona mayor no está asociada a esta organización"));

        verify(personaMayorAcompananteRepository, never()).findById_IdPersonaMayorAndEstado(any(), any());
    }
}
