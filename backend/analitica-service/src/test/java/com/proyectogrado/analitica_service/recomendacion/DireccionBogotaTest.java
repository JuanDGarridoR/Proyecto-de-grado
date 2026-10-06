package com.proyectogrado.analitica_service.recomendacion;

import com.proyectogrado.analitica_service.recomendacion.DireccionBogota.Punto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ubicación de direcciones de Bogotá en la cuadrícula de calles y carreras. */
class DireccionBogotaTest {

    private static void assertPunto(double carrera, double calle, String direccion) {
        Punto p = DireccionBogota.ubicar(direccion);
        assertNotNull(p, "No se pudo ubicar: " + direccion);
        assertEquals(carrera, p.x(), 0.001, "carrera de " + direccion);
        assertEquals(calle, p.y(), 0.001, "calle de " + direccion);
    }

    @Test
    void calleSurConCarrera() {
        // Sobre la Calle 91 Sur, a la altura de la Carrera 3 (+15 m)
        assertPunto(3.15, -91, "Calle 91 Sur # 3-15, Usme Centro");
    }

    @Test
    void carreraEsteConCalleSur() {
        // Sobre la Carrera 4 Este, a la altura de la Calle 76 Sur (+20 m)
        assertPunto(-4, -76.2, "Carrera 4 Este # 76-20 Sur, Yomasa");
    }

    @Test
    void diagonalSeTomaComoCalle() {
        assertPunto(-1.45, -68, "Diagonal 68 Sur # 1-45 Este, La Flora");
    }

    @Test
    void transversalSeTomaComoCarrera() {
        assertPunto(12, -60.3, "Transversal 12 # 60-30 Sur");
    }

    @Test
    void aceptaAbreviaturasLetrasYBis() {
        assertPunto(14.3, -73, "Cl 73 Sur No. 14-30");
        assertPunto(14.3, -73, "CALLE 73 SUR N° 14-30");
        assertPunto(-4, -76.2, "Kr 4 Este #76-20 Sur");
        assertPunto(5.6, -89.3, "Calle 89A Sur # 5-60");
        assertPunto(5.1, 80.15, "Av Calle 80 Bis # 5-10");
    }

    @Test
    void norteYOccidenteSonPositivos() {
        assertPunto(7.2, 26, "Calle 26 # 7-20");
    }

    @Test
    void sinNomenclaturaDevuelveNull() {
        assertNull(DireccionBogota.ubicar("Barrio Yomasa, cerca a la iglesia"));
        assertNull(DireccionBogota.ubicar("Vereda Los Soches"));
        assertNull(DireccionBogota.ubicar(null));
        assertNull(DireccionBogota.ubicar(""));
    }

    @Test
    void distanciaEnKilometros() {
        Punto a = DireccionBogota.ubicar("Calle 76 Sur # 4-00");
        Punto b = DireccionBogota.ubicar("Calle 86 Sur # 4-00");
        // 10 cuadras de distancia: alrededor de 1 km
        assertEquals(1.0, DireccionBogota.distanciaKm(a, b), 0.01);
        assertTrue(DireccionBogota.distanciaKm(a, a) < 0.001);
    }
}
