package com.proyectogrado.analitica_service.salud;

/**
 * Rangos clínicos de referencia para personas mayores. Es el único lugar
 * del backend donde están; el frontend tiene los mismos en
 * core/signos-vitales/rangos.ts y deben coincidir.
 *
 * Son orientativos y no reemplazan el criterio médico. No son los límites
 * de valores imposibles del RF-29 (SignoVitalValidador en salud-service):
 * esos dicen si un valor puede ser real; estos, si un valor real es
 * normal.
 *
 * Fuentes:
 *  - Presión: ACC/AHA 2017 (Whelton et al., Hypertension 2018;71:e13-e115)
 *    para la clasificación; la hipotensión (sistólica ≤ 90) es la de NEWS2.
 *  - Pulso, frecuencia respiratoria y valores críticos: NEWS2 (Royal
 *    College of Physicians, 2017).
 *  - Saturación: BTS 2017 (O'Driscoll et al., Thorax 2017;72:i1-i90),
 *    objetivo 94–98 % y 88–92 % con EPOC; gravedad según NEWS2 (escala 1
 *    y, con EPOC, escala 2).
 *  - Temperatura: fiebre ≥ 37,8 °C en personas mayores (IDSA 2008, High et
 *    al., Clin Infect Dis 2009;48:149-171); extremos según NEWS2.
 *  - IMC: puntos de corte para personas mayores de Minsalud (2024) y la
 *    OPS (2002); reemplazan la escala de la OMS para adultos.
 */
public final class RangosClinicos {

    private RangosClinicos() {
    }

    /** Qué tan lejos de lo normal está un valor. */
    public enum Gravedad {
        /** Dentro del rango. */
        NORMAL,
        /** Dentro del rango pero en el borde (solo saturación 94–95 %). */
        LEVE,
        /** Fuera de rango. */
        MODERADA,
        /** Fuera de rango y en un valor que pide atención pronto. */
        CRITICA;

        public boolean fueraDeRango() {
            return this == MODERADA || this == CRITICA;
        }
    }

    // Presión arterial (mmHg)
    public static final int SISTOLICA_HIPOTENSION = 90;   // ≤ 90: fuera (NEWS2)
    public static final int SISTOLICA_ELEVADA = 120;      // 120–129 con diastólica < 80
    public static final int SISTOLICA_ESTADIO_1 = 130;    // 130–139
    public static final int SISTOLICA_ESTADIO_2 = 140;    // ≥ 140: fuera
    public static final int DIASTOLICA_ESTADIO_1 = 80;    // 80–89
    public static final int DIASTOLICA_ESTADIO_2 = 90;    // ≥ 90: fuera

    // Pulso (lpm): normal 60–100
    public static final int PULSO_MINIMO = 60;
    public static final int PULSO_MAXIMO = 100;
    public static final int PULSO_CRITICO_BAJO = 40;      // ≤ 40
    public static final int PULSO_CRITICO_ALTO = 131;     // ≥ 131

    // Saturación de oxígeno (%)
    public static final int SATURACION_MINIMA = 94;       // < 94: fuera
    public static final int SATURACION_LEVE = 95;         // 94–95: leve
    public static final int SATURACION_CRITICA = 91;      // ≤ 91: crítica
    public static final int SATURACION_EPOC_MINIMA = 88;  // con EPOC: 88–92
    public static final int SATURACION_EPOC_MAXIMA = 92;
    public static final int SATURACION_EPOC_CRITICA = 83; // ≤ 83: crítica (NEWS2, escala 2)

    // Temperatura (°C)
    public static final double TEMPERATURA_BAJA = 36.0;          // ≤ 36,0: fuera
    public static final double TEMPERATURA_FIEBRE = 37.8;        // ≥ 37,8: fuera
    public static final double TEMPERATURA_CRITICA_BAJA = 35.0;  // ≤ 35,0
    public static final double TEMPERATURA_CRITICA_ALTA = 39.1;  // ≥ 39,1

    // Frecuencia respiratoria (rpm): normal 12–20
    public static final int RESPIRACION_MINIMA = 12;
    public static final int RESPIRACION_MAXIMA = 20;
    public static final int RESPIRACION_CRITICA_BAJA = 8;   // ≤ 8
    public static final int RESPIRACION_CRITICA_ALTA = 25;  // ≥ 25

    // IMC (kg/m²) de persona mayor
    public static final double IMC_DELGADEZ = 23.0;   // ≤ 23,0
    public static final double IMC_SOBREPESO = 28.0;  // ≥ 28 y < 32
    public static final double IMC_OBESIDAD = 32.0;   // ≥ 32

