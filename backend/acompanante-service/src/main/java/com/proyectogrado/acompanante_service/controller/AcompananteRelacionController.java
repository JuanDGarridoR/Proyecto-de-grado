package com.proyectogrado.acompanante_service.controller;

import com.proyectogrado.acompanante_service.dto.AgregarPersonaMayorRequest;
import com.proyectogrado.acompanante_service.dto.PersonaMayorResponse;
import com.proyectogrado.acompanante_service.model.AcompananteInfoLookup;
import com.proyectogrado.acompanante_service.model.PersonaMayorAcompanante;
import com.proyectogrado.acompanante_service.model.PersonaMayorAcompananteId;
import com.proyectogrado.acompanante_service.model.UsuarioLookup;
import com.proyectogrado.acompanante_service.repository.AcompananteInfoLookupRepository;
import com.proyectogrado.acompanante_service.repository.PersonaMayorAcompananteRepository;
import com.proyectogrado.acompanante_service.repository.UsuarioLookupRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
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
 * Lado del acompañante en el vínculo con personas mayores: ver sus personas
 * mayores, enviarles solicitudes por celular, responder las que le envían
 * (aceptar o rechazar) y cancelar un vínculo. Las solicitudes van en los dos
 * sentidos: responde siempre quien no la envió (ver solicitadaPor).
 *
 * El id del usuario autenticado llega en el encabezado X-User-Id, que pone
 * el gateway después de validar el token. Este servicio no valida tokens.
 */
@RestController
@RequestMapping("/api/acompanante/personas-mayores")
public class AcompananteRelacionController {

    private final PersonaMayorAcompananteRepository relacionRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;
    private final AcompananteInfoLookupRepository acompananteInfoLookupRepository;

    public AcompananteRelacionController(
            PersonaMayorAcompananteRepository relacionRepository,
            UsuarioLookupRepository usuarioLookupRepository,
            AcompananteInfoLookupRepository acompananteInfoLookupRepository
    ) {
        this.relacionRepository = relacionRepository;
        this.usuarioLookupRepository = usuarioLookupRepository;
        this.acompananteInfoLookupRepository = acompananteInfoLookupRepository;
    }

    /** Personas mayores con vínculo aceptado. */
    @GetMapping
    public ResponseEntity<List<PersonaMayorResponse>> obtenerPersonasMayores(
            @RequestHeader("X-User-Id") Integer idAcompanante
    ) {
        return ResponseEntity.ok(listarPorEstado(idAcompanante, "ACEPTADA"));
    }

    /** Solicitudes que le enviaron personas mayores y que aún no ha respondido. */
    @GetMapping("/solicitudes")
    public ResponseEntity<List<PersonaMayorResponse>> obtenerSolicitudesPendientes(
            @RequestHeader("X-User-Id") Integer idAcompanante
    ) {
        return ResponseEntity.ok(listarPendientes(idAcompanante, false));
    }

    /** Solicitudes que el acompañante envió y la persona mayor aún no ha respondido. */
    @GetMapping("/solicitudes/enviadas")
    public ResponseEntity<List<PersonaMayorResponse>> obtenerSolicitudesEnviadas(
            @RequestHeader("X-User-Id") Integer idAcompanante
    ) {
        return ResponseEntity.ok(listarPendientes(idAcompanante, true));
    }

    /**
     * Envía una solicitud de acompañamiento a la persona mayor con ese
     * celular. Es el mismo flujo que usa la persona mayor para agregar un
     * acompañante, pero al revés: la persona mayor la acepta o la rechaza.
     */
    @PostMapping
    public ResponseEntity<String> agregarPersonaMayor(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @RequestBody AgregarPersonaMayorRequest request
    ) {
        AcompananteInfoLookup acompanante =
                acompananteInfoLookupRepository.findById(idAcompanante).orElse(null);

        if (acompanante == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo un acompañante puede enviar solicitudes de acompañamiento");
        }

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

        Integer idPersonaMayor = usuario.getIdUsuario();

        if (!usuarioLookupRepository.tieneRol(idPersonaMayor, "PERSONA_MAYOR")) {
            return ResponseEntity.badRequest()
                    .body("El usuario existe, pero no está registrado como persona mayor");
        }

        PersonaMayorAcompanante relacion = relacionRepository
                .findById(new PersonaMayorAcompananteId(idPersonaMayor, idAcompanante))
                .orElse(null);

        if (relacion != null) {

            if ("ACEPTADA".equals(relacion.getEstado())) {
                return ResponseEntity.badRequest().body("Ya acompañas a esta persona mayor");
            }

            if ("PENDIENTE".equals(relacion.getEstado())) {
                return ResponseEntity.badRequest().body(relacion.laEnvioElAcompanante()
                        ? "Ya le enviaste una solicitud a esta persona mayor"
                        : "Esta persona mayor ya te envió una solicitud: acéptala en Solicitudes pendientes");
            }

            // Una cuenta inactivada: no se revela ni se sobrescribe el vínculo.
            if (!"RECHAZADA".equals(relacion.getEstado())) {
                return ResponseEntity.badRequest()
                        .body("No es posible enviar la solicitud a esta persona en este momento");
            }

            // Estaba RECHAZADA: se permite volver a intentar.
        } else {
            relacion = new PersonaMayorAcompanante(idPersonaMayor, idAcompanante);
        }

        relacion.setEstado("PENDIENTE");
        relacion.setSolicitadaPor(PersonaMayorAcompanante.ACOMPANANTE);
        relacionRepository.saveAndFlush(relacion);

        // Misma relación que guarda la persona mayor al agregar un acompañante
        if (request.relacion() != null && !request.relacion().isBlank()) {
            acompanante.setRelacion(request.relacion().trim());
            acompananteInfoLookupRepository.saveAndFlush(acompanante);
        }

        return ResponseEntity.ok("Solicitud de acompañamiento enviada correctamente");
    }

