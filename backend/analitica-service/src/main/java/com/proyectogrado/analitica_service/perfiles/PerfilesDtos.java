package com.proyectogrado.analitica_service.perfiles;

import java.util.List;

/**
 * Respuesta de /api/analitica/perfiles: la evaluación de los datos, el
 * modelo K-means y los perfiles (grupos) que encontró, con su
 * interpretación. Solo informa (RNF-13): orienta a la organización, no
 * clasifica ni diagnostica a nadie.
 */
public final class PerfilesDtos {

    private PerfilesDtos() {
    }

    /** LISTO: se entrenó el modelo. INSUFICIENTE: no hay datos suficientes (ver calidad y advertencias). */
    public enum EstadoSegmentacion { LISTO, INSUFICIENTE }

    /** BUENA, ACEPTABLE, LIMITADA o INSUFICIENTE (ver SegmentacionPerfiles.nivelCalidad). */
    public enum NivelCalidad { BUENA, ACEPTABLE, LIMITADA, INSUFICIENTE }

    /** Cuántos registros hay y qué tan completos son, antes de entrenar. */
    public record CalidadDatos(
            int personasAsociadas,
            int personasIncluidas,
            int personasExcluidas,
            int minimoPersonas,
            /** % de los valores del modelo que se completaron con la mediana. */
            double porcentajeImputado,
            /** Personas de los datos ficticios (@vitaplus.test). */
            int registrosDePrueba,
            NivelCalidad nivel
    ) {
    }

    /**
     * Una variable: de qué dimensión es, con cuántas personas tiene dato
     * (completitud, %), su promedio y desviación entre las incluidas, y si
     * entró al modelo (si no, por qué).
     */
    public record VariableModelo(
            String clave,
            String nombre,
            String dimension,
            String unidad,
            double completitud,
            Double media,
            Double desviacion,
            boolean usada,
            String motivoDescarte
    ) {
    }

    /** Inercia y silueta del modelo con k grupos (para elegir k). */
    public record EvaluacionK(int k, double inercia, double silueta) {
    }

    public record Modelo(
            int k,
            int kSugerido,
            boolean kElegidoPorUsuario,
            int kMaximo,
            double silueta,
            /** Cómo leer la silueta: FUERTE, RAZONABLE, DEBIL o SIN_ESTRUCTURA. */
            String estructura,
            double inercia,
            int iteraciones,
            int reinicios,
            long semilla,
            /** Varianza que explica cada eje del mapa (PCA), 0 a 1. */
            double[] varianzaExplicada,
            List<EvaluacionK> evaluaciones
    ) {
    }

    /** Valor promedio del grupo en una variable: en sus unidades y en desviaciones (z) respecto a la organización. */
    public record ValorGrupo(String clave, double valor, double z) {
    }

    /** Puntaje de necesidad del grupo en una dimensión (promedio de z; positivo = más necesidad). */
    public record PuntajeDimension(String dimension, double puntaje) {
    }

    public record GrupoPerfil(
            int numero,
            String nombre,
            String descripcion,
            int personas,
            double porcentaje,
            List<String> rasgos,
            List<ValorGrupo> centroide,
            List<PuntajeDimension> dimensiones,
            List<String> recomendacionesAtencion,
            List<String> recomendacionesActividades,
            List<String> interesesFrecuentes,
            /** % de asistencia a actividades con asistencia registrada; null si nadie tiene registros. */
            Double asistencia
    ) {
    }

    /** Persona incluida: su grupo y su punto en el mapa (PCA). */
    public record PersonaPerfil(int id, String nombre, int grupo, double x, double y) {
    }

    /** Persona que no entró por falta de datos, con lo que le falta registrar. */
    public record PersonaExcluida(int id, String nombre, List<String> faltantes) {
    }

    public record PerfilesResponse(
            EstadoSegmentacion estado,
            String generado,
            CalidadDatos calidad,
            List<VariableModelo> variables,
            Modelo modelo,
            List<GrupoPerfil> grupos,
            List<PersonaPerfil> personas,
            List<PersonaExcluida> excluidas,
            List<String> advertencias
    ) {
    }
}
