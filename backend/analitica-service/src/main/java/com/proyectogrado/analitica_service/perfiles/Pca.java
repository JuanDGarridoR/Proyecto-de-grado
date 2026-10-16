package com.proyectogrado.analitica_service.perfiles;

import java.util.Random;

/**
 * Análisis de componentes principales a 2 dimensiones, solo para dibujar
 * los grupos en un plano (K-means agrupa con todas las variables, no con
 * estas dos). Calcula los dos vectores propios principales de la matriz de
 * covarianza con el método de la potencia y deflación.
 */
public final class Pca {

    private static final int ITERACIONES = 500;

    private Pca() {
    }

    /** Coordenadas (x, y) de cada fila y qué parte de la varianza total explica cada eje (0 a 1). */
    public record Proyeccion(double[][] coordenadas, double[] varianzaExplicada) {
    }

    public static Proyeccion proyectar(double[][] datos) {
        int n = datos.length;
        int d = datos[0].length;

        double[] medias = new double[d];
        for (double[] fila : datos) {
            for (int j = 0; j < d; j++) {
                medias[j] += fila[j] / n;
            }
        }

        double[][] covarianza = new double[d][d];
        for (double[] fila : datos) {
            for (int a = 0; a < d; a++) {
                for (int b = 0; b < d; b++) {
                    covarianza[a][b] += (fila[a] - medias[a]) * (fila[b] - medias[b]) / Math.max(1, n - 1);
                }
            }
        }

        double total = 0;
        for (int j = 0; j < d; j++) {
            total += covarianza[j][j];
        }

        int componentes = Math.min(2, d);
        double[][] vectores = new double[2][d];
        double[] valores = new double[2];
        Random azar = new Random(7);
        for (int c = 0; c < componentes; c++) {
            double[] v = new double[d];
            for (int j = 0; j < d; j++) {
                v[j] = azar.nextDouble() - 0.5;
            }
            normalizar(v);
            for (int it = 0; it < ITERACIONES; it++) {
                double[] siguiente = multiplicar(covarianza, v);
                if (normalizar(siguiente) == 0) {
                    break;
                }
                v = siguiente;
            }
            double valor = productoPunto(v, multiplicar(covarianza, v));
            vectores[c] = v;
            valores[c] = Math.max(0, valor);
            // Deflación: se quita esta componente para encontrar la siguiente
            for (int a = 0; a < d; a++) {
                for (int b = 0; b < d; b++) {
                    covarianza[a][b] -= valor * v[a] * v[b];
                }
            }
        }

        double[][] coordenadas = new double[n][2];
        for (int i = 0; i < n; i++) {
            for (int c = 0; c < componentes; c++) {
                double suma = 0;
                for (int j = 0; j < d; j++) {
                    suma += (datos[i][j] - medias[j]) * vectores[c][j];
                }
                coordenadas[i][c] = suma;
            }
        }

        double[] explicada = new double[2];
        for (int c = 0; c < 2; c++) {
            explicada[c] = total > 0 ? valores[c] / total : 0;
        }
        return new Proyeccion(coordenadas, explicada);
    }

    private static double[] multiplicar(double[][] matriz, double[] v) {
        double[] resultado = new double[v.length];
        for (int a = 0; a < v.length; a++) {
            resultado[a] = productoPunto(matriz[a], v);
        }
        return resultado;
    }

    private static double productoPunto(double[] a, double[] b) {
        double suma = 0;
        for (int j = 0; j < a.length; j++) {
            suma += a[j] * b[j];
        }
        return suma;
    }

    /** Deja el vector con norma 1 y devuelve la norma que tenía. */
    private static double normalizar(double[] v) {
        double norma = Math.sqrt(productoPunto(v, v));
        if (norma > 0) {
            for (int j = 0; j < v.length; j++) {
                v[j] /= norma;
            }
        }
        return norma;
    }
}
