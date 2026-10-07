package com.proyectogrado.voluntario_service.controller;

import com.proyectogrado.voluntario_service.dto.GustoResponse;
import com.proyectogrado.voluntario_service.model.GustoLookup;
import com.proyectogrado.voluntario_service.model.VoluntarioGusto;
import com.proyectogrado.voluntario_service.repository.GustoLookupRepository;
import com.proyectogrado.voluntario_service.repository.UsuarioLookupRepository;
import com.proyectogrado.voluntario_service.repository.VoluntarioGustoRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gustos, talentos y pasatiempos del voluntario autenticado (tabla
 * voluntario_gusto). Se eligen del mismo catálogo que usan las personas
 * mayores (GET /api/gustos) y sirven para recomendarle organizaciones.
 */
@RestController
@RequestMapping("/api/voluntario/gustos")
public class VoluntarioGustoController {

    private final VoluntarioGustoRepository voluntarioGustoRepository;
    private final GustoLookupRepository gustoLookupRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;

    public VoluntarioGustoController(
            VoluntarioGustoRepository voluntarioGustoRepository,
            GustoLookupRepository gustoLookupRepository,
            UsuarioLookupRepository usuarioLookupRepository
    ) {
        this.voluntarioGustoRepository = voluntarioGustoRepository;
        this.gustoLookupRepository = gustoLookupRepository;
        this.usuarioLookupRepository = usuarioLookupRepository;
    }

    /** Gustos que tiene marcados, por nombre. */
    @GetMapping
    public ResponseEntity<?> listar(@RequestHeader("X-User-Id") Integer idVoluntario) {
        if (!esVoluntario(idVoluntario)) {
            return soloVoluntarios();
        }
        return ResponseEntity.ok(gustosDe(idVoluntario));
    }

    /**
     * Reemplaza sus gustos por los de la lista. Cuerpo: { "idsGustos": [1, 4] }.
     * Los ids que no están en el catálogo se ignoran.
     */
    @PutMapping
    @Transactional
    public ResponseEntity<?> guardar(
            @RequestHeader("X-User-Id") Integer idVoluntario,
            @RequestBody Map<String, List<Integer>> cuerpo
    ) {
        if (!esVoluntario(idVoluntario)) {
            return soloVoluntarios();
        }

        List<Integer> pedidos = cuerpo.get("idsGustos");
        if (pedidos == null) {
            return ResponseEntity.badRequest().body("Falta la lista idsGustos");
        }

        Set<Integer> validos = new LinkedHashSet<>();
        for (GustoLookup gusto : gustoLookupRepository.findAllById(pedidos)) {
            validos.add(gusto.getIdGusto());
        }

        voluntarioGustoRepository.deleteAll(voluntarioGustoRepository.findById_IdVoluntario(idVoluntario));
        voluntarioGustoRepository.flush();
        voluntarioGustoRepository.saveAll(validos.stream()
                .map(idGusto -> new VoluntarioGusto(idVoluntario, idGusto))
                .toList());

        return ResponseEntity.ok(gustosDe(idVoluntario));
    }

    private List<GustoResponse> gustosDe(Integer idVoluntario) {
        List<Integer> ids = voluntarioGustoRepository.findById_IdVoluntario(idVoluntario)
                .stream()
                .map(vg -> vg.getId().getIdGusto())
                .toList();

        return gustoLookupRepository.findAllById(ids)
                .stream()
                .sorted(Comparator.comparing(GustoLookup::getNombre))
                .map(g -> new GustoResponse(g.getIdGusto(), g.getNombre(), g.getCategoria()))
                .toList();
    }

    // Se mira el rol: hay cuentas viejas con rol VOLUNTARIO sin fila en la tabla voluntario.
    private boolean esVoluntario(Integer idUsuario) {
        return usuarioLookupRepository.tieneRol(idUsuario, "VOLUNTARIO");
    }

    private ResponseEntity<String> soloVoluntarios() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Solo un voluntario puede tener gustos aquí");
    }
}
