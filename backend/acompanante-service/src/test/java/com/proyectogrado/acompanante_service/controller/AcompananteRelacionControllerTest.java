package com.proyectogrado.acompanante_service.controller;

import com.proyectogrado.acompanante_service.model.AcompananteInfoLookup;
import com.proyectogrado.acompanante_service.model.PersonaMayorAcompanante;
import com.proyectogrado.acompanante_service.model.PersonaMayorAcompananteId;
import com.proyectogrado.acompanante_service.model.UsuarioLookup;
import com.proyectogrado.acompanante_service.repository.AcompananteInfoLookupRepository;
import com.proyectogrado.acompanante_service.repository.PersonaMayorAcompananteRepository;
import com.proyectogrado.acompanante_service.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
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
 * Vínculo desde el lado del acompañante (RF-11 y RF-14): ve a las personas
 * mayores que acompaña, les envía solicitudes por celular y responde las que
 * ellas le envían.
 */
class AcompananteRelacionControllerTest {

    private static final int ACOMPANANTE = 20;
    private static final int PERSONA_MAYOR = 10;
    private static final String CELULAR_PERSONA = "+573001110000";

    private PersonaMayorAcompananteRepository relacionRepository;
    private UsuarioLookupRepository usuarioLookupRepository;
    private AcompananteInfoLookupRepository acompananteInfoLookupRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        relacionRepository = mock(PersonaMayorAcompananteRepository.class);
        usuarioLookupRepository = mock(UsuarioLookupRepository.class);
        acompananteInfoLookupRepository = mock(AcompananteInfoLookupRepository.class);

        AcompananteInfoLookup acompanante = mock(AcompananteInfoLookup.class);
        when(acompananteInfoLookupRepository.findById(ACOMPANANTE)).thenReturn(Optional.of(acompanante));

        UsuarioLookup personaMayor = mock(UsuarioLookup.class);
        when(personaMayor.getIdUsuario()).thenReturn(PERSONA_MAYOR);
        when(personaMayor.getNombreUsuario()).thenReturn("Rosa Díaz");
        when(personaMayor.getCelular()).thenReturn(CELULAR_PERSONA);
        when(usuarioLookupRepository.findById(PERSONA_MAYOR)).thenReturn(Optional.of(personaMayor));
        when(usuarioLookupRepository.findByCelular(CELULAR_PERSONA)).thenReturn(Optional.of(personaMayor));
        when(usuarioLookupRepository.tieneRol(PERSONA_MAYOR, "PERSONA_MAYOR")).thenReturn(true);

