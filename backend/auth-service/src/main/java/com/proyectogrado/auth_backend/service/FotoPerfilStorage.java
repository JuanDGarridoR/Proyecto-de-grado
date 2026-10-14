package com.proyectogrado.auth_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.UUID;

/**
 * Fotos de perfil en Supabase Storage, en el bucket público fotos-perfil.
 * Cada foto queda en {idUsuario}/{uuid}.{extensión}: la URL es pública (los
 * vínculos del usuario la cargan con un simple img), pero no se
 * puede adivinar, y el bucket no se puede listar sin la clave secreta.
 *
 * Se usa la clave secreta del proyecto (supabase.secret-key), que salta las
 * reglas de acceso de Storage; por eso solo vive en el backend. Si falta, el
 * servicio arranca igual y solo falla la subida de fotos, para no tumbar el
 * login de quien aún no la tiene en secretos.properties.
 */
@Service
public class FotoPerfilStorage {

    private static final Logger log = LoggerFactory.getLogger(FotoPerfilStorage.class);

    /** Las fotos ya llegan reducidas desde el navegador; esto es solo un tope. */
    public static final int TAMANO_MAXIMO = 2 * 1024 * 1024;

    private final RestClient restClient;
    private final String urlPublica;
    private final String bucket;
    private final boolean configurado;

    public FotoPerfilStorage(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.secret-key:}") String secretKey,
            @Value("${supabase.bucket-fotos}") String bucket
    ) {
        this.bucket = bucket;
        this.configurado = secretKey != null && !secretKey.isBlank();
        this.urlPublica = supabaseUrl + "/storage/v1/object/public/" + bucket + "/";

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(supabaseUrl + "/storage/v1")
                .defaultHeader("apikey", secretKey);

        // Las claves nuevas (sb_secret_...) bastan en "apikey". La service_role
        // antigua es un JWT y Storage además la pide en Authorization.
        if (configurado && !secretKey.startsWith("sb_")) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey);
        }

        this.restClient = builder.build();
    }

    /** Crea el bucket al arrancar si no existe, para no tener que hacerlo a mano en Supabase. */
    @EventListener(ApplicationReadyEvent.class)
    public void crearBucketSiFalta() {
        if (!configurado) {
            log.warn("supabase.secret-key no está definida: no se podrán subir fotos de perfil");
            return;
        }

        try {
            restClient.get().uri("/bucket/{id}", bucket).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            // Storage responde 400 o 404 cuando el bucket no existe.
            if (e.getStatusCode().value() != 400 && e.getStatusCode().value() != 404) {
                log.warn("No se pudo revisar el bucket {}: {}", bucket, e.getMessage());
                return;
            }

            try {
                restClient.post()
                        .uri("/bucket")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "id", bucket,
                                "name", bucket,
                                "public", true,
                                "file_size_limit", TAMANO_MAXIMO,
                                "allowed_mime_types", new String[] {"image/jpeg", "image/png", "image/webp"}
                        ))
                        .retrieve()
                        .toBodilessEntity();
                log.info("Bucket {} creado en Supabase Storage", bucket);
            } catch (Exception creacion) {
                log.warn("No se pudo crear el bucket {}: {}", bucket, creacion.getMessage());
            }
        } catch (Exception e) {
            log.warn("No se pudo revisar el bucket {}: {}", bucket, e.getMessage());
        }
    }

    public boolean estaConfigurado() {
        return configurado;
    }

    /**
     * Tipo de imagen según sus primeros bytes (JPEG, PNG o WebP), o null si
     * no es ninguno. No se confía en la extensión ni en el tipo que declara
     * el navegador.
     */
    public static MediaType tipoDeImagen(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return MediaType.IMAGE_JPEG;
        }

        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return MediaType.IMAGE_PNG;
        }

        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return MediaType.parseMediaType("image/webp");
        }

        return null;
    }

    /** Sube la foto y devuelve su URL pública. Lanza RuntimeException si Storage falla. */
    public String subir(Integer idUsuario, byte[] bytes, MediaType tipo) {
        String archivo = UUID.randomUUID() + "." + tipo.getSubtype();

        // Carpeta y archivo van como variables separadas: dentro de una sola
        // variable, el "/" se enviaría codificado como %2F.
        restClient.post()
                .uri("/object/{bucket}/{carpeta}/{archivo}", bucket, idUsuario, archivo)
                .contentType(tipo)
                .header("cache-control", "max-age=31536000")
                .body(bytes)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new RuntimeException("Supabase Storage respondió " + response.getStatusCode());
                })
                .toBodilessEntity();

        return urlPublica + idUsuario + "/" + archivo;
    }

    /**
     * Borra la foto anterior. Si falla solo queda un archivo huérfano en el
     * bucket, así que no se interrumpe al usuario.
     */
    public void borrar(String url) {
        if (url == null || !url.startsWith(urlPublica)) {
            return;
        }

        String ruta = url.substring(urlPublica.length());
        String[] partes = ruta.split("/");
        if (partes.length != 2) {
            return;
        }

        try {
            restClient.delete()
                    .uri("/object/{bucket}/{carpeta}/{archivo}", bucket, partes[0], partes[1])
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("No se pudo borrar la foto {}: {}", ruta, e.getMessage());
        }
    }
}
