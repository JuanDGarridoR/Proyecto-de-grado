package com.proyectogrado.persona_mayor_service.controller;

import com.proyectogrado.persona_mayor_service.model.OrganizacionLookup;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorOrganizacion;
import com.proyectogrado.persona_mayor_service.model.UsuarioLookup;
import com.proyectogrado.persona_mayor_service.repository.OrganizacionLookupRepository;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorOrganizacionRepository;
import com.proyectogrado.persona_mayor_service.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Ver todas las organizaciones" de la persona mayor: solo las que no tienen
 * con ella un vínculo aceptado ni una solicitud pendiente.
 */
class OrganizacionesDisponiblesControllerTest {

    private static final int PERSONA_MAYOR = 10;

    private OrganizacionLookup organizacion(int id, String nombre) {
        OrganizacionLookup organizacion = mock(OrganizacionLookup.class);
        when(organizacion.getIdOrganizacion()).thenReturn(id);
        when(organizacion.getNombre()).thenReturn(nombre);
        when(organizacion.getDireccion()).thenReturn("Calle " + id);
        return organizacion;
    }

    @Test
    void noMuestraLasOrganizacionesConVinculoNiSolicitudPendiente() throws Exception {
        OrganizacionLookupRepository organizacionRepository = mock(OrganizacionLookupRepository.class);
        PersonaMayorOrganizacionRepository relacionRepository = mock(PersonaMayorOrganizacionRepository.class);
        UsuarioLookupRepository usuarioLookupRepository = mock(UsuarioLookupRepository.class);

        OrganizacionLookup fundacion = organizacion(5, "Fundación Entrenubes");
        OrganizacionLookup comedor = organizacion(6, "Comedor Comunitario");
        OrganizacionLookup hogar = organizacion(7, "Hogar San Rafael");
        when(organizacionRepository.findAllByOrderByNombreAsc()).thenReturn(List.of(comedor, fundacion, hogar));

        // Ya pertenece a la 5 y tiene una solicitud pendiente con la 7.
        when(relacionRepository.findById_IdPersonaMayorAndEstado(PERSONA_MAYOR, "ACEPTADA"))
                .thenReturn(List.of(new PersonaMayorOrganizacion(PERSONA_MAYOR, 5)));
        when(relacionRepository.findById_IdPersonaMayorAndEstado(PERSONA_MAYOR, "PENDIENTE"))
                .thenReturn(List.of(new PersonaMayorOrganizacion(PERSONA_MAYOR, 7)));

        UsuarioLookup cuentaComedor = mock(UsuarioLookup.class);
        when(cuentaComedor.getCorreo()).thenReturn("comedor@vitamas.co");
        when(usuarioLookupRepository.findByIdOrganizacion(6)).thenReturn(List.of(cuentaComedor));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new OrganizacionesDisponiblesController(
                organizacionRepository, relacionRepository, usuarioLookupRepository)).build();

        mockMvc.perform(get("/api/persona-mayor/organizaciones/disponibles").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Comedor Comunitario"))
                .andExpect(jsonPath("$[0].direccion").value("Calle 6"))
                .andExpect(jsonPath("$[0].correo").value("comedor@vitamas.co"));
    }
}
