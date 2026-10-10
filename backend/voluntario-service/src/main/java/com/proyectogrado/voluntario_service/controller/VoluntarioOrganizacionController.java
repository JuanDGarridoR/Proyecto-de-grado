package com.proyectogrado.voluntario_service.controller;

import com.proyectogrado.voluntario_service.dto.OrganizacionVoluntarioResponse;
import com.proyectogrado.voluntario_service.dto.VoluntarioOrganizacionResponse;
import com.proyectogrado.voluntario_service.model.OrganizacionLookup;
import com.proyectogrado.voluntario_service.model.UsuarioLookup;
import com.proyectogrado.voluntario_service.model.VoluntarioOrganizacion;
import com.proyectogrado.voluntario_service.model.VoluntarioOrganizacionId;
import com.proyectogrado.voluntario_service.repository.OrganizacionLookupRepository;
import com.proyectogrado.voluntario_service.repository.UsuarioLookupRepository;
import com.proyectogrado.voluntario_service.repository.VoluntarioOrganizacionRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vinculación de voluntarios con organizaciones:
 * - El voluntario consulta las organizaciones y envía solicitudes.
 * - La organización acepta o rechaza las solicitudes y ve sus voluntarios.
 *
 * Igual que PersonaMayorOrganizacionController en persona-mayor-service,
 * aquí viven los dos lados del vínculo. El gateway enruta
 * /api/organizacion/voluntarios/** a este servicio.
 *
 * El id del usuario autenticado llega en el encabezado X-User-Id, que pone
 * el gateway después de validar el token.
 */
@RestController
public class VoluntarioOrganizacionController {

    private final VoluntarioOrganizacionRepository relacionRepository;
    private final OrganizacionLookupRepository organizacionLookupRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;

    public VoluntarioOrganizacionController(
            VoluntarioOrganizacionRepository relacionRepository,
            OrganizacionLookupRepository organizacionLookupRepository,
            UsuarioLookupRepository usuarioLookupRepository
    ) {
        this.relacionRepository = relacionRepository;
        this.organizacionLookupRepository = organizacionLookupRepository;
        this.usuarioLookupRepository = usuarioLookupRepository;
    }

    // Lado del voluntario: organizaciones y solicitudes

