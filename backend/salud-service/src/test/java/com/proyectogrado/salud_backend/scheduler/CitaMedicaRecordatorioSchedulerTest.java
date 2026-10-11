package com.proyectogrado.salud_backend.scheduler;

import com.proyectogrado.salud_backend.client.MensajeriaClient;
import com.proyectogrado.salud_backend.model.CitaMedica;
import com.proyectogrado.salud_backend.model.RelacionAcompananteId;
import com.proyectogrado.salud_backend.model.RelacionAcompananteLookup;
import com.proyectogrado.salud_backend.model.UsuarioLookup;
import com.proyectogrado.salud_backend.repository.CitaMedicaRepository;
import com.proyectogrado.salud_backend.repository.RelacionAcompananteLookupRepository;
import com.proyectogrado.salud_backend.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Simula el scheduler minuto a minuto sobre una cita en memoria y verifica
 * que salgan exactamente dos avisos (un día antes y una hora antes) a la
 * persona mayor y al acompañante, sin repetirse.
 */
class CitaMedicaRecordatorioSchedulerTest {

    private static final String CEL_PERSONA = "+570000000001";
    private static final String CEL_ACOMPANANTE = "+570000000002";

    private record Envio(LocalDateTime momento, String celular, String mensaje) {
    }

    private CitaMedica cita;
    private final List<Envio> envios = new ArrayList<>();
    private LocalDateTime reloj;
    private CitaMedicaRecordatorioScheduler scheduler;

    @BeforeEach
    void setUp() {
        cita = new CitaMedica();
        cita.setIdCita(1);
        cita.setIdPersonaMayor(10);
        cita.setTitulo("Cardiología");
        cita.setLugar("Hospital San José");

        CitaMedicaRepository citaRepository = mock(CitaMedicaRepository.class);

        // Simula la consulta y las actualizaciones condicionales de la BD.
        when(citaRepository.findByFechaBetween(any(), any()))
                .thenAnswer(inv -> {
                    LocalDate desde = inv.getArgument(0);
                    LocalDate hasta = inv.getArgument(1);
                    return !cita.getFecha().isBefore(desde) && !cita.getFecha().isAfter(hasta)
                            ? List.of(cita) : List.of();
                });

        when(citaRepository.reservarAvisoDia(anyInt(), any()))
                .thenAnswer(inv -> {
                    LocalDateTime inicio = inv.getArgument(1);
                    if (Objects.equals(cita.getRecordatorioDiaEnviadoPara(), inicio)) {
                        return 0;
                    }
                    cita.setRecordatorioDiaEnviadoPara(inicio);
                    return 1;
                });

        when(citaRepository.reservarAvisoHora(anyInt(), any()))
                .thenAnswer(inv -> {
                    LocalDateTime inicio = inv.getArgument(1);
                    if (Objects.equals(cita.getRecordatorioHoraEnviadoPara(), inicio)) {
                        return 0;
                    }
                    cita.setRecordatorioHoraEnviadoPara(inicio);
                    return 1;
                });

        UsuarioLookup persona = mock(UsuarioLookup.class);
        when(persona.getNombreUsuario()).thenReturn("Juan");
        when(persona.getCelular()).thenReturn(CEL_PERSONA);

        UsuarioLookup acompanante = mock(UsuarioLookup.class);
        when(acompanante.getCelular()).thenReturn(CEL_ACOMPANANTE);

        UsuarioLookupRepository usuarioRepository = mock(UsuarioLookupRepository.class);
        when(usuarioRepository.findById(10)).thenReturn(Optional.of(persona));
        when(usuarioRepository.findById(20)).thenReturn(Optional.of(acompanante));

        RelacionAcompananteId idRelacion = mock(RelacionAcompananteId.class);
        when(idRelacion.getIdAcompanante()).thenReturn(20);
        RelacionAcompananteLookup relacion = mock(RelacionAcompananteLookup.class);
        when(relacion.getId()).thenReturn(idRelacion);

        RelacionAcompananteLookupRepository relacionRepository = mock(RelacionAcompananteLookupRepository.class);
        when(relacionRepository.findById_IdPersonaMayorAndEstado(eq(10), eq("ACEPTADA")))
                .thenReturn(List.of(relacion));

        MensajeriaClient mensajeriaClient = mock(MensajeriaClient.class);
        when(mensajeriaClient.enviarMensaje(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    envios.add(new Envio(reloj, inv.getArgument(0), inv.getArgument(1)));
                    return true;
                });

        scheduler = new CitaMedicaRecordatorioScheduler(
                citaRepository, relacionRepository, usuarioRepository, mensajeriaClient);
    }

    private void programar(LocalDateTime inicio) {
        cita.setFecha(inicio.toLocalDate());
        cita.setHora(inicio.toLocalTime());
    }

    /** Ejecuta el scheduler una vez por minuto entre las dos fechas. */
    private void simularMinutos(LocalDateTime desde, LocalDateTime hasta) {
        for (reloj = desde; !reloj.isAfter(hasta); reloj = reloj.plusMinutes(1)) {
            scheduler.revisarRecordatorios(reloj);
        }
    }

    private List<String> mensajesA(String celular) {
        return envios.stream().filter(e -> e.celular().equals(celular)).map(Envio::mensaje).toList();
    }

