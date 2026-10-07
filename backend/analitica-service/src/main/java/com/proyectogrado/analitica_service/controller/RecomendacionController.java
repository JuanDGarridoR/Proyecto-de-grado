package com.proyectogrado.analitica_service.controller;

import com.proyectogrado.analitica_service.recomendacion.RecomendacionDtos.RecomendacionesResponse;
import com.proyectogrado.analitica_service.recomendacion.RecomendadorOrganizaciones;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recomendaciones para la persona mayor y el voluntario: organizaciones a
 * las que podría unirse según sus gustos y la cercanía (ver
 * RecomendadorOrganizaciones).
 */
@RestController
@RequestMapping("/api/analitica/recomendaciones")
public class RecomendacionController {

    private final RecomendadorOrganizaciones recomendador;

    public RecomendacionController(RecomendadorOrganizaciones recomendador) {
        this.recomendador = recomendador;
    }

    @GetMapping("/organizaciones")
    public ResponseEntity<?> organizaciones(@RequestHeader("X-User-Id") Integer idUsuario) {
        RecomendacionesResponse respuesta = recomendador.recomendar(idUsuario);
        if (respuesta == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo una persona mayor o un voluntario puede ver recomendaciones de organizaciones");
        }
        return ResponseEntity.ok(respuesta);
    }
}