    /** Gravedad de la presión; null si no se midió. */
    public static Gravedad presion(Double sistolica, Double diastolica) {
        if (sistolica == null && diastolica == null) {
            return null;
        }
        boolean fuera = (sistolica != null && (sistolica >= SISTOLICA_ESTADIO_2 || sistolica <= SISTOLICA_HIPOTENSION))
                || (diastolica != null && diastolica >= DIASTOLICA_ESTADIO_2);
        return fuera ? Gravedad.MODERADA : Gravedad.NORMAL;
    }

    /** Clasificación informativa ACC/AHA 2017 (más la hipotensión de NEWS2). */
    public static String clasificarPresion(double sistolica, double diastolica) {
        if (sistolica <= SISTOLICA_HIPOTENSION) return "Hipotensión";
        if (sistolica >= SISTOLICA_ESTADIO_2 || diastolica >= DIASTOLICA_ESTADIO_2) return "Hipertensión estadio 2";
        if (sistolica >= SISTOLICA_ESTADIO_1 || diastolica >= DIASTOLICA_ESTADIO_1) return "Hipertensión estadio 1";
        if (sistolica >= SISTOLICA_ELEVADA) return "Presión elevada";
        return "Normal";
    }

    public static Gravedad pulso(Double valor) {
        if (valor == null) return null;
        if (valor <= PULSO_CRITICO_BAJO || valor >= PULSO_CRITICO_ALTO) return Gravedad.CRITICA;
        if (valor < PULSO_MINIMO || valor > PULSO_MAXIMO) return Gravedad.MODERADA;
        return Gravedad.NORMAL;
    }

    /** Con EPOC el rango objetivo es 88–92 % (BTS 2017). */
    public static Gravedad saturacion(Double valor, boolean epoc) {
        if (valor == null) return null;
        if (epoc) {
            if (valor <= SATURACION_EPOC_CRITICA) return Gravedad.CRITICA;
            if (valor < SATURACION_EPOC_MINIMA || valor > SATURACION_EPOC_MAXIMA) return Gravedad.MODERADA;
            return Gravedad.NORMAL;
        }
        if (valor <= SATURACION_CRITICA) return Gravedad.CRITICA;
        if (valor < SATURACION_MINIMA) return Gravedad.MODERADA;
        if (valor <= SATURACION_LEVE) return Gravedad.LEVE;
        return Gravedad.NORMAL;
    }

    public static Gravedad temperatura(Double valor) {
        if (valor == null) return null;
        if (valor <= TEMPERATURA_CRITICA_BAJA || valor >= TEMPERATURA_CRITICA_ALTA) return Gravedad.CRITICA;
        if (valor <= TEMPERATURA_BAJA || valor >= TEMPERATURA_FIEBRE) return Gravedad.MODERADA;
        return Gravedad.NORMAL;
    }

    public static Gravedad respiracion(Double valor) {
        if (valor == null) return null;
        if (valor <= RESPIRACION_CRITICA_BAJA || valor >= RESPIRACION_CRITICA_ALTA) return Gravedad.CRITICA;
        if (valor < RESPIRACION_MINIMA || valor > RESPIRACION_MAXIMA) return Gravedad.MODERADA;
        return Gravedad.NORMAL;
    }

    /** Una medición; los valores que no se midieron van en null. */
    public record Signos(Double sistolica, Double diastolica, Double pulso, Double temperatura,
                         Double saturacion, Double respiracion) {

        public boolean tieneAlguno() {
            return sistolica != null || diastolica != null || pulso != null
                    || temperatura != null || saturacion != null || respiracion != null;
        }
    }

    /** true si algún indicador medido está fuera de rango. */
    public static boolean fueraDeRango(Signos s, boolean epoc) {
        return fuera(presion(s.sistolica(), s.diastolica()))
                || fuera(pulso(s.pulso()))
                || fuera(temperatura(s.temperatura()))
                || fuera(saturacion(s.saturacion(), epoc))
                || fuera(respiracion(s.respiracion()));
    }

    /** Categoría del IMC de una persona mayor. */
    public static String categoriaImc(double imc) {
        if (imc <= IMC_DELGADEZ) return "Delgadez";
        if (imc < IMC_SOBREPESO) return "Normal";
        if (imc < IMC_OBESIDAD) return "Sobrepeso";
        return "Obesidad";
    }

    private static boolean fuera(Gravedad g) {
        return g != null && g.fueraDeRango();
    }
}
