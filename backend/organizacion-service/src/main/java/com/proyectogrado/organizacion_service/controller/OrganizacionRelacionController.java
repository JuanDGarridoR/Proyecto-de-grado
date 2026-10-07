package com.proyectogrado.organizacion_service.controller;

import com.proyectogrado.organizacion_service.dto.AsociarPersonaMayorRequest;
import com.proyectogrado.organizacion_service.dto.PersonaMayorResponse;
import com.proyectogrado.organizacion_service.model.PersonaMayorOrganizacion;
import com.proyectogrado.organizacion_service.model.PersonaMayorOrganizacionId;
import com.proyectogrado.organizacion_service.model.UsuarioLookup;
import com.proyectogrado.organizacion_service.repository.PersonaMayorOrganizacionRepository;
import com.proyectogrado.organizacion_service.repository.UsuarioLookupRepository;

import org.springframework.http.HttpStatus;
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
 * Lado de la organización en el vínculo con personas mayores: verlas,
 * enviar una solicitud a una nueva por celular, responder las que le
 * enviaron las personas mayores y cancelar un vínculo.
 *
 * El id del usuario autenticado llega en el encabezado X-User-Id, que pone
 * el gateway después de validar el token. Este servicio no valida tokens.
 *
 * Ese id es el de la cuenta que inició sesión, no el id_organizacion; por
 * eso cada endpoint empieza por obtenerIdOrganizacion().
 */
@RestController
@RequestMapping("/api/organizacion/personas-mayores")
public class OrganizacionRelacionController {

    private final PersonaMayorOrganizacionRepository relacionRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;

    public OrganizacionRelacionController(
            PersonaMayorOrganizacionRepository relacionRepository,
            UsuarioLookupRepository usuarioLookupRepository
    ) {
        this.relacionRepository = relacionRepository;
        this.usuarioLookupRepository = usuarioLookupRepository;
    }

    /** Personas mayores con vínculo aceptado. */
    @GetMapping
    public ResponseEntity<?> obtenerPersonasMayores(
            @RequestHeader("X-User-Id") Integer idUsuarioOrganizacion
    ) {
        Integer idOrganizacion = obtenerIdOrganizacion(idUsuarioOrganizacion);

        if (idOrganizacion == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("El usuario no tiene una organización asociada");
        }

        List<PersonaMayorOrganizacion> relaciones =
                relacionRepository.findById_IdOrganizacionAndEstado(idOrganizacion, "ACEPTADA");

        return ResponseEntity.ok(mapearAPersonaMayor(relaciones));
    }

    /**
     * Envía una solicitud de vínculo a la persona mayor con ese celular, que
     * ella acepta o rechaza desde su panel. Si antes la rechazó, la solicitud
     * vuelve a PENDIENTE. Si ella ya había pedido unirse, quedan vinculadas
     * de una vez.
     */
    @PostMapping
    public ResponseEntity<?> asociarPersonaMayor(
            @RequestHeader("X-User-Id") Integer idUsuarioOrganizacion,
            @RequestBody AsociarPersonaMayorRequest request
    ) {
        Integer idOrganizacion = obtenerIdOrganizacion(idUsuarioOrganizacion);

        if (idOrganizacion == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("El usuario no tiene una organización asociada");
        }

        UsuarioLookup usuarioPersonaMayor = usuarioLookupRepository
                .findByCelular(request.celular())
                .orElse(null);

        if (usuarioPersonaMayor == null) {
            return ResponseEntity.badRequest()
                    .body("No existe un usuario registrado con ese celular");
        }

        Integer idPersonaMayor = usuarioPersonaMayor.getIdUsuario();

        PersonaMayorOrganizacionId idRelacion =
                new PersonaMayorOrganizacionId(idPersonaMayor, idOrganizacion);

        PersonaMayorOrganizacion relacionExistente =
                relacionRepository.findById(idRelacion).orElse(null);

        if (relacionExistente != null) {

            if ("ACEPTADA".equals(relacionExistente.getEstado())) {
                return ResponseEntity.badRequest()
                        .body("Esta persona mayor ya está asociada a la organización");
            }

            if ("PENDIENTE".equals(relacionExistente.getEstado())) {
                if (relacionExistente.laEnvioLaPersonaMayor()) {
                    // Ella ya había pedido unirse: invitarla es aceptarla.
                    relacionExistente.setEstado("ACEPTADA");
                    relacionRepository.saveAndFlush(relacionExistente);
                    return ResponseEntity.ok("Esta persona mayor ya había pedido unirse: quedó vinculada");
                }

                return ResponseEntity.badRequest()
                        .body("Ya existe una solicitud pendiente para esta persona mayor");
            }

            relacionExistente.setEstado("PENDIENTE");
            relacionExistente.setSolicitadaPor(PersonaMayorOrganizacion.ORGANIZACION);
            relacionRepository.saveAndFlush(relacionExistente);

            return ResponseEntity.ok("Solicitud de asociación enviada correctamente");
        }

        PersonaMayorOrganizacion relacion =
                new PersonaMayorOrganizacion(idPersonaMayor, idOrganizacion);

        relacion.setEstado("PENDIENTE");
        relacion.setSolicitadaPor(PersonaMayorOrganizacion.ORGANIZACION);
        relacionRepository.saveAndFlush(relacion);

        return ResponseEntity.ok("Solicitud de asociación enviada correctamente");
    }

