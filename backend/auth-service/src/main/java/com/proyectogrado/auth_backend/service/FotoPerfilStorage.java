package com.proyectogrado.auth_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.http.ResponseEntity;
import com.proyectogrado.auth_backend.repository.UsuarioRepository;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;

/**
 * Fotos de perfil en Supabase Storage, en el bucket privado fotos-perfil.
 * Cada foto queda en {idUsuario}/{uuid}.{extensión}, y esa ruta es lo que se
 * guarda en usuario.foto_perfil. El bucket es privado: la foto no se puede
 * abrir con un enlace de Supabase, solo a través de auth-service
 * (GET /api/auth/fotos/..., con sesión iniciada; ver FotoController).
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

    /** Ruta válida de una foto: {idUsuario}/{uuid}.{jpeg|png|webp}. Evita pedir otra cosa del bucket. */
    private static final Pattern RUTA = Pattern.compile("^(\\d+)/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpeg|png|webp))$");

    /** Foto descargada del bucket. */
    public record Foto(byte[] bytes, MediaType tipo) {
    }

    private final RestClient restClient;
    private final String urlPublica;
    private final String bucket;
    private final boolean configurado;
    private final UsuarioRepository usuarioRepository;

    public FotoPerfilStorage(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.secret-key:}") String secretKey,
            @Value("${supabase.bucket-fotos}") String bucket,
            UsuarioRepository usuarioRepository
    ) {
        this.bucket = bucket;
        this.usuarioRepository = usuarioRepository;
        this.configurado = secretKey != null && !secretKey.isBlank();
        this.urlPublica = supabaseUrl + "/storage/v1/object/public/" + bucket + "/";

        // Si Supabase Storage no responde, la subida falla en vez de dejar el
        // hilo esperando para siempre (las fotos pesan hasta 2 MB: 30 s alcanzan).
        JdkClientHttpRequestFactory conexion = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        conexion.setReadTimeout(Duration.ofSeconds(30));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(supabaseUrl + "/storage/v1")
                .requestFactory(conexion)
                .defaultHeader("apikey", secretKey);

        // Las claves nuevas (sb_secret_...) bastan en "apikey". La service_role
        // antigua es un JWT y Storage además la pide en Authorization.
        if (configurado && !secretKey.startsWith("sb_")) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey);
        }

        this.restClient = builder.build();
    }

    /**
     * Al arrancar: deja el bucket privado (lo crea si no existe, para no
     * tener que hacerlo a mano en Supabase) y pasa las fotos guardadas con la
     * URL pública de antes a su ruta. Las dos cosas se pueden repetir sin
     * problema, así que no importa que lo hagan las dos réplicas.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void prepararAlArrancar() {
        int convertidas = usuarioRepository.quitarPrefijoFotoPerfil(urlPublica + "%", urlPublica.length() + 1);
        if (convertidas > 0) {
            log.info("{} fotos de perfil pasaron de URL pública a ruta en el bucket privado", convertidas);
        }

        if (!configurado) {
            log.warn("supabase.secret-key no está definida: no se podrán subir ni mostrar fotos de perfil");
            return;
        }

        Map<String, Object> privado = Map.of(
                "id", bucket,
                "name", bucket,
                "public", false,
                "file_size_limit", TAMANO_MAXIMO,
                "allowed_mime_types", new String[] {"image/jpeg", "image/png", "image/webp"}
        );

        try {
            restClient.get().uri("/bucket/{id}", bucket).retrieve().toBodilessEntity();

            // Existe: se deja privado (antes era público).
            restClient.put().uri("/bucket/{id}", bucket)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(privado)
                    .retrieve()
                    .toBodilessEntity();
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
                        .body(privado)
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

    /** Sube la foto y devuelve su ruta en el bucket. Lanza RuntimeException si Storage falla. */
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

        return idUsuario + "/" + archivo;
    }

    /** true si es la ruta de una foto ({idUsuario}/{uuid}.{jpeg|png|webp}) y no otra cosa del bucket. */
    public static boolean rutaValida(String ruta) {
        return ruta != null && RUTA.matcher(ruta).matches();
    }

    /**
     * Descarga la foto de esa ruta, o vacío si la ruta no es válida, no
     * existe o Storage no responde (el frontend muestra la inicial o el ícono).
     */
    public Optional<Foto> descargar(String ruta) {
        if (!configurado || !rutaValida(ruta)) {
            return Optional.empty();
        }
        Matcher partes = RUTA.matcher(ruta);
        partes.matches();

        try {
            ResponseEntity<byte[]> respuesta = restClient.get()
                    .uri("/object/authenticated/{bucket}/{carpeta}/{archivo}", bucket, partes.group(1), partes.group(2))
                    .retrieve()
                    .toEntity(byte[].class);

            byte[] bytes = respuesta.getBody();
            MediaType tipo = bytes == null ? null : tipoDeImagen(bytes);
            return tipo == null ? Optional.empty() : Optional.of(new Foto(bytes, tipo));
        } catch (Exception e) {
            log.warn("No se pudo descargar la foto {}: {}", ruta, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Borra la foto anterior. Si falla solo queda un archivo huérfano en el
     * bucket, así que no se interrumpe al usuario.
     */
    public void borrar(String guardada) {
        if (guardada == null) {
            return;
        }

        // Puede ser una ruta o, si no se alcanzó a convertir, la URL pública de antes.
        String ruta = guardada.startsWith(urlPublica) ? guardada.substring(urlPublica.length()) : guardada;
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
