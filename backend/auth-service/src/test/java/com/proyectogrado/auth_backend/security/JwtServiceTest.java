package com.proyectogrado.auth_backend.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tokens de sesión (RNF-09): llevan el id y el rol del usuario, vencen y no
 * se pueden falsificar sin la clave.
 */
class JwtServiceTest {

    private static final String CLAVE = "clave_de_pruebas_de_vita_mas_con_al_menos_32_bytes";

    private JwtService jwtService(String clave, long duracionMs) {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", clave);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", duracionMs);
        return jwtService;
    }

    @Test
    void elTokenLlevaElIdYElRolDelUsuario() {
        JwtService jwtService = jwtService(CLAVE, 60_000);

        String token = jwtService.generarToken(31, "ORGANIZACION");

        assertEquals(31, jwtService.extraerIdUsuario(token));
        assertEquals("ORGANIZACION", jwtService.extraerRol(token));
    }

    @Test
    void elTokenSoloEsValidoParaSuPropioUsuario() {
        JwtService jwtService = jwtService(CLAVE, 60_000);

        String token = jwtService.generarToken(31, "ORGANIZACION");

        assertTrue(jwtService.esTokenValido(token, 31));
        assertFalse(jwtService.esTokenValido(token, 32));
    }

    @Test
    void unTokenVencidoSeRechaza() {
        JwtService jwtService = jwtService(CLAVE, -1_000);

        String token = jwtService.generarToken(31, "ORGANIZACION");

        assertThrows(ExpiredJwtException.class, () -> jwtService.extraerIdUsuario(token));
    }

    @Test
    void unTokenFirmadoConOtraClaveSeRechaza() {
        String falso = jwtService("otra_clave_que_no_es_la_de_vita_mas_1234567890", 60_000)
                .generarToken(1, "ORGANIZACION");

        assertThrows(SignatureException.class, () -> jwtService(CLAVE, 60_000).extraerIdUsuario(falso));
    }

    @Test
    void unTokenAlteradoSeRechaza() {
        JwtService jwtService = jwtService(CLAVE, 60_000);
        String token = jwtService.generarToken(31, "PERSONA_MAYOR");

        // Se cambia el primer carácter de la firma (el último puede ser solo
        // relleno de base64 y no alterarla).
        int inicioFirma = token.lastIndexOf('.') + 1;
        char original = token.charAt(inicioFirma);
        String alterado = token.substring(0, inicioFirma) + (original == 'A' ? 'B' : 'A')
                + token.substring(inicioFirma + 1);

        assertThrows(SignatureException.class, () -> jwtService.extraerIdUsuario(alterado));
    }
}
