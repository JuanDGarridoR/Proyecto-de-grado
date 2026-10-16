package com.proyectogrado.analitica_service.perfiles;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee de la base compartida (solo lectura) los datos de cada persona mayor
 * de la organización y los convierte en una fila de VariablePerfil. Un
 * valor que no existe queda en NaN; SegmentacionPerfiles decide qué hacer
 * con él.
 *
 * Personas: vínculo ACEPTADO con la organización y cuenta activa. Las
 * mediciones con valores imposibles (errores de digitación) se ignoran.
 */
@Component
public class DatosPerfiles {

    /** Signos vitales de los últimos 180 días. */
    static final int DIAS_SIGNOS = 180;
    /** Citas del último año y las próximas 90 días. */
    static final int DIAS_CITAS_ATRAS = 365;
    static final int DIAS_CITAS_ADELANTE = 90;
    static final int DIAS_EMERGENCIAS = 90;

    /** Edades por fuera de este rango son errores de registro. */
    private static final int EDAD_MINIMA = 40;
    private static final int EDAD_MAXIMA = 120;

    // Límites de lo físicamente posible; iguales a los de SignoVitalValidador
    // (salud-service) y LIMITES (frontend, core/signos-vitales/rangos.ts).
    private static final double[] SISTOLICA = {60, 260};
    private static final double[] DIASTOLICA = {30, 160};
    private static final double[] PULSO = {30, 220};
    private static final double[] TEMPERATURA = {32, 43};
    private static final double[] SATURACION = {50, 100};
    private static final double[] PESO = {20, 250};
    private static final double[] ESTATURA = {100, 220};

    // Rangos de referencia para adultos (los de RANGOS en el frontend).
    private static final double[] SISTOLICA_NORMAL = {90, 139};
    private static final double[] DIASTOLICA_NORMAL = {60, 89};
    private static final double[] PULSO_NORMAL = {60, 100};
    private static final double[] TEMPERATURA_NORMAL = {36, 37.5};
    private static final double SATURACION_MINIMA = 95;

    private static final String PERSONAS = """
            SELECT pmo.id_persona_mayor
              FROM persona_mayor_organizacion pmo
              JOIN usuario u ON u.id_usuario = pmo.id_persona_mayor
             WHERE pmo.id_organizacion = :org
               AND pmo.estado = 'ACEPTADA'
               AND (u.activo IS NULL OR u.activo = TRUE)
            """;

