package com.proyectogrado.organizacion_service.controller;

import com.proyectogrado.organizacion_service.dto.ActualizarOrganizacionRequest;
import com.proyectogrado.organizacion_service.dto.OrganizacionInformacionResponse;
import com.proyectogrado.organizacion_service.model.OrganizacionLookup;
import com.proyectogrado.organizacion_service.model.UsuarioLookup;
import com.proyectogrado.organizacion_service.repository.OrganizacionLookupRepository;
import com.proyectogrado.organizacion_service.repository.UsuarioLookupRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Perfil propio de la organización; aquí solo se edita la dirección.
 *
 * El nombre, el correo y el celular son de la cuenta (auth-service): aquí
 * solo se leen con UsuarioLookup, y se editan con PUT /api/auth/informacion.
 */
@RestController
@RequestMapping("/api/organizacion/informacion")
public class OrganizacionInformacionController {

    private final UsuarioLookupRepository usuarioLookupRepository;
    private final OrganizacionLookupRepository organizacionLookupRepository;

    public OrganizacionInformacionController(
            UsuarioLookupRepository usuarioLookupRepository,
            OrganizacionLookupRepository organizacionLookupRepository
    ) {
        this.usuarioLookupRepository = usuarioLookupRepository;
        this.organizacionLookupRepository = organizacionLookupRepository;
    }

    /** Datos de la organización del usuario autenticado. */
    @GetMapping
    public ResponseEntity<?> obtenerInformacion(
            @RequestHeader("X-User-Id") Integer idUsuario
    ) {
        UsuarioLookup usuario = usuarioLookupRepository.findById(idUsuario).orElse(null);

        if (usuario == null || usuario.getIdOrganizacion() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("El usuario no tiene una organización asociada");
        }

        OrganizacionLookup organizacion = organizacionLookupRepository
                .findById(usuario.getIdOrganizacion())
                .orElse(null);

        if (organizacion == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Organización no encontrada");
        }

        return ResponseEntity.ok(aRespuesta(usuario, organizacion));
    }

    /** Cambia la dirección de la organización. */
    @PutMapping
    public ResponseEntity<?> actualizarInformacion(
            @RequestHeader("X-User-Id") Integer idUsuario,
            @RequestBody ActualizarOrganizacionRequest request
    ) {
        UsuarioLookup usuario = usuarioLookupRepository.findById(idUsuario).orElse(null);

        if (usuario == null || usuario.getIdOrganizacion() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("El usuario no tiene una organización asociada");
        }

        OrganizacionLookup organizacion = organizacionLookupRepository
                .findById(usuario.getIdOrganizacion())
                .orElse(null);

        if (organizacion == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Organización no encontrada");
        }

        // La ubicación en el mapa no se toca: analitica-service la saca de la
        // dirección (UbicadorAutomatico) y la actualiza si la dirección cambia.
        organizacion.setDireccion(request.getDireccion());
        organizacion = organizacionLookupRepository.save(organizacion);

        return ResponseEntity.ok(aRespuesta(usuario, organizacion));
    }

    private OrganizacionInformacionResponse aRespuesta(UsuarioLookup usuario, OrganizacionLookup organizacion) {
        return new OrganizacionInformacionResponse(
                organizacion.getIdOrganizacion(),
                usuario.getNombreUsuario(),
                usuario.getCorreo(),
                usuario.getCelular(),
                organizacion.getDireccion(),
                organizacion.getLatitud(),
                organizacion.getLongitud()
        );
    }
}