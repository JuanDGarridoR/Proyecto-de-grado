package com.proyectogrado.actividad_service.scheduler;

import com.proyectogrado.actividad_service.client.MensajeriaClient;
import com.proyectogrado.actividad_service.model.Actividad;
import com.proyectogrado.actividad_service.model.Participacion;
import com.proyectogrado.actividad_service.model.UsuarioLookup;
import com.proyectogrado.actividad_service.repository.ActividadRepository;
import com.proyectogrado.actividad_service.repository.ParticipacionRepository;
import com.proyectogrado.actividad_service.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Simula el scheduler minuto a minuto y verifica que cada persona mayor
 * inscrita reciba un solo aviso exactamente 1 hora antes de la actividad.
 */
class ActividadRecordatorioSchedulerTest {

    private static final String CEL_INSCRITA = "+570000000001";
    private static final String CEL_OTRA_INSCRITA = "+570000000003";

    private record Envio(LocalDateTime momento, String celular, String mensaje) {
    }

    private Actividad actividad;

    /** idPersonaMayor -> recordatorioEnviadoPara; simula la tabla participacion. */
    private final Map<Integer, LocalDateTime> inscritos = new HashMap<>();
    private final List<Envio> envios = new ArrayList<>();
    private LocalDateTime reloj;
    private ActividadRecordatorioScheduler scheduler;

    @BeforeEach
    void setUp() {
        actividad = new Actividad();
        actividad.setIdActividad(5);
        actividad.setNombre("Yoga");
        actividad.setLugar("Salón comunal");
        actividad.setFecha(LocalDate.of(2026, 9, 29));
        actividad.setHora("15:00");

        // Personas 10 y 30 inscritas; la 20 no está inscrita.
        inscritos.put(10, null);
        inscritos.put(30, null);

        ActividadRepository actividadRepository = mock(ActividadRepository.class);
        when(actividadRepository.findByFechaBetween(any(), any()))
                .thenAnswer(inv -> {
                    LocalDate desde = inv.getArgument(0);
                    LocalDate hasta = inv.getArgument(1);
                    LocalDate fecha = actividad.getFecha();
                    return !fecha.isBefore(desde) && !fecha.isAfter(hasta) ? List.of(actividad) : List.of();
                });

        ParticipacionRepository participacionRepository = mock(ParticipacionRepository.class);
        when(participacionRepository.findById_IdActividad(5))
                .thenAnswer(inv -> inscritos.keySet().stream()
                        .map(id -> new Participacion(id, 5))
                        .toList());
        when(participacionRepository.reservarRecordatorio(anyInt(), anyInt(), any()))
                .thenAnswer(inv -> {
                    Integer idPersona = inv.getArgument(0);
                    LocalDateTime inicio = inv.getArgument(2);
                    if (!inscritos.containsKey(idPersona)
                            || Objects.equals(inscritos.get(idPersona), inicio)) {
                        return 0;
                    }
                    inscritos.put(idPersona, inicio);
                    return 1;
                });

        UsuarioLookupRepository usuarioRepository = mock(UsuarioLookupRepository.class);
        when(usuarioRepository.findById(anyInt())).thenAnswer(inv -> {
            Integer id = inv.getArgument(0);
            UsuarioLookup usuario = mock(UsuarioLookup.class);
            when(usuario.getCelular()).thenReturn(
                    id == 10 ? CEL_INSCRITA : id == 30 ? CEL_OTRA_INSCRITA : "+570000000002");
            return Optional.of(usuario);
        });

        MensajeriaClient mensajeriaClient = mock(MensajeriaClient.class);
        when(mensajeriaClient.enviarMensaje(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    envios.add(new Envio(reloj, inv.getArgument(0), inv.getArgument(1)));
                    return true;
                });

        scheduler = new ActividadRecordatorioScheduler(
                actividadRepository, participacionRepository, usuarioRepository, mensajeriaClient);
    }

