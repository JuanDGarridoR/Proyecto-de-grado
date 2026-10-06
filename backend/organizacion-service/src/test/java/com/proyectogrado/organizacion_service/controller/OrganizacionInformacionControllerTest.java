package com.proyectogrado.organizacion_service.controller;

import com.proyectogrado.organizacion_service.model.OrganizacionLookup;
import com.proyectogrado.organizacion_service.model.UsuarioLookup;
import com.proyectogrado.organizacion_service.repository.OrganizacionLookupRepository;
import com.proyectogrado.organizacion_service.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perfil de la organización (RF-04 y RF-05): nombre y contacto de su cuenta,
 * y la dirección, que es lo único que se edita aquí.
 */
class OrganizacionInformacionControllerTest {

    private OrganizacionLookupRepository organizacionLookupRepository;
    private OrganizacionLookup organizacion;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UsuarioLookupRepository usuarioLookupRepository = mock(UsuarioLookupRepository.class);
        organizacionLookupRepository = mock(OrganizacionLookupRepository.class);

        UsuarioLookup cuenta = mock(UsuarioLookup.class);
        when(cuenta.getIdOrganizacion()).thenReturn(5);
        when(cuenta.getNombreUsuario()).thenReturn("Fundación Entrenubes");
        when(cuenta.getCelular()).thenReturn("+573003330000");
        when(cuenta.getCorreo()).thenReturn("contacto@entrenubes.org");
        when(usuarioLookupRepository.findById(30)).thenReturn(Optional.of(cuenta));

        UsuarioLookup personaMayor = mock(UsuarioLookup.class);
        when(personaMayor.getIdOrganizacion()).thenReturn(null);
        when(usuarioLookupRepository.findById(10)).thenReturn(Optional.of(personaMayor));

        organizacion = mock(OrganizacionLookup.class);
        when(organizacion.getIdOrganizacion()).thenReturn(5);
        when(organizacion.getDireccion()).thenReturn("Carrera 5 # 80-10 Sur");
        when(organizacionLookupRepository.findById(5)).thenReturn(Optional.of(organizacion));
        when(organizacionLookupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc = MockMvcBuilders.standaloneSetup(
                new OrganizacionInformacionController(usuarioLookupRepository, organizacionLookupRepository)).build();
    }

    @Test
    void elPerfilMuestraLosDatosDeLaOrganizacion() throws Exception {
        mockMvc.perform(get("/api/organizacion/informacion").header("X-User-Id", 30))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idOrganizacion").value(5))
                .andExpect(jsonPath("$.nombre").value("Fundación Entrenubes"))
                .andExpect(jsonPath("$.correo").value("contacto@entrenubes.org"))
                .andExpect(jsonPath("$.direccion").value("Carrera 5 # 80-10 Sur"));
    }

    @Test
    void cambiarLaDireccionLaGuarda() throws Exception {
        mockMvc.perform(put("/api/organizacion/informacion")
                        .header("X-User-Id", 30)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direccion\": \"Calle 90 Sur # 5-12\"}"))
                .andExpect(status().isOk());

        verify(organizacion).setDireccion("Calle 90 Sur # 5-12");
        verify(organizacionLookupRepository).save(organizacion);
    }

    @Test
    void unUsuarioQueNoEsOrganizacionNoTienePerfilDeOrganizacion() throws Exception {
        mockMvc.perform(get("/api/organizacion/informacion").header("X-User-Id", 10))
                .andExpect(status().isNotFound());
    }
}
