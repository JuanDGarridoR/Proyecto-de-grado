package com.proyectogrado.auth_backend.service;

import com.proyectogrado.auth_backend.model.Sesion;
import com.proyectogrado.auth_backend.repository.SesionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sesiones revocables contra una base H2 en memoria: el token de renovación
 * solo se guarda como hash, sirve mientras la sesión esté abierta y deja de
 * servir al cerrarla, al revocarla o al vencer.
 */
@DataJpaTest
@Import(SesionService.class)
class SesionServiceTest {

    @Autowired
    private SesionService sesionService;

    @Autowired
    private SesionRepository sesionRepository;

    @Test
    void elTokenDeRenovacionSoloSeGuardaComoHashYSirveParaSaberDeQuienEsLaSesion() {
        String token = sesionService.abrir(25);

        Sesion guardada = sesionRepository.findAll().get(0);
        assertNotEquals(token, guardada.getHashToken());
        assertEquals(64, guardada.getHashToken().length());
        assertEquals(Optional.of(25), sesionService.usuarioDe(token));
    }

    @Test
    void cadaInicioDeSesionTieneSuPropioToken() {
        String enUnaPestana = sesionService.abrir(25);
        String enOtra = sesionService.abrir(25);

        assertNotEquals(enUnaPestana, enOtra);
        assertEquals(Optional.of(25), sesionService.usuarioDe(enUnaPestana));
        assertEquals(Optional.of(25), sesionService.usuarioDe(enOtra));
    }

    @Test
    void unTokenInventadoOVacioNoSirve() {
        sesionService.abrir(25);

        assertFalse(sesionService.usuarioDe("token-inventado").isPresent());
        assertFalse(sesionService.usuarioDe("").isPresent());
        assertFalse(sesionService.usuarioDe(null).isPresent());
    }

    @Test
    void alCerrarLaSesionSuTokenDejaDeServir() {
        String token = sesionService.abrir(25);

        sesionService.cerrar(token);

        assertFalse(sesionService.usuarioDe(token).isPresent());
    }

    @Test
    void cerrarTodasMenosUnaDejaSoloEsaYNoTocaLasDeOtraPersona() {
        String estaPestana = sesionService.abrir(25);
        String otroDispositivo = sesionService.abrir(25);
        String deOtraPersona = sesionService.abrir(30);

        sesionService.cerrarTodasMenos(25, estaPestana);

        assertTrue(sesionService.usuarioDe(estaPestana).isPresent());
        assertFalse(sesionService.usuarioDe(otroDispositivo).isPresent());
        assertTrue(sesionService.usuarioDe(deOtraPersona).isPresent());
    }

    @Test
    void cerrarTodasLasCierraTodas() {
        String una = sesionService.abrir(25);
        String otra = sesionService.abrir(25);

        sesionService.cerrarTodas(25);

        assertFalse(sesionService.usuarioDe(una).isPresent());
        assertFalse(sesionService.usuarioDe(otra).isPresent());
    }

    @Test
    void unaSesionVencidaNoSirveYSeBorra() {
        String token = sesionService.abrir(25);
        Sesion sesion = sesionRepository.findAll().get(0);
        sesion.setExpira(LocalDateTime.now(ZoneId.of("America/Bogota")).minusMinutes(1));
        sesionRepository.saveAndFlush(sesion);

        assertFalse(sesionService.usuarioDe(token).isPresent());
        assertEquals(0, sesionRepository.count());
    }

    @Test
    void usarLaSesionCorreSuVencimiento() {
        String token = sesionService.abrir(25);
        Sesion sesion = sesionRepository.findAll().get(0);
        LocalDateTime casiVencida = LocalDateTime.now(ZoneId.of("America/Bogota")).plusMinutes(5);
        sesion.setExpira(casiVencida);
        sesionRepository.saveAndFlush(sesion);

        sesionService.usuarioDe(token);

        assertTrue(sesionRepository.findAll().get(0).getExpira().isAfter(casiVencida.plusDays(29)));
    }
}