    /**
     * Todas las organizaciones, cada una con el estado del vínculo del
     * voluntario (null si nunca ha solicitado). Con esto el frontend arma
     * "mis organizaciones", "mis solicitudes" y "disponibles".
     */
    @GetMapping("/api/voluntario/organizaciones")
    public ResponseEntity<?> listarOrganizaciones(
            @RequestHeader("X-User-Id") Integer idVoluntario
    ) {
        if (!esVoluntario(idVoluntario)) {
            return noEsVoluntario();
        }

        Map<Integer, String> estados = relacionRepository.findById_IdVoluntario(idVoluntario)
                .stream()
                .collect(Collectors.toMap(
                        relacion -> relacion.getId().getIdOrganizacion(),
                        VoluntarioOrganizacion::getEstado));

        // Las organizaciones inactivas no aparecen, ni los vínculos que
        // auth-service ocultó porque la cuenta del voluntario está inactiva.
        List<OrganizacionVoluntarioResponse> respuesta = organizacionLookupRepository.findActivas()
                .stream()
                .filter(organizacion -> !ocultoPorCuentaInactiva(estados.get(organizacion.getIdOrganizacion())))
                .sorted(Comparator.comparing(
                        OrganizacionLookup::getNombre,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(organizacion -> aOrganizacion(
                        organizacion,
                        estados.get(organizacion.getIdOrganizacion())))
                .toList();

        return ResponseEntity.ok(respuesta);
    }

    /**
     * Envía una solicitud de vinculación. Si la organización la había
     * rechazado antes, la solicitud vuelve a quedar PENDIENTE.
     */
    @PostMapping("/api/voluntario/organizaciones/{idOrganizacion}/solicitud")
    public ResponseEntity<String> solicitarVinculacion(
            @RequestHeader("X-User-Id") Integer idVoluntario,
            @PathVariable Integer idOrganizacion
    ) {
        if (!esVoluntario(idVoluntario)) {
            return noEsVoluntario();
        }

        if (!organizacionLookupRepository.existsById(idOrganizacion)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("La organización no existe");
        }

        VoluntarioOrganizacion relacion = relacionRepository
                .findById(new VoluntarioOrganizacionId(idVoluntario, idOrganizacion))
                .orElse(null);

        if (relacion != null) {
            if (VoluntarioOrganizacion.ACEPTADA.equals(relacion.getEstado())) {
                return ResponseEntity.badRequest()
                        .body("Ya estás vinculado a esta organización");
            }

            if (VoluntarioOrganizacion.PENDIENTE.equals(relacion.getEstado())) {
                return ResponseEntity.badRequest()
                        .body("Ya tienes una solicitud pendiente con esta organización");
            }

            if (VoluntarioOrganizacion.INACTIVA.equals(relacion.getEstado())) {
                return ResponseEntity.badRequest()
                        .body("Ya tienes esta organización como inactiva: reactívala desde Mis organizaciones");
            }

            // La cuenta de una de las dos partes está inactiva: no se sobrescribe.
            if (!VoluntarioOrganizacion.RECHAZADA.equals(relacion.getEstado())) {
                return ResponseEntity.badRequest()
                        .body("Esta organización no está disponible en este momento");
            }

            // Rechazada antes: se puede volver a solicitar
            relacion.setEstado(VoluntarioOrganizacion.PENDIENTE);
        } else {
            relacion = new VoluntarioOrganizacion(idVoluntario, idOrganizacion);
        }

        relacionRepository.saveAndFlush(relacion);

        return ResponseEntity.ok("Solicitud de vinculación enviada correctamente");
    }

    /**
     * Borra el vínculo, sea cual sea su estado: cancela una solicitud
     * pendiente, descarta una rechazada o desvincula al voluntario.
     */
    @DeleteMapping("/api/voluntario/organizaciones/{idOrganizacion}")
    public ResponseEntity<String> eliminarVinculoDesdeVoluntario(
            @RequestHeader("X-User-Id") Integer idVoluntario,
            @PathVariable Integer idOrganizacion
    ) {
        VoluntarioOrganizacion relacion = relacionRepository
                .findById(new VoluntarioOrganizacionId(idVoluntario, idOrganizacion))
                .orElse(null);

        if (relacion == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No tienes ningún vínculo ni solicitud con esta organización");
        }

        String mensaje = switch (relacion.getEstado()) {
            case VoluntarioOrganizacion.PENDIENTE -> "Solicitud cancelada correctamente";
            case VoluntarioOrganizacion.ACEPTADA, VoluntarioOrganizacion.INACTIVA ->
                    "Te desvinculaste de la organización correctamente";
            default -> "Solicitud eliminada correctamente";
        };

        relacionRepository.delete(relacion);
        relacionRepository.flush();

        return ResponseEntity.ok(mensaje);
    }

    /**
     * El voluntario pausa un vínculo aceptado: la organización deja de verlo
     * y él deja de ver lo de la organización hasta que lo reactive.
     */
    @PutMapping("/api/voluntario/organizaciones/{idOrganizacion}/inactivar")
    public ResponseEntity<String> inactivarVinculo(
            @RequestHeader("X-User-Id") Integer idVoluntario,
            @PathVariable Integer idOrganizacion
    ) {
        VoluntarioOrganizacion relacion = relacionRepository
                .findById(new VoluntarioOrganizacionId(idVoluntario, idOrganizacion))
                .orElse(null);

        if (relacion == null || !VoluntarioOrganizacion.ACEPTADA.equals(relacion.getEstado())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No estás vinculado a esta organización");
        }

        relacion.setEstado(VoluntarioOrganizacion.INACTIVA);
        relacionRepository.saveAndFlush(relacion);

        return ResponseEntity.ok("Inactivaste tu vínculo con la organización. Puedes reactivarlo cuando quieras.");
    }

    /**
     * Reactiva un vínculo pausado. Si la cuenta de la organización está
     * inactiva, queda como CUENTA_INACTIVA y vuelve cuando la reactive.
     */
    @PutMapping("/api/voluntario/organizaciones/{idOrganizacion}/reactivar")
    public ResponseEntity<String> reactivarVinculo(
            @RequestHeader("X-User-Id") Integer idVoluntario,
            @PathVariable Integer idOrganizacion
    ) {
        VoluntarioOrganizacion relacion = relacionRepository
                .findById(new VoluntarioOrganizacionId(idVoluntario, idOrganizacion))
                .orElse(null);

        if (relacion == null || !VoluntarioOrganizacion.INACTIVA.equals(relacion.getEstado())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No tienes un vínculo inactivo con esta organización");
        }

        boolean organizacionActiva = usuarioLookupRepository.findCuentasOrganizacion(idOrganizacion)
                .stream()
                .anyMatch(UsuarioLookup::estaActivo);

        relacion.setEstado(organizacionActiva
                ? VoluntarioOrganizacion.ACEPTADA
                : VoluntarioOrganizacion.CUENTA_INACTIVA);
        relacionRepository.saveAndFlush(relacion);

        return ResponseEntity.ok("Reactivaste tu vínculo con la organización");
    }

    /** Vínculos que auth-service ocultó porque una de las dos cuentas está inactiva. */
    private static boolean ocultoPorCuentaInactiva(String estado) {
        return VoluntarioOrganizacion.CUENTA_INACTIVA.equals(estado)
                || VoluntarioOrganizacion.PENDIENTE_CUENTA_INACTIVA.equals(estado);
    }

    // Lado de la organización: voluntarios y solicitudes

    /** Voluntarios vinculados (solicitud ACEPTADA), ordenados por nombre. */
    @GetMapping("/api/organizacion/voluntarios")
    public ResponseEntity<?> listarVoluntarios(
            @RequestHeader("X-User-Id") Integer idUsuarioOrganizacion
    ) {
        return listarPorEstado(idUsuarioOrganizacion, VoluntarioOrganizacion.ACEPTADA);
    }

    /** Solicitudes PENDIENTES de voluntarios, ordenadas por nombre. */
    @GetMapping("/api/organizacion/voluntarios/solicitudes")
    public ResponseEntity<?> listarSolicitudes(
            @RequestHeader("X-User-Id") Integer idUsuarioOrganizacion
    ) {
        return listarPorEstado(idUsuarioOrganizacion, VoluntarioOrganizacion.PENDIENTE);
    }

    /** Acepta una solicitud pendiente; si ya se respondió, responde 400. */
    @PutMapping("/api/organizacion/voluntarios/solicitudes/{idVoluntario}/aceptar")
    public ResponseEntity<String> aceptarSolicitud(
            @RequestHeader("X-User-Id") Integer idUsuarioOrganizacion,
            @PathVariable Integer idVoluntario
    ) {
        return responderSolicitud(idUsuarioOrganizacion, idVoluntario,
                VoluntarioOrganizacion.ACEPTADA, "Solicitud aceptada correctamente");
    }

    /** Rechaza una solicitud pendiente; el voluntario puede volver a enviarla. */
    @PutMapping("/api/organizacion/voluntarios/solicitudes/{idVoluntario}/rechazar")
    public ResponseEntity<String> rechazarSolicitud(
            @RequestHeader("X-User-Id") Integer idUsuarioOrganizacion,
            @PathVariable Integer idVoluntario
    ) {
        return responderSolicitud(idUsuarioOrganizacion, idVoluntario,
                VoluntarioOrganizacion.RECHAZADA, "Solicitud rechazada correctamente");
    }

    /** Quita el vínculo con un voluntario aceptado. */
    @DeleteMapping("/api/organizacion/voluntarios/{idVoluntario}")
    public ResponseEntity<String> desvincularVoluntario(
            @RequestHeader("X-User-Id") Integer idUsuarioOrganizacion,
            @PathVariable Integer idVoluntario
    ) {
        Integer idOrganizacion = obtenerIdOrganizacion(idUsuarioOrganizacion);

        if (idOrganizacion == null) {
            return sinOrganizacion();
        }

        VoluntarioOrganizacion relacion = relacionRepository
                .findById(new VoluntarioOrganizacionId(idVoluntario, idOrganizacion))
                .orElse(null);

        if (relacion == null || !VoluntarioOrganizacion.ACEPTADA.equals(relacion.getEstado())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("El voluntario no está vinculado a esta organización");
        }

        relacionRepository.delete(relacion);
        relacionRepository.flush();

        return ResponseEntity.ok("Voluntario desvinculado correctamente");
    }

    // Métodos auxiliares

    /**
     * Se mira el rol y no la tabla voluntario: hay cuentas viejas con el rol
     * VOLUNTARIO que no tienen su fila en esa tabla.
     */
    private boolean esVoluntario(Integer idUsuario) {
        return usuarioLookupRepository.tieneRol(idUsuario, "VOLUNTARIO");
    }

    /**
     * Organización del usuario autenticado, o null si no es una cuenta de
     * organización. Se exige el rol: otros usuarios podrían tener
     * id_organizacion por datos viejos.
     */
    private Integer obtenerIdOrganizacion(Integer idUsuario) {
        if (!usuarioLookupRepository.tieneRol(idUsuario, "ORGANIZACION")) {
            return null;
        }

        return usuarioLookupRepository.findById(idUsuario)
                .map(UsuarioLookup::getIdOrganizacion)
                .orElse(null);
    }

    /** Voluntarios de la organización con ese estado, ordenados por nombre. */
    private ResponseEntity<?> listarPorEstado(Integer idUsuarioOrganizacion, String estado) {
        Integer idOrganizacion = obtenerIdOrganizacion(idUsuarioOrganizacion);

        if (idOrganizacion == null) {
            return sinOrganizacion();
        }

        List<Integer> ids = relacionRepository.findById_IdOrganizacionAndEstado(idOrganizacion, estado)
                .stream()
                .map(relacion -> relacion.getId().getIdVoluntario())
                .toList();

        Map<Integer, UsuarioLookup> usuarios = usuarioLookupRepository.findAllById(ids)
                .stream()
                .collect(Collectors.toMap(UsuarioLookup::getIdUsuario, Function.identity()));

        List<VoluntarioOrganizacionResponse> respuesta = ids.stream()
                .map(usuarios::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        UsuarioLookup::getNombreUsuario,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(usuario -> new VoluntarioOrganizacionResponse(
                        usuario.getIdUsuario(),
                        usuario.getNombreUsuario(),
                        usuario.getCelular(),
                        usuario.getCorreo()))
                .toList();

        return ResponseEntity.ok(respuesta);
    }

    /** Pasa una solicitud PENDIENTE al nuevo estado (ACEPTADA o RECHAZADA). */
    private ResponseEntity<String> responderSolicitud(
            Integer idUsuarioOrganizacion,
            Integer idVoluntario,
            String nuevoEstado,
            String mensaje
    ) {
        Integer idOrganizacion = obtenerIdOrganizacion(idUsuarioOrganizacion);

        if (idOrganizacion == null) {
            return sinOrganizacion();
        }

        VoluntarioOrganizacion relacion = relacionRepository
                .findById(new VoluntarioOrganizacionId(idVoluntario, idOrganizacion))
                .orElse(null);

        if (relacion == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No se encontró la solicitud de este voluntario");
        }

        if (!VoluntarioOrganizacion.PENDIENTE.equals(relacion.getEstado())) {
            return ResponseEntity.badRequest().body("Esta solicitud ya fue procesada");
        }

        relacion.setEstado(nuevoEstado);
        relacionRepository.saveAndFlush(relacion);

        return ResponseEntity.ok(mensaje);
    }

    /**
     * Organización tal como la ve el voluntario. El celular y el correo de
     * contacto son los de la primera cuenta de la organización.
     */
    private OrganizacionVoluntarioResponse aOrganizacion(OrganizacionLookup organizacion, String estado) {
        UsuarioLookup contacto = usuarioLookupRepository
                .findCuentasOrganizacion(organizacion.getIdOrganizacion())
                .stream()
                .findFirst()
                .orElse(null);

        return new OrganizacionVoluntarioResponse(
                organizacion.getIdOrganizacion(),
                organizacion.getNombre(),
                organizacion.getDireccion(),
                contacto != null ? contacto.getCelular() : null,
                contacto != null ? contacto.getCorreo() : null,
                estado
        );
    }

    private static ResponseEntity<String> noEsVoluntario() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Esta función es solo para voluntarios");
    }

    private static ResponseEntity<String> sinOrganizacion() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("El usuario no tiene una organización asociada");
    }
}
