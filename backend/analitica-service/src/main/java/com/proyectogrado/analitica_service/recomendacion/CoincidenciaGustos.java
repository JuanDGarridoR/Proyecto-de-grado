package com.proyectogrado.analitica_service.recomendacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decide si una actividad corresponde a un gusto de la persona mayor.
 *
 * Compara las palabras del gusto con las de la actividad (nombre, tipo y
 * descripción) por su raíz (las primeras 5 letras: "Caminatas" coincide con
 * "Caminata ecológica"). Además usa equivalencias para gustos que no
 * comparten palabras con sus actividades ("Jardinería" con "Huerta").
 */
public final class CoincidenciaGustos {

    // Palabras que no dicen nada del gusto
    private static final Set<String> VACIAS = Set.of(
            "de", "del", "la", "las", "el", "los", "con", "por", "para", "y", "en", "un", "una",
            "tomar", "amigos", "television", "clasicas", "clasicos");

    // Gusto (normalizado) -> palabras de actividades que le corresponden
    private static final Map<String, List<String>> EQUIVALENCIAS = Map.ofEntries(
            Map.entry("jardineria", List.of("huerta", "jardin", "siembra", "plantas")),
            Map.entry("tejido", List.of("manualidades", "tejer", "crochet")),
            Map.entry("bordado", List.of("manualidades", "bordar")),
            Map.entry("costura", List.of("manualidades", "coser")),
            Map.entry("carpinteria", List.of("manualidades", "madera")),
            Map.entry("pintura", List.of("dibujo", "arte", "pintar")),
            Map.entry("canto", List.of("musica", "coro", "cantar")),
            Map.entry("boleros", List.of("musica")),
            Map.entry("musica ranchera", List.of("musica")),
            Map.entry("musica carranguera", List.of("musica")),
            Map.entry("baile", List.of("rumba", "bailar", "danza")),
            Map.entry("peliculas clasicas", List.of("cine", "pelicula")),
            Map.entry("novelas de television", List.of("cine")),
            Map.entry("contar historias", List.of("cuentos", "recuerdos", "historias")),
            Map.entry("crucigramas", List.of("memoria", "juegos")),
            Map.entry("parques", List.of("juegos", "mesa")),
            Map.entry("misa dominical", List.of("espiritual", "misa", "oracion")),
            Map.entry("tomar cafe con amigos", List.of("social", "encuentro", "tertulia")),
            Map.entry("cocina tradicional", List.of("cocina", "culinaria")),
            Map.entry("reposteria", List.of("cocina", "reposteria")),
            Map.entry("caminatas", List.of("paseo", "caminata"))
    );

    private CoincidenciaGustos() {
    }

    /** true si el texto de la actividad corresponde al gusto. */
    public static boolean coincide(String gusto, String textoActividad) {
        if (gusto == null || textoActividad == null) {
            return false;
        }
        List<String> palabrasActividad = palabras(textoActividad);
        for (String clave : clavesDe(gusto)) {
            for (String palabra : palabrasActividad) {
                if (raiz(palabra).equals(raiz(clave))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Palabras del gusto y sus equivalencias. */
    private static List<String> clavesDe(String gusto) {
        String normalizado = Barrios.normalizar(gusto);
        List<String> claves = new ArrayList<>(palabras(normalizado));
        claves.addAll(EQUIVALENCIAS.getOrDefault(normalizado, List.of()));
        return claves;
    }

    private static List<String> palabras(String texto) {
        List<String> lista = new ArrayList<>();
        for (String palabra : Barrios.normalizar(texto).split("[^a-z0-9]+")) {
            if (palabra.length() >= 4 && !VACIAS.contains(palabra)) {
                lista.add(palabra);
            }
        }
        return lista;
    }

    /** Raíz aproximada: las primeras 5 letras ("caminatas" y "caminata" -> "camin"). */
    private static String raiz(String palabra) {
        return palabra.length() <= 5 ? palabra : palabra.substring(0, 5);
    }
}
