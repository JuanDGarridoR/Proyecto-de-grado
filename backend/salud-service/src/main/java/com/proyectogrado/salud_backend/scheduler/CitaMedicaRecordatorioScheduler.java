package com.proyectogrado.salud_backend.scheduler;

import com.proyectogrado.salud_backend.client.MensajeriaClient;
import com.proyectogrado.salud_backend.config.ZonaHoraria;
import com.proyectogrado.salud_backend.model.CitaMedica;
import com.proyectogrado.salud_backend.model.RelacionAcompananteLookup;
import com.proyectogrado.salud_backend.model.UsuarioLookup;
import com.proyectogrado.salud_backend.repository.CitaMedicaRepository;
import com.proyectogrado.salud_backend.repository.RelacionAcompananteLookupRepository;
import com.proyectogrado.salud_backend.repository.UsuarioLookupRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * Recordatorios de citas médicas. Por cada cita se envían dos SMS, a la
 * persona mayor y a sus acompañantes aceptados:
 *
 *   1. Un día antes (HORAS_AVISO_DIA horas antes de la cita).
 *   2. Una hora antes (MINUTOS_AVISO_HORA minutos antes de la cita).
 *
 * Si la cita se registra cuando ya falta menos de un día, el primer aviso
 * sale en el siguiente minuto ("Hoy a las ..."); si falta menos de una
 * hora, solo sale el segundo, con los minutos reales que faltan.
 *
 * Cada aviso se "reserva" en la base de datos antes de enviarlo (ver
 * CitaMedicaRepository), así sale una sola vez aunque el servicio se
 * reinicie o haya dos instancias. Si la persona cambia la fecha o la hora
 * de la cita, se vuelve a avisar para la nueva.
 */
@Component
public class CitaMedicaRecordatorioScheduler {

    public static final long HORAS_AVISO_DIA = 24;
    public static final long MINUTOS_AVISO_HORA = 60;

