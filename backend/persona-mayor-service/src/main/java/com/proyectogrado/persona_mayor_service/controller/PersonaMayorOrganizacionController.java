package com.proyectogrado.persona_mayor_service.controller;

import com.proyectogrado.persona_mayor_service.dto.OrganizacionSolicitudResponse;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorOrganizacion;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorOrganizacionId;
import com.proyectogrado.persona_mayor_service.model.UsuarioLookup;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorOrganizacionRepository;
import com.proyectogrado.persona_mayor_service.repository.UsuarioLookupRepository;
import com.proyectogrado.persona_mayor_service.repository.AcompananteLookupRepository;
import com.proyectogrado.persona_mayor_service.dto.AcompananteResponse;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorAcompananteRepository;
import com.proyectogrado.persona_mayor_service.model.AcompananteLookup;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorAcompanante;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Vínculos entre personas mayores y organizaciones. La solicitud la puede
 * enviar la organización (la persona mayor la acepta o la rechaza aquí) o la
 * persona mayor (la responde la organización en organizacion-service). El
 * campo solicitada_por dice quién la envió. Mientras no esté ACEPTADA, la
 * organización no ve a esa persona.
 *
 * Por el gateway llegan aquí /api/persona-mayor/organizaciones/** y
 * /api/organizacion/personas-mayores/{id}/acompanantes. El resto de
 * /api/organizacion/personas-mayores (ver, invitar y desvincular personas)
 * lo atiende organizacion-service.
 */
@RestController
public class PersonaMayorOrganizacionController {

    private final PersonaMayorOrganizacionRepository relacionRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;
    private final AcompananteLookupRepository acompananteLookupRepository;
    private final PersonaMayorAcompananteRepository personaMayorAcompananteRepository;
    

public PersonaMayorOrganizacionController(
        PersonaMayorOrganizacionRepository relacionRepository,
        UsuarioLookupRepository usuarioLookupRepository,
        AcompananteLookupRepository acompananteLookupRepository,
        PersonaMayorAcompananteRepository personaMayorAcompananteRepository
) {
    this.relacionRepository = relacionRepository;
    this.usuarioLookupRepository = usuarioLookupRepository;
    this.acompananteLookupRepository = acompananteLookupRepository;
    this.personaMayorAcompananteRepository = personaMayorAcompananteRepository;
}

    /**
     * Acompañantes aceptados de una persona mayor, para la organización. Solo
     * responde si la persona tiene un vínculo aceptado con esa organización.
     */
@GetMapping("/api/organizacion/personas-mayores/{idPersonaMayor}/acompanantes")
public ResponseEntity<?> obtenerAcompanantesPersonaMayor(
        @RequestHeader("X-User-Id") Integer idUsuarioOrganizacion,
        @PathVariable Integer idPersonaMayor
) {

    Integer idOrganizacion = obtenerIdOrganizacion(idUsuarioOrganizacion);

    if (idOrganizacion == null) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("El usuario no tiene una organización asociada");
    }

    // La persona mayor tiene que estar vinculada a esta organización.
    PersonaMayorOrganizacionId idRelacion =
            new PersonaMayorOrganizacionId(
                    idPersonaMayor,
                    idOrganizacion
            );

    PersonaMayorOrganizacion relacion =
            relacionRepository.findById(idRelacion).orElse(null);

    if (relacion == null || !"ACEPTADA".equals(relacion.getEstado())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("La persona mayor no está asociada a esta organización");
    }

    List<PersonaMayorAcompanante> relaciones =
            personaMayorAcompananteRepository
                    .findById_IdPersonaMayorAndEstado(
                            idPersonaMayor,
                            "ACEPTADA"
                    );

