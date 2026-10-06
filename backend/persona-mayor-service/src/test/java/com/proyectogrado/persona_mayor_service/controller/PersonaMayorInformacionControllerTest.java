package com.proyectogrado.persona_mayor_service.controller;

import com.proyectogrado.persona_mayor_service.model.PersonaMayorLookup;
import com.proyectogrado.persona_mayor_service.model.UsuarioLookup;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorLookupRepository;
import com.proyectogrado.persona_mayor_service.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perfil de la persona mayor (RF-12 y RF-13): fecha de nacimiento, género y
 * dirección, con el nombre y el contacto de su cuenta.
 */
class PersonaMayorInformacionControllerTest {

    private static final int PERSONA_MAYOR = 10;

    private PersonaMayorLookupRepository personaMayorLookupRepository;
    private PersonaMayorLookup personaMayor;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        personaMayorLookupRepository = mock(PersonaMayorLookupRepository.class);
        UsuarioLookupRepository usuarioLookupRepository = mock(UsuarioLookupRepository.class);

        personaMayor = mock(PersonaMayorLookup.class);
        when(personaMayor.getIdUsuario()).thenReturn(PERSONA_MAYOR);
        when(personaMayor.getFechaNacimiento()).thenReturn(LocalDate.of(1950, 3, 14));
        when(personaMayor.getGenero()).thenReturn("Femenino");
        when(personaMayor.getDireccion()).thenReturn("Calle 91 Sur # 3-20");
        when(personaMayorLookupRepository.findById(PERSONA_MAYOR)).thenReturn(Optional.of(personaMayor));
        when(personaMayorLookupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UsuarioLookup usuario = mock(UsuarioLookup.class);
        when(usuario.getNombreUsuario()).thenReturn("Rosa Díaz");
        when(usuario.getCelular()).thenReturn("+573001110000");
        when(usuarioLookupRepository.findById(PERSONA_MAYOR)).thenReturn(Optional.of(usuario));

        mockMvc = MockMvcBuilders.standaloneSetup(new PersonaMayorInformacionController(
                personaMayorLookupRepository, usuarioLookupRepository)).build();
    }

    @Test
    void elPerfilMuestraLosDatosDeLaPersonaMayor() throws Exception {
        mockMvc.perform(get("/api/persona-mayor/perfil").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Rosa Díaz"))
                .andExpect(jsonPath("$.celular").value("+573001110000"))
                .andExpect(jsonPath("$.fechaNacimiento").value("1950-03-14"))
                .andExpect(jsonPath("$.genero").value("Femenino"))
                .andExpect(jsonPath("$.direccion").value("Calle 91 Sur # 3-20"));
    }

    @Test
    void consultarAQuienNoEsPersonaMayorResponde404() throws Exception {
        when(personaMayorLookupRepository.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/persona-mayor/perfil").header("X-User-Id", 99))
                .andExpect(status().isNotFound());
    }

    @Test
    void actualizarElPerfilGuardaLosCambios() throws Exception {
        mockMvc.perform(put("/api/persona-mayor/perfil")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaNacimiento\": \"1950-03-15\", \"genero\": \"Femenino\","
                                + " \"direccion\": \"Carrera 2 # 88-10 Sur\"}"))
                .andExpect(status().isOk());

        verify(personaMayor).setFechaNacimiento(LocalDate.of(1950, 3, 15));
        verify(personaMayor).setDireccion("Carrera 2 # 88-10 Sur");
        verify(personaMayorLookupRepository).save(personaMayor);
    }

    /**
     * Hallazgo del QC (CP-GUA-009): una fecha mal escrita no se valida y la
     * excepción llega sin manejar (en el servidor es un error 500).
     */
    @Test
    @Disabled("Hallazgo CP-GUA-009: una fecha de nacimiento mal escrita produce un error 500")
    void unaFechaDeNacimientoMalEscritaSeRechazaCon400() throws Exception {
        mockMvc.perform(put("/api/persona-mayor/perfil")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaNacimiento\": \"14/03/1950\", \"genero\": \"Femenino\"}"))
                .andExpect(status().isBadRequest());

        verify(personaMayorLookupRepository, never()).save(any());
    }
}
