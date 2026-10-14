package com.proyectogrado.donacion_service.service;

import java.math.BigDecimal;

/**
 * Revisiones de texto y cantidades que comparten los controladores. Cada
 * método devuelve el mensaje de error para el usuario, o null si está bien.
 */
public final class Validaciones {

    /** Las columnas de cantidad son numeric(12, 2): hasta 10 dígitos enteros. */
    private static final BigDecimal CANTIDAD_MAXIMA = new BigDecimal("9999999999.99");

    private Validaciones() {
    }

    /** Texto sin espacios sobrantes, o null si viene vacío. */
    public static String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /**
     * Cantidad mayor que cero (o mayor o igual, si permiteCero), con máximo
     * dos decimales.
     */
    public static String revisarCantidad(BigDecimal cantidad, boolean permiteCero, String que) {
        if (cantidad == null) {
            return "Indica la cantidad de " + que;
        }

        int comparacion = cantidad.signum();
        if (comparacion < 0 || (comparacion == 0 && !permiteCero)) {
            return permiteCero
                    ? "La cantidad de " + que + " no puede ser negativa"
                    : "La cantidad de " + que + " debe ser mayor que cero";
        }

        if (cantidad.stripTrailingZeros().scale() > 2) {
            return "La cantidad de " + que + " puede tener máximo dos decimales";
        }

        if (cantidad.compareTo(CANTIDAD_MAXIMA) > 0) {
            return "La cantidad de " + que + " es demasiado grande";
        }

        return null;
    }

    /** Texto obligatorio con un largo máximo. */
    public static String revisarTexto(String valor, int maximo, String campo) {
        if (valor == null) {
            return campo + " es obligatorio";
        }

        if (valor.length() > maximo) {
            return campo + " puede tener máximo " + maximo + " caracteres";
        }

        return null;
    }

    /** "10" en lugar de "10.00", para los mensajes. */
    public static String formatear(BigDecimal cantidad) {
        return cantidad.stripTrailingZeros().toPlainString();
    }
}
