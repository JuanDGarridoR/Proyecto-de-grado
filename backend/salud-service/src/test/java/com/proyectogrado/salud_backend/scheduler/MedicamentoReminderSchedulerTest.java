package com.proyectogrado.salud_backend.scheduler;

import com.proyectogrado.salud_backend.client.MessagingClient;
import com.proyectogrado.salud_backend.model.Medicamento;
import com.proyectogrado.salud_backend.model.RelacionAcompananteId;
import com.proyectogrado.salud_backend.model.RelacionAcompananteLookup;
import com.proyectogrado.salud_backend.model.UsuarioLookup;
import com.proyectogrado.salud_backend.repository.MedicamentoRepository;
import com.proyectogrado.salud_backend.repository.RelacionAcompananteLookupRepository;
import com.proyectogrado.salud_backend.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
 * Simula el scheduler minuto a minuto sobre un medicamento en memoria y
 * verifica que por cada toma salgan exactamente dos avisos (15 minutos
 * antes y a la hora exacta) a la persona mayor y al acompañante, sin
 * repetirse.
 */
class MedicamentoReminderSchedulerTest {

    private static final String CEL_PERSONA = "+570000000001";
    private static final String CEL_ACOMPANANTE = "+570000000002";

    private record Envio(LocalDateTime momento, String celular, String mensaje) {
    }

    private Medicamento medicamento;
    private final List<Envio> envios = new ArrayList<>();
    private LocalDateTime reloj;
    private MedicamentoReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        medicamento = new Medicamento();
        medicamento.setIdMedicamento(1);
        medicamento.setIdPersonaMayor(10);
        medicamento.setNombre("Losartán");
        medicamento.setDosis("50mg");
        medicamento.setIntervaloHoras(8);
        medicamento.setActivo(true);

        MedicamentoRepository medicamentoRepository = mock(MedicamentoRepository.class);

        // Simula la consulta y las actualizaciones condicionales de la BD.
        when(medicamentoRepository.findByActivoTrueAndProximaTomaLessThanEqual(any()))
                .thenAnswer(inv -> {
                    LocalDateTime limite = inv.getArgument(0);
                    return medicamento.getActivo() && !medicamento.getProximaToma().isAfter(limite)
                            ? List.of(copia(medicamento)) : List.of();
                });

        when(medicamentoRepository.reservarAvisoPrevio(anyInt(), any(), any(), any()))
                .thenAnswer(inv -> {
                    LocalDateTime proxima = inv.getArgument(1);
                    LocalDateTime inicioVentana = inv.getArgument(2);
                    LocalDateTime ultimo = medicamento.getUltimoRecordatorioEnviado();
                    if (Objects.equals(medicamento.getProximaToma(), proxima)
                            && (ultimo == null || ultimo.isBefore(inicioVentana))) {
                        medicamento.setUltimoRecordatorioEnviado(inv.getArgument(3));
                        return 1;
                    }
                    return 0;
                });

