package com.proyectogrado.salud_backend.controller;

import com.proyectogrado.salud_backend.dto.CondicionSaludResponse;
import com.proyectogrado.salud_backend.dto.PersonaMayorCondicionSaludRequest;
import com.proyectogrado.salud_backend.dto.PersonaMayorCondicionSaludResponse;
import com.proyectogrado.salud_backend.model.CondicionSalud;
import com.proyectogrado.salud_backend.model.PersonaMayorCondicionSalud;
import com.proyectogrado.salud_backend.model.SeveridadCondicion;
import com.proyectogrado.salud_backend.repository.CondicionSaludRepository;
import com.proyectogrado.salud_backend.repository.PersonaMayorCondicionSaludRepository;

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

import java.util.Comparator;
import java.util.List;

/**
 * Datos de salud de la persona mayor: sus enfermedades, alergias y
 * discapacidades. Ninguno es obligatorio. El id del usuario llega en el
 * encabezado X-User-Id, que agrega el gateway.
 */
@RestController
@RequestMapping("/api/persona-mayor/condiciones-salud")
public class CondicionSaludController {

    private static final int LARGO_MAXIMO_DETALLE = 500;

    private final CondicionSaludRepository condicionSaludRepository;
    private final PersonaMayorCondicionSaludRepository personaMayorCondicionSaludRepository;

    public CondicionSaludController(
            CondicionSaludRepository condicionSaludRepository,
            PersonaMayorCondicionSaludRepository personaMayorCondicionSaludRepository
    ) {
        this.condicionSaludRepository = condicionSaludRepository;
        this.personaMayorCondicionSaludRepository = personaMayorCondicionSaludRepository;
    }

    /** Catálogo completo, del que la persona mayor elige. */
    @GetMapping("/catalogo")
    public List<CondicionSaludResponse> catalogo() {
        return condicionSaludRepository.findAllByOrderByTipoAscCategoriaAscNombreAsc()
                .stream()
                .map(c -> new CondicionSaludResponse(
                        c.getIdCondicionSalud(), c.getTipo().name(), c.getNombre(),
                        c.getCategoria(), c.getEsOtra()))
                .toList();
    }

    /** Enfermedades, alergias y discapacidades de la persona mayor autenticada. */
    @GetMapping
    public List<PersonaMayorCondicionSaludResponse> listar(
            @RequestHeader("X-User-Id") Integer idPersonaMayor
    ) {
        return personaMayorCondicionSaludRepository.findByIdPersonaMayor(idPersonaMayor)
                .stream()
                .sorted(Comparator.comparing((PersonaMayorCondicionSalud r) -> r.getCondicion().getNombre()))
                .map(this::aRespuesta)
                .toList();
    }

    /**
     * Registra una condición del catálogo. La misma no se puede registrar dos
     * veces (409), salvo la opción "Otra", que exige el detalle.
     */
    @PostMapping
    public ResponseEntity<?> crear(
            @RequestHeader("X-User-Id") Integer idPersonaMayor,
            @RequestBody PersonaMayorCondicionSaludRequest request
    ) {
        if (request.getIdCondicionSalud() == null) {
            return ResponseEntity.badRequest().body("Selecciona una opción de la lista");
        }

        CondicionSalud condicion = condicionSaludRepository.findById(request.getIdCondicionSalud()).orElse(null);
        if (condicion == null) {
            return ResponseEntity.badRequest().body("La opción seleccionada no existe");
        }

        if (!condicion.getEsOtra() && personaMayorCondicionSaludRepository
                .existsByIdPersonaMayorAndCondicion_IdCondicionSalud(idPersonaMayor, condicion.getIdCondicionSalud())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Ya registraste " + condicion.getNombre());
        }

        PersonaMayorCondicionSalud registro = new PersonaMayorCondicionSalud();
        registro.setIdPersonaMayor(idPersonaMayor);
        registro.setCondicion(condicion);

        String error = aplicarCambios(registro, request);
        if (error != null) {
            return ResponseEntity.badRequest().body(error);
        }

        registro = personaMayorCondicionSaludRepository.save(registro);

        return ResponseEntity.status(HttpStatus.CREATED).body(aRespuesta(registro));
    }

    /**
     * Cambia la severidad y el detalle. La condición no se cambia: para eso
     * se borra y se registra otra. Si no es de esta persona mayor, responde 403.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(
            @RequestHeader("X-User-Id") Integer idPersonaMayor,
            @PathVariable Integer id,
            @RequestBody PersonaMayorCondicionSaludRequest request
    ) {
        PersonaMayorCondicionSalud registro = obtenerPropio(id, idPersonaMayor);
        if (registro == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Este registro no pertenece a este usuario");
        }

        String error = aplicarCambios(registro, request);
        if (error != null) {
            return ResponseEntity.badRequest().body(error);
        }

        registro = personaMayorCondicionSaludRepository.save(registro);

        return ResponseEntity.ok(aRespuesta(registro));
    }

    /** Borra el registro; si no es de esta persona mayor, responde 403. */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(
            @RequestHeader("X-User-Id") Integer idPersonaMayor,
            @PathVariable Integer id
    ) {
        PersonaMayorCondicionSalud registro = obtenerPropio(id, idPersonaMayor);
        if (registro == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Este registro no pertenece a este usuario");
        }

        personaMayorCondicionSaludRepository.delete(registro);

        return ResponseEntity.noContent().build();
    }

    /** El registro, o null si no existe o es de otra persona mayor. */
    private PersonaMayorCondicionSalud obtenerPropio(Integer id, Integer idPersonaMayor) {
        PersonaMayorCondicionSalud registro = personaMayorCondicionSaludRepository.findById(id).orElse(null);

        if (registro == null || !registro.getIdPersonaMayor().equals(idPersonaMayor)) {
            return null;
        }

        return registro;
    }

    /**
     * Valida y copia la severidad y el detalle del formulario. Devuelve el
     * mensaje de error, o null si todo está bien.
     */
    private String aplicarCambios(PersonaMayorCondicionSalud registro, PersonaMayorCondicionSaludRequest request) {
        SeveridadCondicion severidad = null;
        if (request.getSeveridad() != null && !request.getSeveridad().isBlank()) {
            try {
                severidad = SeveridadCondicion.valueOf(request.getSeveridad().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return "La severidad debe ser LEVE, MODERADA o SEVERA";
            }
        }

        String detalle = request.getDetalle() == null ? null : request.getDetalle().trim();
        if (detalle != null && detalle.isEmpty()) {
            detalle = null;
        }

        if (detalle != null && detalle.length() > LARGO_MAXIMO_DETALLE) {
            return "El detalle no puede tener más de " + LARGO_MAXIMO_DETALLE + " caracteres";
        }

        if (registro.getCondicion().getEsOtra() && detalle == null) {
            return "Escribe cuál es";
        }

        registro.setSeveridad(severidad);
        registro.setDetalle(detalle);
        return null;
    }

    private PersonaMayorCondicionSaludResponse aRespuesta(PersonaMayorCondicionSalud registro) {
        CondicionSalud condicion = registro.getCondicion();

        return new PersonaMayorCondicionSaludResponse(
                registro.getIdPersonaMayorCondicionSalud(),
                condicion.getIdCondicionSalud(),
                condicion.getTipo().name(),
                condicion.getNombre(),
                condicion.getCategoria(),
                condicion.getEsOtra(),
                registro.getSeveridad() != null ? registro.getSeveridad().name() : null,
                registro.getDetalle(),
                registro.getFechaRegistro() != null ? registro.getFechaRegistro().toString() : null
        );
    }
}
