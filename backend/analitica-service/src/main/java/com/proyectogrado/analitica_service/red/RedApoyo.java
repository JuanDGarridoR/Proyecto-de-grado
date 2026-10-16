package com.proyectogrado.analitica_service.red;

import com.proyectogrado.analitica_service.recomendacion.Barrios;
import com.proyectogrado.analitica_service.red.RedDtos.AcompananteRed;
import com.proyectogrado.analitica_service.red.RedDtos.ActividadRed;
import com.proyectogrado.analitica_service.red.RedDtos.BarrioRed;
import com.proyectogrado.analitica_service.red.RedDtos.GustoRed;
import com.proyectogrado.analitica_service.red.RedDtos.PersonaRed;
import com.proyectogrado.analitica_service.red.RedDtos.RedResponse;
import com.proyectogrado.analitica_service.red.RedDtos.Sede;
import com.proyectogrado.analitica_service.red.RedDtos.VinculoRed;
import com.proyectogrado.analitica_service.red.RedDtos.VoluntarioRed;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Arma la red de apoyo de una organización (solo lectura de la base
 * compartida): sus personas mayores con vínculo aceptado, los acompañantes
 * de esas personas, sus voluntarios, los gustos de todos y las actividades
 * de los últimos DIAS_ATRAS días y los próximos DIAS_ADELANTE, con quién
 * se inscribió en cada una.
 *
 * Solo informa (RNF-13): no cambia nada ni toma decisiones.
 */
@Component
public class RedApoyo {

    static final int DIAS_ATRAS = 90;
    static final int DIAS_ADELANTE = 60;

    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");

    private final NamedParameterJdbcTemplate jdbc;

