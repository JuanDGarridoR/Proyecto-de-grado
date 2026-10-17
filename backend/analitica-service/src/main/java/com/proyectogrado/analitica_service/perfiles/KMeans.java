package com.proyectogrado.analitica_service.perfiles;

import java.util.Arrays;
import java.util.Random;

/**
 * K-means (algoritmo de Lloyd) con inicialización K-means++ y varios
 * reinicios; se queda con el que deja los grupos más compactos (menor
 * inercia). Recibe los datos ya normalizados: una fila por persona, una
 * columna por variable. Usa una semilla fija, así que con los mismos datos
 * siempre da los mismos grupos.
 *
 * Ver docs/KMEANS_PERFILES.md para la explicación del método.
 */
public final class KMeans {

    static final int MAX_ITERACIONES = 300;
    static final double TOLERANCIA = 1e-6;

    private KMeans() {
    }

    /**
     * Resultado de una ejecución: el grupo de cada fila (0..k-1), los
     * centroides, la inercia (suma de distancias al cuadrado de cada punto a
     * su centroide) y las iteraciones del mejor reinicio.
     */
    public record Resultado(int[] etiquetas, double[][] centroides, double inercia, int iteraciones) {
    }

    public static Resultado agrupar(double[][] datos, int k, int reinicios, long semilla) {
        if (k < 1 || k > datos.length) {
            throw new IllegalArgumentException("k debe estar entre 1 y el número de filas");
        }
        Random azar = new Random(semilla);
        Resultado mejor = null;
        for (int r = 0; r < reinicios; r++) {
            Resultado actual = lloyd(datos, iniciarKMeansMasMas(datos, k, azar));
            if (mejor == null || actual.inercia() < mejor.inercia() - 1e-12) {
                mejor = actual;
            }
        }
        return mejor;
    }

    /**
     * K-means++: el primer centroide es un punto al azar; cada siguiente se
     * elige con probabilidad proporcional a su distancia al cuadrado al
     * centroide más cercano, así quedan repartidos y el algoritmo converge
     * mejor que con centroides totalmente al azar.
     */
    static double[][] iniciarKMeansMasMas(double[][] datos, int k, Random azar) {
        int n = datos.length;
        double[][] centroides = new double[k][];
        centroides[0] = datos[azar.nextInt(n)].clone();

        double[] distancias = new double[n];
        Arrays.fill(distancias, Double.MAX_VALUE);
        for (int c = 1; c < k; c++) {
            double total = 0;
            for (int i = 0; i < n; i++) {
                distancias[i] = Math.min(distancias[i], distancia2(datos[i], centroides[c - 1]));
                total += distancias[i];
            }
            int elegido;
            if (total == 0) {
                // Todos los puntos coinciden con algún centroide (datos repetidos)
                elegido = azar.nextInt(n);
            } else {
                double objetivo = azar.nextDouble() * total;
                elegido = n - 1;
                for (int i = 0; i < n; i++) {
                    objetivo -= distancias[i];
                    if (objetivo <= 0) {
                        elegido = i;
                        break;
                    }
                }
            }
            centroides[c] = datos[elegido].clone();
        }
        return centroides;
    }

