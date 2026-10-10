package com.proyectogrado.voluntario_service.controller;

import com.proyectogrado.voluntario_service.model.OrganizacionLookup;
import com.proyectogrado.voluntario_service.model.UsuarioLookup;
import com.proyectogrado.voluntario_service.model.VoluntarioOrganizacion;
import com.proyectogrado.voluntario_service.model.VoluntarioOrganizacionId;
import com.proyectogrado.voluntario_service.repository.OrganizacionLookupRepository;
import com.proyectogrado.voluntario_service.repository.UsuarioLookupRepository;
import com.proyectogrado.voluntario_service.repository.VoluntarioOrganizacionRepository;
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
 * Vinculación de voluntarios con organizaciones (RF-03): el voluntario pide
 * vincularse y la organización acepta o rechaza; cada lado solo usa sus
 * propios endpoints.
 */
class VoluntarioOrganizacionControllerTest {

    private static final int VOLUNTARIO = 40;
    private static final int CUENTA_ORGANIZACION = 30;
    private static final int ORGANIZACION = 5;

    private VoluntarioOrganizacionRepository relacionRepository;
    private OrganizacionLookupRepository organizacionLookupRepository;
    private UsuarioLookupRepository usuarioLookupRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        relacionRepository = mock(VoluntarioOrganizacionRepository.class);
        organizacionLookupRepository = mock(OrganizacionLookupRepository.class);
        usuarioLookupRepository = mock(UsuarioLookupRepository.class);

        when(usuarioLookupRepository.tieneRol(VOLUNTARIO, "VOLUNTARIO")).thenReturn(true);
        when(usuarioLookupRepository.tieneRol(CUENTA_ORGANIZACION, "ORGANIZACION")).thenReturn(true);

        UsuarioLookup cuenta = mock(UsuarioLookup.class);
        when(cuenta.getIdOrganizacion()).thenReturn(ORGANIZACION);
        when(usuarioLookupRepository.findById(CUENTA_ORGANIZACION)).thenReturn(Optional.of(cuenta));

        UsuarioLookup voluntario = mock(UsuarioLookup.class);
        when(voluntario.getIdUsuario()).thenReturn(VOLUNTARIO);
        when(voluntario.getNombreUsuario()).thenReturn("Andrea López");
        when(voluntario.getCelular()).thenReturn("+573004440000");
        when(usuarioLookupRepository.findAllById(List.of(VOLUNTARIO))).thenReturn(List.of(voluntario));

