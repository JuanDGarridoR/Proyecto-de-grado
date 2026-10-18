package com.proyectogrado.auth_backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Con la configuración de seguridad real: sin token, o con uno vencido,
 * auth-service responde 401 (no 403), que es lo que le dice al frontend
 * que renueve el token. Renovar y cerrar la sesión no piden token.
 */
@SpringBootTest
class SinTokenValidoTest {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private JwtService jwtService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    @Test
    void sinTokenUnaRutaProtegidaResponde401() throws Exception {
        mockMvc().perform(get("/api/auth/informacion"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conUnTokenVencidoUnaRutaProtegidaResponde401() throws Exception {
        JwtService vencido = new JwtService();
        ReflectionTestUtils.setField(vencido, "jwtSecret", ReflectionTestUtils.getField(jwtService, "jwtSecret"));
        ReflectionTestUtils.setField(vencido, "jwtExpiration", -60_000L);
        String token = vencido.generarToken(25, "PERSONA_MAYOR");

        mockMvc().perform(get("/api/auth/informacion").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void renovarLaSesionNoPideTokenDeAcceso() throws Exception {
        // La sesión no existe: responde 401 el controlador, no Spring Security.
        mockMvc().perform(post("/api/auth/sesion/renovar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"no-existe\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc().perform(post("/api/auth/sesion/cerrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"no-existe\"}"))
                .andExpect(status().isNoContent());
    }
}
