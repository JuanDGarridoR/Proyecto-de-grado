package com.proyectogrado.persona_mayor_service.controller;

import com.proyectogrado.persona_mayor_service.client.MensajeriaClient;
import com.proyectogrado.persona_mayor_service.config.ZonaHoraria;
import com.proyectogrado.persona_mayor_service.model.Emergencia;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorAcompanante;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorOrganizacion;
import com.proyectogrado.persona_mayor_service.model.UsuarioLookup;
import com.proyectogrado.persona_mayor_service.repository.EmergenciaRepository;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorAcompananteRepository;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorOrganizacionRepository;
import com.proyectogrado.persona_mayor_service.repository.UsuarioLookupRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Botón de emergencia de la persona mayor: envía un SMS a todos sus
 * acompañantes y organizaciones con vínculo aceptado. Este servicio decide
 * a quién avisar; mensajeria-service solo hace el envío. Además la guarda
 * (tabla emergencia) para que los acompañantes la vean en su inicio.
 */
@RestController
@RequestMapping("/api/persona-mayor/emergencia")
public class EmergenciaController {

    private final PersonaMayorAcompananteRepository relacionAcompananteRepository;
    private final PersonaMayorOrganizacionRepository relacionOrganizacionRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;
    private final MensajeriaClient mensajeriaClient;
    private final EmergenciaRepository emergenciaRepository;

    public EmergenciaController(
            PersonaMayorAcompananteRepository relacionAcompananteRepository,
            PersonaMayorOrganizacionRepository relacionOrganizacionRepository,
            UsuarioLookupRepository usuarioLookupRepository,
            MensajeriaClient mensajeriaClient,
            EmergenciaRepository emergenciaRepository
    ) {
        this.relacionAcompananteRepository = relacionAcompananteRepository;
        this.relacionOrganizacionRepository = relacionOrganizacionRepository;
        this.usuarioLookupRepository = usuarioLookupRepository;
        this.mensajeriaClient = mensajeriaClient;
        this.emergenciaRepository = emergenciaRepository;
    }

    /** Envía la alerta. Responde 400 si no se pudo avisar a nadie. */
    @PostMapping
    public ResponseEntity<String> enviarEmergencia(
            @RequestHeader("X-User-Id") Integer idPersonaMayor
    ) {
        // Primero se guarda: aunque ningún SMS salga, la alerta queda en la aplicación.
        emergenciaRepository.save(new Emergencia(idPersonaMayor, LocalDateTime.now(ZonaHoraria.COLOMBIA)));

        UsuarioLookup personaMayor = usuarioLookupRepository.findById(idPersonaMayor).orElse(null);

        String nombrePersonaMayor =
                personaMayor != null ? personaMayor.getNombreUsuario() : "Una persona mayor";

        String mensaje = "ALERTA DE EMERGENCIA: " + nombrePersonaMayor
                + " ha activado una alerta desde VITA+. Por favor, verifica que se encuentre bien.";

        int enviadosAcompanantes = 0;
        int enviadosOrganizaciones = 0;

        List<PersonaMayorAcompanante> relacionesAcompanantes =
                relacionAcompananteRepository.findById_IdPersonaMayorAndEstado(idPersonaMayor, "ACEPTADA");

        for (PersonaMayorAcompanante relacion : relacionesAcompanantes) {

            Integer idAcompanante = relacion.getId().getIdAcompanante();

            String celular = usuarioLookupRepository.findById(idAcompanante)
                    .map(UsuarioLookup::getCelular)
                    .orElse(null);

            if (celular == null || celular.isBlank()) {
                continue;
            }

            if (mensajeriaClient.enviarMensaje(celular, mensaje, "EMERGENCIA")) {
                enviadosAcompanantes++;
            }
        }

        List<PersonaMayorOrganizacion> relacionesOrganizaciones =
                relacionOrganizacionRepository.findById_IdPersonaMayorAndEstado(idPersonaMayor, "ACEPTADA");

        for (PersonaMayorOrganizacion relacion : relacionesOrganizaciones) {

            Integer idOrganizacion = relacion.getId().getIdOrganizacion();

            // De cada organización se avisa al celular de su primera cuenta.
            List<UsuarioLookup> usuariosOrganizacion =
                    usuarioLookupRepository.findByIdOrganizacion(idOrganizacion);

            String celular = usuariosOrganizacion.isEmpty()
                    ? null
                    : usuariosOrganizacion.get(0).getCelular();

            if (celular == null || celular.isBlank()) {
                continue;
            }

            if (mensajeriaClient.enviarMensaje(celular, mensaje, "EMERGENCIA")) {
                enviadosOrganizaciones++;
            }
        }

        int totalEnviados = enviadosAcompanantes + enviadosOrganizaciones;

        if (totalEnviados == 0) {
            return ResponseEntity.badRequest()
                    .body("No se pudo enviar la alerta a ningún acompañante u organización");
        }

        return ResponseEntity.ok(
                "Alerta de emergencia enviada a " + enviadosAcompanantes
                        + " acompañante(s) y " + enviadosOrganizaciones + " organización(es)"
        );
    }
}
