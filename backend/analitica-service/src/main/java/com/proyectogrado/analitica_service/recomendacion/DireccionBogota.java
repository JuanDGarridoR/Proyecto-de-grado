package com.proyectogrado.analitica_service.recomendacion;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ubica una dirección de Bogotá en la cuadrícula de la ciudad a partir de
 * su nomenclatura, sin servicios externos.
 *
 * En Bogotá las calles van de oriente a occidente y se numeran hacia el
 * norte (o hacia el sur si dicen "Sur"); las carreras van de norte a sur y
 * se numeran hacia el occidente (o hacia el oriente si dicen "Este").
 * "Calle 76 Sur # 4-20" está sobre la Calle 76 Sur, a la altura de la
 * Carrera 4, a 20 m de la esquina. Así cada dirección es un punto
 * (carrera, calle) y la distancia entre dos puntos se estima en km,
 * tomando cada número como una cuadra de unos 100 m.
 *
 * Es una aproximación: en zonas donde las vías no son rectas (como parte
 * de Usme) el error puede ser de algunos cientos de metros.
 */
public final class DireccionBogota {

    /** Metros aproximados entre dos números consecutivos de calle o carrera. */
    private static final double KM_POR_CUADRA = 0.1;

    /** Punto en la cuadrícula: x = carrera (oeste +), y = calle (norte +). */
    public record Punto(double x, double y) {
    }

    // Tipo de vía (con abreviaturas comunes); "av" opcional al inicio
    private static final String VIA = "(?:av(?:enida)?\\s+)?"
            + "(calle|cll|cl|clle|ac|avenida calle|carrera|cra|kra|kr|cr|crr|ak|avenida carrera"
            + "|diagonal|diag|dg|transversal|transv|trans|tv|tr)";

    // Número con letra y "bis" opcionales: 76, 76a, 76 a bis, 76 bis b
    private static final String NUMERO = "(\\d{1,3})\\s*([a-h])?\\s*(bis)?\\s*([a-h])?";

    // Vías que se numeran como calles (las diagonales van como calles; las
    // transversales, como carreras)
    private static final Set<String> VIAS_TIPO_CALLE = Set.of(
            "calle", "cll", "cl", "clle", "ac", "avenida calle", "diagonal", "diag", "dg");

    private static final Pattern PATRON = Pattern.compile(
            "\\b" + VIA +"\\s*" + NUMERO + "(?:\\s*(?:sur|este))?\\s*(?:#|no|n|numero)?\\s*" + NUMERO
                    + "\\s*-\\s*(\\d{1,3})");

    private DireccionBogota() {
    }

    /** El punto de la dirección, o null si no se pudo interpretar. */
    public static Punto ubicar(String direccion) {
        if (direccion == null || direccion.isBlank()) {
            return null;
        }
        String texto = Barrios.normalizar(direccion)
                .replace("n°", "#").replace("nº", "#").replace("no.", "#")
                .replaceAll("[.,]", " ")
                .replaceAll("\\s+", " ");

        Matcher m = PATRON.matcher(texto);
        if (!m.find()) {
            return null;
        }

        String via = m.group(1);
        double principal = numero(m.group(2), m.group(3), m.group(4), m.group(5));
        double cruce = numero(m.group(6), m.group(7), m.group(8), m.group(9));
        double placa = Integer.parseInt(m.group(10)) / 100.0; // metros desde la esquina

        boolean esCalle = VIAS_TIPO_CALLE.contains(via);

        // Sur y Este pueden aparecer en cualquier parte de la dirección
        boolean sur = texto.matches(".*\\bsur\\b.*");
        boolean este = texto.matches(".*\\beste\\b.*");

        double calle;
        double carrera;
        if (esCalle) {
            calle = principal;
            carrera = cruce + placa;
        } else {
            carrera = principal;
            calle = cruce + placa;
        }

        return new Punto(este ? -carrera : carrera, sur ? -calle : calle);
    }

    /** Distancia aproximada en km entre dos puntos de la cuadrícula. */
    public static double distanciaKm(Punto a, Punto b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        return Math.sqrt(dx * dx + dy * dy) * KM_POR_CUADRA;
    }

    /** 76 -> 76; 76A -> 76,3; 76 Bis -> 76,15; 76A Bis B -> 76,6 (aprox.). */
    private static double numero(String numero, String letra1, String bis, String letra2) {
        double valor = Integer.parseInt(numero);
        if (letra1 != null) valor += 0.3;
        if (bis != null) valor += 0.15;
        if (letra2 != null) valor += 0.15;
        return valor;
    }
}
