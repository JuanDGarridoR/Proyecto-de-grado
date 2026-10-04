package com.proyectogrado.salud_backend.controller;

import com.proyectogrado.salud_backend.dto.SignoVitalRequest;
import com.proyectogrado.salud_backend.dto.SignoVitalResponse;
import com.proyectogrado.salud_backend.model.SignoVital;
import com.proyectogrado.salud_backend.repository.SignoVitalRepository;
import com.proyectogrado.salud_backend.validacion.SignoVitalValidador;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Signos vitales de la persona mayor autenticada: su historial y las
 * mediciones que registra ella misma. La organización también registra
 * mediciones (ver SignoVitalOrganizacionController). El gateway solo expone
 * GET y POST, así que editar y borrar desde aquí no está disponible para el
 * frontend.
 */
@RestController
@RequestMapping("/api/persona-mayor/signos-vitales")
public class SignoVitalController {

    private final SignoVitalRepository signoVitalRepository;

    public SignoVitalController(SignoVitalRepository signoVitalRepository) {
        this.signoVitalRepository = signoVitalRepository;
    }

    /** Historial completo, del registro más reciente al más antiguo. */
    @GetMapping
    public ResponseEntity<List<SignoVitalResponse>> listar(
            @RequestHeader("X-User-Id") Integer idPersonaMayor
    ) {
        List<SignoVitalResponse> respuesta = signoVitalRepository
                .findByIdPersonaMayorOrderByFechaHoraDesc(idPersonaMayor)
                .stream()
                .map(this::aRespuesta)
                .toList();

        return ResponseEntity.ok(respuesta);
    }

    /**
     * La persona mayor registra una medición propia, con la fecha y hora
     * actuales y la misma validación que usa la organización.
     */
    @PostMapping
    public ResponseEntity<?> crear(
            @RequestHeader("X-User-Id") Integer idPersonaMayor,
            @RequestBody SignoVitalRequest request
    ) {
        String errorMedicion = SignoVitalValidador.validar(request);
        if (errorMedicion != null) {
            return ResponseEntity.badRequest().body(errorMedicion);
        }

        SignoVital signoVital = new SignoVital();

        signoVital.setIdPersonaMayor(idPersonaMayor);
        signoVital.setFechaHora(LocalDateTime.now());

        aplicarCambios(signoVital, request);

        signoVital = signoVitalRepository.save(signoVital);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(aRespuesta(signoVital));
    }

    /** Edita una medición; si no es de esta persona mayor, responde 403. */
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(
            @RequestHeader("X-User-Id") Integer idPersonaMayor,
            @PathVariable Integer id,
            @RequestBody SignoVitalRequest request
    ) {
        SignoVital signoVital = obtenerPropio(id, idPersonaMayor);

        if (signoVital == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Este registro de signos vitales no pertenece a este usuario");
        }

        aplicarCambios(signoVital, request);

        signoVital = signoVitalRepository.save(signoVital);

        return ResponseEntity.ok(aRespuesta(signoVital));
    }

    /** Borra una medición; si no es de esta persona mayor, responde 403. */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(
            @RequestHeader("X-User-Id") Integer idPersonaMayor,
            @PathVariable Integer id
    ) {
        SignoVital signoVital = obtenerPropio(id, idPersonaMayor);

        if (signoVital == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Este registro de signos vitales no pertenece a este usuario");
        }

        signoVitalRepository.delete(signoVital);

        return ResponseEntity.noContent().build();
    }

    /** La medición, o null si no existe o es de otra persona mayor. */
    private SignoVital obtenerPropio(
            Integer id,
            Integer idPersonaMayor
    ) {
        SignoVital signoVital = signoVitalRepository
                .findById(id)
                .orElse(null);

        if (signoVital == null ||
                !signoVital.getIdPersonaMayor().equals(idPersonaMayor)) {
            return null;
        }

        return signoVital;
    }

    private void aplicarCambios(
            SignoVital signoVital,
            SignoVitalRequest request
    ) {
        signoVital.setPresionSistolica(
                request.getPresionSistolica()
        );

        signoVital.setPresionDiastolica(
                request.getPresionDiastolica()
        );

        signoVital.setFrecuenciaCardiaca(
                request.getFrecuenciaCardiaca()
        );

        signoVital.setTemperatura(
                request.getTemperatura()
        );

        signoVital.setSaturacionOxigeno(
                request.getSaturacionOxigeno()
        );

        signoVital.setFrecuenciaRespiratoria(
                request.getFrecuenciaRespiratoria()
        );

        signoVital.setPeso(
                request.getPeso()
        );

        signoVital.setEstatura(
                request.getEstatura()
        );

        signoVital.setObservaciones(
                request.getObservaciones()
        );
    }

    private SignoVitalResponse aRespuesta(SignoVital signoVital) {

        DateTimeFormatter formato =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

        return new SignoVitalResponse(
                signoVital.getIdSignoVital(),
                signoVital.getFechaHora() != null
                        ? signoVital.getFechaHora().format(formato)
                        : null,
                signoVital.getPresionSistolica(),
                signoVital.getPresionDiastolica(),
                signoVital.getFrecuenciaCardiaca(),
                signoVital.getTemperatura(),
                signoVital.getSaturacionOxigeno(),
                signoVital.getFrecuenciaRespiratoria(),
                signoVital.getPeso(),
                signoVital.getEstatura(),
                signoVital.getObservaciones()
        );
    }
}