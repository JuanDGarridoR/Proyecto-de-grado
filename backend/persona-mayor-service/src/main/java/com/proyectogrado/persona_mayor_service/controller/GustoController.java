package com.proyectogrado.persona_mayor_service.controller;

import com.proyectogrado.persona_mayor_service.dto.GustoRequest;
import com.proyectogrado.persona_mayor_service.dto.GustoResponse;
import com.proyectogrado.persona_mayor_service.model.Gusto;
import com.proyectogrado.persona_mayor_service.repository.GustoRepository;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorGustoRepository;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Catálogo de gustos, talentos y pasatiempos que las personas mayores
 * pueden marcar, agrupados por categoría.
 */
@RestController
@RequestMapping("/api/gustos")
public class GustoController {

    /** Categorías válidas (ver CatalogoProfesiones para PROFESION). */
    private static final Set<String> CATEGORIAS = Set.of("GUSTO", "TALENTO", "HOBBY", "PROFESION");

    private static final int LARGO_MAXIMO_NOMBRE = 60;

    private final GustoRepository gustoRepository;
    private final PersonaMayorGustoRepository personaMayorGustoRepository;

    public GustoController(
            GustoRepository gustoRepository,
            PersonaMayorGustoRepository personaMayorGustoRepository
    ) {
        this.gustoRepository = gustoRepository;
        this.personaMayorGustoRepository = personaMayorGustoRepository;
    }

    /**
     * Todo el catálogo, o solo una categoría si se indica. Va en caché:
     * casi no cambia y lo piden todos los formularios de intereses.
     */
    @GetMapping
    @Cacheable(cacheNames = "gustos", key = "#categoria ?: 'todas'")
    public List<GustoResponse> listar(@RequestParam(required = false) String categoria) {
        List<Gusto> gustos = categoria == null
                ? gustoRepository.findAll()
                : gustoRepository.findByCategoria(categoria);

        return gustos.stream()
                .map(g -> new GustoResponse(g.getIdGusto(), g.getNombre(), g.getCategoria()))
                .toList();
    }

    /**
     * Agrega un gusto al catálogo. Lo usan la persona mayor y el voluntario
     * cuando el suyo no está en la lista. El nombre se guarda sin espacios
     * de más y no se puede repetir, aunque cambien las mayúsculas.
     */
    @PostMapping
    @CacheEvict(cacheNames = "gustos", allEntries = true)
    public ResponseEntity<?> crear(@RequestBody GustoRequest request) {
        String nombre = request.getNombre() == null
                ? ""
                : request.getNombre().trim().replaceAll("\\s+", " ");

        if (nombre.isEmpty()) {
            return ResponseEntity.badRequest().body("El nombre es obligatorio");
        }
        if (nombre.length() > LARGO_MAXIMO_NOMBRE) {
            return ResponseEntity.badRequest()
                    .body("El nombre no puede tener más de " + LARGO_MAXIMO_NOMBRE + " caracteres");
        }
        if (!CATEGORIAS.contains(request.getCategoria())) {
            return ResponseEntity.badRequest().body("La categoría no es válida");
        }
        if (gustoRepository.existsByNombreIgnoreCase(nombre)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Ya existe un gusto con ese nombre");
        }

        Gusto gusto = gustoRepository.save(new Gusto(nombre, request.getCategoria()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new GustoResponse(gusto.getIdGusto(), gusto.getNombre(), gusto.getCategoria()));
    }

    /** Cambia el nombre o la categoría de un gusto. */
    @PutMapping("/{id}")
    @CacheEvict(cacheNames = "gustos", allEntries = true)
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @RequestBody GustoRequest request) {
        Gusto gusto = gustoRepository.findById(id).orElse(null);
        if (gusto == null) {
            return ResponseEntity.notFound().build();
        }
        if (request.getNombre() == null || request.getNombre().isBlank()) {
            return ResponseEntity.badRequest().body("El nombre es obligatorio");
        }

        gusto.setNombre(request.getNombre());
        gusto.setCategoria(request.getCategoria());
        gusto = gustoRepository.save(gusto);
        return ResponseEntity.ok(new GustoResponse(gusto.getIdGusto(), gusto.getNombre(), gusto.getCategoria()));
    }

    /** Borra un gusto si nadie lo tiene asignado; si alguien lo tiene, responde 409. */
    @DeleteMapping("/{id}")
    @CacheEvict(cacheNames = "gustos", allEntries = true)
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        if (!gustoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        if (personaMayorGustoRepository.existsByGusto_IdGusto(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("No se puede eliminar: hay personas mayores con este gusto asignado");
        }

        gustoRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
