package com.proyectogrado.analitica_service.controller;

import com.proyectogrado.analitica_service.perfiles.SegmentacionPerfiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Perfiles de las personas mayores de la organización (segmentación con
 * K-means), para la pestaña «Perfiles» de su analítica. Ver
 * SegmentacionPerfiles y docs/KMEANS_PERFILES.md.
 *
 * k es opcional: sin él se usa el número de grupos con mejor silueta.
 */
@RestController
@RequestMapping("/api/analitica/perfiles")
public class PerfilesController {

    private final OrganizacionActual organizacionActual;
    private final SegmentacionPerfiles segmentacion;

    public PerfilesController(OrganizacionActual organizacionActual, SegmentacionPerfiles segmentacion) {
        this.organizacionActual = organizacionActual;
        this.segmentacion = segmentacion;
    }

    @GetMapping
    public ResponseEntity<?> perfiles(
            @RequestHeader("X-User-Id") Integer idUsuario,
            @RequestParam(required = false) Integer k
    ) {
        OrganizacionActual.Organizacion organizacion = organizacionActual.de(idUsuario);
        if (organizacion == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo una organización puede ver los perfiles de sus personas mayores");
        }
        return ResponseEntity.ok(segmentacion.segmentar(organizacion.id(), k));
    }
}
