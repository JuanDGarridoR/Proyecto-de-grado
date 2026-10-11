package com.proyectogrado.organizacion_service.controller;

import com.proyectogrado.organizacion_service.dto.RetiroResponse;
import com.proyectogrado.organizacion_service.model.UsuarioLookup;
import com.proyectogrado.organizacion_service.repository.UsuarioLookupRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Personas mayores de la organización que eliminaron su cuenta y por qué
 * (por ejemplo, fallecimiento). La tabla retiro_cuenta es de auth-service,
 * que guarda una fila por organización justo antes de borrar la cuenta;
 * aquí solo se lee.
 */
@RestController
public class RetiroController {

    private final UsuarioLookupRepository usuarioLookupRepository;
    private final NamedParameterJdbcTemplate jdbc;

    public RetiroController(UsuarioLookupRepository usuarioLookupRepository, NamedParameterJdbcTemplate jdbc) {
        this.usuarioLookupRepository = usuarioLookupRepository;
        this.jdbc = jdbc;
    }

    /** De la más reciente a la más antigua. */
    @GetMapping("/api/organizacion/retiros")
    public ResponseEntity<?> listar(@RequestHeader("X-User-Id") Integer idUsuario) {
        Integer idOrganizacion = usuarioLookupRepository.findById(idUsuario)
                .map(UsuarioLookup::getIdOrganizacion)
                .orElse(null);

        if (idOrganizacion == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("El usuario no tiene una organización asociada");
        }

        // La crea auth-service al arrancar; si aún no existe, no hay retiros.
        Boolean existeTabla = jdbc.queryForObject(
                "SELECT to_regclass('retiro_cuenta') IS NOT NULL", new MapSqlParameterSource(), Boolean.class);
        if (!Boolean.TRUE.equals(existeTabla)) {
            return ResponseEntity.ok(List.of());
        }

        List<RetiroResponse> retiros = jdbc.query("""
                SELECT nombre, razon, comentario, fecha
                  FROM retiro_cuenta
                 WHERE id_organizacion = :org
                 ORDER BY fecha DESC
                """, new MapSqlParameterSource("org", idOrganizacion),
                (rs, i) -> new RetiroResponse(
                        rs.getString("nombre"),
                        rs.getString("razon"),
                        rs.getString("comentario"),
                        rs.getTimestamp("fecha").toLocalDateTime()));

        return ResponseEntity.ok(retiros);
    }
}
