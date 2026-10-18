package com.proyectogrado.persona_mayor_service.controller;

import com.proyectogrado.persona_mayor_service.dto.GustoResponse;
import com.proyectogrado.persona_mayor_service.dto.GustosAsignadosRequest;
import com.proyectogrado.persona_mayor_service.model.Gusto;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorGusto;
import com.proyectogrado.persona_mayor_service.repository.GustoRepository;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorGustoRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Gustos que tiene marcados cada persona mayor. Cualquier usuario con
 * sesión puede consultarlos, pero solo la propia persona mayor los cambia.
 */
@RestController
@RequestMapping("/api/persona-mayor/{idPersonaMayor}/gustos")
public class PersonaMayorGustoController {

    private final GustoRepository gustoRepository;
    private final PersonaMayorGustoRepository personaMayorGustoRepository;

    public PersonaMayorGustoController(
            GustoRepository gustoRepository,
            PersonaMayorGustoRepository personaMayorGustoRepository
    ) {
        this.gustoRepository = gustoRepository;
        this.personaMayorGustoRepository = personaMayorGustoRepository;
    }

    /** Gustos de la persona mayor. Solo los ve ella misma. */
    @GetMapping
    public ResponseEntity<?> listar(
            @RequestHeader("X-User-Id") Integer idUsuarioAutenticado,
            @PathVariable Integer idPersonaMayor
    ) {
        if (!idUsuarioAutenticado.equals(idPersonaMayor)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo puedes ver tus propios gustos");
        }

        return ResponseEntity.ok(personaMayorGustoRepository.findById_IdPersonaMayor(idPersonaMayor)
                .stream()
                .map(pmg -> new GustoResponse(pmg.getGusto().getIdGusto(), pmg.getGusto().getNombre()))
                .toList());
    }

    /** Reemplaza todos los gustos de la persona mayor por los de la lista. */
    @PutMapping
    public ResponseEntity<?> asignar(
            @RequestHeader("X-User-Id") Integer idUsuarioAutenticado,
            @PathVariable Integer idPersonaMayor,
            @RequestBody GustosAsignadosRequest request
    ) {
        if (!idUsuarioAutenticado.equals(idPersonaMayor)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo puedes gestionar tus propios gustos");
        }

        List<Integer> idsGustos = request.getIdsGustos() == null ? List.of() : request.getIdsGustos();

        List<Gusto> gustos = gustoRepository.findAllById(idsGustos);

        if (gustos.size() != idsGustos.size()) {
            return ResponseEntity.badRequest().body("Alguno de los gustos indicados no existe");
        }

        personaMayorGustoRepository.deleteById_IdPersonaMayor(idPersonaMayor);

        for (Gusto gusto : gustos) {
            personaMayorGustoRepository.save(new PersonaMayorGusto(idPersonaMayor, gusto));
        }

        return ResponseEntity.ok(
                gustos.stream()
                        .map(g -> new GustoResponse(g.getIdGusto(), g.getNombre()))
                        .toList()
        );
    }
}
