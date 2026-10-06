package com.proyectogrado.organizacion_service.controller;

import com.proyectogrado.organizacion_service.model.PersonaMayorOrganizacion;
import com.proyectogrado.organizacion_service.model.PersonaMayorOrganizacionId;
import com.proyectogrado.organizacion_service.model.UsuarioLookup;
import com.proyectogrado.organizacion_service.repository.PersonaMayorOrganizacionRepository;
import com.proyectogrado.organizacion_service.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Personas mayores de la organización (RF-04 y RF-14): las consulta, les
 * envía una solicitud de vínculo por celular y cancela el vínculo. Solo ve a
 * las personas que aceptaron.
 */
class OrganizacionRelacionControllerTest {

    private static final int CUENTA_ORGANIZACION = 30;
    private static final int ORGANIZACION = 5;
    private static final int PERSONA_MAYOR = 10;
    private static final String CELULAR_PERSONA = "+573001110000";

    private PersonaMayorOrganizacionRepository relacionRepository;
    private UsuarioLookupRepository usuarioLookupRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        relacionRepository = mock(PersonaMayorOrganizacionRepository.class);
        usuarioLookupRepository = mock(UsuarioLookupRepository.class);

        UsuarioLookup cuenta = mock(UsuarioLookup.class);
        when(cuenta.getIdOrganizacion()).thenReturn(ORGANIZACION);
        when(usuarioLookupRepository.findById(CUENTA_ORGANIZACION)).thenReturn(Optional.of(cuenta));

        UsuarioLookup personaMayor = mock(UsuarioLookup.class);
        when(personaMayor.getIdUsuario()).thenReturn(PERSONA_MAYOR);
        when(personaMayor.getIdOrganizacion()).thenReturn(null);
        when(personaMayor.getNombreUsuario()).thenReturn("Rosa Díaz");
        when(personaMayor.getCelular()).thenReturn(CELULAR_PERSONA);
        when(personaMayor.getCorreo()).thenReturn("rosa@vitamas.co");
        when(usuarioLookupRepository.findById(PERSONA_MAYOR)).thenReturn(Optional.of(personaMayor));
        when(usuarioLookupRepository.findByCelular(CELULAR_PERSONA)).thenReturn(Optional.of(personaMayor));

        mockMvc = MockMvcBuilders.standaloneSetup(
                new OrganizacionRelacionController(relacionRepository, usuarioLookupRepository)).build();
    }

    private PersonaMayorOrganizacion vinculo(String estado) {
        PersonaMayorOrganizacion relacion = new PersonaMayorOrganizacion(PERSONA_MAYOR, ORGANIZACION);
        relacion.setEstado(estado);
        return relacion;
    }

    private ResultActions solicitar() throws Exception {
        return mockMvc.perform(post("/api/organizacion/personas-mayores")
                .header("X-User-Id", CUENTA_ORGANIZACION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"celular\": \"" + CELULAR_PERSONA + "\"}"));
    }

    @Test
    void laOrganizacionConsultaLasPersonasMayoresVinculadas() throws Exception {
        when(relacionRepository.findById_IdOrganizacionAndEstado(ORGANIZACION, "ACEPTADA"))
                .thenReturn(List.of(vinculo("ACEPTADA")));

        mockMvc.perform(get("/api/organizacion/personas-mayores").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idUsuario").value(PERSONA_MAYOR))
                .andExpect(jsonPath("$[0].nombre").value("Rosa Díaz"))
                .andExpect(jsonPath("$[0].correo").value("rosa@vitamas.co"));
    }

    @Test
    void laSolicitudDeVinculoQuedaPendienteHastaQueLaPersonaResponda() throws Exception {
        when(relacionRepository.findById(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/organizacion/personas-mayores")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"celular\": \"" + CELULAR_PERSONA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("Solicitud de asociación enviada correctamente"));

        ArgumentCaptor<PersonaMayorOrganizacion> guardada = ArgumentCaptor.forClass(PersonaMayorOrganizacion.class);
        verify(relacionRepository).saveAndFlush(guardada.capture());
        assertEquals(new PersonaMayorOrganizacionId(PERSONA_MAYOR, ORGANIZACION), guardada.getValue().getId());
        assertEquals("PENDIENTE", guardada.getValue().getEstado());
    }

    @Test
    void noSeEnviaUnaSolicitudAUnCelularSinCuenta() throws Exception {
        when(usuarioLookupRepository.findByCelular("+573000000000")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/organizacion/personas-mayores")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"celular\": \"+573000000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("No existe un usuario registrado con ese celular"));

        verify(relacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void noSeRepiteUnaSolicitudPendienteNiUnVinculoAceptado() throws Exception {
        when(relacionRepository.findById(new PersonaMayorOrganizacionId(PERSONA_MAYOR, ORGANIZACION)))
                .thenReturn(Optional.of(vinculo("ACEPTADA")));
        solicitar()
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Esta persona mayor ya está asociada a la organización"));

        when(relacionRepository.findById(new PersonaMayorOrganizacionId(PERSONA_MAYOR, ORGANIZACION)))
                .thenReturn(Optional.of(vinculo("PENDIENTE")));
        solicitar()
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Ya existe una solicitud pendiente para esta persona mayor"));

        verify(relacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void trasUnRechazoLaSolicitudSePuedeEnviarDeNuevo() throws Exception {
        PersonaMayorOrganizacion rechazado = vinculo("RECHAZADA");
        when(relacionRepository.findById(new PersonaMayorOrganizacionId(PERSONA_MAYOR, ORGANIZACION)))
                .thenReturn(Optional.of(rechazado));

        solicitar().andExpect(status().isOk());

        assertEquals("PENDIENTE", rechazado.getEstado());
        verify(relacionRepository).saveAndFlush(rechazado);
    }

    @Test
    void unUsuarioSinOrganizacionNoGestionaPersonasMayores() throws Exception {
        mockMvc.perform(get("/api/organizacion/personas-mayores").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isNotFound())
                .andExpect(content().string("El usuario no tiene una organización asociada"));
    }

    @Test
    void cancelarElVinculoLoBorraYSiNoExisteResponde404() throws Exception {
        PersonaMayorOrganizacion aceptado = vinculo("ACEPTADA");
        when(relacionRepository.findById(new PersonaMayorOrganizacionId(PERSONA_MAYOR, ORGANIZACION)))
                .thenReturn(Optional.of(aceptado));

        mockMvc.perform(delete("/api/organizacion/personas-mayores/" + PERSONA_MAYOR)
                        .header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isOk());
        verify(relacionRepository).delete(aceptado);

        mockMvc.perform(delete("/api/organizacion/personas-mayores/77").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isNotFound());
    }
}
