package com.proyectogrado.salud_backend.controller;

import com.proyectogrado.salud_backend.client.MensajeriaClient;
import com.proyectogrado.salud_backend.config.ZonaHoraria;
import com.proyectogrado.salud_backend.model.CitaMedica;
import com.proyectogrado.salud_backend.model.UsuarioLookup;
import com.proyectogrado.salud_backend.repository.CitaMedicaRepository;
import com.proyectogrado.salud_backend.repository.RelacionAcompananteLookupRepository;
import com.proyectogrado.salud_backend.repository.UsuarioLookupRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Un acompañante se ofrece a ir con la persona mayor a una cita que
 * necesita acompañamiento (la cita lo requiere o ella quiere ir acompañada)
 * y dice dónde se encuentran para salir. La persona mayor recibe un SMS (que
 * también queda en su campana). Solo un acompañante por cita: el que aceptó
 * puede cambiar el punto de encuentro o decir que ya no puede ir.
 *
 * Solo para citas futuras de una persona mayor que el acompañante acompaña
 * (vínculo ACEPTADO).
 */
@RestController
@RequestMapping("/api/acompanante/personas-mayores/{idPersonaMayor}/citas-medicas/{idCita}/acompanamiento")
public class AcompanamientoCitaController {

    private static final Locale ESPANOL = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ESPANOL);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("h:mm a", ESPANOL);

    /** Lo que escribe el acompañante al aceptar. */
    public record AceptarRequest(String puntoEncuentro) {
    }

    private final CitaMedicaRepository citaMedicaRepository;
    private final RelacionAcompananteLookupRepository relacionAcompananteLookupRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;
    private final MensajeriaClient mensajeriaClient;
    private final CitaMedicaController citaMedicaController;

    public AcompanamientoCitaController(
            CitaMedicaRepository citaMedicaRepository,
            RelacionAcompananteLookupRepository relacionAcompananteLookupRepository,
            UsuarioLookupRepository usuarioLookupRepository,
            MensajeriaClient mensajeriaClient,
            CitaMedicaController citaMedicaController
    ) {
        this.citaMedicaRepository = citaMedicaRepository;
        this.relacionAcompananteLookupRepository = relacionAcompananteLookupRepository;
        this.usuarioLookupRepository = usuarioLookupRepository;
        this.mensajeriaClient = mensajeriaClient;
        this.citaMedicaController = citaMedicaController;
    }

    /**
     * Acepta acompañar la cita con el punto de encuentro. Si ya la había
     * aceptado, cambia el punto de encuentro. Si la aceptó otro acompañante,
     * responde 409.
     */
    @PutMapping
    public ResponseEntity<?> aceptar(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor,
            @PathVariable Integer idCita,
            @RequestBody(required = false) AceptarRequest request
    ) {
        ResponseEntity<?> error = revisar(idAcompanante, idPersonaMayor);
        if (error != null) {
            return error;
        }

        CitaMedica cita = citaDe(idCita, idPersonaMayor);
        if (cita == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No se encontró la cita");
        }

        if (!cita.getInicio().isAfter(ZonaHoraria.ahora())) {
            return ResponseEntity.badRequest().body("Esta cita ya pasó");
        }

        if (!cita.necesitaAcompanamiento()) {
            return ResponseEntity.badRequest().body("Esta cita no necesita acompañamiento");
        }

        String punto = request != null && request.puntoEncuentro() != null ? request.puntoEncuentro().trim() : "";
        if (punto.isEmpty()) {
            return ResponseEntity.badRequest().body("Escribe dónde se van a encontrar para salir a la cita");
        }

        if (punto.length() > CitaMedica.MAX_PUNTO_ENCUENTRO) {
            return ResponseEntity.badRequest().body(
                    "El punto de encuentro puede tener máximo " + CitaMedica.MAX_PUNTO_ENCUENTRO + " caracteres");
        }

        Integer asignado = cita.getIdAcompananteCita();
        if (asignado != null && !asignado.equals(idAcompanante)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(nombre(asignado) + " ya se ofreció a acompañar esta cita");
        }

        boolean cambioDePunto = asignado != null;
        cita.asignarAcompanante(idAcompanante, punto, ZonaHoraria.ahora());
        cita = citaMedicaRepository.save(cita);

        String acompanante = nombre(idAcompanante);
        avisarALaPersonaMayor(cita, cambioDePunto
                ? "VITA+: " + acompanante + " cambió el punto de encuentro para tu cita " + detalle(cita)
                  + ". Ahora se encontrarán en: " + punto + "."
                : "VITA+: " + acompanante + " te acompañará a tu cita " + detalle(cita)
                  + ". Se encontrarán en: " + punto + ".");

        return ResponseEntity.ok(citaMedicaController.aRespuesta(cita));
    }

    /** El acompañante que había aceptado ya no puede ir; la cita queda libre para otro. */
    @DeleteMapping
    public ResponseEntity<?> cancelar(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor,
            @PathVariable Integer idCita
    ) {
        ResponseEntity<?> error = revisar(idAcompanante, idPersonaMayor);
        if (error != null) {
            return error;
        }

        CitaMedica cita = citaDe(idCita, idPersonaMayor);
        if (cita == null || !idAcompanante.equals(cita.getIdAcompananteCita())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No estás acompañando esta cita");
        }

        cita.liberarAcompanante();
        cita = citaMedicaRepository.save(cita);

        if (cita.getInicio().isAfter(ZonaHoraria.ahora())) {
            avisarALaPersonaMayor(cita, "VITA+: " + nombre(idAcompanante) + " ya no podrá acompañarte a tu cita "
                    + detalle(cita) + ". Otro de tus acompañantes puede ofrecerse desde la aplicación.");
        }

        return ResponseEntity.ok(citaMedicaController.aRespuesta(cita));
    }

    /** 403 si no acompaña a esta persona mayor; null si puede seguir. */
    private ResponseEntity<?> revisar(Integer idAcompanante, Integer idPersonaMayor) {
        boolean acompana = relacionAcompananteLookupRepository
                .existsById_IdPersonaMayorAndId_IdAcompananteAndEstado(idPersonaMayor, idAcompanante, "ACEPTADA");

        return acompana
                ? null
                : ResponseEntity.status(HttpStatus.FORBIDDEN).body("No acompañas a esta persona mayor");
    }

    private CitaMedica citaDe(Integer idCita, Integer idPersonaMayor) {
        return citaMedicaRepository.findById(idCita)
                .filter(cita -> cita.getIdPersonaMayor().equals(idPersonaMayor))
                .orElse(null);
    }

    private String nombre(Integer idUsuario) {
        return usuarioLookupRepository.findById(idUsuario)
                .map(UsuarioLookup::getNombreUsuario)
                .orElse("Uno de tus acompañantes");
    }

    /** "de Control de presión del lunes 20 de octubre a las 9:30 a. m." */
    private static String detalle(CitaMedica cita) {
        return "de " + cita.getTitulo() + " del " + cita.getFecha().format(FECHA)
                + " a las " + cita.getHora().format(HORA);
    }

    /** SMS a la persona mayor (si tiene celular); un error no interrumpe la operación. */
    private void avisarALaPersonaMayor(CitaMedica cita, String mensaje) {
        usuarioLookupRepository.findById(cita.getIdPersonaMayor())
                .map(UsuarioLookup::getCelular)
                .filter(celular -> !celular.isBlank())
                .ifPresent(celular -> mensajeriaClient.enviarMensaje(celular, mensaje, "CITA_MEDICA"));
    }
}
