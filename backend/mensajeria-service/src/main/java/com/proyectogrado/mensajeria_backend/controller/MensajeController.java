package com.proyectogrado.mensajeria_backend.controller;

import com.proyectogrado.mensajeria_backend.model.Notificacion;
import com.proyectogrado.mensajeria_backend.model.TipoNotificacion;
import com.proyectogrado.mensajeria_backend.repository.NotificacionRepository;
import com.proyectogrado.mensajeria_backend.repository.PreferenciaNotificacionRepository;
import com.proyectogrado.mensajeria_backend.repository.UsuarioLookupRepository;
import com.proyectogrado.mensajeria_backend.security.TextBeeOtpService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Envío de SMS con texto libre (no OTP).
 *
 * Lo usan persona-mayor-service (emergencias y cumpleaños), salud-service
 * (recordatorios de medicamentos y citas) y actividad-service
 * (recordatorios de actividades). Esos servicios saben a quién avisar y qué
 * decir, pero no tienen credenciales de TextBee: solo mensajeria-service
 * envía SMS.
 *
 * El gateway no expone esta ruta; se llama de servicio a servicio, nunca
 * desde el frontend.
 */
@RestController
@RequestMapping("/api/mensajes")
public class MensajeController {

    private final TextBeeOtpService textBeeOtpService;
    private final NotificacionRepository notificacionRepository;
    private final PreferenciaNotificacionRepository preferenciaRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;

    public MensajeController(
            TextBeeOtpService textBeeOtpService,
            NotificacionRepository notificacionRepository,
            PreferenciaNotificacionRepository preferenciaRepository,
            UsuarioLookupRepository usuarioLookupRepository
    ) {
        this.textBeeOtpService = textBeeOtpService;
        this.notificacionRepository = notificacionRepository;
        this.preferenciaRepository = preferenciaRepository;
        this.usuarioLookupRepository = usuarioLookupRepository;
    }

    /**
     * Envía el SMS y guarda una copia para la campanita.
     *
     * Cuerpo: { "celular": "+573001234567", "mensaje": "...", "tipo": "MEDICAMENTO" }
     *
     * Si el dueño del celular desactivó ese tipo de notificación en su
     * perfil, no se envía nada y se responde success = false con
     * omitido = true. Sin tipo (o con uno desconocido) siempre se envía.
     */
    @PostMapping("/enviar")
    public ResponseEntity<?> enviar(@RequestBody Map<String, String> request) {

        String celular = request.get("celular");
        String mensaje = request.get("mensaje");
        TipoNotificacion tipo = TipoNotificacion.desde(request.get("tipo"));

        if (estaDesactivada(celular, tipo)) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "omitido", true,
                    "message", "El usuario desactivó este tipo de notificación"
            ));
        }

        try {
            textBeeOtpService.enviarMensaje(celular, mensaje);

            // Se guarda para la campanita del panel. Si falla el guardado,
            // el SMS ya salió: no se reporta como error del envío.
            try {
                notificacionRepository.save(new Notificacion(celular, mensaje));
            } catch (Exception e) {
                System.out.println("No se pudo guardar la notificacion: " + e.getMessage());
            }

            return ResponseEntity.ok(Map.of("success", true));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "No se pudo enviar el mensaje"));
        }
    }

    /** true si el dueño del celular apagó ese tipo de notificación. */
    private boolean estaDesactivada(String celular, TipoNotificacion tipo) {
        if (tipo == null || celular == null || celular.isBlank()) {
            return false;
        }

        return usuarioLookupRepository.findFirstByCelular(celular)
                .flatMap(usuario -> preferenciaRepository.findByIdUsuarioAndTipo(usuario.getIdUsuario(), tipo))
                .map(preferencia -> !preferencia.isActiva())
                .orElse(false);
    }
}