    private long enviosA(String celular, LocalDateTime momento) {
        return envios.stream()
                .filter(e -> e.celular().equals(celular) && e.momento().equals(momento))
                .count();
    }

    @Test
    void avisaUnDiaAntesYUnaHoraAntesAAmbosSinRepetir() {
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 5, 15, 0);
        programar(inicio);

        // Desde dos días antes hasta una hora después de la cita.
        simularMinutos(inicio.minusDays(2), inicio.plusHours(1));

        // 2 avisos x 2 destinatarios.
        assertEquals(4, envios.size(), "Envios: " + envios);
        assertEquals(1, enviosA(CEL_PERSONA, inicio.minusDays(1)));
        assertEquals(1, enviosA(CEL_ACOMPANANTE, inicio.minusDays(1)));
        assertEquals(1, enviosA(CEL_PERSONA, inicio.minusHours(1)));
        assertEquals(1, enviosA(CEL_ACOMPANANTE, inicio.minusHours(1)));
    }

    @Test
    void losMensajesDicenCuandoQueYDonde() {
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 5, 15, 0);
        programar(inicio);
        cita.setObservaciones("Llevar electrocardiograma anterior");

        simularMinutos(inicio.minusDays(1).minusMinutes(5), inicio);

        assertEquals(List.of(
                "Mañana, a las 3:00 p. m., tienes cita médica: Cardiología en Hospital San José."
                        + " Recuerda: Llevar electrocardiograma anterior",
                "En 1 hora, a las 3:00 p. m., tienes cita médica: Cardiología en Hospital San José."
        ), mensajesA(CEL_PERSONA));
        assertEquals(List.of(
                "Mañana, a las 3:00 p. m., Juan tiene cita médica: Cardiología en Hospital San José."
                        + " Recuerda: Llevar electrocardiograma anterior",
                "En 1 hora, a las 3:00 p. m., Juan tiene cita médica: Cardiología en Hospital San José."
        ), mensajesA(CEL_ACOMPANANTE));
    }

    @Test
    void elConsultorioVaDespuesDelLugarSinRepetirLaPalabra() {
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 5, 15, 0);
        programar(inicio);
        cita.setRecordatorioDiaEnviadoPara(inicio); // solo interesa el aviso de una hora antes
        cita.setConsultorio("204");

        simularMinutos(inicio.minusMinutes(61), inicio.minusMinutes(59));

        cita.setConsultorio("Consultorio 305");
        cita.setRecordatorioHoraEnviadoPara(null);
        simularMinutos(inicio.minusMinutes(59), inicio.minusMinutes(58));

        assertEquals(List.of(
                "En 1 hora, a las 3:00 p. m., tienes cita médica: Cardiología en Hospital San José, consultorio 204.",
                "En 59 minutos, a las 3:00 p. m., tienes cita médica: Cardiología en Hospital San José, Consultorio 305."
        ), mensajesA(CEL_PERSONA));
    }

    @Test
    void siSeRegistraElMismoDiaElPrimerAvisoDiceHoy() {
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 5, 15, 0);
        programar(inicio);

        // Registrada a las 9:00 del mismo día.
        simularMinutos(inicio.minusHours(6), inicio);

        assertEquals(List.of(
                "Hoy, a las 3:00 p. m., tienes cita médica: Cardiología en Hospital San José.",
                "En 1 hora, a las 3:00 p. m., tienes cita médica: Cardiología en Hospital San José."
        ), mensajesA(CEL_PERSONA));
    }

    @Test
    void siFaltaMenosDeUnaHoraSoloAvisaConLosMinutosReales() {
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 5, 15, 0);
        programar(inicio);

        simularMinutos(inicio.minusMinutes(25), inicio.plusMinutes(5));

        assertEquals(List.of(
                "En 25 minutos, a las 3:00 p. m., tienes cita médica: Cardiología en Hospital San José."
        ), mensajesA(CEL_PERSONA));
    }

    @Test
    void siCambiaLaHoraVuelveAAvisarParaLaNueva() {
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 5, 15, 0);
        programar(inicio);
        simularMinutos(inicio.minusDays(1).minusMinutes(1), inicio.minusDays(1).plusMinutes(1));
        assertEquals(1, mensajesA(CEL_PERSONA).size());

        // La persona la mueve para las 5:00 p. m.: a las 5:00 p. m. del día
        // anterior llega el nuevo aviso.
        LocalDateTime nuevoInicio = inicio.plusHours(2);
        programar(nuevoInicio);
        simularMinutos(inicio.minusDays(1).plusMinutes(2), nuevoInicio);

        assertEquals(3, mensajesA(CEL_PERSONA).size(), "Envios: " + envios);
        assertEquals(1, enviosA(CEL_PERSONA, nuevoInicio.minusDays(1)));
        assertEquals(1, enviosA(CEL_PERSONA, nuevoInicio.minusHours(1)));
    }

    @Test
    void lasCitasPasadasNoSeAvisan() {
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 5, 15, 0);
        programar(inicio);

        simularMinutos(inicio, inicio.plusHours(3));

        assertEquals(0, envios.size(), "Envios: " + envios);
    }
}
