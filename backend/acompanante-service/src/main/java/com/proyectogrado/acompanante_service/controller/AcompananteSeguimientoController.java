package com.proyectogrado.acompanante_service.controller;

import com.proyectogrado.acompanante_service.dto.CitaMedicaSeguimientoResponse;
import com.proyectogrado.acompanante_service.dto.ContactoResponse;
import com.proyectogrado.acompanante_service.dto.MedicamentoSeguimientoResponse;
import com.proyectogrado.acompanante_service.dto.SignoVitalSeguimientoResponse;
import com.proyectogrado.acompanante_service.model.AcompananteInfoLookup;
import com.proyectogrado.acompanante_service.model.PersonaMayorAcompanante;
import com.proyectogrado.acompanante_service.model.UsuarioLookup;
import com.proyectogrado.acompanante_service.repository.AcompananteInfoLookupRepository;
import com.proyectogrado.acompanante_service.repository.CitaMedicaLookupRepository;
import com.proyectogrado.acompanante_service.repository.MedicamentoLookupRepository;
import com.proyectogrado.acompanante_service.repository.PersonaMayorAcompananteRepository;
import com.proyectogrado.acompanante_service.repository.SignoVitalLookupRepository;
import com.proyectogrado.acompanante_service.repository.UsuarioLookupRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Seguimiento de una persona mayor por parte de su acompañante: signos
 * vitales, medicamentos, citas médicas y los demás contactos. Solo responde si el vínculo
 * entre los dos está ACEPTADO.
 *
 * Cruza datos de varios servicios (la cuenta en auth-service, los vínculos
 * en este servicio, los medicamentos, signos vitales y citas en salud-service)
 * con entidades Lookup de solo lectura.
 */
@RestController
@RequestMapping("/api/acompanante/seguimiento")
public class AcompananteSeguimientoController {

    private final PersonaMayorAcompananteRepository relacionRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;
    private final AcompananteInfoLookupRepository acompananteInfoLookupRepository;
    private final MedicamentoLookupRepository medicamentoLookupRepository;
    private final SignoVitalLookupRepository signoVitalLookupRepository;
    private final CitaMedicaLookupRepository citaMedicaLookupRepository;

    public AcompananteSeguimientoController(
            PersonaMayorAcompananteRepository relacionRepository,
            UsuarioLookupRepository usuarioLookupRepository,
            AcompananteInfoLookupRepository acompananteInfoLookupRepository,
            MedicamentoLookupRepository medicamentoLookupRepository,
            SignoVitalLookupRepository signoVitalLookupRepository,
            CitaMedicaLookupRepository citaMedicaLookupRepository
    ) {
        this.relacionRepository = relacionRepository;
        this.usuarioLookupRepository = usuarioLookupRepository;
        this.acompananteInfoLookupRepository = acompananteInfoLookupRepository;
        this.medicamentoLookupRepository = medicamentoLookupRepository;
        this.signoVitalLookupRepository = signoVitalLookupRepository;
        this.citaMedicaLookupRepository = citaMedicaLookupRepository;
    }

    /** Últimos 10 registros de signos vitales, del más reciente al más antiguo. */
    @GetMapping("/{idPersonaMayor}/signos-vitales")
    public ResponseEntity<?> obtenerSignosVitales(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor
    ) {
        if (!tieneRelacionAceptada(idAcompanante, idPersonaMayor)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No tienes autorización para consultar a esta persona mayor");
        }

        DateTimeFormatter formatoFechaHora = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

        List<SignoVitalSeguimientoResponse> respuesta = signoVitalLookupRepository
                .findTop10ByIdPersonaMayorOrderByFechaHoraDesc(idPersonaMayor)
                .stream()
                .map(s -> new SignoVitalSeguimientoResponse(
                        s.getIdSignoVital(),
                        s.getFechaHora() != null ? s.getFechaHora().format(formatoFechaHora) : null,
                        s.getPresionSistolica(),
                        s.getPresionDiastolica(),
                        s.getFrecuenciaCardiaca(),
                        s.getTemperatura(),
                        s.getSaturacionOxigeno(),
                        s.getFrecuenciaRespiratoria(),
                        s.getPeso(),
                        s.getEstatura(),
                        s.getObservaciones()
                ))
                .toList();

        return ResponseEntity.ok(respuesta);
    }

