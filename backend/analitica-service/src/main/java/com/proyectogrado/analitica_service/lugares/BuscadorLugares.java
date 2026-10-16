package com.proyectogrado.analitica_service.lugares;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Busca lugares por nombre o dirección con Google Places (Text Search)
 * para UbicadorAutomatico:
 * "Parque La Flora", "Salón comunal Yomasa" o "Calle 91 Sur # 3-15"
 * devuelven los sitios que coinciden con sus coordenadas, igual que el
 * buscador de Google Maps. Así nadie tiene que escribir coordenadas.
 *
 * La búsqueda se limita a Bogotá (con Usme en el centro del sesgo), porque
 * ahí están las organizaciones; sin eso "Parque La Flora" también trae los
 * de Cali o Bucaramanga.
 *
 * La clave va en google.places.api-key (GOOGLE_MAPS_API_KEY en
 * secretos.properties o en el .env del servidor). Es opcional: sin ella el
 * servicio arranca igual y los mapas se quedan sin los puntos que falten.
 *
 * Las respuestas se guardan en memoria para no gastar cupo de Google
 * repitiendo la misma búsqueda.
 */
@Service
public class BuscadorLugares {

    private static final Logger log = LoggerFactory.getLogger(BuscadorLugares.class);

    /** Lo que se le pide a Google de cada lugar (Google cobra según estos campos). */
    private static final String CAMPOS =
            "places.displayName,places.formattedAddress,places.location,places.addressComponents";

    private static final int MAX_RESULTADOS = 5;
    private static final int MAX_EN_MEMORIA = 500;

    /** Bogotá completa, incluida la zona rural de Usme. */
    private static final Rectangulo BOGOTA = new Rectangulo(
            new LatLng(4.20, -74.25),
            new LatLng(4.85, -73.99));

    private final String apiKey;
    private final RestClient google;

    /** Búsquedas recientes (texto normalizado -> resultados); se borra la más vieja al llenarse. */
    private final Map<String, List<LugarEncontrado>> enMemoria =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<LugarEncontrado>> masVieja) {
                    return size() > MAX_EN_MEMORIA;
                }
            };

    public BuscadorLugares(@Value("${google.places.api-key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();

        JdkClientHttpRequestFactory conexion = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        conexion.setReadTimeout(Duration.ofSeconds(10));

        this.google = RestClient.builder()
                .baseUrl("https://places.googleapis.com/v1")
                .requestFactory(conexion)
                .build();

        if (this.apiKey.isEmpty()) {
            log.warn("google.places.api-key no está definida: la búsqueda de lugares queda desactivada.");
        }
    }

    /** true si hay clave de Google; sin ella no se puede buscar. */
    public boolean disponible() {
        return !apiKey.isEmpty();
    }

    /**
     * Lugares de Bogotá que coinciden con el texto, del más al menos
     * relevante (máximo 5). Lista vacía si Google no encuentra nada.
     *
     * @throws IllegalStateException si no hay clave configurada.
     * @throws RestClientException si Google no responde o responde con error.
     */
    public List<LugarEncontrado> buscar(String texto) {
        if (!disponible()) {
            throw new IllegalStateException("La búsqueda de lugares no está configurada");
        }

        String clave = texto.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        synchronized (enMemoria) {
            List<LugarEncontrado> guardados = enMemoria.get(clave);
            if (guardados != null) {
                return guardados;
            }
        }

        RespuestaGoogle respuesta = google.post()
                .uri("/places:searchText")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", CAMPOS)
                .body(new PeticionGoogle(texto.trim(), "es", "co", MAX_RESULTADOS, new Restriccion(BOGOTA)))
                .retrieve()
                .body(RespuestaGoogle.class);

        List<LugarEncontrado> lugares = respuesta == null || respuesta.places() == null
                ? List.of()
                : respuesta.places().stream()
                        .filter(lugar -> lugar.location() != null)
                        .map(lugar -> new LugarEncontrado(
                                lugar.displayName() != null ? lugar.displayName().text() : null,
                                lugar.formattedAddress(),
                                lugar.location().latitude(),
                                lugar.location().longitude(),
                                barrioDe(lugar)))
                        .toList();

        synchronized (enMemoria) {
            enMemoria.put(clave, lugares);
        }

        return lugares;
    }

    /**
     * El barrio que Google le asigna al lugar (componente "neighborhood"), o
     * null: en Bogotá a veces solo da la localidad ("Usme"), que no sirve.
     */
    private static String barrioDe(LugarGoogle lugar) {
        if (lugar.addressComponents() == null) {
            return null;
        }
        return lugar.addressComponents().stream()
                .filter(c -> c.types() != null && c.types().contains("neighborhood"))
                .map(ComponenteDireccion::longText)
                .findFirst()
                .orElse(null);
    }

    // ---------- Formato de la API de Google Places (Text Search) ----------

    record LatLng(double latitude, double longitude) {
    }

    record Rectangulo(LatLng low, LatLng high) {
    }

    record Restriccion(Rectangulo rectangle) {
    }

    record PeticionGoogle(String textQuery, String languageCode, String regionCode,
                          int pageSize, Restriccion locationRestriction) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Texto(String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ComponenteDireccion(String longText, List<String> types) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LugarGoogle(Texto displayName, String formattedAddress, LatLng location,
                       List<ComponenteDireccion> addressComponents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaGoogle(List<LugarGoogle> places) {
    }
}
