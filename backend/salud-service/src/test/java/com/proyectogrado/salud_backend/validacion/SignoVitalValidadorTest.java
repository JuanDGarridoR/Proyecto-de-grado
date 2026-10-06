package com.proyectogrado.salud_backend.validacion;

import com.proyectogrado.salud_backend.dto.SignoVitalRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Validación de una medición de signos vitales con análisis de valores
 * límite: los extremos de cada rango se aceptan y el valor siguiente por
 * fuera se rechaza como error de digitación.
 */
class SignoVitalValidadorTest {

    private SignoVitalRequest presion(Integer sistolica, Integer diastolica) {
        SignoVitalRequest medicion = new SignoVitalRequest();
        medicion.setPresionSistolica(sistolica);
        medicion.setPresionDiastolica(diastolica);
        return medicion;
    }

    @Test
    void unaMedicionNormalEsValida() {
        SignoVitalRequest medicion = presion(120, 80);
        medicion.setFrecuenciaCardiaca(72);
        medicion.setTemperatura(36.6);
        medicion.setSaturacionOxigeno(97);
        medicion.setPeso(64.5);
        medicion.setEstatura(158.0);

        assertNull(SignoVitalValidador.validar(medicion));
    }

    @Test
    void sinNingunValorNoHayMedicion() {
        assertEquals("Ingresa al menos un signo vital.", SignoVitalValidador.validar(new SignoVitalRequest()));
    }

    @Test
    void laPresionSeRegistraCompleta() {
        assertEquals("La presión arterial se registra completa: sistólica y diastólica.",
                SignoVitalValidador.validar(presion(120, null)));
    }

    @Test
    void laSistolicaDebeSerMayorQueLaDiastolica() {
        assertEquals("La presión sistólica debe ser mayor que la diastólica.",
                SignoVitalValidador.validar(presion(80, 80)));
    }

    @ParameterizedTest(name = "sistólica {0}: válida = {1}")
    @CsvSource({"59, false", "60, true", "260, true", "261, false"})
    void limitesDeLaPresionSistolica(int sistolica, boolean valida) {
        assertEquals(valida, SignoVitalValidador.validar(presion(sistolica, 40)) == null);
    }

    @ParameterizedTest(name = "temperatura {0}: válida = {1}")
    @CsvSource({"31.9, false", "32.0, true", "43.0, true", "43.1, false"})
    void limitesDeLaTemperatura(double temperatura, boolean valida) {
        SignoVitalRequest medicion = new SignoVitalRequest();
        medicion.setTemperatura(temperatura);

        assertEquals(valida, SignoVitalValidador.validar(medicion) == null);
    }

    @ParameterizedTest(name = "saturación {0}: válida = {1}")
    @CsvSource({"49, false", "50, true", "100, true", "101, false"})
    void limitesDeLaSaturacionDeOxigeno(int saturacion, boolean valida) {
        SignoVitalRequest medicion = new SignoVitalRequest();
        medicion.setSaturacionOxigeno(saturacion);

        assertEquals(valida, SignoVitalValidador.validar(medicion) == null);
    }

    @ParameterizedTest(name = "estatura {0}: válida = {1}")
    @CsvSource({"99, false", "100, true", "220, true", "221, false"})
    void limitesDeLaEstatura(double estatura, boolean valida) {
        SignoVitalRequest medicion = new SignoVitalRequest();
        medicion.setEstatura(estatura);

        assertEquals(valida, SignoVitalValidador.validar(medicion) == null);
    }

    @Test
    void elMensajeExplicaElRangoPosible() {
        SignoVitalRequest medicion = new SignoVitalRequest();
        medicion.setFrecuenciaCardiaca(250);

        String error = SignoVitalValidador.validar(medicion);

        assertNotNull(error);
        assertEquals("Frecuencia cardíaca: 250 lpm no es un valor posible (debe estar entre 30 y 220).", error);
    }
}
