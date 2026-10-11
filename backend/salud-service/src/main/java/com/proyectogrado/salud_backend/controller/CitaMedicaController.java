package com.proyectogrado.salud_backend.controller;

import com.proyectogrado.salud_backend.config.ZonaHoraria;
import com.proyectogrado.salud_backend.dto.CitaMedicaRequest;
import com.proyectogrado.salud_backend.dto.CitaMedicaResponse;
import com.proyectogrado.salud_backend.model.CitaMedica;
import com.proyectogrado.salud_backend.repository.CitaMedicaRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Citas médicas de la persona mayor autenticada: crear, listar, editar y
 * borrar. Solo se registran citas futuras, y las que ya pasaron no se
 * editan (quedan como historial; sí se pueden borrar). Los recordatorios (un día y una hora antes) los envía
 * CitaMedicaRecordatorioScheduler; los acompañantes las consultan desde
 * acompanante-service.
 */
@RestController
@RequestMapping("/api/persona-mayor/citas-medicas")
public class CitaMedicaController {

    private final CitaMedicaRepository citaMedicaRepository;

    public CitaMedicaController(CitaMedicaRepository citaMedicaRepository) {
        this.citaMedicaRepository = citaMedicaRepository;
    }

    /** Citas de la persona mayor autenticada, ordenadas por fecha y hora. */
    @GetMapping
    public ResponseEntity<List<CitaMedicaResponse>> listar(
            @RequestHeader("X-User-Id") Integer idPersonaMayor
    ) {
        List<CitaMedicaResponse> respuesta = citaMedicaRepository
                .findByIdPersonaMayorOrderByFechaAscHoraAsc(idPersonaMayor)
                .stream()
                .map(this::aRespuesta)
                .toList();

        return ResponseEntity.ok(respuesta);
    }

    /** Crea una cita para la persona mayor autenticada. */
    @PostMapping
    public ResponseEntity<?> crear(
            @RequestHeader("X-User-Id") Integer idPersonaMayor,
            @RequestBody CitaMedicaRequest request
    ) {
        String errorCita = validar(request);
        if (errorCita != null) {
            return ResponseEntity.badRequest().body(errorCita);
        }

        CitaMedica cita = new CitaMedica();

        cita.setIdPersonaMayor(idPersonaMayor);
        aplicarCambios(cita, request);

        cita = citaMedicaRepository.save(cita);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(aRespuesta(cita));
    }

    /**
     * Edita una cita; si no es de esta persona mayor, responde 403, y si ya
     * pasó, 400.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(
            @RequestHeader("X-User-Id") Integer idPersonaMayor,
            @PathVariable Integer id,
            @RequestBody CitaMedicaRequest request
    ) {
        CitaMedica cita = obtenerPropia(id, idPersonaMayor);

        if (cita == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Esta cita médica no pertenece a este usuario");
        }

        if (!cita.getInicio().isAfter(ZonaHoraria.ahora())) {
            return ResponseEntity.badRequest()
                    .body("Esta cita ya pasó y no se puede editar");
        }

        String errorCita = validar(request);
        if (errorCita != null) {
            return ResponseEntity.badRequest().body(errorCita);
        }

        aplicarCambios(cita, request);

        cita = citaMedicaRepository.save(cita);

        return ResponseEntity.ok(aRespuesta(cita));
    }

    /** Borra una cita; si no es de esta persona mayor, responde 403. */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(
            @RequestHeader("X-User-Id") Integer idPersonaMayor,
            @PathVariable Integer id
    ) {
        CitaMedica cita = obtenerPropia(id, idPersonaMayor);

        if (cita == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Esta cita médica no pertenece a este usuario");
        }

        citaMedicaRepository.delete(cita);

        return ResponseEntity.noContent().build();
    }

    /** La cita, o null si no existe o es de otra persona mayor. */
    private CitaMedica obtenerPropia(
            Integer id,
            Integer idPersonaMayor
    ) {
        CitaMedica cita = citaMedicaRepository
                .findById(id)
                .orElse(null);

        if (cita == null ||
                !cita.getIdPersonaMayor().equals(idPersonaMayor)) {
            return null;
        }

        return cita;
    }

    /**
     * Mensaje de error si falta un dato obligatorio, la fecha/hora no se
     * entiende o ya pasó; null si todo está bien.
     */
    private String validar(CitaMedicaRequest request) {
        if (estaVacio(request.getTitulo()) || estaVacio(request.getLugar())
                || estaVacio(request.getFecha()) || estaVacio(request.getHora())) {
            return "Escribe el motivo, el lugar, la fecha y la hora de la cita";
        }

        LocalDateTime inicio;
        try {
            inicio = LocalDate.parse(request.getFecha())
                    .atTime(LocalTime.parse(request.getHora()));
        } catch (DateTimeParseException e) {
            return "La fecha o la hora de la cita no son válidas";
        }

        if (!inicio.isAfter(ZonaHoraria.ahora())) {
            return "La fecha y la hora de la cita deben ser posteriores a este momento";
        }

        return null;
    }

    private boolean estaVacio(String texto) {
        return texto == null || texto.isBlank();
    }

    /**
     * Copia los datos del formulario (ya validados). Si cambian la fecha o
     * la hora, el scheduler vuelve a enviar los recordatorios para el nuevo
     * horario.
     */
    private void aplicarCambios(
            CitaMedica cita,
            CitaMedicaRequest request
    ) {
        cita.setTitulo(request.getTitulo().trim());
        cita.setLugar(request.getLugar().trim());
        cita.setConsultorio(
                estaVacio(request.getConsultorio())
                        ? null
                        : request.getConsultorio().trim()
        );
        cita.setObservaciones(
                estaVacio(request.getObservaciones())
                        ? null
                        : request.getObservaciones().trim()
        );
        cita.setFecha(LocalDate.parse(request.getFecha()));
        cita.setHora(LocalTime.parse(request.getHora()));
    }

    private CitaMedicaResponse aRespuesta(CitaMedica cita) {

        DateTimeFormatter formatoHora =
                DateTimeFormatter.ofPattern("HH:mm");

        return new CitaMedicaResponse(
                cita.getIdCita(),
                cita.getTitulo(),
                cita.getLugar(),
                cita.getConsultorio(),
                cita.getFecha() != null
                        ? cita.getFecha().toString()
                        : null,
                cita.getHora() != null
                        ? cita.getHora().format(formatoHora)
                        : null,
                cita.getObservaciones()
        );
    }
}