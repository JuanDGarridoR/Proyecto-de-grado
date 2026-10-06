package com.proyectogrado.analitica_service.recomendacion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Barrios en direcciones y coincidencia entre gustos y actividades. */
class RecomendacionTest {

    @Test
    void detectaElBarrioAlFinalDeLaDireccion() {
        assertEquals("Yomasa", Barrios.detectar("Carrera 4 Este # 76-20 Sur, Yomasa").nombre());
        assertEquals("Santa Librada", Barrios.detectar("calle 73 sur # 14-30, santa librada").nombre());
        assertEquals("Alfonso López", Barrios.detectar("Calle 1 # 2-3, Alfonso Lopez").nombre());
    }

    @Test
    void prefiereElNombreMasLargo() {
        assertEquals("Gran Yomasa", Barrios.detectar("Calle 80 Sur, Gran Yomasa").nombre());
        assertEquals("Danubio Azul", Barrios.detectar("Calle 1, Danubio Azul").nombre());
    }

    @Test
    void sinBarrioConocidoDevuelveNull() {
        assertNull(Barrios.detectar("Calle 100 # 15-20, Chicó"));
        assertNull(Barrios.detectar(null));
        assertNull(Barrios.detectar(""));
    }

    @Test
    void distanciaEntreBarriosEsRazonable() {
        Barrios.Barrio yomasa = Barrios.detectar("Yomasa");
        Barrios.Barrio kennedy = Barrios.detectar("Kennedy");
        assertNotNull(yomasa);
        assertNotNull(kennedy);
        assertEquals(0, Barrios.distanciaKm(yomasa, yomasa), 0.001);
        double km = Barrios.distanciaKm(yomasa, kennedy);
        assertTrue(km > 10 && km < 16, "Yomasa-Kennedy debería estar a unos 13 km, dio " + km);
    }

    @Test
    void coincidePorRaizDeLaPalabra() {
        assertTrue(CoincidenciaGustos.coincide("Caminatas", "Caminata ecológica Deportiva"));
        assertTrue(CoincidenciaGustos.coincide("Bingo", "Bingo comunitario Social"));
        assertTrue(CoincidenciaGustos.coincide("Lectura", "Grupo de lectura Educativa"));
        assertTrue(CoincidenciaGustos.coincide("Juegos de mesa", "Tarde de juegos de mesa Social"));
        assertTrue(CoincidenciaGustos.coincide("Baile", "Noche de música y baile Recreativa"));
    }

    @Test
    void coincidePorEquivalencias() {
        assertTrue(CoincidenciaGustos.coincide("Jardinería", "Huerta comunitaria Recreativa"));
        assertTrue(CoincidenciaGustos.coincide("Tejido", "Taller de manualidades Manualidades"));
        assertTrue(CoincidenciaGustos.coincide("Películas clásicas", "Cine foro Recreativa"));
        assertTrue(CoincidenciaGustos.coincide("Boleros", "Noche de música y baile"));
        assertTrue(CoincidenciaGustos.coincide("Baile", "Rumba terapia Deportiva"));
    }

    @Test
    void noCoincideSinRelacion() {
        assertFalse(CoincidenciaGustos.coincide("Bingo", "Clase de yoga suave Deportiva"));
        assertFalse(CoincidenciaGustos.coincide("Jardinería", "Taller de uso del celular Educativa"));
        assertFalse(CoincidenciaGustos.coincide("Tomar café con amigos", "Jornada de vacunación Salud y bienestar"));
        assertFalse(CoincidenciaGustos.coincide(null, "Bingo"));
    }
}
