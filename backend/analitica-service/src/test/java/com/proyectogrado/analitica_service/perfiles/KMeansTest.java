package com.proyectogrado.analitica_service.perfiles;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * K-means, silueta y PCA con datos sintéticos de resultado conocido:
 * nubes de puntos bien separadas.
 */
class KMeansTest {

    /** Tres nubes de 10 puntos alrededor de (0,0), (10,0) y (0,10). */
    private static double[][] tresNubes() {
        Random azar = new Random(1);
        double[][] centros = {{0, 0}, {10, 0}, {0, 10}};
        double[][] datos = new double[30][];
        for (int i = 0; i < 30; i++) {
            double[] c = centros[i / 10];
            datos[i] = new double[]{c[0] + azar.nextGaussian() * 0.5, c[1] + azar.nextGaussian() * 0.5};
        }
        return datos;
    }

    @Test
    void separaNubesBienDiferenciadas() {
        double[][] datos = tresNubes();
        KMeans.Resultado resultado = KMeans.agrupar(datos, 3, 10, 42);

        // Los 10 puntos de cada nube quedan juntos y en grupos distintos
        int[] e = resultado.etiquetas();
        for (int nube = 0; nube < 3; nube++) {
            for (int i = nube * 10; i < nube * 10 + 10; i++) {
                assertEquals(e[nube * 10], e[i]);
            }
        }
        assertEquals(3, Arrays.stream(e).distinct().count());
    }

    @Test
    void conLaMismaSemillaDaSiempreLosMismosGrupos() {
        double[][] datos = tresNubes();
        KMeans.Resultado a = KMeans.agrupar(datos, 3, 10, 42);
        KMeans.Resultado b = KMeans.agrupar(datos, 3, 10, 42);
        assertArrayEquals(a.etiquetas(), b.etiquetas());
        assertEquals(a.inercia(), b.inercia(), 1e-12);
    }

    @Test
    void laSiluetaPrefiereElNumeroRealDeGrupos() {
        double[][] datos = tresNubes();
        double conDos = KMeans.silueta(datos, KMeans.agrupar(datos, 2, 10, 42).etiquetas(), 2);
        double conTres = KMeans.silueta(datos, KMeans.agrupar(datos, 3, 10, 42).etiquetas(), 3);
        double conCuatro = KMeans.silueta(datos, KMeans.agrupar(datos, 4, 10, 42).etiquetas(), 4);

        assertTrue(conTres > 0.8, "silueta con 3 grupos: " + conTres);
        assertTrue(conTres > conDos);
        assertTrue(conTres > conCuatro);
    }

    @Test
    void laInerciaBajaAlAumentarK() {
        double[][] datos = tresNubes();
        double anterior = Double.MAX_VALUE;
        for (int k = 1; k <= 5; k++) {
            double inercia = KMeans.agrupar(datos, k, 10, 42).inercia();
            assertTrue(inercia <= anterior + 1e-9);
            anterior = inercia;
        }
    }

    @Test
    void soportaPuntosRepetidosSinGruposVaciosNiNaN() {
        double[][] datos = new double[8][];
        for (int i = 0; i < 8; i++) {
            datos[i] = i < 6 ? new double[]{1, 1} : new double[]{5, 5};
        }
        KMeans.Resultado resultado = KMeans.agrupar(datos, 3, 5, 42);

        assertFalse(Double.isNaN(resultado.inercia()));
        for (double[] centroide : resultado.centroides()) {
            assertFalse(Double.isNaN(centroide[0]));
        }
    }

    @Test
    void elPrimerEjeDelPcaSigueLaDireccionDeMayorVarianza() {
        // Puntos sobre la diagonal y = x con poco ruido: el primer eje explica casi todo
        Random azar = new Random(3);
        double[][] datos = new double[40][];
        for (int i = 0; i < 40; i++) {
            double t = azar.nextGaussian() * 3;
            datos[i] = new double[]{t, t + azar.nextGaussian() * 0.1};
        }
        Pca.Proyeccion proyeccion = Pca.proyectar(datos);

        assertTrue(proyeccion.varianzaExplicada()[0] > 0.99);
        assertEquals(1.0, proyeccion.varianzaExplicada()[0] + proyeccion.varianzaExplicada()[1], 1e-6);
    }
}
