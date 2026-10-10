package com.proyectogrado.actividad_service.scheduler;

import com.proyectogrado.actividad_service.config.ZonaHoraria;
import com.proyectogrado.actividad_service.model.Actividad;
import com.proyectogrado.actividad_service.repository.ActividadRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Crea la siguiente ocurrencia de las actividades periódicas. Cuando pasa
 * el día de una actividad que se repite cada N días, se crea una nueva con
 * los mismos datos (nombre, hora, lugar, cupos...) N días después, y la
 * periodicidad pasa a la nueva. Así las personas mayores ven una sola
 * ocurrencia a la vez: la siguiente aparece cuando termina la actual.
 *
 * Cada ocurrencia es una actividad aparte, con sus propias inscripciones,
 * asistencia y recordatorios. Para dejar de repetirla basta con quitarle la
 * periodicidad a la ocurrencia actual o borrarla.
 *
 * Si el servicio estuvo apagado varios días, se salta las fechas que ya
 * pasaron y crea directamente la próxima desde hoy.
 */
@Component
public class ActividadPeriodicaScheduler {

    private final ActividadRepository actividadRepository;
    private final TransactionTemplate transactionTemplate;

    public ActividadPeriodicaScheduler(
            ActividadRepository actividadRepository,
            TransactionTemplate transactionTemplate
    ) {
        this.actividadRepository = actividadRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /** Cada 5 minutos, con la fecha de Colombia; la siguiente sale poco después de medianoche. */
    @Scheduled(cron = "0 */5 * * * *", zone = "America/Bogota")
    public void crearSiguientes() {
        crearSiguientes(ZonaHoraria.ahora().toLocalDate());
    }

    /** Separado para que las pruebas puedan elegir el día. */
    void crearSiguientes(LocalDate hoy) {
        for (Actividad actual : actividadRepository.findPeriodicasVencidas(hoy)) {
            try {
                transactionTemplate.executeWithoutResult(estado -> crearSiguiente(actual, hoy));
            } catch (Exception e) {
                System.out.println("[ACTIVIDAD PERIODICA] Error con actividad "
                        + actual.getIdActividad() + ": " + e.getMessage());
            }
        }
    }

    /** Pasa la periodicidad de la actual a una copia en la primera fecha de la serie desde hoy. */
    private void crearSiguiente(Actividad actual, LocalDate hoy) {
        int frecuencia = actual.getFrecuenciaDias();

        // Otra instancia ya la tomó.
        if (actividadRepository.soltarPeriodicidad(actual.getIdActividad()) != 1) {
            return;
        }

        long diasPasados = ChronoUnit.DAYS.between(actual.getFecha(), hoy);
        long saltos = (diasPasados + frecuencia - 1) / frecuencia;

        Actividad siguiente = new Actividad();
        siguiente.setIdOrganizacion(actual.getIdOrganizacion());
        siguiente.setNombre(actual.getNombre());
        siguiente.setDescripcion(actual.getDescripcion());
        siguiente.setFecha(actual.getFecha().plusDays(saltos * frecuencia));
        siguiente.setHora(actual.getHora());
        siguiente.setLugar(actual.getLugar());
        siguiente.setTipo(actual.getTipo());
        siguiente.setCupos(actual.getCupos());
        siguiente.setResponsable(actual.getResponsable());
        siguiente.setFrecuenciaDias(frecuencia);
        // Si era una propuesta, la organización ya la aceptó: las siguientes
        // son actividades normales de la organización (estado null) y no
        // vuelven a aparecer en las propuestas de quien la propuso.

        siguiente = actividadRepository.save(siguiente);

        System.out.println("[ACTIVIDAD PERIODICA] Actividad " + actual.getIdActividad()
                + " -> siguiente " + siguiente.getIdActividad() + " el " + siguiente.getFecha());
    }
}
