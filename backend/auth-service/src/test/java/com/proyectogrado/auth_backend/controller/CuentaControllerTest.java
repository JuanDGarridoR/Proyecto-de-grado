package com.proyectogrado.auth_backend.controller;

import com.proyectogrado.auth_backend.security.JwtService;
import com.proyectogrado.auth_backend.service.CuentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Eliminación de la cuenta propia. El id sale del token, así que nadie puede
 * borrar la cuenta de otro.
 */
class CuentaControllerTest {

    private CuentaService cuentaService;
    private MockMvc mockMvc;
    private JwtService jwtService;
    private String token;

    @BeforeEach
    void setUp() {
        cuentaService = mock(CuentaService.class);

        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "clave_de_pruebas_de_vita_mas_con_al_menos_32_bytes");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3_600_000L);
        token = jwtService.generarToken(57, "VOLUNTARIO");

        mockMvc = MockMvcBuilders.standaloneSetup(new CuentaController(cuentaService, jwtService)).build();
    }

    private static final String RAZON = """
            {"razon": "FALLECIMIENTO", "comentario": "  Falleció en septiembre.  "}
            """;

    @Test
    void borraLaCuentaDelUsuarioDelTokenConLaRazon() throws Exception {
        mockMvc.perform(delete("/api/auth/cuenta").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(RAZON))
                .andExpect(status().isOk())
                .andExpect(content().string("Cuenta eliminada correctamente"));

        verify(cuentaService).eliminarCuenta(57, "FALLECIMIENTO", "Falleció en septiembre.");
    }

    @Test
    void sinRazonValidaNoSeBorraLaCuenta() throws Exception {
        mockMvc.perform(delete("/api/auth/cuenta").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/auth/cuenta").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"razon\": \"INVENTADA\"}"))
                .andExpect(status().isBadRequest());

        verify(cuentaService, never()).eliminarCuenta(any(), any(), any());
    }

    @Test
    void laOrganizacionUsaSusPropiasRazones() throws Exception {
        String tokenOrganizacion = jwtService.generarToken(30, "ORGANIZACION");

        // "Fallecimiento" es de las personas, no de una organización.
        mockMvc.perform(delete("/api/auth/cuenta").header("Authorization", "Bearer " + tokenOrganizacion)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"razon\": \"FALLECIMIENTO\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/auth/cuenta").header("Authorization", "Bearer " + tokenOrganizacion)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"razon\": \"CIERRE_ORGANIZACION\"}"))
                .andExpect(status().isOk());

        verify(cuentaService).eliminarCuenta(30, "CIERRE_ORGANIZACION", null);
    }

    @Test
    void inactivaYReactivaLaCuentaDelUsuarioDelToken() throws Exception {
        mockMvc.perform(put("/api/auth/cuenta/inactivar").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        verify(cuentaService).inactivarCuenta(57);

        mockMvc.perform(put("/api/auth/cuenta/reactivar").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        verify(cuentaService).reactivarCuenta(57);
    }

    @Test
    void siFallaLaInactivacionRespondeErrorSinDetallesInternos() throws Exception {
        doThrow(new RuntimeException("error de SQL")).when(cuentaService).inactivarCuenta(57);

        mockMvc.perform(put("/api/auth/cuenta/inactivar").header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("No se pudo inactivar la cuenta"));
    }

    @Test
    void siFallaElBorradoRespondeErrorSinDetallesInternos() throws Exception {
        doThrow(new RuntimeException("violación de llave foránea"))
                .when(cuentaService).eliminarCuenta(57, "FALLECIMIENTO", "Falleció en septiembre.");

        mockMvc.perform(delete("/api/auth/cuenta").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(RAZON))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("No se pudo eliminar la cuenta"));
    }
}