    /**
     * Ejecuta el scheduler una vez por minuto entre las dos fechas. desfaseMs
     * simula que se dispara unos milisegundos antes o después del segundo 0.
     */
    private void simularMinutos(LocalDateTime desde, LocalDateTime hasta, long desfaseMs) {
        for (reloj = desde; !reloj.isAfter(hasta); reloj = reloj.plusMinutes(1)) {
            scheduler.revisarRecordatorios(reloj.plusNanos(desfaseMs * 1_000_000));
        }
    }

    @Test
    void cadaInscritaRecibeUnSoloAvisoExactamenteUnaHoraAntes() {
        for (long desfaseMs : new long[] {0, -3, -900, 900}) {
            envios.clear();
            inscritos.replaceAll((id, enviado) -> null);

            // De 13:30 a 15:30
            simularMinutos(LocalDateTime.of(2026, 9, 29, 13, 30),
                    LocalDateTime.of(2026, 9, 29, 15, 30), desfaseMs);

            assertEquals(2, envios.size(), "desfase " + desfaseMs + "ms: " + envios);
            for (Envio envio : envios) {
                assertEquals(LocalDateTime.of(2026, 9, 29, 14, 0), envio.momento(), "desfase " + desfaseMs);
                assertEquals("En 1 hora, a las 3:00 p. m., empieza tu actividad \"Yoga\" en Salón comunal.",
                        envio.mensaje());
            }
        }
    }

    @Test
    void quienNoEstaInscritoNoRecibeNada() {
        simularMinutos(LocalDateTime.of(2026, 9, 29, 13, 30),
                LocalDateTime.of(2026, 9, 29, 15, 30), 0);

        assertEquals(0, envios.stream().filter(e -> e.celular().equals("+570000000002")).count());
    }

    @Test
    void siCancelaLaInscripcionAntesNoRecibeAviso() {
        inscritos.remove(30);

        simularMinutos(LocalDateTime.of(2026, 9, 29, 13, 30),
                LocalDateTime.of(2026, 9, 29, 15, 30), 0);

        assertEquals(1, envios.size(), "Envios: " + envios);
        assertEquals(CEL_INSCRITA, envios.get(0).celular());
    }

    @Test
    void siSeInscribeCuandoFaltaMenosDeUnaHoraRecibeElAvisoConLosMinutosReales() {
        inscritos.remove(30);
        inscritos.remove(10);

        simularMinutos(LocalDateTime.of(2026, 9, 29, 13, 30),
                LocalDateTime.of(2026, 9, 29, 14, 19), 0);
        inscritos.put(10, null); // se inscribe a las 14:20
        simularMinutos(LocalDateTime.of(2026, 9, 29, 14, 20),
                LocalDateTime.of(2026, 9, 29, 15, 30), 0);

        assertEquals(1, envios.size(), "Envios: " + envios);
        assertEquals("En 40 minutos, a las 3:00 p. m., empieza tu actividad \"Yoga\" en Salón comunal.",
                envios.get(0).mensaje());
    }

    @Test
    void siLaOrganizacionCambiaLaHoraSeAvisaDeNuevoParaLaNueva() {
        inscritos.remove(30);

        simularMinutos(LocalDateTime.of(2026, 9, 29, 13, 30),
                LocalDateTime.of(2026, 9, 29, 14, 10), 0);
        actividad.setHora("17:00"); // la movieron a las 5 p. m.
        simularMinutos(LocalDateTime.of(2026, 9, 29, 14, 11),
                LocalDateTime.of(2026, 9, 29, 17, 30), 0);

        assertEquals(2, envios.size(), "Envios: " + envios);
        assertEquals(LocalDateTime.of(2026, 9, 29, 14, 0), envios.get(0).momento());
        assertEquals(LocalDateTime.of(2026, 9, 29, 16, 0), envios.get(1).momento());
    }
}
