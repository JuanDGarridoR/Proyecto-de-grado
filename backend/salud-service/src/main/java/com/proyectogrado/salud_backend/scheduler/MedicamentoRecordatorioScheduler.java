package com.proyectogrado.salud_backend.scheduler;

import com.proyectogrado.salud_backend.client.MensajeriaClient;
import com.proyectogrado.salud_backend.config.ZonaHoraria;
import com.proyectogrado.salud_backend.model.Medicamento;
import com.proyectogrado.salud_backend.model.RelacionAcompananteLookup;
import com.proyectogrado.salud_backend.model.UsuarioLookup;
import com.proyectogrado.salud_backend.repository.MedicamentoRepository;
import com.proyectogrado.salud_backend.repository.RelacionAcompananteLookupRepository;
import com.proyectogrado.salud_backend.repository.UsuarioLookupRepository;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Recordatorios de medicamentos. Por cada toma se envían exactamente dos
 * mensajes, a la persona mayor y a sus acompañantes aceptados:
 *
 *   1. MINUTOS_AVISO_PREVIO minutos antes de la hora.
 *   2. A la hora exacta. En ese momento la toma se da por hecha y la
 *      próxima toma avanza sola según el intervalo del medicamento.
 *
 * No hay reintentos ni confirmación manual: cada aviso se "reserva" en la
 * base de datos antes de enviarlo (ver MedicamentoRepository), así que no
 * se repite aunque el scheduler corra varias veces o haya dos instancias.
 */
@Component
public class MedicamentoRecordatorioScheduler {

    public static final long MINUTOS_AVISO_PREVIO = 15;

    /**
     * Si el servicio estuvo apagado y la hora de la toma pasó hace más de
     * esto, no se envía un aviso tardío: solo se avanza a la siguiente toma.
     */
    private static final long MINUTOS_TOLERANCIA_ATRASO = 10;

