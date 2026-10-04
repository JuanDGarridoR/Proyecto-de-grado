package com.proyectogrado.actividad_service.scheduler;

import com.proyectogrado.actividad_service.client.MessagingClient;
import com.proyectogrado.actividad_service.config.ZonaHoraria;
import com.proyectogrado.actividad_service.model.Actividad;
import com.proyectogrado.actividad_service.model.Participacion;
import com.proyectogrado.actividad_service.model.UsuarioLookup;
import com.proyectogrado.actividad_service.repository.ActividadRepository;
import com.proyectogrado.actividad_service.repository.ParticipacionRepository;
import com.proyectogrado.actividad_service.repository.UsuarioLookupRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * Avisa por SMS a cada persona mayor inscrita en una actividad 1 hora antes
 * de que empiece. Estar inscrita es tener una fila en participacion; al
 * cancelar la inscripción la fila se borra y ya no se avisa.
 *
 * El aviso se "reserva" en la base de datos antes de enviarlo (columna
 * recordatorio_enviado_para), así sale una sola vez aunque el servicio se
 * reinicie o haya dos instancias. Si la organización cambia la fecha o la
 * hora, se vuelve a avisar para la nueva.
 *
 * Si la persona se inscribe cuando falta menos de 1 hora, el aviso sale en
 * el siguiente minuto con el tiempo real que falta.
 */
@Component
public class ActividadReminderScheduler {

    public static final long MINUTOS_ANTES = 60;

    private static final DateTimeFormatter FORMATO_HORA =
            DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("es-CO"));

    private final ActividadRepository actividadRepository;
    private final ParticipacionRepository participacionRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;
    private final MessagingClient messagingClient;

    public ActividadReminderScheduler(
            ActividadRepository actividadRepository,
            ParticipacionRepository participacionRepository,
            UsuarioLookupRepository usuarioLookupRepository,
            MessagingClient messagingClient
    ) {
        this.actividadRepository = actividadRepository;
        this.participacionRepository = participacionRepository;
        this.usuarioLookupRepository = usuarioLookupRepository;
        this.messagingClient = messagingClient;
    }

    /** Se ejecuta cada minuto, en el segundo 0, con la hora de Colombia. */
    @Scheduled(cron = "0 * * * * *", zone = "America/Bogota")
    public void revisarRecordatorios() {
        revisarRecordatorios(ZonaHoraria.ahora());
    }

    /** Separado para que las pruebas puedan simular el paso del tiempo. */
    void revisarRecordatorios(LocalDateTime momento) {
        // El scheduler puede dispararse unos milisegundos antes o después del
        // segundo 0 (en Windows pasa a menudo); las actividades empiezan en
        // minutos exactos, así que se redondea al minuto más cercano para que
        // el aviso no salga un minuto tarde.
        LocalDateTime ahora = momento.plusSeconds(30).truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime limite = ahora.plusMinutes(MINUTOS_ANTES);

        // Hoy y mañana, por si la ventana cruza la medianoche.
        List<Actividad> candidatas = actividadRepository.findByFechaBetween(
                ahora.toLocalDate(), limite.toLocalDate()
        );

        for (Actividad actividad : candidatas) {
            LocalDateTime inicio = inicioDe(actividad);

            // Solo las que empiezan dentro de la próxima hora.
            if (inicio == null || !inicio.isAfter(ahora) || inicio.isAfter(limite)) {
                continue;
            }

            for (Participacion participacion
                    : participacionRepository.findById_IdActividad(actividad.getIdActividad())) {

                Integer idPersonaMayor = participacion.getId().getIdPersonaMayor();

                try {
                    int reservado = participacionRepository.reservarRecordatorio(
                            idPersonaMayor, actividad.getIdActividad(), inicio);

                    if (reservado == 1) {
                        enviarRecordatorio(actividad, idPersonaMayor, inicio,
                                Duration.between(ahora, inicio).toMinutes());
                    }
                } catch (Exception e) {
                    System.out.println("[RECORDATORIO ACTIVIDAD] Error con actividad "
                            + actividad.getIdActividad() + " / persona mayor " + idPersonaMayor
                            + ": " + e.getMessage());
                }
            }
        }
    }

    /** Arma el SMS ("En 1 hora, a las ..., empieza tu actividad ...") y lo envía. */
    private void enviarRecordatorio(Actividad actividad, Integer idPersonaMayor,
                                    LocalDateTime inicio, long minutosFaltantes) {
        String celular = usuarioLookupRepository.findById(idPersonaMayor)
                .map(UsuarioLookup::getCelular)
                .orElse(null);

        if (celular == null || celular.isBlank()) {
            return;
        }

        String cuando = minutosFaltantes >= MINUTOS_ANTES
                ? "En 1 hora"
                : "En " + minutosFaltantes + (minutosFaltantes == 1 ? " minuto" : " minutos");

        // Java usa espacios de no separación en "p. m."; en un SMS se ven raros.
        String hora = inicio.format(FORMATO_HORA).replace(' ', ' ').replace(' ', ' ');

        String mensaje = cuando + ", a las " + hora + ", empieza tu actividad \""
                + actividad.getNombre() + "\""
                + (actividad.getLugar() != null && !actividad.getLugar().isBlank()
                        ? " en " + actividad.getLugar()
                        : "")
                + ".";

        boolean enviado = messagingClient.enviarMensaje(celular, mensaje, "ACTIVIDAD");
        System.out.println("[RECORDATORIO ACTIVIDAD] Actividad " + actividad.getIdActividad()
                + " -> persona mayor " + idPersonaMayor + ": " + (enviado ? "OK" : "FALLO")
                + " -> \"" + mensaje + "\"");
    }

    /** Fecha más hora ("HH:mm" del formulario), o null si falta alguna o no se entiende. */
    private LocalDateTime inicioDe(Actividad actividad) {
        LocalDate fecha = actividad.getFecha();
        String hora = actividad.getHora();

        if (fecha == null || hora == null || hora.isBlank()) {
            return null;
        }

        try {
            return fecha.atTime(LocalTime.parse(hora.trim()));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
