package com.proyectogrado.salud_backend.validacion;

import com.proyectogrado.salud_backend.dto.SignoVitalRequest;

/**
 * Validación de una medición de signos vitales, la misma para la que
 * registra la organización y la que registra la persona mayor.
 */
public final class SignoVitalValidador {

    // Límites de lo físicamente posible; un valor por fuera es un error de
    // digitación. Deben coincidir con LIMITES en
    // frontend/src/app/core/signos-vitales/rangos.ts.
    private static final double[] SISTOLICA = {60, 260};
    private static final double[] DIASTOLICA = {30, 160};
    private static final double[] PULSO = {30, 220};
    private static final double[] TEMPERATURA = {32, 43};
    private static final double[] OXIGENO = {50, 100};
    private static final double[] RESPIRACION = {5, 60};
    private static final double[] PESO = {20, 250};
    private static final double[] ESTATURA = {100, 220};

    private SignoVitalValidador() {
    }

    /** Mensaje de error si la medición no es válida; null si está bien. */
    public static String validar(SignoVitalRequest r) {
        Integer sis = r.getPresionSistolica();
        Integer dia = r.getPresionDiastolica();

        if (sis == null && dia == null && r.getFrecuenciaCardiaca() == null
                && r.getTemperatura() == null && r.getSaturacionOxigeno() == null
                && r.getFrecuenciaRespiratoria() == null && r.getPeso() == null
                && r.getEstatura() == null) {
            return "Ingresa al menos un signo vital.";
        }

        if ((sis == null) != (dia == null)) {
            return "La presión arterial se registra completa: sistólica y diastólica.";
        }

        String error = fueraDeLimite("Presión sistólica", sis, SISTOLICA, "mmHg");
        if (error == null) error = fueraDeLimite("Presión diastólica", dia, DIASTOLICA, "mmHg");
        if (error == null) error = fueraDeLimite("Frecuencia cardíaca", r.getFrecuenciaCardiaca(), PULSO, "lpm");
        if (error == null) error = fueraDeLimite("Temperatura", r.getTemperatura(), TEMPERATURA, "°C");
        if (error == null) error = fueraDeLimite("Saturación de oxígeno", r.getSaturacionOxigeno(), OXIGENO, "%");
        if (error == null) error = fueraDeLimite("Frecuencia respiratoria", r.getFrecuenciaRespiratoria(), RESPIRACION, "rpm");
        if (error == null) error = fueraDeLimite("Peso", r.getPeso(), PESO, "kg");
        if (error == null) error = fueraDeLimite("Estatura", r.getEstatura(), ESTATURA, "cm");
        if (error != null) {
            return error;
        }

        // Arriba ya se exigió la presión completa; dia != null lo deja explícito
        if (sis != null && dia != null && sis <= dia) {
            return "La presión sistólica debe ser mayor que la diastólica.";
        }

        return null;
    }

    private static String fueraDeLimite(String nombre, Number valor, double[] limite, String unidad) {
        if (valor == null) {
            return null;
        }
        double v = valor.doubleValue();
        if (v < limite[0] || v > limite[1]) {
            return String.format("%s: %s %s no es un valor posible (debe estar entre %s y %s).",
                    nombre, valor, unidad, formatear(limite[0]), formatear(limite[1]));
        }
        return null;
    }

    private static String formatear(double valor) {
        return valor == Math.floor(valor) ? String.valueOf((long) valor) : String.valueOf(valor);
    }
}