        mockMvc = MockMvcBuilders.standaloneSetup(new AcompananteRelacionController(
                relacionRepository, usuarioLookupRepository, acompananteInfoLookupRepository)).build();
    }

    private PersonaMayorAcompanante relacion(String estado, String solicitadaPor) {
        PersonaMayorAcompanante relacion = new PersonaMayorAcompanante(PERSONA_MAYOR, ACOMPANANTE);
        relacion.setEstado(estado);
        relacion.setSolicitadaPor(solicitadaPor);
        return relacion;
    }

    @Test
    void elAcompananteVeLasPersonasMayoresQueAcompana() throws Exception {
        when(relacionRepository.findById_IdAcompananteAndEstado(ACOMPANANTE, "ACEPTADA"))
                .thenReturn(List.of(relacion("ACEPTADA", PersonaMayorAcompanante.PERSONA_MAYOR)));

        mockMvc.perform(get("/api/acompanante/personas-mayores").header("X-User-Id", ACOMPANANTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Rosa Díaz"))
                .andExpect(jsonPath("$[0].celular").value(CELULAR_PERSONA));
    }

    @Test
    void lasSolicitudesRecibidasYLasEnviadasSeListanPorSeparado() throws Exception {
        when(relacionRepository.findById_IdAcompananteAndEstado(ACOMPANANTE, "PENDIENTE"))
                .thenReturn(List.of(relacion("PENDIENTE", PersonaMayorAcompanante.PERSONA_MAYOR)));

        mockMvc.perform(get("/api/acompanante/personas-mayores/solicitudes").header("X-User-Id", ACOMPANANTE))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/acompanante/personas-mayores/solicitudes/enviadas").header("X-User-Id", ACOMPANANTE))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void elAcompananteEnviaUnaSolicitudAUnaPersonaMayorPorSuCelular() throws Exception {
        when(relacionRepository.findById(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/acompanante/personas-mayores")
                        .header("X-User-Id", ACOMPANANTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"celular\": \"" + CELULAR_PERSONA + "\", \"relacion\": \"Hijo\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("Solicitud de acompañamiento enviada correctamente"));

        ArgumentCaptor<PersonaMayorAcompanante> guardada = ArgumentCaptor.forClass(PersonaMayorAcompanante.class);
        verify(relacionRepository).saveAndFlush(guardada.capture());
        assertEquals("PENDIENTE", guardada.getValue().getEstado());
        assertEquals(PersonaMayorAcompanante.ACOMPANANTE, guardada.getValue().getSolicitadaPor());
    }

    @Test
    void soloUnAcompananteEnviaSolicitudesDeAcompanamiento() throws Exception {
        when(acompananteInfoLookupRepository.findById(31)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/acompanante/personas-mayores")
                        .header("X-User-Id", 31)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"celular\": \"" + CELULAR_PERSONA + "\", \"relacion\": \"Hijo\"}"))
                .andExpect(status().isForbidden());

        verify(relacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void noSeEnviaUnaSolicitudAQuienNoEsPersonaMayor() throws Exception {
        when(usuarioLookupRepository.tieneRol(PERSONA_MAYOR, "PERSONA_MAYOR")).thenReturn(false);

        mockMvc.perform(post("/api/acompanante/personas-mayores")
                        .header("X-User-Id", ACOMPANANTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"celular\": \"" + CELULAR_PERSONA + "\", \"relacion\": \"Hijo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("El usuario existe, pero no está registrado como persona mayor"));
    }

    @Test
    void aceptarLaSolicitudDeLaPersonaMayorCreaElVinculo() throws Exception {
        PersonaMayorAcompanante pendiente = relacion("PENDIENTE", PersonaMayorAcompanante.PERSONA_MAYOR);
        when(relacionRepository.findById_IdAcompanante(ACOMPANANTE)).thenReturn(List.of(pendiente));

        mockMvc.perform(put("/api/acompanante/personas-mayores/solicitudes/" + PERSONA_MAYOR + "/aceptar")
                        .header("X-User-Id", ACOMPANANTE))
                .andExpect(status().isOk())
                .andExpect(content().string("Solicitud de acompañamiento aceptada"));

        assertEquals("ACEPTADA", pendiente.getEstado());
        verify(relacionRepository).save(pendiente);
    }

    @Test
    void rechazarLaSolicitudLaDejaRechazada() throws Exception {
        PersonaMayorAcompanante pendiente = relacion("PENDIENTE", PersonaMayorAcompanante.PERSONA_MAYOR);
        when(relacionRepository.findById_IdAcompanante(ACOMPANANTE)).thenReturn(List.of(pendiente));

        mockMvc.perform(put("/api/acompanante/personas-mayores/solicitudes/" + PERSONA_MAYOR + "/rechazar")
                        .header("X-User-Id", ACOMPANANTE))
                .andExpect(status().isOk());

        assertEquals("RECHAZADA", pendiente.getEstado());
    }

    @Test
    void elAcompananteNoPuedeAceptarSuPropiaSolicitud() throws Exception {
        when(relacionRepository.findById_IdAcompanante(ACOMPANANTE))
                .thenReturn(List.of(relacion("PENDIENTE", PersonaMayorAcompanante.ACOMPANANTE)));

        mockMvc.perform(put("/api/acompanante/personas-mayores/solicitudes/" + PERSONA_MAYOR + "/aceptar")
                        .header("X-User-Id", ACOMPANANTE))
                .andExpect(status().isNotFound());

        verify(relacionRepository, never()).save(any());
    }

    @Test
    void unaSolicitudYaRespondidaNoSeVuelveAResponder() throws Exception {
        when(relacionRepository.findById_IdAcompanante(ACOMPANANTE))
                .thenReturn(List.of(relacion("ACEPTADA", PersonaMayorAcompanante.PERSONA_MAYOR)));

        mockMvc.perform(put("/api/acompanante/personas-mayores/solicitudes/" + PERSONA_MAYOR + "/rechazar")
                        .header("X-User-Id", ACOMPANANTE))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Esta solicitud ya fue procesada"));
    }

    @Test
    void cancelarElVinculoLoBorra() throws Exception {
        PersonaMayorAcompanante aceptada = relacion("ACEPTADA", PersonaMayorAcompanante.PERSONA_MAYOR);
        when(relacionRepository.findById(new PersonaMayorAcompananteId(PERSONA_MAYOR, ACOMPANANTE)))
                .thenReturn(Optional.of(aceptada));

        mockMvc.perform(delete("/api/acompanante/personas-mayores/" + PERSONA_MAYOR).header("X-User-Id", ACOMPANANTE))
                .andExpect(status().isOk());

        verify(relacionRepository).delete(aceptada);
    }
}
