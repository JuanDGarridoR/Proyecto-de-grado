package com.proyectogrado.actividad_service.scheduler;

import com.proyectogrado.actividad_service.model.Actividad;
import com.proyectogrado.actividad_service.repository.ActividadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Simula la tabla actividad en memoria y verifica que de una actividad
 * periódica siempre haya una sola ocurrencia próxima, con los mismos datos.
 */
class ActividadPeriodicaSchedulerTest {

    private final List<Actividad> tabla = new ArrayList<>();
    private ActividadPeriodicaScheduler scheduler;

    @BeforeEach
    void setUp() {
        ActividadRepository repo = mock(ActividadRepository.class);

        when(repo.findPeriodicasVencidas(any())).thenAnswer(inv -> {
            LocalDate hoy = inv.getArgument(0);
            return tabla.stream()
                    .filter(a -> a.getFrecuenciaDias() != null && a.getFecha().isBefore(hoy) && a.esVisible())
                    .toList();
        });
        when(repo.soltarPeriodicidad(anyInt())).thenAnswer(inv -> {
            Actividad a = buscar(inv.getArgument(0));
            if (a.getFrecuenciaDias() == null) {
                return 0;
            }
            a.setFrecuenciaDias(null);
            return 1;
        });
        when(repo.save(any())).thenAnswer(inv -> {
            Actividad a = inv.getArgument(0);
            a.setIdActividad(tabla.size() + 1);
            tabla.add(a);
            return a;
        });

        scheduler = new ActividadPeriodicaScheduler(
                repo, new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    private Actividad buscar(Integer id) {
        return tabla.stream().filter(a -> a.getIdActividad().equals(id)).findFirst().orElseThrow();
    }

    private Actividad crear(LocalDate fecha, Integer frecuencia, String estado) {
        Actividad a = new Actividad();
        a.setIdActividad(tabla.size() + 1);
        a.setIdOrganizacion(7);
        a.setNombre("Yoga");
        a.setHora("09:00");
        a.setLugar("Salón comunal");
        a.setCupos(15);
        a.setFecha(fecha);
        a.setFrecuenciaDias(frecuencia);
        a.setEstado(estado);
        a.setIdVoluntario(estado != null ? 3 : null);
        tabla.add(a);
        return a;
    }

    @Test
    void creaLaSiguienteCuandoPasaElDiaYLePasaLaPeriodicidad() {
        Actividad lunes = crear(LocalDate.of(2026, 10, 5), 7, null);

        // El mismo día todavía no: es la actual.
        scheduler.crearSiguientes(LocalDate.of(2026, 10, 5));
        assertEquals(1, tabla.size());

        scheduler.crearSiguientes(LocalDate.of(2026, 10, 6));
        assertEquals(2, tabla.size());

        Actividad siguiente = tabla.get(1);
        assertEquals(LocalDate.of(2026, 10, 12), siguiente.getFecha());
        assertEquals("Yoga", siguiente.getNombre());
        assertEquals("09:00", siguiente.getHora());
        assertEquals("Salón comunal", siguiente.getLugar());
        assertEquals(15, siguiente.getCupos());
        assertEquals(7, siguiente.getIdOrganizacion());
        assertEquals(7, siguiente.getFrecuenciaDias());
        assertNull(lunes.getFrecuenciaDias());

        // Repetir el scheduler el mismo día no duplica nada.
        scheduler.crearSiguientes(LocalDate.of(2026, 10, 6));
        scheduler.crearSiguientes(LocalDate.of(2026, 10, 12));
        assertEquals(2, tabla.size());
    }

    @Test
    void siElServicioEstuvoApagadoSaltaLasFechasQueYaPasaron() {
        crear(LocalDate.of(2026, 10, 1), 3, null);

        // 1, 4, 7, 10, 13: desde el 11 la próxima es el 13.
        scheduler.crearSiguientes(LocalDate.of(2026, 10, 11));

        assertEquals(2, tabla.size());
        assertEquals(LocalDate.of(2026, 10, 13), tabla.get(1).getFecha());
    }

    @Test
    void lasPropuestasSoloSeRepitenSiFueronAceptadasYLasSiguientesSonDeLaOrganizacion() {
        crear(LocalDate.of(2026, 10, 5), 1, Actividad.PENDIENTE);
        crear(LocalDate.of(2026, 10, 5), 1, Actividad.RECHAZADA);
        Actividad aceptada = crear(LocalDate.of(2026, 10, 5), 1, Actividad.ACEPTADA);

        scheduler.crearSiguientes(LocalDate.of(2026, 10, 6));

        assertEquals(4, tabla.size());
        Actividad siguiente = tabla.get(3);
        assertEquals(LocalDate.of(2026, 10, 6), siguiente.getFecha());
        assertNull(siguiente.getEstado());
        assertNull(siguiente.getIdVoluntario());
        assertNull(aceptada.getFrecuenciaDias());
    }
}
