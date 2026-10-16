package com.proyectogrado.analitica_service.controller;

import com.proyectogrado.analitica_service.red.RedApoyo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Red de apoyo de la organización, para el grafo y el mapa de su panel
 * (sección «Red y mapa»). Ver RedApoyo.
 */
@RestController
@RequestMapping("/api/analitica/red")
public class RedController {

    private final OrganizacionActual organizacionActual;
    private final RedApoyo redApoyo;

    public RedController(OrganizacionActual organizacionActual, RedApoyo redApoyo) {
        this.organizacionActual = organizacionActual;
        this.redApoyo = redApoyo;
    }

    @GetMapping
    public ResponseEntity<?> red(@RequestHeader("X-User-Id") Integer idUsuario) {
        OrganizacionActual.Organizacion organizacion = organizacionActual.de(idUsuario);
        if (organizacion == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo una organización puede ver su red de apoyo");
        }
        return ResponseEntity.ok(redApoyo.de(organizacion.id(), organizacion.nombre()));
    }
}
