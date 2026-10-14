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
            Double peso,
            Double estatura        // cm; con el peso da el IMC
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
            String eps,
            Boolean viveSolo       // null si no lo ha indicado
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
            long acompanantesActivos,
            /** Profesiones u oficios (categoría PROFESION), aparte de los intereses. */
            List<InteresConteo> profesiones,
            long personasConProfesion
    ) {
    }

    /**
     * Una persona mayor que eliminó su cuenta (tabla retiro_cuenta de
     * auth-service): su nombre, la razón (código de RetiroCuenta.RAZONES),
     * el comentario opcional y cuándo.
     */
    public record RetiroAnalitica(
            String nombre,
            String razon,
            String comentario,
            String fecha           // YYYY-MM-DDTHH:mm
    ) {
    }

    /**
     * Respuesta de /retiros: todos los retiros de la organización (el
     * frontend los filtra por período) y cuántas personas tiene hoy, para
     * calcular la tasa de retiro.
     */
    public record RetirosAnalitica(
            List<RetiroAnalitica> retiros,
            long personasActuales
    ) {
    }
}
