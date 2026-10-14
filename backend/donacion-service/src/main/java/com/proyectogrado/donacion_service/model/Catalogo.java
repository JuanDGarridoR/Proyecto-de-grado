package com.proyectogrado.donacion_service.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * Valores fijos de donaciones e inventario. El frontend muestra los mismos
 * (ver frontend/src/app/core/donaciones/catalogo.ts).
 */
public final class Catalogo {

    /** Categorías de lo que se dona o se guarda en el inventario. */
    public static final List<String> CATEGORIAS =
            List.of("ALIMENTOS", "DINERO", "ROPA", "MEDICAMENTOS", "ASEO", "OTRO");

    /** El dinero siempre se cuenta en pesos colombianos. */
    public static final String DINERO = "DINERO";
    public static final String UNIDAD_DINERO = "COP";

    public static final String ENTRADA = "ENTRADA";
    public static final String SALIDA = "SALIDA";

    /**
     * Motivo que solo pone el sistema al recibir una donación de la
     * aplicación; la organización no lo puede elegir.
     */
    public static final String MOTIVO_DONACION = "DONACION";

    /** Motivos que la organización puede elegir para una entrada manual. */
    public static final Set<String> MOTIVOS_ENTRADA =
            Set.of("COMPRA", "DONACION_EXTERNA", "AJUSTE", "OTRO");

    /** Motivos de salida: entregado a personas mayores, usado, vencido... */
    public static final Set<String> MOTIVOS_SALIDA =
            Set.of("ENTREGA", "USO", "VENCIDO", "DANADO", "AJUSTE", "OTRO");

    /** Las fechas se calculan en hora de Colombia, no en la del servidor. */
    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private Catalogo() {
    }

    public static LocalDate hoy() {
        return LocalDate.now(ZONA);
    }

    public static LocalDateTime ahora() {
        return LocalDateTime.now(ZONA);
    }
}
