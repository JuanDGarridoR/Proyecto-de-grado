package com.proyectogrado.acompanante_service.controller;

import com.proyectogrado.acompanante_service.dto.EmergenciaResponse;
import com.proyectogrado.acompanante_service.model.UsuarioLookup;
import com.proyectogrado.acompanante_service.repository.EmergenciaLookupRepository;
import com.proyectogrado.acompanante_service.repository.PersonaMayorAcompananteRepository;
import com.proyectogrado.acompanante_service.repository.UsuarioLookupRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Emergencias recientes de las personas mayores que acompaña el usuario
 * (solo vínculos aceptados), para las alertas de su inicio. Las guarda
 * persona-mayor-service cuando se activa el botón de emergencia.
 */
@RestController
@RequestMapping("/api/acompanante/emergencias")
public class AcompananteEmergenciaController {

    /** Cuánto tiempo sigue apareciendo una emergencia en el inicio. */
    static final int HORAS_VIGENCIA = 24;

    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final PersonaMayorAcompananteRepository relacionRepository;
    private final EmergenciaLookupRepository emergenciaRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;

    public AcompananteEmergenciaController(
            PersonaMayorAcompananteRepository relacionRepository,
            EmergenciaLookupRepository emergenciaRepository,
            UsuarioLookupRepository usuarioLookupRepository
    ) {
        this.relacionRepository = relacionRepository;
        this.emergenciaRepository = emergenciaRepository;
        this.usuarioLookupRepository = usuarioLookupRepository;
    }

    /** Emergencias de las últimas HORAS_VIGENCIA horas, de la más reciente a la más antigua. */
    @GetMapping
    public List<EmergenciaResponse> recientes(@RequestHeader("X-User-Id") Integer idAcompanante) {
        Set<Integer> personas = relacionRepository.findById_IdAcompananteAndEstado(idAcompanante, "ACEPTADA")
                .stream()
                .map(relacion -> relacion.getId().getIdPersonaMayor())
                .collect(Collectors.toSet());

        if (personas.isEmpty()) {
            return List.of();
        }

        LocalDateTime desde = LocalDateTime.now(COLOMBIA).minusHours(HORAS_VIGENCIA);

        return emergenciaRepository.findByIdPersonaMayorInAndFechaHoraAfterOrderByFechaHoraDesc(personas, desde)
                .stream()
                .map(e -> new EmergenciaResponse(
                        e.getIdEmergencia(),
                        e.getIdPersonaMayor(),
                        usuarioLookupRepository.findById(e.getIdPersonaMayor())
                                .map(UsuarioLookup::getNombreUsuario)
                                .orElse(null),
                        e.getFechaHora().format(FORMATO_FECHA_HORA)
                ))
                .toList();
    }
}
