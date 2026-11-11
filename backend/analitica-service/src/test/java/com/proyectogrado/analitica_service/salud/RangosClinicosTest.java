package com.proyectogrado.analitica_service.salud;

import com.proyectogrado.analitica_service.salud.RangosClinicos.Gravedad;
import com.proyectogrado.analitica_service.salud.RangosClinicos.Signos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.proyectogrado.analitica_service.salud.RangosClinicos.Gravedad.CRITICA;
import static com.proyectogrado.analitica_service.salud.RangosClinicos.Gravedad.LEVE;
import static com.proyectogrado.analitica_service.salud.RangosClinicos.Gravedad.MODERADA;
import static com.proyectogrado.analitica_service.salud.RangosClinicos.Gravedad.NORMAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rangos clínicos de las personas mayores: cada límite se prueba justo
 * dentro y justo fuera. Deben dar lo mismo que core/signos-vitales/rangos.ts
 * en el frontend (rangos.spec.ts prueba los mismos valores).
 */
class RangosClinicosTest {

    @ParameterizedTest(name = "{0}/{1} mmHg → {2}")
    @CsvSource({
            // Sistólica: fuera si ≥ 140 o ≤ 90 (hipotensión)
            "139, 70, NORMAL",
            "140, 70, MODERADA",
            "91, 60, NORMAL",
            "90, 60, MODERADA",
            // Diastólica: fuera si ≥ 90
            "130, 89, NORMAL",
            "130, 90, MODERADA"
    })
    void presionFueraDeRango(double sistolica, double diastolica, Gravedad esperada) {
        assertEquals(esperada, RangosClinicos.presion(sistolica, diastolica));
    }

    @Test
    void presionSinMedirEsNull() {
        assertNull(RangosClinicos.presion(null, null));
    }

    @ParameterizedTest(name = "{0}/{1} mmHg → {2}")
    @CsvSource({
            "119, 79, Normal",
            "120, 79, Presión elevada",
            "129, 79, Presión elevada",
            "130, 79, Hipertensión estadio 1",
            "119, 80, Hipertensión estadio 1",
            "139, 89, Hipertensión estadio 1",
            "140, 70, Hipertensión estadio 2",
            "125, 90, Hipertensión estadio 2",
            "91, 60, Normal",
            "90, 60, Hipotensión"
    })
    void clasificacionDePresionAccAha(double sistolica, double diastolica, String esperada) {
        assertEquals(esperada, RangosClinicos.clasificarPresion(sistolica, diastolica));
    }

    @ParameterizedTest(name = "{0} lpm → {1}")
    @CsvSource({
            "40, CRITICA", "41, MODERADA",
            "59, MODERADA", "60, NORMAL",
            "100, NORMAL", "101, MODERADA",
            "130, MODERADA", "131, CRITICA"
    })
    void pulso(double valor, Gravedad esperada) {
        assertEquals(esperada, RangosClinicos.pulso(valor));
    }

    @ParameterizedTest(name = "{0} % → {1}")
    @CsvSource({
            "96, NORMAL",
            "95, LEVE", "94, LEVE",
            "93, MODERADA", "92, MODERADA",
            "91, CRITICA"
    })
    void saturacionSinEpoc(double valor, Gravedad esperada) {
        assertEquals(esperada, RangosClinicos.saturacion(valor, false));
    }

    @ParameterizedTest(name = "EPOC, {0} % → {1}")
    @CsvSource({
            "87, MODERADA", "88, NORMAL",
            "92, NORMAL", "93, MODERADA",
            "84, MODERADA", "83, CRITICA"
    })
    void saturacionConEpoc(double valor, Gravedad esperada) {
        assertEquals(esperada, RangosClinicos.saturacion(valor, true));
    }

    @Test
    void conEpocUnaSaturacionDe90EsNormalYSinEpocEsCritica() {
        assertEquals(NORMAL, RangosClinicos.saturacion(90.0, true));
        assertEquals(CRITICA, RangosClinicos.saturacion(90.0, false));
    }

    @ParameterizedTest(name = "{0} °C → {1}")
    @CsvSource({
            "35.0, CRITICA", "35.1, MODERADA",
            "36.0, MODERADA", "36.1, NORMAL",
            "37.7, NORMAL", "37.8, MODERADA",
            "39.0, MODERADA", "39.1, CRITICA"
    })
    void temperatura(double valor, Gravedad esperada) {
        assertEquals(esperada, RangosClinicos.temperatura(valor));
    }

    @ParameterizedTest(name = "{0} rpm → {1}")
    @CsvSource({
            "8, CRITICA", "9, MODERADA",
            "11, MODERADA", "12, NORMAL",
            "20, NORMAL", "21, MODERADA",
            "24, MODERADA", "25, CRITICA"
    })
    void frecuenciaRespiratoria(double valor, Gravedad esperada) {
        assertEquals(esperada, RangosClinicos.respiracion(valor));
    }

    @Test
    void laSaturacionLeveNoCuentaComoFueraDeRango() {
        assertFalse(LEVE.fueraDeRango());
        assertFalse(NORMAL.fueraDeRango());
        assertTrue(MODERADA.fueraDeRango());
        assertTrue(CRITICA.fueraDeRango());
    }

    @Test
    void unaMedicionEstaFueraDeRangoSiCualquierIndicadorLoEsta() {
        Signos normal = new Signos(118.0, 76.0, 72.0, 36.6, 97.0, 16.0);
        assertFalse(RangosClinicos.fueraDeRango(normal, false));

        // Solo la frecuencia respiratoria está fuera
        assertTrue(RangosClinicos.fueraDeRango(new Signos(118.0, 76.0, 72.0, 36.6, 97.0, 22.0), false));
        // Solo la temperatura (fiebre)
        assertTrue(RangosClinicos.fueraDeRango(new Signos(118.0, 76.0, 72.0, 37.8, 97.0, 16.0), false));
        // Saturación 90 %: fuera sin EPOC, normal con EPOC
        Signos saturacion90 = new Signos(118.0, 76.0, 72.0, 36.6, 90.0, 16.0);
        assertTrue(RangosClinicos.fueraDeRango(saturacion90, false));
        assertFalse(RangosClinicos.fueraDeRango(saturacion90, true));
        // Con EPOC, 97 % está por encima del objetivo
        assertTrue(RangosClinicos.fueraDeRango(normal, true));
    }

    @Test
    void sinValoresNoEstaFueraDeRango() {
        Signos vacia = new Signos(null, null, null, null, null, null);
        assertFalse(vacia.tieneAlguno());
        assertFalse(RangosClinicos.fueraDeRango(vacia, false));
    }

    @ParameterizedTest(name = "IMC {0} → {1}")
    @CsvSource({
            "23.0, Delgadez", "23.1, Normal",
            "27.9, Normal", "28.0, Sobrepeso",
            "31.9, Sobrepeso", "32.0, Obesidad"
    })
    void categoriaDeImcDePersonaMayor(double imc, String esperada) {
        assertEquals(esperada, RangosClinicos.categoriaImc(imc));
    }
}
