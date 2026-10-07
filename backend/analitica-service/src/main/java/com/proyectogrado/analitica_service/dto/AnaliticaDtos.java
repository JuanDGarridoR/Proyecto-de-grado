package com.proyectogrado.analitica_service.dto;

import java.util.List;

/**
 * Respuestas de la analítica de la organización. Son datos "crudos"
 * (una fila por actividad, por medición, por persona); el frontend arma
 * los indicadores y las gráficas a partir de ellos.
 */
public final class AnaliticaDtos {

    private AnaliticaDtos() {
    }

    /** Una actividad del período con sus inscritos y asistentes. */
    public record ActividadAnalitica(
            Integer idActividad,
            String nombre,
            String tipo,
            String fecha,          // YYYY-MM-DD
            Integer cupos,
            long inscritos,
            long asistentes,       // asistio = true
            long conRegistro       // asistio no es null (se tomó asistencia)
    ) {
    }

    /** Persona mayor vinculada a la organización. */
    public record PersonaAnalitica(Integer idUsuario, String nombre) {
    }

    /** Una medición de signos vitales. */
    public record MedicionAnalitica(
            Integer idPersonaMayor,
            String fechaHora,      // YYYY-MM-DDTHH:mm
            Integer presionSistolica,
            Integer presionDiastolica,
            Integer frecuenciaCardiaca,
            Double temperatura,
            Integer saturacionOxigeno,
            Integer frecuenciaRespiratoria,
            Double peso
    ) {
    }

    /** Respuesta de /salud: las personas y todas sus mediciones. */
    public record SaludAnalitica(
            List<PersonaAnalitica> personas,
            List<MedicionAnalitica> mediciones
    ) {
    }

    /** Datos de una persona para el perfil de la población. */
    public record PersonaPoblacion(
            Integer idUsuario,
            String nombre,
            String fechaNacimiento, // YYYY-MM-DD o null
            String genero,
            String eps
    ) {
    }

    /** Cuántas personas tienen marcado un gusto. */
    public record InteresConteo(String nombre, String categoria, long personas) {
    }

    /** Respuesta de /poblacion. */
    public record PoblacionAnalitica(
            List<PersonaPoblacion> personas,
            List<InteresConteo> intereses,
            long personasConIntereses,
            long acompanantesActivos
    ) {
    }
}
