package com.proyectogrado.mensajeria_backend.controller;

import com.proyectogrado.mensajeria_backend.model.Notificacion;
import com.proyectogrado.mensajeria_backend.model.PreferenciaNotificacion;
import com.proyectogrado.mensajeria_backend.model.TipoNotificacion;
import com.proyectogrado.mensajeria_backend.model.UsuarioLookup;
import com.proyectogrado.mensajeria_backend.repository.NotificacionRepository;
import com.proyectogrado.mensajeria_backend.repository.PreferenciaNotificacionRepository;
import com.proyectogrado.mensajeria_backend.repository.UsuarioLookupRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Notificaciones (SMS enviados) del usuario autenticado, para la campanita
 * del panel, y sus preferencias (qué tipos quiere recibir). Pasa por el
 * gateway con JwtAuth, que agrega el encabezado X-User-Id; cada usuario solo
 * ve las de su propio celular.
 */
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private final NotificacionRepository notificacionRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;
    private final PreferenciaNotificacionRepository preferenciaRepository;

    public NotificacionController(
            NotificacionRepository notificacionRepository,
            UsuarioLookupRepository usuarioLookupRepository,
            PreferenciaNotificacionRepository preferenciaRepository
    ) {
        this.notificacionRepository = notificacionRepository;
        this.usuarioLookupRepository = usuarioLookupRepository;
        this.preferenciaRepository = preferenciaRepository;
    }

    /** Las 20 más recientes y cuántas faltan por leer. */
    @GetMapping
    public ResponseEntity<?> listar(@RequestHeader("X-User-Id") Integer idUsuario) {

        String celular = celularDe(idUsuario);

        if (celular == null) {
            return ResponseEntity.ok(Map.of("noLeidas", 0, "notificaciones", List.of()));
        }

        List<NotificacionResponse> notificaciones =
                notificacionRepository.findTop20ByCelularOrderByFechaEnvioDesc(celular)
                        .stream()
                        .map(NotificacionResponse::de)
                        .toList();

        return ResponseEntity.ok(Map.of(
                "noLeidas", notificacionRepository.countByCelularAndLeidaFalse(celular),
                "notificaciones", notificaciones
        ));
    }

    /** Se llama al abrir la campanita: todas quedan como leídas. */
    @PutMapping("/leidas")
    public ResponseEntity<Void> marcarLeidas(@RequestHeader("X-User-Id") Integer idUsuario) {

        String celular = celularDe(idUsuario);

        if (celular != null) {
            notificacionRepository.marcarLeidas(celular);
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * Si cada tipo de notificación está activo para el usuario. Se devuelven
     * todos los tipos; el frontend muestra solo los que aplican a su rol.
     */
    @GetMapping("/preferencias")
    public Map<TipoNotificacion, Boolean> preferencias(@RequestHeader("X-User-Id") Integer idUsuario) {

        Map<TipoNotificacion, Boolean> resultado = new EnumMap<>(TipoNotificacion.class);
        for (TipoNotificacion tipo : TipoNotificacion.values()) {
            resultado.put(tipo, true); // sin fila, el tipo está activo
        }

        for (PreferenciaNotificacion preferencia : preferenciaRepository.findByIdUsuario(idUsuario)) {
            resultado.put(preferencia.getTipo(), preferencia.isActiva());
        }

        return resultado;
    }

    /** Activa o desactiva un tipo de notificación. Cuerpo: { "activa": false } */
    @PutMapping("/preferencias/{tipo}")
    public ResponseEntity<?> cambiarPreferencia(
            @RequestHeader("X-User-Id") Integer idUsuario,
            @PathVariable String tipo,
            @RequestBody Map<String, Boolean> cuerpo
    ) {
        TipoNotificacion tipoNotificacion = TipoNotificacion.desde(tipo);
        Boolean activa = cuerpo.get("activa");

        if (tipoNotificacion == null || activa == null) {
            return ResponseEntity.badRequest().body("Tipo de notificación o valor no válido");
        }

        PreferenciaNotificacion preferencia = preferenciaRepository
                .findByIdUsuarioAndTipo(idUsuario, tipoNotificacion)
                .orElseGet(() -> new PreferenciaNotificacion(idUsuario, tipoNotificacion, activa));
        preferencia.setActiva(activa);
        preferenciaRepository.save(preferencia);

        return ResponseEntity.ok(Map.of("tipo", tipoNotificacion, "activa", activa));
    }

    /** Celular del usuario, o null si no tiene. Las notificaciones se guardan por celular. */
    private String celularDe(Integer idUsuario) {
        return usuarioLookupRepository.findById(idUsuario)
                .map(UsuarioLookup::getCelular)
                .filter(celular -> !celular.isBlank())
                .orElse(null);
    }

    /** Notificación tal como la recibe el frontend. */
    public record NotificacionResponse(Long id, String mensaje, Instant fechaEnvio, boolean leida) {
        static NotificacionResponse de(Notificacion n) {
            return new NotificacionResponse(n.getIdNotificacion(), n.getMensaje(), n.getFechaEnvio(), n.isLeida());
        }
    }
}