        when(medicamentoRepository.avanzarToma(anyInt(), any(), any(), any()))
                .thenAnswer(inv -> {
                    LocalDateTime proxima = inv.getArgument(1);
                    if (Objects.equals(medicamento.getProximaToma(), proxima)) {
                        medicamento.setUltimaToma(proxima);
                        medicamento.setProximaToma(inv.getArgument(2));
                        medicamento.setUltimoRecordatorioEnviado(inv.getArgument(3));
                        return 1;
                    }
                    return 0;
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

        MessagingClient messagingClient = mock(MessagingClient.class);
        when(messagingClient.enviarMensaje(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    envios.add(new Envio(reloj, inv.getArgument(0), inv.getArgument(1)));
                    return true;
                });

        scheduler = new MedicamentoReminderScheduler(
                medicamentoRepository, relacionRepository, usuarioRepository, messagingClient);
    }

    /** Copia del medicamento, como si viniera de una consulta nueva a la base de datos. */
    private static Medicamento copia(Medicamento original) {
        Medicamento m = new Medicamento();
        m.setIdMedicamento(original.getIdMedicamento());
        m.setIdPersonaMayor(original.getIdPersonaMayor());
        m.setNombre(original.getNombre());
        m.setDosis(original.getDosis());
        m.setIntervaloHoras(original.getIntervaloHoras());
        m.setProximaToma(original.getProximaToma());
        m.setUltimoRecordatorioEnviado(original.getUltimoRecordatorioEnviado());
        m.setFechaFin(original.getFechaFin());
        m.setActivo(original.getActivo());
        return m;
    }

    private void simularMinutos(LocalDateTime desde, LocalDateTime hasta) {
        simularMinutos(desde, hasta, 0);
    }

    /**
     * Ejecuta el scheduler una vez por minuto entre las dos fechas. desfaseMs
     * simula que se dispara unos milisegundos antes (negativo) o después
     * (positivo) del segundo 0.
     */
    private void simularMinutos(LocalDateTime desde, LocalDateTime hasta, long desfaseMs) {
        for (reloj = desde; !reloj.isAfter(hasta); reloj = reloj.plusMinutes(1)) {
            scheduler.revisarRecordatorios(reloj.plusNanos(desfaseMs * 1_000_000));
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
    void dosTomasEnvianExactamenteDosAvisosCadaUnaAAmbos() {
        LocalDateTime ocho = LocalDateTime.of(2026, 9, 29, 8, 0);
        medicamento.setProximaToma(ocho);

        // Desde las 7:00 hasta las 17:00: tomas a las 8:00 y a las 16:00.
        simularMinutos(ocho.minusHours(1), ocho.plusHours(9));

        // 2 tomas x 2 avisos x 2 destinatarios.
        assertEquals(8, envios.size(), "Envios: " + envios);

        for (LocalDateTime toma : List.of(ocho, ocho.plusHours(8))) {
            LocalDateTime previo = toma.minusMinutes(15);
            assertEquals(1, enviosA(CEL_PERSONA, previo), "aviso previo persona " + previo);
            assertEquals(1, enviosA(CEL_ACOMPANANTE, previo), "aviso previo acompanante " + previo);
            assertEquals(1, enviosA(CEL_PERSONA, toma), "aviso exacto persona " + toma);
            assertEquals(1, enviosA(CEL_ACOMPANANTE, toma), "aviso exacto acompanante " + toma);
        }

        assertEquals(ocho.plusHours(16), medicamento.getProximaToma());
    }

    @Test
    void siElSchedulerSeDisparaUnosMilisegundosAntesOTardeIgualAvisaEnElMinutoExacto() {
        for (long desfaseMs : new long[] {-3, -900, 5, 900}) {
            envios.clear();
            LocalDateTime toma = LocalDateTime.of(2026, 9, 29, 12, 37);
            medicamento.setIntervaloHoras(1);
            medicamento.setProximaToma(toma);
            medicamento.setUltimoRecordatorioEnviado(null);

            simularMinutos(toma.minusMinutes(20), toma.plusMinutes(5), desfaseMs);

            assertEquals(4, envios.size(), "desfase " + desfaseMs + "ms: " + envios);
            assertEquals(1, enviosA(CEL_ACOMPANANTE, toma.minusMinutes(15)), "previo, desfase " + desfaseMs);
            assertEquals(1, enviosA(CEL_ACOMPANANTE, toma), "exacto, desfase " + desfaseMs);
        }
    }

    @Test
    void losMensajesDicenLaHoraYLosMinutosCorrectos() {
        LocalDateTime toma = LocalDateTime.of(2026, 9, 29, 8, 0);
        medicamento.setProximaToma(toma);

        simularMinutos(toma.minusMinutes(20), toma.plusMinutes(1));

        assertEquals(List.of(
                "En 15 minutos, a las 8:00 a. m., te toca tomar Losartán (50mg).",
                "Es hora de tomar Losartán (50mg)."
        ), mensajesA(CEL_PERSONA));
        assertEquals(List.of(
                "En 15 minutos, a las 8:00 a. m., Juan debe tomar Losartán (50mg).",
                "Es hora de que Juan tome Losartán (50mg)."
        ), mensajesA(CEL_ACOMPANANTE));
    }

    @Test
    void siSeCreaDentroDeLos15MinutosElAvisoPrevioDiceLosMinutosReales() {
        LocalDateTime toma = LocalDateTime.of(2026, 9, 29, 8, 0);
        medicamento.setProximaToma(toma);

        // Creado a las 7:53: primer ciclo del scheduler a las 7:53.
        simularMinutos(toma.minusMinutes(7), toma.plusMinutes(1));

        assertEquals(List.of(
                "En 7 minutos, a las 8:00 a. m., te toca tomar Losartán (50mg).",
                "Es hora de tomar Losartán (50mg)."
        ), mensajesA(CEL_PERSONA));
    }

    @Test
    void siElServicioEstuvoApagadoNoMandaAvisosAtrasadosYAvanza() {
        LocalDateTime ahora = LocalDateTime.of(2026, 9, 29, 11, 0);
        medicamento.setProximaToma(ahora.minusHours(3)); // 8:00, ya pasada

        simularMinutos(ahora, ahora.plusMinutes(30));

        assertEquals(0, envios.size(), "Envios: " + envios);
        assertEquals(ahora.plusHours(5), medicamento.getProximaToma()); // 16:00
    }

    @Test
    void cadaHoraTambienManda15MinAntesYALaHora() {
        LocalDateTime ocho = LocalDateTime.of(2026, 9, 29, 8, 0);
        medicamento.setIntervaloHoras(1);
        medicamento.setProximaToma(ocho);

        simularMinutos(ocho.minusMinutes(20), ocho.plusMinutes(70)); // tomas 8:00 y 9:00

        assertEquals(8, envios.size(), "Envios: " + envios);
        assertEquals(1, enviosA(CEL_PERSONA, LocalDateTime.of(2026, 9, 29, 8, 45)));
        assertEquals(1, enviosA(CEL_PERSONA, LocalDateTime.of(2026, 9, 29, 9, 0)));
    }
}
