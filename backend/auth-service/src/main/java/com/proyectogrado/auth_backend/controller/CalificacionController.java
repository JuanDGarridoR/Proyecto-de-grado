package com.proyectogrado.auth_backend.controller;

import com.proyectogrado.auth_backend.model.CalificacionPlataforma;
import com.proyectogrado.auth_backend.repository.CalificacionPlataformaRepository;
import com.proyectogrado.auth_backend.security.JwtService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static com.proyectogrado.auth_backend.controller.UsuarioInformacionController.textoOpcional;

/**
 * Calificación de la plataforma desde el perfil (cualquier rol): de 0 a 5
 * estrellas y una observación opcional. Cada usuario tiene una sola, que
 * puede cambiar cuando quiera.
 */
@RestController
@RequestMapping("/api/auth/calificacion")
public class CalificacionController {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    /** Lo que llega del formulario. */
    public record CalificacionRequest(Integer estrellas, String comentario) {
    }

    /** La calificación que el usuario dejó, para mostrarla en su perfil. */
    public record CalificacionResponse(Integer estrellas, String comentario, LocalDateTime fechaActualizacion) {
    }

    private final CalificacionPlataformaRepository repository;
    private final JwtService jwtService;

    public CalificacionController(CalificacionPlataformaRepository repository, JwtService jwtService) {
        this.repository = repository;
        this.jwtService = jwtService;
    }

    /** La calificación del usuario, o 204 si aún no ha calificado. */
    @GetMapping
    public ResponseEntity<CalificacionResponse> obtener(@RequestHeader("Authorization") String authorizationHeader) {
        return repository.findByIdUsuario(idUsuarioDelToken(authorizationHeader))
                .map(c -> ResponseEntity.ok(aRespuesta(c)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Crea o actualiza la calificación del usuario. */
    @PutMapping
    public ResponseEntity<?> calificar(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody CalificacionRequest request
    ) {
        Integer estrellas = request.estrellas();
        if (estrellas == null || estrellas < 0 || estrellas > CalificacionPlataforma.MAX_ESTRELLAS) {
            return ResponseEntity.badRequest().body("Elige una calificación de 0 a 5 estrellas");
        }

        String comentario = textoOpcional(request.comentario());
        if (comentario != null && comentario.length() > CalificacionPlataforma.MAX_COMENTARIO) {
            return ResponseEntity.badRequest().body(
                    "La observación puede tener máximo " + CalificacionPlataforma.MAX_COMENTARIO + " caracteres");
        }

        Integer idUsuario = idUsuarioDelToken(authorizationHeader);
        LocalDateTime ahora = LocalDateTime.now(ZONA);

        CalificacionPlataforma calificacion = repository.findByIdUsuario(idUsuario)
                .orElseGet(() -> new CalificacionPlataforma(idUsuario, ahora));
        calificacion.calificar(estrellas, comentario, ahora);

        return ResponseEntity.ok(aRespuesta(repository.save(calificacion)));
    }

    /** Como en el resto de /api/auth, el usuario se saca del token. */
    private Integer idUsuarioDelToken(String authorizationHeader) {
        return jwtService.extraerIdUsuario(authorizationHeader.substring(7));
    }

    private static CalificacionResponse aRespuesta(CalificacionPlataforma calificacion) {
        return new CalificacionResponse(
                calificacion.getEstrellas(), calificacion.getComentario(), calificacion.getFechaActualizacion());
    }
}
