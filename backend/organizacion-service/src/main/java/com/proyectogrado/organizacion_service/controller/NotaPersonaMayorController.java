package com.proyectogrado.organizacion_service.controller;

import com.proyectogrado.organizacion_service.model.NotaPersonaMayor;
import com.proyectogrado.organizacion_service.model.PersonaMayorOrganizacionId;
import com.proyectogrado.organizacion_service.model.UsuarioLookup;
import com.proyectogrado.organizacion_service.repository.NotaPersonaMayorRepository;
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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Notas de la organización sobre cada una de sus personas mayores: verlas,
 * agregar, editar y eliminar. Solo para personas con vínculo ACEPTADA, y
 * cada organización ve únicamente las suyas.
 *
 * El X-User-Id es el de la cuenta que inició sesión, no el id_organizacion.
 */
@RestController
@RequestMapping("/api/organizacion/personas-mayores/{idPersonaMayor}/notas")
public class NotaPersonaMayorController {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    /** Lo que llega del formulario. */
    public record NotaRequest(String texto) {
    }

    /** Una nota, con quién la escribió y si se editó. */
    public record NotaResponse(
            Integer idNota,
            String texto,
            String autor,
            LocalDateTime fechaCreacion,
            LocalDateTime fechaActualizacion,
            boolean editada
    ) {
    }

    private final NotaPersonaMayorRepository notaRepository;
    private final PersonaMayorOrganizacionRepository relacionRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;

    public NotaPersonaMayorController(
            NotaPersonaMayorRepository notaRepository,
            PersonaMayorOrganizacionRepository relacionRepository,
            UsuarioLookupRepository usuarioLookupRepository
    ) {
        this.notaRepository = notaRepository;
        this.relacionRepository = relacionRepository;
        this.usuarioLookupRepository = usuarioLookupRepository;
    }

    /** Notas de la persona mayor, la más reciente primero. */
    @GetMapping
    public ResponseEntity<?> listar(
            @RequestHeader("X-User-Id") Integer idUsuario,
            @PathVariable Integer idPersonaMayor
    ) {
        Integer idOrganizacion = idOrganizacionVinculada(idUsuario, idPersonaMayor);
        if (idOrganizacion == null) {
            return sinVinculo();
        }

        Map<Integer, String> autores = new HashMap<>();
        List<NotaResponse> notas = notaRepository
                .findByIdOrganizacionAndIdPersonaMayorOrderByFechaCreacionDesc(idOrganizacion, idPersonaMayor)
                .stream()
                .map(nota -> aRespuesta(nota, autores))
                .toList();

        return ResponseEntity.ok(notas);
    }

    @PostMapping
    public ResponseEntity<?> crear(
            @RequestHeader("X-User-Id") Integer idUsuario,
            @PathVariable Integer idPersonaMayor,
            @RequestBody NotaRequest request
    ) {
        Integer idOrganizacion = idOrganizacionVinculada(idUsuario, idPersonaMayor);
        if (idOrganizacion == null) {
            return sinVinculo();
        }

        String error = revisarTexto(request);
        if (error != null) {
            return ResponseEntity.badRequest().body(error);
        }

        NotaPersonaMayor nota = notaRepository.save(new NotaPersonaMayor(
                idOrganizacion, idPersonaMayor, request.texto().trim(), idUsuario, LocalDateTime.now(ZONA)));

        return ResponseEntity.status(HttpStatus.CREATED).body(aRespuesta(nota, new HashMap<>()));
    }

    @PutMapping("/{idNota}")
    public ResponseEntity<?> editar(
            @RequestHeader("X-User-Id") Integer idUsuario,
            @PathVariable Integer idPersonaMayor,
            @PathVariable Integer idNota,
            @RequestBody NotaRequest request
    ) {
        Integer idOrganizacion = idOrganizacionVinculada(idUsuario, idPersonaMayor);
        if (idOrganizacion == null) {
            return sinVinculo();
        }

        NotaPersonaMayor nota = notaDe(idNota, idOrganizacion, idPersonaMayor);
        if (nota == null) {
            return noEncontrada();
        }

        String error = revisarTexto(request);
        if (error != null) {
            return ResponseEntity.badRequest().body(error);
        }

        nota.editar(request.texto().trim(), idUsuario, LocalDateTime.now(ZONA));

        return ResponseEntity.ok(aRespuesta(notaRepository.save(nota), new HashMap<>()));
    }

    @DeleteMapping("/{idNota}")
    public ResponseEntity<?> eliminar(
            @RequestHeader("X-User-Id") Integer idUsuario,
            @PathVariable Integer idPersonaMayor,
            @PathVariable Integer idNota
    ) {
        Integer idOrganizacion = idOrganizacionVinculada(idUsuario, idPersonaMayor);
        if (idOrganizacion == null) {
            return sinVinculo();
        }

        NotaPersonaMayor nota = notaDe(idNota, idOrganizacion, idPersonaMayor);
        if (nota == null) {
            return noEncontrada();
        }

        notaRepository.delete(nota);

        return ResponseEntity.ok("Nota eliminada");
    }

    /**
     * Organización de la cuenta, solo si tiene a la persona mayor con
     * vínculo ACEPTADA; si no, null.
     */
    private Integer idOrganizacionVinculada(Integer idUsuario, Integer idPersonaMayor) {
        Integer idOrganizacion = usuarioLookupRepository.findById(idUsuario)
                .map(UsuarioLookup::getIdOrganizacion)
                .orElse(null);
        if (idOrganizacion == null) {
            return null;
        }

        boolean vinculada = relacionRepository
                .findById(new PersonaMayorOrganizacionId(idPersonaMayor, idOrganizacion))
                .map(relacion -> "ACEPTADA".equals(relacion.getEstado()))
                .orElse(false);

        return vinculada ? idOrganizacion : null;
    }

    /** La nota, solo si es de esta organización y de esta persona mayor. */
    private NotaPersonaMayor notaDe(Integer idNota, Integer idOrganizacion, Integer idPersonaMayor) {
        return notaRepository.findById(idNota)
                .filter(nota -> nota.getIdOrganizacion().equals(idOrganizacion)
                        && nota.getIdPersonaMayor().equals(idPersonaMayor))
                .orElse(null);
    }

    private static String revisarTexto(NotaRequest request) {
        if (request == null || request.texto() == null || request.texto().isBlank()) {
            return "Escribe la nota";
        }

        if (request.texto().trim().length() > NotaPersonaMayor.MAX_TEXTO) {
            return "La nota puede tener máximo " + NotaPersonaMayor.MAX_TEXTO + " caracteres";
        }

        return null;
    }

    /** autores: nombres ya consultados, para no repetir la consulta en una lista. */
    private NotaResponse aRespuesta(NotaPersonaMayor nota, Map<Integer, String> autores) {
        String autor = autores.computeIfAbsent(nota.getIdAutor(), id -> usuarioLookupRepository.findById(id)
                .map(UsuarioLookup::getNombreUsuario)
                .orElse(null));

        return new NotaResponse(
                nota.getIdNota(), nota.getTexto(), autor,
                nota.getFechaCreacion(), nota.getFechaActualizacion(), nota.fueEditada());
    }

    private static ResponseEntity<String> sinVinculo() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Solo puedes tomar notas de las personas mayores vinculadas a tu organización");
    }

    private static ResponseEntity<String> noEncontrada() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No se encontró la nota");
    }
}
