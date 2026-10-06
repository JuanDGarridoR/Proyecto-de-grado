package com.proyectogrado.analitica_service.recomendacion;

import com.proyectogrado.analitica_service.recomendacion.Barrios.Barrio;
import com.proyectogrado.analitica_service.recomendacion.RecomendacionDtos.OrganizacionRecomendada;
import com.proyectogrado.analitica_service.recomendacion.RecomendacionDtos.RecomendacionesResponse;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Recomienda a una persona mayor organizaciones a las que todavía no
 * pertenece, según tres criterios (cada uno de 0 a 1):
 *
 *  - Actividades (40 %): cuántos de sus gustos tienen actividades en la
 *    organización (últimos 6 meses y próximas).
 *  - Comunidad (25 %): qué tanto se parecen sus gustos a los de las
 *    personas que ya están en la organización (similitud de Jaccard).
 *  - Cercanía (35 %): distancia entre las direcciones (ver DireccionBogota;
 *    si una no se puede interpretar, se usa la de su barrio); 1 a 1 km o
 *    menos y 0 a 8 km o más.
 *
 * Si un criterio no se puede calcular (sin gustos o sin barrio), su peso
 * se reparte entre los demás. Cada recomendación explica sus razones.
 */
@Service
public class RecomendadorOrganizaciones {

    private static final double PESO_ACTIVIDADES = 0.40;
    private static final double PESO_COMUNIDAD = 0.25;
    private static final double PESO_CERCANIA = 0.35;

    private static final double KM_CERCA = 1;
    private static final double KM_LEJOS = 8;

    private static final int MAX_RECOMENDACIONES = 5;

    private final NamedParameterJdbcTemplate jdbc;