    public RedApoyo(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public RedResponse de(Integer idOrganizacion, String nombreOrganizacion) {
        return de(idOrganizacion, nombreOrganizacion, LocalDate.now(COLOMBIA));
    }

    RedResponse de(Integer idOrganizacion, String nombreOrganizacion, LocalDate hoy) {
        MapSqlParameterSource org = new MapSqlParameterSource("org", idOrganizacion)
                .addValue("desde", Date.valueOf(hoy.minusDays(DIAS_ATRAS)))
                .addValue("hasta", Date.valueOf(hoy.plusDays(DIAS_ADELANTE)));

        Sede sede = jdbc.query("""
                SELECT latitud, longitud FROM organizacion WHERE id_organizacion = :org
                """, org, (rs, i) -> new Sede(idOrganizacion, nombreOrganizacion,
                        (Double) rs.getObject("latitud"), (Double) rs.getObject("longitud")))
                .stream().findFirst()
                .orElse(new Sede(idOrganizacion, nombreOrganizacion, null, null));

        // Personas mayores con vínculo aceptado y cuenta activa
        Map<Integer, String> nombres = new LinkedHashMap<>();
        Map<Integer, String> barrioDe = new LinkedHashMap<>();
        Map<String, BarrioRed> barrios = new LinkedHashMap<>();

        jdbc.query("""
                SELECT u.id_usuario, u.nombre_usuario, u.direccion, pm.barrio AS barrio_guardado
                  FROM persona_mayor_organizacion pmo
                  JOIN usuario u ON u.id_usuario = pmo.id_persona_mayor
                  LEFT JOIN persona_mayor pm ON pm.id_usuario = u.id_usuario
                 WHERE pmo.id_organizacion = :org AND pmo.estado = 'ACEPTADA'
                   AND (u.activo IS NULL OR u.activo = TRUE)
                 ORDER BY u.nombre_usuario
                """, org, rs -> {
            int id = rs.getInt("id_usuario");
            nombres.put(id, rs.getString("nombre_usuario"));

            // El que dejó UbicadorAutomatico (sacado de la dirección con Google);
            // si aún no lo hay, el que venga escrito en la dirección.
            Barrios.Barrio barrio = Barrios.porNombre(rs.getString("barrio_guardado"));
            if (barrio == null) {
                barrio = Barrios.detectar(rs.getString("direccion"));
            }
            barrioDe.put(id, barrio != null ? barrio.nombre() : null);
            if (barrio != null) {
                barrios.putIfAbsent(barrio.nombre(),
                        new BarrioRed(barrio.nombre(), barrio.latitud(), barrio.longitud()));
            }
        });

        Map<Integer, List<Integer>> gustosPersona = pares("""
                SELECT pmg.id_persona_mayor AS id, pmg.id_gusto AS otro
                  FROM persona_mayor_gusto pmg
                  JOIN persona_mayor_organizacion pmo ON pmo.id_persona_mayor = pmg.id_persona_mayor
                 WHERE pmo.id_organizacion = :org AND pmo.estado = 'ACEPTADA'
                """, org);

        // Actividades visibles (de la organización o propuestas aceptadas) en la ventana de fechas
        List<ActividadRed> actividades = jdbc.query("""
                SELECT id_actividad, nombre, fecha, lugar, latitud, longitud
                  FROM actividad
                 WHERE id_organizacion = :org
                   AND (estado IS NULL OR estado = 'ACEPTADA')
                   AND fecha BETWEEN :desde AND :hasta
                 ORDER BY fecha, id_actividad
                """, org, (rs, i) -> new ActividadRed(
                        rs.getInt("id_actividad"), rs.getString("nombre"),
                        rs.getDate("fecha").toLocalDate(), rs.getString("lugar"),
                        (Double) rs.getObject("latitud"), (Double) rs.getObject("longitud")));

        Map<Integer, List<Integer>> actividadesPersona = pares("""
                SELECT p.id_persona_mayor AS id, p.id_actividad AS otro
                  FROM participacion p
                  JOIN actividad a ON a.id_actividad = p.id_actividad
                 WHERE a.id_organizacion = :org
                   AND (a.estado IS NULL OR a.estado = 'ACEPTADA')
                   AND a.fecha BETWEEN :desde AND :hasta
                """, org);

        List<PersonaRed> personas = new ArrayList<>();
        nombres.forEach((id, nombre) -> personas.add(new PersonaRed(id, nombre, barrioDe.get(id),
                gustosPersona.getOrDefault(id, List.of()),
                actividadesPersona.getOrDefault(id, List.of()))));

        // Acompañantes de esas personas (vínculo aceptado y cuenta activa)
        List<VinculoRed> vinculos = new ArrayList<>();
        Map<Integer, AcompananteRed> acompanantes = new LinkedHashMap<>();

        jdbc.query("""
                SELECT pma.id_persona_mayor, pma.id_acompanante, u.nombre_usuario,
                       COALESCE(pma.relacion, a.relacion) AS relacion
                  FROM persona_mayor_acompanante pma
                  JOIN persona_mayor_organizacion pmo ON pmo.id_persona_mayor = pma.id_persona_mayor
                  JOIN usuario u ON u.id_usuario = pma.id_acompanante
                  LEFT JOIN acompanante a ON a.id_usuario = pma.id_acompanante
                 WHERE pmo.id_organizacion = :org AND pmo.estado = 'ACEPTADA'
                   AND pma.estado = 'ACEPTADA'
                   AND (u.activo IS NULL OR u.activo = TRUE)
                 ORDER BY u.nombre_usuario
                """, org, rs -> {
            int idPersona = rs.getInt("id_persona_mayor");
            if (!nombres.containsKey(idPersona)) {
                return; // persona con la cuenta inactiva
            }
            int idAcompanante = rs.getInt("id_acompanante");
            acompanantes.putIfAbsent(idAcompanante,
                    new AcompananteRed(idAcompanante, rs.getString("nombre_usuario")));
            vinculos.add(new VinculoRed(idPersona, idAcompanante, rs.getString("relacion")));
        });

        // Voluntarios vinculados
        Map<Integer, List<Integer>> gustosVoluntario = pares("""
                SELECT vg.id_voluntario AS id, vg.id_gusto AS otro
                  FROM voluntario_gusto vg
                  JOIN voluntario_organizacion vo ON vo.id_voluntario = vg.id_voluntario
                 WHERE vo.id_organizacion = :org AND vo.estado = 'ACEPTADA'
                """, org);

        List<VoluntarioRed> voluntarios = jdbc.query("""
                SELECT u.id_usuario, u.nombre_usuario
                  FROM voluntario_organizacion vo
                  JOIN usuario u ON u.id_usuario = vo.id_voluntario
                 WHERE vo.id_organizacion = :org AND vo.estado = 'ACEPTADA'
                   AND (u.activo IS NULL OR u.activo = TRUE)
                 ORDER BY u.nombre_usuario
                """, org, (rs, i) -> new VoluntarioRed(rs.getInt("id_usuario"), rs.getString("nombre_usuario"),
                        gustosVoluntario.getOrDefault(rs.getInt("id_usuario"), List.of())));

        // Solo los gustos que tiene alguien de la red
        Set<Integer> usados = new HashSet<>();
        personas.forEach(p -> usados.addAll(p.gustos()));
        voluntarios.forEach(v -> usados.addAll(v.gustos()));

        List<GustoRed> gustos = jdbc.query(
                "SELECT id_gusto, nombre, categoria FROM gusto ORDER BY nombre",
                (rs, i) -> new GustoRed(rs.getInt("id_gusto"), rs.getString("nombre"), rs.getString("categoria")))
                .stream()
                .filter(g -> usados.contains(g.id()))
                .toList();

        return new RedResponse(sede, personas, List.copyOf(acompanantes.values()), vinculos,
                voluntarios, gustos, List.copyOf(barrios.values()), actividades);
    }

    /** Consulta de dos columnas (id, otro) agrupada por id. */
    private Map<Integer, List<Integer>> pares(String sql, MapSqlParameterSource parametros) {
        Map<Integer, List<Integer>> resultado = new LinkedHashMap<>();
        jdbc.query(sql, parametros, rs -> {
            resultado.computeIfAbsent(rs.getInt("id"), k -> new ArrayList<>()).add(rs.getInt("otro"));
        });
        return resultado;
    }
}
