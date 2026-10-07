package com.proyectogrado.salud_backend.controller;

import com.proyectogrado.salud_backend.dto.CitaMedicaRequest;
import com.proyectogrado.salud_backend.dto.MedicamentoRequest;
import com.proyectogrado.salud_backend.dto.PersonaMayorCondicionSaludRequest;
import com.proyectogrado.salud_backend.repository.RelacionAcompananteLookupRepository;

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

import java.util.function.Supplier;

/**
 * Medicamentos, citas médicas y datos de salud que el acompañante gestiona
 * por una persona mayor que acompaña. Solo si el vínculo entre los dos está
 * ACEPTADO; si no, responde 403.
 *
 * Una vez revisado el vínculo, cada operación se hace con los mismos
 * controladores que usa la persona mayor, pasándoles su id. Así las reglas
 * (cálculo de la próxima toma, citas solo futuras, catálogo de condiciones)
 * son las mismas venga de quien venga el cambio, y los recordatorios por
 * SMS funcionan igual.
 */
@RestController
@RequestMapping("/api/acompanante/personas-mayores/{idPersonaMayor}")
public class AcompananteSaludController {

    private static final String ACEPTADA = "ACEPTADA";

    private final RelacionAcompananteLookupRepository relacionAcompananteLookupRepository;
    private final MedicamentoController medicamentoController;
    private final CitaMedicaController citaMedicaController;
    private final CondicionSaludController condicionSaludController;

    public AcompananteSaludController(
            RelacionAcompananteLookupRepository relacionAcompananteLookupRepository,
            MedicamentoController medicamentoController,
            CitaMedicaController citaMedicaController,
            CondicionSaludController condicionSaludController
    ) {
        this.relacionAcompananteLookupRepository = relacionAcompananteLookupRepository;
        this.medicamentoController = medicamentoController;
        this.citaMedicaController = citaMedicaController;
        this.condicionSaludController = condicionSaludController;
    }

    // Medicamentos

    @GetMapping("/medicamentos")
    public ResponseEntity<?> listarMedicamentos(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor
    ) {
        return siAcompana(idAcompanante, idPersonaMayor,
                () -> medicamentoController.listar(idPersonaMayor));
    }

    @PostMapping("/medicamentos")
    public ResponseEntity<?> crearMedicamento(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor,
            @RequestBody MedicamentoRequest request
    ) {
        return siAcompana(idAcompanante, idPersonaMayor,
                () -> medicamentoController.crear(idPersonaMayor, request));
    }

    @PutMapping("/medicamentos/{id}")
    public ResponseEntity<?> actualizarMedicamento(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor,
            @PathVariable Integer id,
            @RequestBody MedicamentoRequest request
    ) {
        return siAcompana(idAcompanante, idPersonaMayor,
                () -> medicamentoController.actualizar(idPersonaMayor, id, request));
    }

    @DeleteMapping("/medicamentos/{id}")
    public ResponseEntity<?> eliminarMedicamento(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor,
            @PathVariable Integer id
    ) {
        return siAcompana(idAcompanante, idPersonaMayor,
                () -> medicamentoController.eliminar(idPersonaMayor, id));
    }

    // Citas médicas

    @GetMapping("/citas-medicas")
    public ResponseEntity<?> listarCitas(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor
    ) {
        return siAcompana(idAcompanante, idPersonaMayor,
                () -> citaMedicaController.listar(idPersonaMayor));
    }

    @PostMapping("/citas-medicas")
    public ResponseEntity<?> crearCita(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor,
            @RequestBody CitaMedicaRequest request
    ) {
        return siAcompana(idAcompanante, idPersonaMayor,
                () -> citaMedicaController.crear(idPersonaMayor, request));
    }

    @PutMapping("/citas-medicas/{id}")
    public ResponseEntity<?> actualizarCita(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor,
            @PathVariable Integer id,
            @RequestBody CitaMedicaRequest request
    ) {
        return siAcompana(idAcompanante, idPersonaMayor,
                () -> citaMedicaController.actualizar(idPersonaMayor, id, request));
    }

    @DeleteMapping("/citas-medicas/{id}")
    public ResponseEntity<?> eliminarCita(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor,
            @PathVariable Integer id
    ) {
        return siAcompana(idAcompanante, idPersonaMayor,
                () -> citaMedicaController.eliminar(idPersonaMayor, id));
    }

    // Datos de salud (enfermedades, alergias y discapacidades)

    @GetMapping("/condiciones-salud")
    public ResponseEntity<?> listarCondiciones(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor
    ) {
        return siAcompana(idAcompanante, idPersonaMayor,
                () -> ResponseEntity.ok(condicionSaludController.listar(idPersonaMayor)));
    }

    @PostMapping("/condiciones-salud")
    public ResponseEntity<?> crearCondicion(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor,
            @RequestBody PersonaMayorCondicionSaludRequest request
    ) {
        return siAcompana(idAcompanante, idPersonaMayor,
                () -> condicionSaludController.crear(idPersonaMayor, request));
    }

    @PutMapping("/condiciones-salud/{id}")
    public ResponseEntity<?> actualizarCondicion(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor,
            @PathVariable Integer id,
            @RequestBody PersonaMayorCondicionSaludRequest request
    ) {
        return siAcompana(idAcompanante, idPersonaMayor,
                () -> condicionSaludController.actualizar(idPersonaMayor, id, request));
    }

    @DeleteMapping("/condiciones-salud/{id}")
    public ResponseEntity<?> eliminarCondicion(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor,
            @PathVariable Integer id
    ) {
        return siAcompana(idAcompanante, idPersonaMayor,
                () -> condicionSaludController.eliminar(idPersonaMayor, id));
    }

    /** Ejecuta la operación solo si el acompañante tiene el vínculo aceptado; si no, 403. */
    private ResponseEntity<?> siAcompana(
            Integer idAcompanante,
            Integer idPersonaMayor,
            Supplier<ResponseEntity<?>> operacion
    ) {
        boolean acompana = relacionAcompananteLookupRepository
                .existsById_IdPersonaMayorAndId_IdAcompananteAndEstado(idPersonaMayor, idAcompanante, ACEPTADA);

        if (!acompana) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No acompañas a esta persona mayor");
        }

        return operacion.get();
    }
}