    private static final DateTimeFormatter FORMATO_HORA =
            DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("es-CO"));

    private final CitaMedicaRepository citaMedicaRepository;
    private final RelacionAcompananteLookupRepository relacionAcompananteLookupRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;
    private final MensajeriaClient mensajeriaClient;

    public CitaMedicaRecordatorioScheduler(
            CitaMedicaRepository citaMedicaRepository,
            RelacionAcompananteLookupRepository relacionAcompananteLookupRepository,
            UsuarioLookupRepository usuarioLookupRepository,
            MensajeriaClient mensajeriaClient
    ) {
        this.citaMedicaRepository = citaMedicaRepository;
        this.relacionAcompananteLookupRepository = relacionAcompananteLookupRepository;
        this.usuarioLookupRepository = usuarioLookupRepository;
        this.mensajeriaClient = mensajeriaClient;
    }

    /** Se ejecuta cada minuto, en el segundo 0, con la hora de Colombia. */
    @Scheduled(cron = "0 * * * * *", zone = "America/Bogota")
    public void revisarRecordatorios() {
        revisarRecordatorios(ZonaHoraria.ahora());
    }

    /** Separado para que las pruebas puedan simular el paso del tiempo. */
    void revisarRecordatorios(LocalDateTime momento) {
        // Igual que en los medicamentos: el scheduler puede dispararse unos
        // milisegundos antes o después del segundo 0, y las citas caen en
        // minutos exactos, así que se redondea al minuto más cercano.
        LocalDateTime ahora = momento.plusSeconds(30).truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime limiteDia = ahora.plusHours(HORAS_AVISO_DIA);
        LocalDateTime limiteHora = ahora.plusMinutes(MINUTOS_AVISO_HORA);

        // Hoy y mañana: las citas de las próximas 24 horas.
        List<CitaMedica> candidatas = citaMedicaRepository.findByFechaBetween(
                ahora.toLocalDate(), limiteDia.toLocalDate()
        );

        for (CitaMedica cita : candidatas) {
            LocalDateTime inicio = cita.getInicio();

            // Las que ya empezaron o están a más de un día no se avisan.
            if (inicio == null || !inicio.isAfter(ahora) || inicio.isAfter(limiteDia)) {
                continue;
            }

            try {
                if (!inicio.isAfter(limiteHora)) {
                    if (citaMedicaRepository.reservarAvisoHora(cita.getIdCita(), inicio) == 1) {
                        enviarAvisos(cita, inicio, ahora, true);
                    }
                } else if (citaMedicaRepository.reservarAvisoDia(cita.getIdCita(), inicio) == 1) {
                    enviarAvisos(cita, inicio, ahora, false);
                }
            } catch (Exception e) {
                log("Error con cita " + cita.getIdCita() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Envía el SMS a la persona mayor y a sus acompañantes aceptados. Con
     * esLaHora en true es el aviso de una hora antes; si no, el del día antes.
     */
    private void enviarAvisos(CitaMedica cita, LocalDateTime inicio,
                              LocalDateTime ahora, boolean esLaHora) {

        String cuando = esLaHora
                ? enMinutos(Duration.between(ahora, inicio).toMinutes())
                : (inicio.toLocalDate().equals(ahora.toLocalDate()) ? "Hoy" : "Mañana");

        // Java usa espacios de no separación en "p. m."; en un SMS se ven raros.
        String hora = inicio.format(FORMATO_HORA).replace(' ', ' ').replace(' ', ' ');

        String detalle = "cita médica: " + cita.getTitulo() + " en " + dondeEs(cita) + ".";

        // Las indicaciones (ayuno, documentos...) sirven para preparar la cita
        // con tiempo: van solo en el aviso del día antes.
        String indicaciones = !esLaHora
                && cita.getObservaciones() != null && !cita.getObservaciones().isBlank()
                ? " Recuerda: " + cita.getObservaciones().trim()
                : "";

        UsuarioLookup personaMayor = usuarioLookupRepository
                .findById(cita.getIdPersonaMayor())
                .orElse(null);

        String nombrePersona = personaMayor != null ? personaMayor.getNombreUsuario() : "la persona mayor";

        if (personaMayor != null && personaMayor.getCelular() != null) {
            String mensaje = cuando + ", a las " + hora + ", tienes " + detalle + indicaciones;
            intentarEnviar(cita, personaMayor.getCelular(), mensaje);
        }

        List<RelacionAcompananteLookup> vinculos = relacionAcompananteLookupRepository
                .findById_IdPersonaMayorAndEstado(cita.getIdPersonaMayor(), "ACEPTADA");

        for (RelacionAcompananteLookup vinculo : vinculos) {

            String celularAcompanante = usuarioLookupRepository
                    .findById(vinculo.getId().getIdAcompanante())
                    .map(UsuarioLookup::getCelular)
                    .orElse(null);

            if (celularAcompanante != null) {
                String mensaje = cuando + ", a las " + hora + ", " + nombrePersona + " tiene "
                        + detalle + indicaciones;
                intentarEnviar(cita, celularAcompanante, mensaje);
            }
        }
    }

    /**
     * Lugar y, si lo hay, consultorio: "Hospital San José, consultorio 204".
     * Si la persona ya escribió "Consultorio 204", no se repite la palabra.
     */
    private String dondeEs(CitaMedica cita) {
        String consultorio = cita.getConsultorio();
        if (consultorio == null || consultorio.isBlank()) {
            return cita.getLugar();
        }
        consultorio = consultorio.trim();
        return cita.getLugar() + ", "
                + (consultorio.toLowerCase(Locale.ROOT).startsWith("consultorio")
                        ? consultorio
                        : "consultorio " + consultorio);
    }

    /** "En 1 hora", o "En N minutos" si la cita se registró con menos tiempo. */
    private String enMinutos(long minutosFaltantes) {
        if (minutosFaltantes >= MINUTOS_AVISO_HORA) {
            return "En 1 hora";
        }
        return "En " + minutosFaltantes + (minutosFaltantes == 1 ? " minuto" : " minutos");
    }

    private void intentarEnviar(CitaMedica cita, String celular, String mensaje) {
        boolean enviado = mensajeriaClient.enviarMensaje(celular, mensaje, "CITA_MEDICA");
        log("Cita " + cita.getIdCita() + " -> " + celular + ": " + (enviado ? "OK" : "FALLO")
                + " -> \"" + mensaje + "\"");
    }

    private void log(String texto) {
        System.out.println("[RECORDATORIO CITA] " + texto);
    }
}