    private static final DateTimeFormatter FORMATO_LOG = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("es-CO"));

    /**
     * Identificador único por arranque. Sirve para detectar en los logs si hay
     * dos instancias de este servicio corriendo a la vez (dos schedulers).
     */
    private final String instanciaId = UUID.randomUUID().toString().substring(0, 8);

    private final MedicamentoRepository medicamentoRepository;
    private final RelacionAcompananteLookupRepository relacionAcompananteLookupRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;
    private final MensajeriaClient mensajeriaClient;

    public MedicamentoRecordatorioScheduler(
            MedicamentoRepository medicamentoRepository,
            RelacionAcompananteLookupRepository relacionAcompananteLookupRepository,
            UsuarioLookupRepository usuarioLookupRepository,
            MensajeriaClient mensajeriaClient
    ) {
        this.medicamentoRepository = medicamentoRepository;
        this.relacionAcompananteLookupRepository = relacionAcompananteLookupRepository;
        this.usuarioLookupRepository = usuarioLookupRepository;
        this.mensajeriaClient = mensajeriaClient;
    }

    @PostConstruct
    public void alArrancar() {
        System.out.println("[SCHEDULER " + instanciaId + "] Instancia creada al arrancar salud-backend.");
    }

    /** Se ejecuta cada minuto, en el segundo 0, con la hora de Colombia. */
    @Scheduled(cron = "0 * * * * *", zone = "America/Bogota")
    public void revisarRecordatorios() {
        revisarRecordatorios(ZonaHoraria.ahora());
    }

    /** Separado para que las pruebas puedan simular el paso del tiempo. */
    void revisarRecordatorios(LocalDateTime momento) {
        // El scheduler puede dispararse unos milisegundos antes o después del
        // segundo 0 (en Windows pasa a menudo). Si corre a las 12:36:59.998,
        // la toma de las 12:37 "aún no ha llegado" y el aviso saldría un
        // minuto tarde. Las tomas siempre caen en minutos exactos, así que se
        // redondea al minuto más cercano.
        LocalDateTime ahora = momento.plusSeconds(30).truncatedTo(ChronoUnit.MINUTES);

        List<Medicamento> proximos = medicamentoRepository
                .findByActivoTrueAndProximaTomaLessThanEqual(ahora.plusMinutes(MINUTOS_AVISO_PREVIO));

        if (!proximos.isEmpty()) {
            System.out.println("[SCHEDULER " + instanciaId + "] " + ahora.format(FORMATO_LOG)
                    + " - medicamentos por avisar: " + proximos.size());
        }

        for (Medicamento medicamento : proximos) {
            try {
                procesar(medicamento, ahora);
            } catch (Exception e) {
                System.out.println("[SCHEDULER " + instanciaId + "] Error con medicamento "
                        + medicamento.getIdMedicamento() + ": " + e.getMessage());
            }
        }
    }

    /** Decide si a este medicamento le toca el aviso previo o el de la hora exacta. */
    private void procesar(Medicamento medicamento, LocalDateTime ahora) {
        LocalDateTime toma = medicamento.getProximaToma();

        // Tratamiento terminado: no se avisa más.
        if (medicamento.getFechaFin() != null
                && toma.toLocalDate().isAfter(medicamento.getFechaFin())) {
            return;
        }

        if (ahora.isBefore(toma)) {
            // Ventana del aviso previo: [toma - 15 min, toma)
            LocalDateTime inicioVentana = toma.minusMinutes(MINUTOS_AVISO_PREVIO);

            int reservado = medicamentoRepository.reservarAvisoPrevio(
                    medicamento.getIdMedicamento(), toma, inicioVentana, ahora);

            if (reservado == 1) {
                // Normalmente 15; menos si el medicamento se creó dentro de la ventana.
                long minutosFaltantes = Duration.between(ahora, toma).toMinutes();
                log("Aviso previo (" + minutosFaltantes + " min) de medicamento "
                        + medicamento.getIdMedicamento() + " para la toma de las " + toma.format(FORMATO_LOG));
                enviarAvisos(medicamento, toma, minutosFaltantes);
            }
            return;
        }

        // Ya es la hora (o ya pasó): se avanza a la siguiente toma futura.
        LocalDateTime siguiente = calcularSiguienteToma(toma, medicamento.getIntervaloHoras(), ahora);

        int reservado = medicamentoRepository.avanzarToma(
                medicamento.getIdMedicamento(), toma, siguiente, ahora);

        if (reservado != 1) {
            return;
        }

        boolean aTiempo = !ahora.isAfter(toma.plusMinutes(MINUTOS_TOLERANCIA_ATRASO));

        if (aTiempo) {
            log("Aviso de hora exacta de medicamento " + medicamento.getIdMedicamento()
                    + " para la toma de las " + toma.format(FORMATO_LOG)
                    + ". Siguiente toma: " + siguiente.format(FORMATO_LOG));
            enviarAvisos(medicamento, toma, 0);
        } else {
            log("Toma de las " + toma.format(FORMATO_LOG) + " del medicamento "
                    + medicamento.getIdMedicamento() + " ya habia pasado, no se avisa. Siguiente toma: "
                    + siguiente.format(FORMATO_LOG));
        }
    }

    /** Primera toma posterior a "ahora", saltando de intervalo en intervalo (24 h si no hay intervalo). */
    private LocalDateTime calcularSiguienteToma(LocalDateTime toma, Integer intervaloHoras, LocalDateTime ahora) {
        int intervalo = intervaloHoras != null && intervaloHoras > 0 ? intervaloHoras : 24;

        LocalDateTime siguiente = toma.plusHours(intervalo);
        while (!siguiente.isAfter(ahora)) {
            siguiente = siguiente.plusHours(intervalo);
        }
        return siguiente;
    }

    /**
     * Envía el SMS a la persona mayor y a sus acompañantes aceptados. Con
     * minutosFaltantes en 0 es el aviso de la hora exacta.
     */
    private void enviarAvisos(Medicamento medicamento, LocalDateTime toma, long minutosFaltantes) {

        boolean esLaHora = minutosFaltantes <= 0;
        String enMinutos = "En " + minutosFaltantes + (minutosFaltantes == 1 ? " minuto" : " minutos");

        String medicina = medicamento.getNombre()
                + (medicamento.getDosis() != null && !medicamento.getDosis().isBlank()
                    ? " (" + medicamento.getDosis() + ")" : "");
        // Java usa espacios de no separación en "a. m."; en un SMS se ven raros.
        String hora = toma.format(FORMATO_HORA).replace(' ', ' ').replace(' ', ' ');

        UsuarioLookup personaMayor = usuarioLookupRepository
                .findById(medicamento.getIdPersonaMayor())
                .orElse(null);

        String nombrePersona = personaMayor != null ? personaMayor.getNombreUsuario() : "la persona mayor";

        if (personaMayor != null && personaMayor.getCelular() != null) {
            String mensaje = esLaHora
                    ? "Es hora de tomar " + medicina + "."
                    : enMinutos + ", a las " + hora + ", te toca tomar " + medicina + ".";
            intentarEnviar(personaMayor.getCelular(), mensaje);
        }

        List<RelacionAcompananteLookup> vinculos = relacionAcompananteLookupRepository
                .findById_IdPersonaMayorAndEstado(medicamento.getIdPersonaMayor(), "ACEPTADA");

        for (RelacionAcompananteLookup vinculo : vinculos) {

            Integer idAcompanante = vinculo.getId().getIdAcompanante();

            String celularAcompanante = usuarioLookupRepository.findById(idAcompanante)
                    .map(UsuarioLookup::getCelular)
                    .orElse(null);

            if (celularAcompanante != null) {
                String mensaje = esLaHora
                        ? "Es hora de que " + nombrePersona + " tome " + medicina + "."
                        : enMinutos + ", a las " + hora + ", " + nombrePersona + " debe tomar " + medicina + ".";
                intentarEnviar(celularAcompanante, mensaje);
            }
        }
    }

    private void intentarEnviar(String celular, String mensaje) {
        boolean enviado = mensajeriaClient.enviarMensaje(celular, mensaje, "MEDICAMENTO");
        log("  Envio a " + celular + ": " + (enviado ? "OK" : "FALLO") + " -> \"" + mensaje + "\"");
    }

    private void log(String texto) {
        System.out.println("[SCHEDULER " + instanciaId + "] " + texto);
    }
}