    public RecomendadorOrganizaciones(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record Organizacion(Integer id, String nombre, String direccion, String celular, String correo) {
    }

    private record Actividad(Integer idOrganizacion, String nombre, String texto) {
    }

    /** null si el usuario no es una persona mayor. */
    public RecomendacionesResponse recomendar(Integer idPersonaMayor) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", idPersonaMayor);

        // ---------- Persona: dirección y gustos ----------

        List<String> direcciones = jdbc.query("""
                SELECT COALESCE(NULLIF(TRIM(pm.direccion), ''), u.direccion) AS direccion
                  FROM persona_mayor pm
                  JOIN usuario u ON u.id_usuario = pm.id_usuario
                 WHERE pm.id_usuario = :id
                """, params, (rs, i) -> rs.getString("direccion"));
        if (direcciones.isEmpty()) {
            return null;
        }
        Ubicacion ubicacionPersona = Ubicacion.de(direcciones.get(0));

        Map<Integer, String> gustosPersona = new LinkedHashMap<>();
        jdbc.query("""
                SELECT g.id_gusto, g.nombre
                  FROM persona_mayor_gusto pg
                  JOIN gusto g ON g.id_gusto = pg.id_gusto
                 WHERE pg.id_persona_mayor = :id
                 ORDER BY g.nombre
                """, params, (rs) -> {
            gustosPersona.put(rs.getInt("id_gusto"), rs.getString("nombre"));
        });

        // ---------- Organizaciones a las que NO pertenece (ni tiene solicitud) ----------

        List<Organizacion> organizaciones = jdbc.query("""
                SELECT o.id_organizacion, o.nombre, o.direccion, cuenta.celular, cuenta.correo
                  FROM organizacion o
                  LEFT JOIN LATERAL (
                        SELECT u.celular, u.correo
                          FROM usuario u
                         WHERE u.id_organizacion = o.id_organizacion
                         ORDER BY u.id_usuario
                         LIMIT 1
                  ) cuenta ON TRUE
                 WHERE o.id_organizacion NOT IN (
                        SELECT po.id_organizacion
                          FROM persona_mayor_organizacion po
                         WHERE po.id_persona_mayor = :id
                           AND po.estado IN ('ACEPTADA', 'PENDIENTE'))
                 ORDER BY o.nombre
                """, params, (rs, i) -> new Organizacion(
                rs.getInt("id_organizacion"), rs.getString("nombre"), rs.getString("direccion"),
                rs.getString("celular"), rs.getString("correo")));

        if (organizaciones.isEmpty()) {
            return new RecomendacionesResponse(!gustosPersona.isEmpty(), ubicacionPersona.reconocida(), List.of());
        }

        // ---------- Actividades recientes y próximas de cada organización ----------

        Map<Integer, List<Actividad>> actividades = new HashMap<>();
        jdbc.query("""
                SELECT a.id_organizacion, a.nombre,
                       CONCAT_WS(' ', a.nombre, a.tipo, a.descripcion) AS texto
                  FROM actividad a
                 WHERE (a.estado IS NULL OR a.estado = 'ACEPTADA')
                   AND (a.fecha IS NULL OR a.fecha >= CURRENT_DATE - 180)
                 ORDER BY a.fecha DESC NULLS LAST
                """, (rs) -> {
            Actividad a = new Actividad(rs.getInt("id_organizacion"), rs.getString("nombre"), rs.getString("texto"));
            actividades.computeIfAbsent(a.idOrganizacion(), k -> new ArrayList<>()).add(a);
        });

        // ---------- Gustos de los miembros de cada organización ----------

        Map<Integer, Map<Integer, Set<Integer>>> gustosMiembros = new HashMap<>(); // org -> persona -> gustos
        Map<Integer, Integer> totalMiembros = new HashMap<>();
        jdbc.query("""
                SELECT po.id_organizacion, po.id_persona_mayor, pg.id_gusto
                  FROM persona_mayor_organizacion po
                  LEFT JOIN persona_mayor_gusto pg ON pg.id_persona_mayor = po.id_persona_mayor
                 WHERE po.estado = 'ACEPTADA'
                """, (rs) -> {
            int org = rs.getInt("id_organizacion");
            int persona = rs.getInt("id_persona_mayor");
            Map<Integer, Set<Integer>> miembros = gustosMiembros.computeIfAbsent(org, k -> new HashMap<>());
            Set<Integer> gustos = miembros.computeIfAbsent(persona, k -> new HashSet<>());
            int idGusto = rs.getInt("id_gusto");
            if (!rs.wasNull()) {
                gustos.add(idGusto);
            }
        });
        gustosMiembros.forEach((org, miembros) -> totalMiembros.put(org, miembros.size()));

        // ---------- Puntaje de cada organización ----------

        List<OrganizacionRecomendada> resultado = new ArrayList<>();
        for (Organizacion org : organizaciones) {
            resultado.add(evaluar(org, ubicacionPersona, gustosPersona,
                    actividades.getOrDefault(org.id(), List.of()),
                    gustosMiembros.getOrDefault(org.id(), Map.of())));
        }

        resultado.sort(Comparator.comparingInt(OrganizacionRecomendada::puntaje).reversed()
                .thenComparing(r -> r.distanciaKm() == null ? Double.MAX_VALUE : r.distanciaKm())
                .thenComparing(OrganizacionRecomendada::nombre));

        return new RecomendacionesResponse(
                !gustosPersona.isEmpty(),
                ubicacionPersona.reconocida(),
                resultado.subList(0, Math.min(MAX_RECOMENDACIONES, resultado.size())));
    }

