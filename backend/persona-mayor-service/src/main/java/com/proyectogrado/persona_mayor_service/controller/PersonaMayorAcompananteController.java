package com.proyectogrado.persona_mayor_service.controller;

import com.proyectogrado.persona_mayor_service.dto.AcompananteResponse;
import com.proyectogrado.persona_mayor_service.dto.AgregarAcompananteRequest;
import com.proyectogrado.persona_mayor_service.model.AcompananteLookup;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorAcompanante;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorAcompananteId;
import com.proyectogrado.persona_mayor_service.model.UsuarioLookup;
import com.proyectogrado.persona_mayor_service.repository.AcompananteLookupRepository;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorAcompananteRepository;
import com.proyectogrado.persona_mayor_service.repository.UsuarioLookupRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lado de la persona mayor en el vínculo con sus acompañantes: los agrega
 * por celular (queda una solicitud PENDIENTE que el acompañante acepta o
 * rechaza), responde las solicitudes que le envían los acompañantes, los
 * lista y los quita. Responde siempre quien no envió la solicitud (ver
 * solicitadaPor).
 *
 * El id del usuario autenticado llega en el encabezado X-User-Id, que pone
 * el gateway después de validar el token. Este servicio no valida tokens.
 */
@RestController
@RequestMapping("/api/persona-mayor/acompanantes")
public class PersonaMayorAcompananteController {

    private final PersonaMayorAcompananteRepository relacionRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;
    private final AcompananteLookupRepository acompananteLookupRepository;

    public PersonaMayorAcompananteController(
            PersonaMayorAcompananteRepository relacionRepository,
            UsuarioLookupRepository usuarioLookupRepository,
            AcompananteLookupRepository acompananteLookupRepository
    ) {
        this.relacionRepository = relacionRepository;
        this.usuarioLookupRepository = usuarioLookupRepository;
        this.acompananteLookupRepository = acompananteLookupRepository;
    }

    /** Acompañantes que ya aceptaron la solicitud. */
    @GetMapping
    public ResponseEntity<List<AcompananteResponse>> listarAcompanantes(
            @RequestHeader("X-User-Id") Integer idPersonaMayor
    ) {
        List<PersonaMayorAcompanante> relaciones =
                relacionRepository.findById_IdPersonaMayorAndEstado(idPersonaMayor, "ACEPTADA");

        List<AcompananteResponse> respuesta = relaciones.stream()
                .map(relacion -> construirRespuesta(relacion.getId().getIdAcompanante()))
                .toList();

        return ResponseEntity.ok(respuesta);
    }

    /** Solicitudes que le enviaron acompañantes y que aún no ha respondido. */
    @GetMapping("/solicitudes")
    public ResponseEntity<List<AcompananteResponse>> listarSolicitudes(
            @RequestHeader("X-User-Id") Integer idPersonaMayor
    ) {
        List<AcompananteResponse> respuesta = relacionRepository
                .findById_IdPersonaMayorAndEstado(idPersonaMayor, "PENDIENTE")
                .stream()
                .filter(PersonaMayorAcompanante::laEnvioElAcompanante)
                .map(relacion -> construirRespuesta(relacion.getId().getIdAcompanante()))
                .toList();

        return ResponseEntity.ok(respuesta);
    }

    @PutMapping("/solicitudes/{idAcompanante}/aceptar")
    public ResponseEntity<String> aceptarSolicitud(
            @RequestHeader("X-User-Id") Integer idPersonaMayor,
            @PathVariable Integer idAcompanante
    ) {
        return responderSolicitud(idPersonaMayor, idAcompanante, "ACEPTADA", "aceptada");
    }

    @PutMapping("/solicitudes/{idAcompanante}/rechazar")
    public ResponseEntity<String> rechazarSolicitud(
            @RequestHeader("X-User-Id") Integer idPersonaMayor,
            @PathVariable Integer idAcompanante
    ) {
        return responderSolicitud(idPersonaMayor, idAcompanante, "RECHAZADA", "rechazada");
    }

