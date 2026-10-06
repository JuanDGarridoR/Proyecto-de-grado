package com.proyectogrado.analitica_service.recomendacion;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Barrios de Usme (UPL Entrenubes) y alrededores con coordenadas
 * APROXIMADAS de su centro. Sirven para estimar la distancia entre una
 * persona mayor y una organización a partir del barrio que aparece en su
 * dirección (p. ej. "Calle 76 Sur # 4-20, Yomasa"). No es una distancia
 * exacta: es la distancia entre los centros de los dos barrios.
 */
public final class Barrios {

    /** Barrio reconocido en una dirección. */
    public record Barrio(String nombre, double latitud, double longitud) {
    }

    private static final Map<String, Barrio> BARRIOS = new LinkedHashMap<>();

    static {
        agregar("Usme Centro", 4.4805, -74.1255);
        agregar("La Flora", 4.5280, -74.0850);
        agregar("Yomasa", 4.5180, -74.1075);
        agregar("Gran Yomasa", 4.5180, -74.1075);
        agregar("Danubio Azul", 4.5335, -74.1230);
        agregar("Danubio", 4.5335, -74.1230);
        agregar("Santa Librada", 4.5285, -74.1150);
        agregar("Los Soches", 4.5050, -74.0790);
        agregar("El Uval", 4.4930, -74.1110);
        agregar("Comuneros", 4.5185, -74.1265);
        agregar("Alfonso López", 4.5065, -74.1175);
        agregar("La Aurora", 4.5205, -74.1145);
        agregar("Arrayanes", 4.5015, -74.1240);
        agregar("El Destino", 4.4300, -74.1350);
        agregar("Tocaimita", 4.4905, -74.1050);
        agregar("La Requilina", 4.4855, -74.1095);
        agregar("Marichuela", 4.5100, -74.1220);
        agregar("Chuniza", 4.5110, -74.1160);
        agregar("Bolonia", 4.5225, -74.1040);
        agregar("Monteblanco", 4.4975, -74.1295);
        agregar("Betania", 4.4985, -74.1060);
        agregar("Juan José Rondón", 4.5260, -74.0920);
        agregar("Tunjuelito", 4.5760, -74.1320);
        agregar("Kennedy", 4.6290, -74.1540);
    }

    private Barrios() {
    }

    private static void agregar(String nombre, double latitud, double longitud) {
        BARRIOS.put(normalizar(nombre), new Barrio(nombre, latitud, longitud));
    }

    /**
     * Busca un barrio conocido dentro de la dirección. Si varios coinciden,
     * gana el nombre más largo ("Gran Yomasa" antes que "Yomasa").
     * Devuelve null si no reconoce ninguno.
     */
    public static Barrio detectar(String direccion) {
        if (direccion == null || direccion.isBlank()) {
            return null;
        }
        String texto = " " + normalizar(direccion).replaceAll("[^a-z0-9]+", " ") + " ";

        Barrio encontrado = null;
        int largo = 0;
        for (Map.Entry<String, Barrio> entrada : BARRIOS.entrySet()) {
            String clave = entrada.getKey();
            if (texto.contains(" " + clave + " ") && clave.length() > largo) {
                encontrado = entrada.getValue();
                largo = clave.length();
            }
        }
        return encontrado;
    }

    /** Distancia en km entre los centros de dos barrios (fórmula de Haversine). */
    public static double distanciaKm(Barrio a, Barrio b) {
        double radioTierra = 6371;
        double dLat = Math.toRadians(b.latitud() - a.latitud());
        double dLon = Math.toRadians(b.longitud() - a.longitud());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.latitud())) * Math.cos(Math.toRadians(b.latitud()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * radioTierra * Math.asin(Math.sqrt(h));
    }

    /** Minúsculas, sin tildes y sin espacios repetidos. */
    static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("\\s+", " ")
                .trim();
    }
}