        mockMvc = MockMvcBuilders.standaloneSetup(new VoluntarioOrganizacionController(
                relacionRepository, organizacionLookupRepository, usuarioLookupRepository)).build();
    }

    private VoluntarioOrganizacion vinculo(String estado) {
        VoluntarioOrganizacion relacion = new VoluntarioOrganizacion(VOLUNTARIO, ORGANIZACION);
        relacion.setEstado(estado);
        return relacion;
    }

    private void existeVinculo(String estado) {
        when(relacionRepository.findById(new VoluntarioOrganizacionId(VOLUNTARIO, ORGANIZACION)))
                .thenReturn(Optional.of(vinculo(estado)));
    }

    @Test
    void elVoluntarioVeLasOrganizacionesConElEstadoDeSuVinculo() throws Exception {
        OrganizacionLookup fundacion = mock(OrganizacionLookup.class);
        when(fundacion.getIdOrganizacion()).thenReturn(ORGANIZACION);
        when(fundacion.getNombre()).thenReturn("Fundación Entrenubes");
        OrganizacionLookup comedor = mock(OrganizacionLookup.class);
        when(comedor.getIdOrganizacion()).thenReturn(6);
        when(comedor.getNombre()).thenReturn("Comedor Comunitario");
        when(organizacionLookupRepository.findActivas()).thenReturn(List.of(fundacion, comedor));
        when(relacionRepository.findById_IdVoluntario(VOLUNTARIO)).thenReturn(List.of(vinculo("PENDIENTE")));

        mockMvc.perform(get("/api/voluntario/organizaciones").header("X-User-Id", VOLUNTARIO))
                .andExpect(status().isOk())
                // Ordenadas por nombre.
                .andExpect(jsonPath("$[0].nombre").value("Comedor Comunitario"))
                .andExpect(jsonPath("$[0].estado").doesNotExist())
                .andExpect(jsonPath("$[1].nombre").value("Fundación Entrenubes"))
                .andExpect(jsonPath("$[1].estado").value("PENDIENTE"));
    }

    @Test
    void laSolicitudDeVinculacionQuedaPendiente() throws Exception {
        when(organizacionLookupRepository.existsById(ORGANIZACION)).thenReturn(true);
        when(relacionRepository.findById(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/voluntario/organizaciones/" + ORGANIZACION + "/solicitud").header("X-User-Id", VOLUNTARIO))
                .andExpect(status().isOk())
                .andExpect(content().string("Solicitud de vinculación enviada correctamente"));

        ArgumentCaptor<VoluntarioOrganizacion> guardada = ArgumentCaptor.forClass(VoluntarioOrganizacion.class);
        verify(relacionRepository).saveAndFlush(guardada.capture());
        assertEquals(VoluntarioOrganizacion.PENDIENTE, guardada.getValue().getEstado());
    }

    @Test
    void noSeSolicitaDosVecesNiAUnaOrganizacionQueNoExiste() throws Exception {
        when(organizacionLookupRepository.existsById(ORGANIZACION)).thenReturn(true);
        existeVinculo(VoluntarioOrganizacion.ACEPTADA);

        mockMvc.perform(post("/api/voluntario/organizaciones/" + ORGANIZACION + "/solicitud").header("X-User-Id", VOLUNTARIO))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Ya estás vinculado a esta organización"));

        mockMvc.perform(post("/api/voluntario/organizaciones/99/solicitud").header("X-User-Id", VOLUNTARIO))
                .andExpect(status().isNotFound());

        verify(relacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void soloUnVoluntarioPideVincularse() throws Exception {
        mockMvc.perform(post("/api/voluntario/organizaciones/" + ORGANIZACION + "/solicitud").header("X-User-Id", 10))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Esta función es solo para voluntarios"));
    }

    @Test
    void laOrganizacionVeLasSolicitudesYAceptaUna() throws Exception {
        when(relacionRepository.findById_IdOrganizacionAndEstado(ORGANIZACION, VoluntarioOrganizacion.PENDIENTE))
                .thenReturn(List.of(vinculo(VoluntarioOrganizacion.PENDIENTE)));

        mockMvc.perform(get("/api/organizacion/voluntarios/solicitudes").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Andrea López"));

        VoluntarioOrganizacion pendiente = vinculo(VoluntarioOrganizacion.PENDIENTE);
        when(relacionRepository.findById(new VoluntarioOrganizacionId(VOLUNTARIO, ORGANIZACION)))
                .thenReturn(Optional.of(pendiente));

        mockMvc.perform(put("/api/organizacion/voluntarios/solicitudes/" + VOLUNTARIO + "/aceptar")
                        .header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isOk())
                .andExpect(content().string("Solicitud aceptada correctamente"));

        assertEquals(VoluntarioOrganizacion.ACEPTADA, pendiente.getEstado());
    }

    @Test
    void unaSolicitudYaRespondidaNoSeVuelveAResponder() throws Exception {
        existeVinculo(VoluntarioOrganizacion.RECHAZADA);

        mockMvc.perform(put("/api/organizacion/voluntarios/solicitudes/" + VOLUNTARIO + "/aceptar")
                        .header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Esta solicitud ya fue procesada"));
    }

    @Test
    void sinElRolDeOrganizacionNoSeGestionanVoluntarios() throws Exception {
        // Tiene id_organizacion por datos viejos, pero no el rol.
        UsuarioLookup otraCuenta = mock(UsuarioLookup.class);
        when(otraCuenta.getIdOrganizacion()).thenReturn(ORGANIZACION);
        when(usuarioLookupRepository.findById(31)).thenReturn(Optional.of(otraCuenta));

        mockMvc.perform(get("/api/organizacion/voluntarios").header("X-User-Id", 31))
                .andExpect(status().isNotFound())
                .andExpect(content().string("El usuario no tiene una organización asociada"));
    }

    @Test
    void elVoluntarioInactivaYReactivaSuVinculo() throws Exception {
        VoluntarioOrganizacion vinculo = vinculo(VoluntarioOrganizacion.ACEPTADA);
        when(relacionRepository.findById(new VoluntarioOrganizacionId(VOLUNTARIO, ORGANIZACION)))
                .thenReturn(Optional.of(vinculo));
        UsuarioLookup cuentaActiva = mock(UsuarioLookup.class);
        when(cuentaActiva.estaActivo()).thenReturn(true);
        when(usuarioLookupRepository.findCuentasOrganizacion(ORGANIZACION)).thenReturn(List.of(cuentaActiva));

        mockMvc.perform(put("/api/voluntario/organizaciones/" + ORGANIZACION + "/inactivar")
                        .header("X-User-Id", VOLUNTARIO))
                .andExpect(status().isOk());
        assertEquals(VoluntarioOrganizacion.INACTIVA, vinculo.getEstado());

        // Mientras está inactivo no puede volver a solicitar: tiene que reactivarlo.
        when(organizacionLookupRepository.existsById(ORGANIZACION)).thenReturn(true);
        mockMvc.perform(post("/api/voluntario/organizaciones/" + ORGANIZACION + "/solicitud")
                        .header("X-User-Id", VOLUNTARIO))
                .andExpect(status().isBadRequest());
        assertEquals(VoluntarioOrganizacion.INACTIVA, vinculo.getEstado());

        mockMvc.perform(put("/api/voluntario/organizaciones/" + ORGANIZACION + "/reactivar")
                        .header("X-User-Id", VOLUNTARIO))
                .andExpect(status().isOk());
        assertEquals(VoluntarioOrganizacion.ACEPTADA, vinculo.getEstado());
    }

    @Test
    void elVoluntarioPuedeDesvincularseYLaOrganizacionTambien() throws Exception {
        VoluntarioOrganizacion aceptado = vinculo(VoluntarioOrganizacion.ACEPTADA);
        when(relacionRepository.findById(new VoluntarioOrganizacionId(VOLUNTARIO, ORGANIZACION)))
                .thenReturn(Optional.of(aceptado));

        mockMvc.perform(delete("/api/voluntario/organizaciones/" + ORGANIZACION).header("X-User-Id", VOLUNTARIO))
                .andExpect(status().isOk())
                .andExpect(content().string("Te desvinculaste de la organización correctamente"));

        mockMvc.perform(delete("/api/organizacion/voluntarios/" + VOLUNTARIO).header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isOk())
                .andExpect(content().string("Voluntario desvinculado correctamente"));
    }
}