    /**
     * Envía una solicitud de acompañamiento al usuario con ese celular, que
     * debe estar registrado como acompañante. También guarda la relación que
     * indica la persona mayor (por ejemplo, "Hija").
     */
    @PostMapping
    public ResponseEntity<?> agregarAcompanante(
            @RequestHeader("X-User-Id") Integer idPersonaMayor,
            @RequestBody AgregarAcompananteRequest request
    ) {
        if (request.celular() == null || request.celular().isBlank()) {
            return ResponseEntity.badRequest().body("El celular es obligatorio");
        }

        UsuarioLookup usuario = usuarioLookupRepository
                .findByCelular(request.celular())
                .orElse(null);

        if (usuario == null) {
            return ResponseEntity.badRequest()
                    .body("No existe un usuario registrado con ese celular");
        }

        AcompananteLookup acompanante = acompananteLookupRepository
                .findById(usuario.getIdUsuario())
                .orElse(null);

        if (acompanante == null) {
            return ResponseEntity.badRequest()
                    .body("El usuario existe, pero no está registrado como acompañante");
        }

        PersonaMayorAcompananteId idRelacion =
                new PersonaMayorAcompananteId(idPersonaMayor, acompanante.getIdUsuario());

        PersonaMayorAcompanante relacionExistente =
                relacionRepository.findById(idRelacion).orElse(null);

        acompanante.setRelacion(request.relacion());
        acompananteLookupRepository.saveAndFlush(acompanante);

        if (relacionExistente != null) {

            if ("ACEPTADA".equals(relacionExistente.getEstado())) {
                return ResponseEntity.badRequest().body("Este acompañante ya está registrado");
            }

            if ("PENDIENTE".equals(relacionExistente.getEstado())) {
                return ResponseEntity.badRequest().body(relacionExistente.laEnvioElAcompanante()
                        ? "Este acompañante ya te envió una solicitud: acéptala en tus contactos"
                        : "Ya existe una solicitud pendiente para este acompañante");
            }

            // Una cuenta inactivada: no se revela ni se sobrescribe el vínculo.
            if (!"RECHAZADA".equals(relacionExistente.getEstado())) {
                return ResponseEntity.badRequest()
                        .body("No es posible enviar la solicitud a este acompañante en este momento");
            }

            // Estaba RECHAZADA: se permite volver a intentar.
            relacionExistente.setEstado("PENDIENTE");
            relacionExistente.setSolicitadaPor(PersonaMayorAcompanante.PERSONA_MAYOR);
            relacionRepository.saveAndFlush(relacionExistente);

            return ResponseEntity.ok("Solicitud de acompañamiento enviada correctamente");
        }

        PersonaMayorAcompanante relacion =
                new PersonaMayorAcompanante(idPersonaMayor, acompanante.getIdUsuario());

        relacion.setEstado("PENDIENTE");
        relacion.setSolicitadaPor(PersonaMayorAcompanante.PERSONA_MAYOR);
        relacionRepository.saveAndFlush(relacion);

        return ResponseEntity.ok("Solicitud de acompañamiento enviada correctamente");
    }

    /** Quita el vínculo con un acompañante, esté en el estado que esté. */
    @DeleteMapping("/{idAcompanante}")
    public ResponseEntity<String> cancelarAsociacion(
            @RequestHeader("X-User-Id") Integer idPersonaMayor,
            @PathVariable Integer idAcompanante
    ) {
        PersonaMayorAcompananteId idRelacion =
                new PersonaMayorAcompananteId(idPersonaMayor, idAcompanante);

        PersonaMayorAcompanante relacion =
                relacionRepository.findById(idRelacion).orElse(null);

        if (relacion == null) {
            return ResponseEntity.status(404).body("No existe una asociación registrada");
        }

        relacionRepository.delete(relacion);
        relacionRepository.flush();

        return ResponseEntity.ok("Asociación cancelada correctamente");
    }

    /** Solo se responde una solicitud PENDIENTE que haya enviado el acompañante. */
    private ResponseEntity<String> responderSolicitud(
            Integer idPersonaMayor,
            Integer idAcompanante,
            String nuevoEstado,
            String participioParaMensaje
    ) {
        PersonaMayorAcompanante relacion = relacionRepository
                .findById(new PersonaMayorAcompananteId(idPersonaMayor, idAcompanante))
                .orElse(null);

        if (relacion == null || !relacion.laEnvioElAcompanante()) {
            return ResponseEntity.status(404).body("No se encontró la solicitud de este acompañante");
        }

        if (!"PENDIENTE".equals(relacion.getEstado())) {
            return ResponseEntity.badRequest().body("Esta solicitud ya fue procesada");
        }

        relacion.setEstado(nuevoEstado);
        relacionRepository.saveAndFlush(relacion);

        return ResponseEntity.ok("Solicitud de acompañamiento " + participioParaMensaje);
    }

    private AcompananteResponse construirRespuesta(Integer idAcompanante) {

        UsuarioLookup usuario = usuarioLookupRepository.findById(idAcompanante).orElse(null);

        String relacion = acompananteLookupRepository.findById(idAcompanante)
                .map(AcompananteLookup::getRelacion)
                .orElse(null);

        return new AcompananteResponse(
                idAcompanante,
                usuario != null ? usuario.getNombreUsuario() : null,
                usuario != null ? usuario.getCelular() : null,
                relacion
        );
    }
}