    /** Medicamentos de la persona mayor con su próxima y su última toma. */
    @GetMapping("/{idPersonaMayor}/medicamentos")
    public ResponseEntity<?> obtenerMedicamentos(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor
    ) {
        if (!tieneRelacionAceptada(idAcompanante, idPersonaMayor)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No tienes autorización para consultar a esta persona mayor");
        }

        DateTimeFormatter formatoHora = DateTimeFormatter.ofPattern("HH:mm");
        DateTimeFormatter formatoFechaHora = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

        List<MedicamentoSeguimientoResponse> respuesta = medicamentoLookupRepository
                .findByIdPersonaMayor(idPersonaMayor)
                .stream()
                .map(m -> new MedicamentoSeguimientoResponse(
                        m.getIdMedicamento(),
                        m.getNombre(),
                        m.getDosis(),
                        m.getFrecuencia(),
                        m.getIntervaloHoras(),
                        m.getHora() != null ? m.getHora().format(formatoHora) : null,
                        m.getProximaToma() != null ? m.getProximaToma().format(formatoFechaHora) : null,
                        m.getUltimaToma() != null ? m.getUltimaToma().format(formatoFechaHora) : null,
                        m.getActivo()
                ))
                .toList();

        return ResponseEntity.ok(respuesta);
    }

    /**
     * Todas las citas médicas de la persona mayor, pasadas y futuras,
     * ordenadas por fecha y hora. El frontend las separa en próximas y pasadas.
     */
    @GetMapping("/{idPersonaMayor}/citas-medicas")
    public ResponseEntity<?> obtenerCitasMedicas(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor
    ) {
        if (!tieneRelacionAceptada(idAcompanante, idPersonaMayor)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No tienes autorización para consultar a esta persona mayor");
        }

        DateTimeFormatter formatoHora = DateTimeFormatter.ofPattern("HH:mm");

        List<CitaMedicaSeguimientoResponse> respuesta = citaMedicaLookupRepository
                .findByIdPersonaMayorOrderByFechaAscHoraAsc(idPersonaMayor)
                .stream()
                .map(c -> new CitaMedicaSeguimientoResponse(
                        c.getIdCita(),
                        c.getTitulo(),
                        c.getLugar(),
                        c.getConsultorio(),
                        c.getFecha() != null ? c.getFecha().toString() : null,
                        c.getHora() != null ? c.getHora().format(formatoHora) : null,
                        c.getObservaciones()
                ))
                .toList();

        return ResponseEntity.ok(respuesta);
    }

    /** Todos los acompañantes aceptados de la persona mayor, incluido el que consulta. */
    @GetMapping("/{idPersonaMayor}/contactos")
    public ResponseEntity<?> obtenerContactos(
            @RequestHeader("X-User-Id") Integer idAcompanante,
            @PathVariable Integer idPersonaMayor
    ) {
        if (!tieneRelacionAceptada(idAcompanante, idPersonaMayor)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No tienes autorización para consultar a esta persona mayor");
        }

        List<PersonaMayorAcompanante> relaciones = relacionRepository
                .findById_IdPersonaMayorAndEstado(idPersonaMayor, "ACEPTADA");

        List<ContactoResponse> respuesta = relaciones.stream()
                .map(relacionAcompanante -> {
                    Integer idOtroAcompanante =
                            relacionAcompanante.getId().getIdAcompanante();

                    UsuarioLookup usuario =
                            usuarioLookupRepository
                                    .findById(idOtroAcompanante)
                                    .orElse(null);

                    String tipoRelacion =
                            acompananteInfoLookupRepository
                                    .findById(idOtroAcompanante)
                                    .map(AcompananteInfoLookup::getRelacion)
                                    .orElse(null);

                    return new ContactoResponse(
                            idOtroAcompanante,
                            usuario != null
                                    ? usuario.getNombreUsuario()
                                    : null,
                            usuario != null
                                    ? usuario.getCelular()
                                    : null,
                            tipoRelacion
                    );
                })
                .toList();

        return ResponseEntity.ok(respuesta);
    }

    /** Si el acompañante tiene un vínculo aceptado con esa persona mayor. */
    private boolean tieneRelacionAceptada(
            Integer idAcompanante,
            Integer idPersonaMayor
    ) {
        return relacionRepository
                .findById_IdAcompananteAndEstado(
                        idAcompanante,
                        "ACEPTADA"
                )
                .stream()
                .anyMatch(
                        relacion -> relacion.getId()
                                .getIdPersonaMayor()
                                .equals(idPersonaMayor)
                );
    }
}