    @PutMapping("/solicitudes/{idPersonaMayor}/aceptar")
    public ResponseEntity<String> aceptarSolicitud(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor
    ) {
        return cambiarEstado(idAcompanante, idPersonaMayor, "ACEPTADA", "aceptada");
    }

    @PutMapping("/solicitudes/{idPersonaMayor}/rechazar")
    public ResponseEntity<String> rechazarSolicitud(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor
    ) {
        return cambiarEstado(idAcompanante, idPersonaMayor, "RECHAZADA", "rechazada");
    }

    /** Quita el vínculo con una persona mayor, esté en el estado que esté. */
    @DeleteMapping("/{idPersonaMayor}")
    public ResponseEntity<String> cancelarAsociacion(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor
    ) {
        PersonaMayorAcompananteId idRelacion =
                new PersonaMayorAcompananteId(idPersonaMayor, idAcompanante);

        PersonaMayorAcompanante relacion =
                relacionRepository.findById(idRelacion).orElse(null);

        if (relacion == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No existe una asociación registrada");
        }

        relacionRepository.delete(relacion);
        relacionRepository.flush();

        return ResponseEntity.ok("Asociación cancelada correctamente");
    }

    /**
     * Acepta o rechaza una solicitud que le enviaron; solo se puede si sigue
     * PENDIENTE y no la envió el mismo acompañante.
     */
    private ResponseEntity<String> cambiarEstado(
            Integer idAcompanante,
            Integer idPersonaMayor,
            String nuevoEstado,
            String participioParaMensaje
    ) {
        PersonaMayorAcompanante relacion = relacionRepository
                .findById_IdAcompanante(idAcompanante).stream()
                .filter(r -> r.getId().getIdPersonaMayor().equals(idPersonaMayor))
                .findFirst()
                .orElse(null);

        if (relacion == null || relacion.laEnvioElAcompanante()) {
            return ResponseEntity.notFound().build();
        }

        if (!"PENDIENTE".equals(relacion.getEstado())) {
            return ResponseEntity.badRequest().body("Esta solicitud ya fue procesada");
        }

        relacion.setEstado(nuevoEstado);
        relacionRepository.save(relacion);

        return ResponseEntity.ok("Solicitud de acompañamiento " + participioParaMensaje);
    }

    /** Pendientes enviadas por el acompañante (enviadas = true) o recibidas. */
    private List<PersonaMayorResponse> listarPendientes(Integer idAcompanante, boolean enviadas) {
        return aRespuestas(
                relacionRepository.findById_IdAcompananteAndEstado(idAcompanante, "PENDIENTE")
                        .stream()
                        .filter(relacion -> relacion.laEnvioElAcompanante() == enviadas)
                        .toList()
        );
    }

    private List<PersonaMayorResponse> listarPorEstado(Integer idAcompanante, String estado) {
        return aRespuestas(relacionRepository.findById_IdAcompananteAndEstado(idAcompanante, estado));
    }

    private List<PersonaMayorResponse> aRespuestas(List<PersonaMayorAcompanante> relaciones) {
        return relaciones.stream()
                .map(relacion -> {
                    Integer idPersonaMayor = relacion.getId().getIdPersonaMayor();
                    UsuarioLookup usuario = usuarioLookupRepository.findById(idPersonaMayor).orElse(null);

                    return new PersonaMayorResponse(
                            idPersonaMayor,
                            usuario != null ? usuario.getNombreUsuario() : null,
                            usuario != null ? usuario.getCelular() : null,
                            usuario != null ? usuario.getCorreo() : null
                    );
                })
                .toList();
    }
}