    /**
     * Una persona mayor: sus valores (en el orden de VariablePerfil, NaN si
     * falta), si es una cuenta de los datos de prueba, sus intereses y su
     * asistencia a las actividades (para describir los grupos; no entran al modelo).
     */
    public record RegistroPersona(
            int id,
            String nombre,
            double[] valores,
            boolean dePrueba,
            List<String> intereses,
            int actividadesConRegistro,
            int asistencias
    ) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public DatosPerfiles(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RegistroPersona> leer(int idOrganizacion, LocalDate hoy) {
        MapSqlParameterSource params = new MapSqlParameterSource("org", idOrganizacion)
                .addValue("hoy", Date.valueOf(hoy))
                .addValue("desdeSignos", Timestamp.valueOf(hoy.minusDays(DIAS_SIGNOS).atStartOfDay()))
                .addValue("desdeCitas", Date.valueOf(hoy.minusDays(DIAS_CITAS_ATRAS)))
                .addValue("hastaCitas", Date.valueOf(hoy.plusDays(DIAS_CITAS_ADELANTE)))
                .addValue("desdeEmergencias", Timestamp.valueOf(hoy.minusDays(DIAS_EMERGENCIAS).atStartOfDay()));

        int columnas = VariablePerfil.values().length;
        Map<Integer, double[]> valores = new LinkedHashMap<>();
        Map<Integer, String> nombres = new HashMap<>();
        Map<Integer, Boolean> dePrueba = new HashMap<>();

        jdbc.query("""
                SELECT u.id_usuario, u.nombre_usuario, u.fecha_nacimiento, u.correo, u.celular, pm.vive_solo
                  FROM usuario u
                  LEFT JOIN persona_mayor pm ON pm.id_usuario = u.id_usuario
                 WHERE u.id_usuario IN (%s)
                 ORDER BY u.nombre_usuario
                """.formatted(PERSONAS), params, rs -> {
            int id = rs.getInt("id_usuario");
            double[] fila = new double[columnas];
            Arrays.fill(fila, Double.NaN);
            // Los conteos empiezan en 0 (no registró ninguno)
            for (VariablePerfil v : VariablePerfil.values()) {
                if (!v.puedeFaltar()) {
                    fila[v.ordinal()] = 0;
                }
            }

            Date nacimiento = rs.getDate("fecha_nacimiento");
            if (nacimiento != null) {
                int edad = Period.between(nacimiento.toLocalDate(), hoy).getYears();
                if (edad >= EDAD_MINIMA && edad <= EDAD_MAXIMA) {
                    fila[VariablePerfil.EDAD.ordinal()] = edad;
                }
            }
            Boolean viveSolo = (Boolean) rs.getObject("vive_solo");
            if (viveSolo != null) {
                // En porcentaje: el promedio del grupo queda como "% que vive sola"
                fila[VariablePerfil.VIVE_SOLO.ordinal()] = viveSolo ? 100 : 0;
            }

            valores.put(id, fila);
            nombres.put(id, rs.getString("nombre_usuario"));
            // Cuentas del script de datos ficticios (backend/scripts/seed_datos_fictisios.sql)
            String correo = rs.getString("correo");
            String celular = rs.getString("celular");
            dePrueba.put(id, (correo != null && correo.toLowerCase().endsWith("@vitaplus.test"))
                    || (celular != null && celular.startsWith("+570000")));
        });

        if (valores.isEmpty()) {
            return List.of();
        }

        // Enfermedades, discapacidades y condiciones severas (las alergias no
        // entran: no describen necesidad de cuidado ni de acompañamiento)
        jdbc.query("""
                SELECT pc.id_persona_mayor, c.tipo, pc.severidad
                  FROM persona_mayor_condicion_salud pc
                  JOIN condicion_salud c ON c.id_condicion_salud = pc.id_condicion_salud
                 WHERE pc.id_persona_mayor IN (%s)
                """.formatted(PERSONAS), params, rs -> {
            double[] fila = valores.get(rs.getInt("id_persona_mayor"));
            String tipo = rs.getString("tipo");
            if (fila == null || "ALERGIA".equals(tipo)) {
                return;
            }
            if ("ENFERMEDAD".equals(tipo)) {
                fila[VariablePerfil.ENFERMEDADES.ordinal()]++;
            } else if ("DISCAPACIDAD".equals(tipo)) {
                fila[VariablePerfil.DISCAPACIDADES.ordinal()]++;
            }
            if ("SEVERA".equals(rs.getString("severidad"))) {
                fila[VariablePerfil.CONDICIONES_SEVERAS.ordinal()]++;
            }
        });

        conteo("""
                SELECT id_persona_mayor AS id, COUNT(*) AS n
                  FROM medicamento
                 WHERE id_persona_mayor IN (%s)
                   AND (activo IS NULL OR activo = TRUE)
                   AND (fecha_fin IS NULL OR fecha_fin >= :hoy)
                 GROUP BY id_persona_mayor
                """, params, valores, VariablePerfil.MEDICAMENTOS);

        leerSignosVitales(params, valores);

        conteo("""
                SELECT pa.id_persona_mayor AS id, COUNT(*) AS n
                  FROM persona_mayor_acompanante pa
                  JOIN usuario u ON u.id_usuario = pa.id_acompanante
                 WHERE pa.id_persona_mayor IN (%s)
                   AND pa.estado = 'ACEPTADA'
                   AND (u.activo IS NULL OR u.activo = TRUE)
                 GROUP BY pa.id_persona_mayor
                """, params, valores, VariablePerfil.ACOMPANANTES);

        jdbc.query("""
                SELECT id_persona_mayor AS id, COUNT(*) AS total,
                       SUM(CASE WHEN acompanamiento IN ('REQUERIDO', 'PREFERIDO') THEN 1 ELSE 0 END) AS con
                  FROM cita_medica
                 WHERE id_persona_mayor IN (%s)
                   AND fecha BETWEEN :desdeCitas AND :hastaCitas
                 GROUP BY id_persona_mayor
                """.formatted(PERSONAS), params, rs -> {
            double[] fila = valores.get(rs.getInt("id"));
            long total = rs.getLong("total");
            if (fila != null && total > 0) {
                fila[VariablePerfil.CITAS_CON_ACOMPANAMIENTO.ordinal()] = 100.0 * rs.getLong("con") / total;
            }
        });

        conteo("""
                SELECT id_persona_mayor AS id, COUNT(*) AS n
                  FROM emergencia
                 WHERE id_persona_mayor IN (%s)
                   AND fecha_hora >= :desdeEmergencias
                 GROUP BY id_persona_mayor
                """, params, valores, VariablePerfil.EMERGENCIAS);

        // Para describir los grupos (no entran al modelo)
        Map<Integer, List<String>> intereses = new HashMap<>();
        jdbc.query("""
                SELECT pg.id_persona_mayor AS id, g.nombre
                  FROM persona_mayor_gusto pg
                  JOIN gusto g ON g.id_gusto = pg.id_gusto
                 WHERE pg.id_persona_mayor IN (%s)
                   AND COALESCE(g.categoria, '') <> 'PROFESION'
                 ORDER BY g.nombre
                """.formatted(PERSONAS), params,
                rs -> { intereses.computeIfAbsent(rs.getInt("id"), k -> new ArrayList<>()).add(rs.getString("nombre")); });

        Map<Integer, int[]> asistencia = new HashMap<>();
        jdbc.query("""
                SELECT p.id_persona_mayor AS id, COUNT(*) AS registradas,
                       SUM(CASE WHEN p.asistio = TRUE THEN 1 ELSE 0 END) AS asistio
                  FROM participacion p
                  JOIN actividad a ON a.id_actividad = p.id_actividad
                 WHERE a.id_organizacion = :org
                   AND p.asistio IS NOT NULL
                   AND p.id_persona_mayor IN (%s)
                 GROUP BY p.id_persona_mayor
                """.formatted(PERSONAS), params,
                rs -> { asistencia.put(rs.getInt("id"), new int[]{rs.getInt("registradas"), rs.getInt("asistio")}); });

        List<RegistroPersona> registros = new ArrayList<>();
        valores.forEach((id, fila) -> {
            int[] a = asistencia.getOrDefault(id, new int[]{0, 0});
            registros.add(new RegistroPersona(id, nombres.get(id), fila, dePrueba.get(id),
                    intereses.getOrDefault(id, List.of()), a[0], a[1]));
        });
        return registros;
    }

    /**
     * Promedios de presión sistólica, saturación y pulso, IMC de la última
     * medición con peso y estatura, y porcentaje de mediciones con algún
     * indicador fuera del rango de referencia.
     */
    private void leerSignosVitales(MapSqlParameterSource params, Map<Integer, double[]> valores) {
        Map<Integer, double[]> acumulado = new HashMap<>();
        // [suma sistólica, n, suma saturación, n, suma pulso, n, evaluadas, fuera de rango, imc]
        jdbc.query("""
                SELECT id_persona_mayor, presion_sistolica, presion_diastolica, frecuencia_cardiaca,
                       temperatura, saturacion_oxigeno, peso, estatura
                  FROM signo_vital
                 WHERE id_persona_mayor IN (%s)
                   AND fecha_hora >= :desdeSignos
                 ORDER BY fecha_hora
                """.formatted(PERSONAS), params, rs -> {
            double[] a = acumulado.computeIfAbsent(rs.getInt("id_persona_mayor"), k -> {
                double[] nuevo = new double[9];
                nuevo[8] = Double.NaN;
                return nuevo;
            });
            Double sistolica = valido(rs.getObject("presion_sistolica"), SISTOLICA);
            Double diastolica = valido(rs.getObject("presion_diastolica"), DIASTOLICA);
            Double pulso = valido(rs.getObject("frecuencia_cardiaca"), PULSO);
            Double temperatura = valido(rs.getObject("temperatura"), TEMPERATURA);
            Double saturacion = valido(rs.getObject("saturacion_oxigeno"), SATURACION);
            Double peso = valido(rs.getObject("peso"), PESO);
            Double estatura = valido(rs.getObject("estatura"), ESTATURA);

            if (sistolica != null) { a[0] += sistolica; a[1]++; }
            if (saturacion != null) { a[2] += saturacion; a[3]++; }
            if (pulso != null) { a[4] += pulso; a[5]++; }

            boolean evaluada = sistolica != null || diastolica != null || pulso != null
                    || temperatura != null || saturacion != null;
            if (evaluada) {
                a[6]++;
                boolean fuera = fuera(sistolica, SISTOLICA_NORMAL) || fuera(diastolica, DIASTOLICA_NORMAL)
                        || fuera(pulso, PULSO_NORMAL) || fuera(temperatura, TEMPERATURA_NORMAL)
                        || (saturacion != null && saturacion < SATURACION_MINIMA);
                if (fuera) {
                    a[7]++;
                }
            }
            if (peso != null && estatura != null) {
                double metros = estatura / 100;
                a[8] = peso / (metros * metros); // queda el de la medición más reciente
            }
        });

        acumulado.forEach((id, a) -> {
            double[] fila = valores.get(id);
            if (fila == null) {
                return;
            }
            if (a[1] > 0) fila[VariablePerfil.PRESION_SISTOLICA.ordinal()] = a[0] / a[1];
            if (a[3] > 0) fila[VariablePerfil.SATURACION_OXIGENO.ordinal()] = a[2] / a[3];
            if (a[5] > 0) fila[VariablePerfil.FRECUENCIA_CARDIACA.ordinal()] = a[4] / a[5];
            if (a[6] > 0) fila[VariablePerfil.FUERA_DE_RANGO.ordinal()] = 100 * a[7] / a[6];
            fila[VariablePerfil.IMC.ordinal()] = a[8];
        });
    }

    /** Consulta (id, n) que suma n en la columna de la variable. */
    private void conteo(String sql, MapSqlParameterSource params, Map<Integer, double[]> valores, VariablePerfil variable) {
        jdbc.query(sql.formatted(PERSONAS), params, rs -> {
            double[] fila = valores.get(rs.getInt("id"));
            if (fila != null) {
                fila[variable.ordinal()] = rs.getLong("n");
            }
        });
    }

    /** El valor si está dentro de lo posible; null si falta o es un error de digitación. */
    private static Double valido(Object valor, double[] limites) {
        if (valor == null) {
            return null;
        }
        double numero = ((Number) valor).doubleValue();
        return numero >= limites[0] && numero <= limites[1] ? numero : null;
    }

    private static boolean fuera(Double valor, double[] rango) {
        return valor != null && (valor < rango[0] || valor > rango[1]);
    }
}