    /** Personas mayores que pidieron unirse a la organización y aún no tienen respuesta. */
    @GetMapping("/solicitudes")
    public ResponseEntity<?> obtenerSolicitudes(
            @RequestHeader("X-User-Id") Integer idUsuarioOrganizacion
    ) {
        Integer idOrganizacion = obtenerIdOrganizacion(idUsuarioOrganizacion);

        if (idOrganizacion == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("El usuario no tiene una organización asociada");
        }

        return ResponseEntity.ok(mapearAPersonaMayor(
                relacionRepository.findById_IdOrganizacionAndEstado(idOrganizacion, "PENDIENTE")
                        .stream()
                        .filter(PersonaMayorOrganizacion::laEnvioLaPersonaMayor)
                        .toList()));
    }

    @PutMapping("/solicitudes/{idPersonaMayor}/aceptar")
    public ResponseEntity<?> aceptarSolicitud(
            @RequestHeader("X-User-Id") Integer idUsuarioOrganizacion,
            @PathVariable Integer idPersonaMayor
    ) {
        return responderSolicitud(idUsuarioOrganizacion, idPersonaMayor, "ACEPTADA", "aceptada");
    }

    @PutMapping("/solicitudes/{idPersonaMayor}/rechazar")
    public ResponseEntity<?> rechazarSolicitud(
            @RequestHeader("X-User-Id") Integer idUsuarioOrganizacion,
            @PathVariable Integer idPersonaMayor
    ) {
        return responderSolicitud(idUsuarioOrganizacion, idPersonaMayor, "RECHAZADA", "rechazada");
    }

    /** Quita el vínculo con una persona mayor, esté en el estado que esté. */
    @DeleteMapping("/{idPersonaMayor}")
    public ResponseEntity<?> cancelarAsociacion(
            @RequestHeader("X-User-Id") Integer idUsuarioOrganizacion,
            @PathVariable Integer idPersonaMayor
    ) {
        Integer idOrganizacion = obtenerIdOrganizacion(idUsuarioOrganizacion);

        if (idOrganizacion == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("El usuario no tiene una organización asociada");
        }

        PersonaMayorOrganizacionId idRelacion =
                new PersonaMayorOrganizacionId(idPersonaMayor, idOrganizacion);

        PersonaMayorOrganizacion relacion =
                relacionRepository.findById(idRelacion).orElse(null);

        if (relacion == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No existe una asociación registrada");
        }

        relacionRepository.delete(relacion);
        relacionRepository.flush();

        return ResponseEntity.ok("Asociación cancelada correctamente");
    }

    /** Acepta o rechaza la solicitud de una persona mayor; solo si sigue PENDIENTE y la envió ella. */
    private ResponseEntity<?> responderSolicitud(
            Integer idUsuarioOrganizacion,
            Integer idPersonaMayor,
            String nuevoEstado,
            String participio
    ) {
        Integer idOrganizacion = obtenerIdOrganizacion(idUsuarioOrganizacion);

        if (idOrganizacion == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("El usuario no tiene una organización asociada");
        }

        PersonaMayorOrganizacion relacion = relacionRepository
                .findById(new PersonaMayorOrganizacionId(idPersonaMayor, idOrganizacion))
                .orElse(null);

        if (relacion == null || !relacion.laEnvioLaPersonaMayor()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No se encontró la solicitud de esta persona mayor");
        }

        if (!"PENDIENTE".equals(relacion.getEstado())) {
            return ResponseEntity.badRequest().body("Esta solicitud ya fue procesada");
        }

        relacion.setEstado(nuevoEstado);
        relacionRepository.saveAndFlush(relacion);

        return ResponseEntity.ok("Solicitud " + participio + " correctamente");
    }

    /** Organización de la cuenta, o null si el usuario no es una organización. */
    private Integer obtenerIdOrganizacion(Integer idUsuario) {
        return usuarioLookupRepository.findById(idUsuario)
                .map(UsuarioLookup::getIdOrganizacion)
                .orElse(null);
    }

    private List<PersonaMayorResponse> mapearAPersonaMayor(List<PersonaMayorOrganizacion> relaciones) {
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