    private OrganizacionRecomendada evaluar(
            Organizacion org,
            Ubicacion ubicacionPersona,
            Map<Integer, String> gustosPersona,
            List<Actividad> actividades,
            Map<Integer, Set<Integer>> miembros
    ) {
        List<String> razones = new ArrayList<>();
        double suma = 0;
        double pesos = 0;

        // ---- Cercanía ----
        Ubicacion ubicacionOrg = Ubicacion.de(org.direccion());
        Barrio barrioOrg = ubicacionOrg.barrio();
        Double km = ubicacionPersona.distanciaKm(ubicacionOrg);
        Double distancia = km != null ? Math.round(km * 10) / 10.0 : null;
        if (distancia != null) {
            double cercania = Math.max(0, Math.min(1, (KM_LEJOS - distancia) / (KM_LEJOS - KM_CERCA)));
            suma += PESO_CERCANIA * cercania;
            pesos += PESO_CERCANIA;

            String donde = barrioOrg != null ? ", en " + barrioOrg.nombre() : "";
            if (distancia < 0.5) {
                razones.add("Queda muy cerca de tu casa (a menos de 500 m" + donde + ")");
            } else {
                razones.add(String.format(new Locale("es", "CO"), "Queda a unos %.1f km de tu casa%s", distancia, donde));
            }
        }

        // ---- Actividades que coinciden con sus gustos ----
        Set<String> gustosConActividad = new LinkedHashSet<>();
        Set<String> actividadesCoincidentes = new LinkedHashSet<>();
        if (!gustosPersona.isEmpty()) {
            for (String gusto : gustosPersona.values()) {
                for (Actividad actividad : actividades) {
                    if (CoincidenciaGustos.coincide(gusto, actividad.texto())) {
                        gustosConActividad.add(gusto);
                        actividadesCoincidentes.add(actividad.nombre());
                    }
                }
            }
            double porActividades = Math.min(1.0, gustosConActividad.size() / (double) Math.min(3, gustosPersona.size()));
            suma += PESO_ACTIVIDADES * porActividades;
            pesos += PESO_ACTIVIDADES;

            if (!actividadesCoincidentes.isEmpty()) {
                razones.add("Tiene actividades de " + unir(new ArrayList<>(gustosConActividad), 3)
                        + ": " + unir(new ArrayList<>(actividadesCoincidentes), 3));
            }
        }

        // ---- Comunidad: personas con gustos parecidos ----
        int afines = 0;
        if (!gustosPersona.isEmpty() && !miembros.isEmpty()) {
            Set<Integer> propios = gustosPersona.keySet();
            double sumaJaccard = 0;
            int conGustos = 0;
            Map<Integer, Integer> gustosCompartidos = new HashMap<>();

            for (Set<Integer> gustosMiembro : miembros.values()) {
                if (gustosMiembro.isEmpty()) {
                    continue;
                }
                conGustos++;
                Set<Integer> comunes = new HashSet<>(gustosMiembro);
                comunes.retainAll(propios);
                Set<Integer> union = new HashSet<>(gustosMiembro);
                union.addAll(propios);
                sumaJaccard += comunes.size() / (double) union.size();
                if (!comunes.isEmpty()) {
                    afines++;
                    comunes.forEach(g -> gustosCompartidos.merge(g, 1, Integer::sum));
                }
            }

            if (conGustos > 0) {
                // La similitud de Jaccard suele ser baja (0,1-0,3): se escala x3
                double comunidad = Math.min(1.0, (sumaJaccard / conGustos) * 3);
                suma += PESO_COMUNIDAD * comunidad;
                pesos += PESO_COMUNIDAD;
            }

            if (afines > 0) {
                List<String> masCompartidos = gustosCompartidos.entrySet().stream()
                        .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                        .limit(3)
                        .map(e -> gustosPersona.get(e.getKey()))
                        .toList();
                razones.add((afines == 1 ? "1 persona de esta organización comparte" : afines + " personas de esta organización comparten")
                        + " tus gustos (" + unir(masCompartidos, 3) + ")");
            }
        }

        int puntaje = pesos > 0 ? (int) Math.round(100 * suma / pesos) : 0;

        return new OrganizacionRecomendada(
                org.id(), org.nombre(), org.direccion(),
                barrioOrg != null ? barrioOrg.nombre() : null,
                distancia, org.celular(), org.correo(), puntaje, razones,
                new ArrayList<>(gustosConActividad),
                new ArrayList<>(actividadesCoincidentes).subList(0, Math.min(3, actividadesCoincidentes.size())),
                afines);
    }

    /** "a", "a y b", "a, b y c" (máximo n elementos). */
    private static String unir(List<String> lista, int n) {
        List<String> parte = lista.subList(0, Math.min(n, lista.size()));
        if (parte.size() <= 1) {
            return String.join("", parte);
        }
        return String.join(", ", parte.subList(0, parte.size() - 1)) + " y " + parte.get(parte.size() - 1);
    }

    /**
     * Dónde queda una dirección: su punto en la cuadrícula de Bogotá y, de
     * respaldo, su barrio (si lo menciona).
     */
    private record Ubicacion(DireccionBogota.Punto punto, Barrio barrio) {

        static Ubicacion de(String direccion) {
            return new Ubicacion(DireccionBogota.ubicar(direccion), Barrios.detectar(direccion));
        }

        boolean reconocida() {
            return punto != null || barrio != null;
        }

        /** Distancia por dirección; si alguna no se pudo ubicar, por barrio; si no, null. */
        Double distanciaKm(Ubicacion otra) {
            if (punto != null && otra.punto != null) {
                return DireccionBogota.distanciaKm(punto, otra.punto);
            }
            if (barrio != null && otra.barrio != null) {
                return Barrios.distanciaKm(barrio, otra.barrio);
            }
            return null;
        }
    }
}
