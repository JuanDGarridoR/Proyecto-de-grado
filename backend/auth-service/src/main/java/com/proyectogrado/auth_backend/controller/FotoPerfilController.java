package com.proyectogrado.auth_backend.controller;

import com.proyectogrado.auth_backend.model.Usuario;
import com.proyectogrado.auth_backend.repository.UsuarioRepository;
import com.proyectogrado.auth_backend.security.JwtService;
import com.proyectogrado.auth_backend.service.FotoPerfilStorage;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Foto de perfil de cualquier rol (persona mayor, acompañante, voluntario u
 * organización). La ven sus vínculos en sus listas y el propio usuario junto
 * a su nombre en el panel. El archivo queda en Supabase Storage (ver
 * FotoPerfilStorage) y la tabla usuario guarda su ruta en el bucket.
 *
 * La foto de una organización es la de su cuenta (el usuario con
 * id_organizacion), que es la que los demás servicios ya usan para mostrar
 * su nombre, celular y correo.
 */
@RestController
@RequestMapping("/api/auth/informacion/foto")
public class FotoPerfilController {

    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;
    private final FotoPerfilStorage fotoPerfilStorage;

    public FotoPerfilController(
            UsuarioRepository usuarioRepository,
            JwtService jwtService,
            FotoPerfilStorage fotoPerfilStorage
    ) {
        this.usuarioRepository = usuarioRepository;
        this.jwtService = jwtService;
        this.fotoPerfilStorage = fotoPerfilStorage;
    }

    /** Sube o reemplaza la foto. Acepta JPEG, PNG o WebP de hasta 2 MB. */
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> subir(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestParam("foto") MultipartFile foto
    ) throws IOException {
        Usuario usuario = usuarioDelToken(authorizationHeader);

        if (foto.isEmpty()) {
            return ResponseEntity.badRequest().body("Selecciona una imagen");
        }

        if (foto.getSize() > FotoPerfilStorage.TAMANO_MAXIMO) {
            return ResponseEntity.badRequest().body("La imagen no puede pesar más de 2 MB");
        }

        byte[] bytes = foto.getBytes();
        MediaType tipo = FotoPerfilStorage.tipoDeImagen(bytes);
        if (tipo == null) {
            return ResponseEntity.badRequest().body("La imagen debe ser JPG, PNG o WebP");
        }

        if (!fotoPerfilStorage.estaConfigurado()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("La subida de fotos no está disponible en este momento");
        }

        String ruta;
        try {
            ruta = fotoPerfilStorage.subir(usuario.getIdUsuario(), bytes, tipo);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("No se pudo guardar la foto. Intenta de nuevo más tarde.");
        }

        String anterior = usuario.getFotoPerfil();
        usuario.setFotoPerfil(ruta);
        usuarioRepository.save(usuario);

        // Se borra después de guardar la nueva, para no dejar al usuario sin foto si algo falla.
        fotoPerfilStorage.borrar(anterior);

        return ResponseEntity.ok(respuesta(ruta));
    }

    /** Quita la foto; el perfil vuelve a mostrar el ícono. */
    @DeleteMapping
    public ResponseEntity<?> quitar(@RequestHeader("Authorization") String authorizationHeader) {
        Usuario usuario = usuarioDelToken(authorizationHeader);

        String anterior = usuario.getFotoPerfil();
        if (anterior != null) {
            usuario.setFotoPerfil(null);
            usuarioRepository.save(usuario);
            fotoPerfilStorage.borrar(anterior);
        }

        return ResponseEntity.ok(respuesta(null));
    }

    /** Como en el resto de /api/auth, el usuario se saca del token. */
    private Usuario usuarioDelToken(String authorizationHeader) {
        Integer idUsuario = jwtService.extraerIdUsuario(authorizationHeader.substring(7));

        return usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    /** Map.of no admite null, y al quitar la foto la ruta es null. */
    private static Map<String, String> respuesta(String ruta) {
        Map<String, String> respuesta = new HashMap<>();
        respuesta.put("fotoPerfil", ruta);
        return respuesta;
    }
}