    List<AcompananteResponse> acompanantes = relaciones.stream()
            .map(relacionAcompanante -> {

                Integer idAcompanante =
                        relacionAcompanante.getId().getIdAcompanante();

                UsuarioLookup usuario =
                        usuarioLookupRepository
                                .findById(idAcompanante)
                                .orElse(null);

                AcompananteLookup acompanante =
                        acompananteLookupRepository
                                .findById(idAcompanante)
                                .orElse(null);

                if (usuario == null) {
                    return null;
                }

                return new AcompananteResponse(
                        idAcompanante,
                        usuario.getNombreUsuario(),
                        usuario.getCelular(),
                        acompanante != null
                                ? acompanante.getRelacion()
                                : null
                );
            })
            .filter(java.util.Objects::nonNull)
            .toList();

    return ResponseEntity.ok(acompanantes);
}

    /** Organizaciones con las que la persona mayor tiene un vínculo aceptado. */
    @GetMapping("/api/persona-mayor/organizaciones")
    public ResponseEntity<List<OrganizacionSolicitudResponse>> obtenerOrganizaciones(
            @RequestHeader("X-User-Id") Integer idPersonaMayor
    ) {
        return ResponseEntity.ok(listarOrganizacionesPorEstado(idPersonaMayor, "ACEPTADA"));
    }

    /** Invitaciones de organizaciones que la persona mayor aún no ha respondido. */
    @GetMapping("/api/persona-mayor/organizaciones/solicitudes")
    public ResponseEntity<List<OrganizacionSolicitudResponse>> obtenerSolicitudes(
            @RequestHeader("X-User-Id") Integer idPersonaMayor
    ) {
        return ResponseEntity.ok(listarOrganizaciones(
                relacionRepository.findById_IdPersonaMayorAndEstado(idPersonaMayor, "PENDIENTE")
                        .stream()
                        .filter(relacion -> !relacion.laEnvioLaPersonaMayor())
                        .toList()));
    }

    /** Solicitudes que envió la persona mayor y la organización aún no responde. */
    @GetMapping("/api/persona-mayor/organizaciones/solicitudes/enviadas")
    public ResponseEntity<List<OrganizacionSolicitudResponse>> obtenerSolicitudesEnviadas(
            @RequestHeader("X-User-Id") Integer idPersonaMayor
    ) {
        return ResponseEntity.ok(listarOrganizaciones(
                relacionRepository.findById_IdPersonaMayorAndEstado(idPersonaMayor, "PENDIENTE")
                        .stream()
                        .filter(PersonaMayorOrganizacion::laEnvioLaPersonaMayor)
                        .toList()));
    }

    /**
     * La persona mayor pide unirse a una organización; la organización la
     * acepta o la rechaza desde su panel. Si la organización ya la había
     * invitado, quedan vinculadas de una vez. Si antes la rechazaron, la
     * solicitud vuelve a PENDIENTE.
     */
    @PostMapping("/api/persona-mayor/organizaciones/{idOrganizacion}/solicitud")
    public ResponseEntity<String> solicitarVinculacion(
            @RequestHeader("X-User-Id") Integer idPersonaMayor,
            @PathVariable Integer idOrganizacion
    ) {
        if (usuarioLookupRepository.findByIdOrganizacion(idOrganizacion).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No se encontró la organización");
        }

        PersonaMayorOrganizacion relacion = relacionRepository
                .findById(new PersonaMayorOrganizacionId(idPersonaMayor, idOrganizacion))
                .orElse(null);

        if (relacion != null && "ACEPTADA".equals(relacion.getEstado())) {
            return ResponseEntity.badRequest().body("Ya perteneces a esta organización");
        }

        if (relacion != null && PersonaMayorOrganizacion.INACTIVA.equals(relacion.getEstado())) {
            return ResponseEntity.badRequest()
                    .body("Ya tienes esta organización como inactiva: reactívala desde Mis organizaciones");
        }

        // La cuenta de una de las dos partes está inactiva: no se sobrescribe.
        if (relacion != null && !"PENDIENTE".equals(relacion.getEstado())
                && !"RECHAZADA".equals(relacion.getEstado())) {
            return ResponseEntity.badRequest().body("Esta organización no está disponible en este momento");
        }

        if (relacion != null && "PENDIENTE".equals(relacion.getEstado())) {
            if (relacion.laEnvioLaPersonaMayor()) {
                return ResponseEntity.badRequest().body("Ya enviaste una solicitud a esta organización");
            }

            // La organización ya la había invitado: pedir unirse es aceptar.
            relacion.setEstado("ACEPTADA");
            relacionRepository.saveAndFlush(relacion);
            return ResponseEntity.ok("Esta organización ya te había invitado: quedaste vinculado");
        }

        if (relacion == null) {
            relacion = new PersonaMayorOrganizacion(idPersonaMayor, idOrganizacion);
        }

        relacion.setEstado("PENDIENTE");
        relacion.setSolicitadaPor(PersonaMayorOrganizacion.PERSONA_MAYOR);
        relacionRepository.saveAndFlush(relacion);

        return ResponseEntity.ok("Solicitud enviada. La organización te responderá pronto.");
    }

    @PutMapping("/api/persona-mayor/organizaciones/solicitudes/{idOrganizacion}/aceptar")
    public ResponseEntity<String> aceptarSolicitudOrganizacion(
            @RequestHeader("X-User-Id") Integer idPersonaMayor,
            @PathVariable Integer idOrganizacion
    ) {
        return cambiarEstadoOrganizacion(idPersonaMayor, idOrganizacion, "ACEPTADA", "aceptada");
    }

    @PutMapping("/api/persona-mayor/organizaciones/solicitudes/{idOrganizacion}/rechazar")
    public ResponseEntity<String> rechazarSolicitudOrganizacion(
            @RequestHeader("X-User-Id") Integer idPersonaMayor,
            @PathVariable Integer idOrganizacion
    ) {
        return cambiarEstadoOrganizacion(idPersonaMayor, idOrganizacion, "RECHAZADA", "rechazada");
    }

    /** Organizaciones con las que la persona mayor pausó el vínculo. */
    @GetMapping("/api/persona-mayor/organizaciones/inactivas")
    public ResponseEntity<List<OrganizacionSolicitudResponse>> obtenerOrganizacionesInactivas(
            @RequestHeader("X-User-Id") Integer idPersonaMayor
    ) {
        return ResponseEntity.ok(listarOrganizacionesPorEstado(idPersonaMayor, PersonaMayorOrganizacion.INACTIVA));
    }

    /**
     * La persona mayor pausa un vínculo aceptado: la organización deja de
     * verla y ella deja de ver las actividades y avisos de la organización.
     */
    @PutMapping("/api/persona-mayor/organizaciones/{idOrganizacion}/inactivar")
    public ResponseEntity<String> inactivarAsociacion(
            @RequestHeader("X-User-Id") Integer idPersonaMayor,
            @PathVariable Integer idOrganizacion
    ) {
        PersonaMayorOrganizacion relacion = relacionRepository
                .findById(new PersonaMayorOrganizacionId(idPersonaMayor, idOrganizacion))
                .orElse(null);

        if (relacion == null || !"ACEPTADA".equals(relacion.getEstado())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No perteneces a esta organización");
        }

        relacion.setEstado(PersonaMayorOrganizacion.INACTIVA);
        relacionRepository.saveAndFlush(relacion);

        return ResponseEntity.ok("Inactivaste tu vínculo con la organización. Puedes reactivarlo cuando quieras.");
    }

    /**
     * Reactiva un vínculo pausado. Si la cuenta de la organización está
     * inactiva, queda como CUENTA_INACTIVA y vuelve cuando la reactive.
     */
    @PutMapping("/api/persona-mayor/organizaciones/{idOrganizacion}/reactivar")
    public ResponseEntity<String> reactivarAsociacion(
            @RequestHeader("X-User-Id") Integer idPersonaMayor,
            @PathVariable Integer idOrganizacion
    ) {
        PersonaMayorOrganizacion relacion = relacionRepository
                .findById(new PersonaMayorOrganizacionId(idPersonaMayor, idOrganizacion))
                .orElse(null);

        if (relacion == null || !PersonaMayorOrganizacion.INACTIVA.equals(relacion.getEstado())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No tienes un vínculo inactivo con esta organización");
        }

        boolean organizacionActiva = usuarioLookupRepository.findByIdOrganizacion(idOrganizacion)
                .stream()
                .anyMatch(UsuarioLookup::estaActivo);

        relacion.setEstado(organizacionActiva ? "ACEPTADA" : PersonaMayorOrganizacion.CUENTA_INACTIVA);
        relacionRepository.saveAndFlush(relacion);

        return ResponseEntity.ok("Reactivaste tu vínculo con la organización");
    }

    /** La persona mayor deshace el vínculo con una organización. */
    @DeleteMapping("/api/persona-mayor/organizaciones/{idOrganizacion}")
    public ResponseEntity<?> cancelarAsociacionDesdePersonaMayor(
            @RequestHeader("X-User-Id") Integer idPersonaMayor,
            @PathVariable Integer idOrganizacion
    ) {
        return eliminarRelacion(new PersonaMayorOrganizacionId(idPersonaMayor, idOrganizacion));
    }

    /** Organización a la que pertenece la cuenta, o null si no es una cuenta de organización. */
    private Integer obtenerIdOrganizacion(Integer idUsuario) {
        return usuarioLookupRepository.findById(idUsuario)
                .map(UsuarioLookup::getIdOrganizacion)
                .orElse(null);
    }

    /** Acepta o rechaza una solicitud; solo se puede si sigue PENDIENTE. */
    private ResponseEntity<String> cambiarEstadoOrganizacion(
            Integer idPersonaMayor,
            Integer idOrganizacion,
            String nuevoEstado,
            String participio
    ) {
        PersonaMayorOrganizacion relacion = relacionRepository
                .findById(new PersonaMayorOrganizacionId(idPersonaMayor, idOrganizacion))
                .orElse(null);

        if (relacion == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No se encontró la solicitud de esta organización");
        }

        if (!"PENDIENTE".equals(relacion.getEstado())) {
            return ResponseEntity.badRequest().body("Esta solicitud ya fue procesada");
        }

        // La que envió la persona mayor la responde la organización, no ella.
        if (relacion.laEnvioLaPersonaMayor()) {
            return ResponseEntity.badRequest().body("Esta solicitud la responde la organización");
        }

        relacion.setEstado(nuevoEstado);
        relacionRepository.saveAndFlush(relacion);

        return ResponseEntity.ok("Solicitud " + participio + " correctamente");
    }

    private ResponseEntity<?> eliminarRelacion(PersonaMayorOrganizacionId idRelacion) {

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

    /**
     * Organizaciones de la persona mayor en el estado indicado. El nombre y
     * el contacto salen de la primera cuenta de usuario de cada organización.
     */
    private List<OrganizacionSolicitudResponse> listarOrganizacionesPorEstado(
            Integer idPersonaMayor,
            String estado
    ) {
        return listarOrganizaciones(relacionRepository.findById_IdPersonaMayorAndEstado(idPersonaMayor, estado));
    }

    /** Nombre y contacto de las organizaciones de esos vínculos. */
    private List<OrganizacionSolicitudResponse> listarOrganizaciones(List<PersonaMayorOrganizacion> relaciones) {
        return relaciones.stream()
                .map(relacion -> {
                    Integer idOrganizacion = relacion.getId().getIdOrganizacion();

                    List<UsuarioLookup> usuariosOrganizacion =
                            usuarioLookupRepository.findByIdOrganizacion(idOrganizacion);

                    UsuarioLookup usuarioOrganizacion =
                            usuariosOrganizacion.isEmpty() ? null : usuariosOrganizacion.get(0);

                    return new OrganizacionSolicitudResponse(
                            idOrganizacion,
                            usuarioOrganizacion != null ? usuarioOrganizacion.getNombreUsuario() : "Organización",
                            usuarioOrganizacion != null ? usuarioOrganizacion.getCelular() : null,
                            usuarioOrganizacion != null ? usuarioOrganizacion.getCorreo() : null
                    );
                })
                .toList();
    }


}