    /**
     * Lloyd: asigna cada punto a su centroide más cercano y mueve cada
     * centroide al promedio de sus puntos, hasta que los centroides casi no
     * se mueven. Un grupo que queda vacío recibe el punto más lejano de su
     * centroide.
     */
    static Resultado lloyd(double[][] datos, double[][] centroides) {
        int n = datos.length;
        int k = centroides.length;
        int d = datos[0].length;
        int[] etiquetas = new int[n];
        int iteracion = 0;

        while (iteracion < MAX_ITERACIONES) {
            iteracion++;
            for (int i = 0; i < n; i++) {
                etiquetas[i] = masCercano(datos[i], centroides);
            }

            double[][] nuevos = new double[k][d];
            int[] tamanos = new int[k];
            for (int i = 0; i < n; i++) {
                tamanos[etiquetas[i]]++;
                for (int j = 0; j < d; j++) {
                    nuevos[etiquetas[i]][j] += datos[i][j];
                }
            }
            for (int c = 0; c < k; c++) {
                if (tamanos[c] == 0) {
                    int lejano = masLejano(datos, etiquetas, centroides);
                    nuevos[c] = datos[lejano].clone();
                    etiquetas[lejano] = c;
                } else {
                    for (int j = 0; j < d; j++) {
                        nuevos[c][j] /= tamanos[c];
                    }
                }
            }

            double movimiento = 0;
            for (int c = 0; c < k; c++) {
                movimiento = Math.max(movimiento, distancia2(centroides[c], nuevos[c]));
            }
            centroides = nuevos;
            if (movimiento < TOLERANCIA) {
                break;
            }
        }

        for (int i = 0; i < n; i++) {
            etiquetas[i] = masCercano(datos[i], centroides);
        }
        return new Resultado(etiquetas, centroides, inercia(datos, etiquetas, centroides), iteracion);
    }

    /**
     * Coeficiente de silueta promedio (de -1 a 1). Para cada punto compara
     * a = distancia media a los de su grupo con b = distancia media al grupo
     * vecino más cercano: s = (b - a) / max(a, b). Cerca de 1, grupos bien
     * separados; cerca de 0, grupos que se tocan. Un punto solo en su grupo
     * aporta 0.
     */
    public static double silueta(double[][] datos, int[] etiquetas, int k) {
        int n = datos.length;
        if (k < 2 || n <= k) {
            return 0;
        }
        int[] tamanos = new int[k];
        for (int etiqueta : etiquetas) {
            tamanos[etiqueta]++;
        }

        double suma = 0;
        for (int i = 0; i < n; i++) {
            double[] sumaPorGrupo = new double[k];
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    sumaPorGrupo[etiquetas[j]] += Math.sqrt(distancia2(datos[i], datos[j]));
                }
            }
            int propio = etiquetas[i];
            if (tamanos[propio] <= 1) {
                continue;
            }
            double a = sumaPorGrupo[propio] / (tamanos[propio] - 1);
            double b = Double.MAX_VALUE;
            for (int c = 0; c < k; c++) {
                if (c != propio && tamanos[c] > 0) {
                    b = Math.min(b, sumaPorGrupo[c] / tamanos[c]);
                }
            }
            double maximo = Math.max(a, b);
            suma += maximo > 0 ? (b - a) / maximo : 0;
        }
        return suma / n;
    }

    static double inercia(double[][] datos, int[] etiquetas, double[][] centroides) {
        double total = 0;
        for (int i = 0; i < datos.length; i++) {
            total += distancia2(datos[i], centroides[etiquetas[i]]);
        }
        return total;
    }

    /** Distancia euclidiana al cuadrado. */
    static double distancia2(double[] a, double[] b) {
        double suma = 0;
        for (int j = 0; j < a.length; j++) {
            double diferencia = a[j] - b[j];
            suma += diferencia * diferencia;
        }
        return suma;
    }

    private static int masCercano(double[] punto, double[][] centroides) {
        int mejor = 0;
        double menor = Double.MAX_VALUE;
        for (int c = 0; c < centroides.length; c++) {
            double distancia = distancia2(punto, centroides[c]);
            if (distancia < menor) {
                menor = distancia;
                mejor = c;
            }
        }
        return mejor;
    }

    private static int masLejano(double[][] datos, int[] etiquetas, double[][] centroides) {
        int lejano = 0;
        double mayor = -1;
        for (int i = 0; i < datos.length; i++) {
            double distancia = distancia2(datos[i], centroides[etiquetas[i]]);
            if (distancia > mayor) {
                mayor = distancia;
                lejano = i;
            }
        }
        return lejano;
    }
}
