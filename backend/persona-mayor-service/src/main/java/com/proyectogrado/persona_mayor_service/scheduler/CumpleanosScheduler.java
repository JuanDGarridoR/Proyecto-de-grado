package com.proyectogrado.persona_mayor_service.scheduler;

import com.proyectogrado.persona_mayor_service.client.MessagingClient;
import com.proyectogrado.persona_mayor_service.config.ZonaHoraria;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorAcompanante;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorLookup;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorOrganizacion;
import com.proyectogrado.persona_mayor_service.model.UsuarioLookup;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorAcompananteRepository;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorLookupRepository;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorOrganizacionRepository;
import com.proyectogrado.persona_mayor_service.repository.UsuarioLookupRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Month;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Todos los días a las 8 a.m. (hora de Colombia):
 * - Por cada persona mayor que cumple años, VITA+ la felicita por SMS (que
 *   también queda en la campanita) y se les recuerda a sus acompañantes y
 *   organizaciones con relación ACEPTADA para que la feliciten.
 * - Por cada acompañante o voluntario que cumple años, VITA+ lo felicita.
 *
 * Los nacidos un 29 de febrero reciben los mensajes el 28 en años no bisiestos.
 */
@Component
public class CumpleanosScheduler {

    private final PersonaMayorLookupRepository personaMayorLookupRepository;
    private final PersonaMayorAcompananteRepository relacionAcompananteRepository;
    private final PersonaMayorOrganizacionRepository relacionOrganizacionRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;
    private final MessagingClient messagingClient;

    public CumpleanosScheduler(
            PersonaMayorLookupRepository personaMayorLookupRepository,
            PersonaMayorAcompananteRepository relacionAcompananteRepository,
            PersonaMayorOrganizacionRepository relacionOrganizacionRepository,
            UsuarioLookupRepository usuarioLookupRepository,
            MessagingClient messagingClient
    ) {
        this.personaMayorLookupRepository = personaMayorLookupRepository;
        this.relacionAcompananteRepository = relacionAcompananteRepository;
        this.relacionOrganizacionRepository = relacionOrganizacionRepository;
        this.usuarioLookupRepository = usuarioLookupRepository;
        this.messagingClient = messagingClient;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Bogota")
    public void revisarCumpleanos() {
        revisarCumpleanos(ZonaHoraria.hoy());
    }

    /** Separado para poder probar el flujo con cualquier fecha. */
    void revisarCumpleanos(LocalDate hoy) {
        List<Integer> dias = (hoy.getMonth() == Month.FEBRUARY && hoy.getDayOfMonth() == 28 && !hoy.isLeapYear())
                ? List.of(28, 29)
                : List.of(hoy.getDayOfMonth());

        for (PersonaMayorLookup personaMayor
                : personaMayorLookupRepository.findCumpleanos(hoy.getMonthValue(), dias)) {
            try {
                avisar(personaMayor);
            } catch (Exception e) {
                System.out.println("[CUMPLEAÑOS] Error con persona mayor " + personaMayor.getIdUsuario()
                        + ": " + e.getMessage());
            }
        }

        for (UsuarioLookup usuario
                : usuarioLookupRepository.findCumpleanosAcompanantesYVoluntarios(hoy.getMonthValue(), dias)) {
            try {
                felicitar(usuario);
            } catch (Exception e) {
                System.out.println("[CUMPLEAÑOS] Error con usuario " + usuario.getIdUsuario()
                        + ": " + e.getMessage());
            }
        }
    }

    private void avisar(PersonaMayorLookup personaMayor) {
        Integer idPersonaMayor = personaMayor.getIdUsuario();

        Optional<UsuarioLookup> usuario = usuarioLookupRepository.findById(idPersonaMayor);

        String nombre = usuario
                .map(UsuarioLookup::getNombreUsuario)
                .orElse("una persona mayor que acompañas");

        // Felicitación de VITA+ a la persona mayor
        usuario.ifPresent(this::felicitar);

        String recordatorio = "Hoy es el cumpleaños de " + nombre
                + ". Un saludo tuyo puede alegrarle el día.";

        // Un Set por si el mismo celular aparece dos veces
        Set<String> celulares = new LinkedHashSet<>();

        for (PersonaMayorAcompanante relacion
                : relacionAcompananteRepository.findById_IdPersonaMayorAndEstado(idPersonaMayor, "ACEPTADA")) {

            usuarioLookupRepository.findById(relacion.getId().getIdAcompanante())
                    .map(UsuarioLookup::getCelular)
                    .filter(celular -> !celular.isBlank())
                    .ifPresent(celulares::add);
        }

        // Igual que la alerta de emergencia: el celular del primer usuario de la organización
        for (PersonaMayorOrganizacion relacion
                : relacionOrganizacionRepository.findById_IdPersonaMayorAndEstado(idPersonaMayor, "ACEPTADA")) {

            usuarioLookupRepository.findByIdOrganizacion(relacion.getId().getIdOrganizacion())
                    .stream()
                    .findFirst()
                    .map(UsuarioLookup::getCelular)
                    .filter(celular -> celular != null && !celular.isBlank())
                    .ifPresent(celulares::add);
        }

        for (String celular : celulares) {
            enviar(idPersonaMayor, celular, recordatorio, "CUMPLEANOS_PERSONA_MAYOR");
        }
    }

    /**
     * Felicitación de VITA+ al usuario que cumple años (persona mayor,
     * acompañante o voluntario).
     */
    private void felicitar(UsuarioLookup usuario) {
        String celular = usuario.getCelular();
        if (celular == null || celular.isBlank()) {
            return;
        }
        enviar(usuario.getIdUsuario(), celular,
                "¡Feliz cumpleaños, " + usuario.getNombreUsuario()
                        + "! Todo el equipo de VITA+ te desea un día lleno de alegría y salud.",
                "CUMPLEANOS");
    }

    private void enviar(Integer idUsuario, String celular, String mensaje, String tipo) {
        boolean enviado = messagingClient.enviarMensaje(celular, mensaje, tipo);
        System.out.println("[CUMPLEAÑOS] Usuario " + idUsuario + " -> " + celular
                + ": " + (enviado ? "OK" : "FALLO"));
    }
}
