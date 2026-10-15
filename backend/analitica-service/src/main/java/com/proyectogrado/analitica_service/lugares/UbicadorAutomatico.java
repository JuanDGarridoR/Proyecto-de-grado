package com.proyectogrado.analitica_service.lugares;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Ubica solas en el mapa las sedes de las organizaciones y las actividades
 * próximas, a partir de lo que escribieron: la dirección de la
 * organización y el lugar de la actividad ("Parque La Flora"). Nadie tiene
 * que escribir coordenadas ni marcar nada.
 *
 * Cada minuto busca las filas sin ubicar o cuyo texto cambió y le pregunta
 * a Google Places (BuscadorLugares) dónde quedan; se queda con el primer
 * resultado. Si el lugar de una actividad no aparece (p. ej. "Salón
 * principal"), la actividad queda en la sede de su organización.
 *
 * ubicacion_buscada guarda el texto que se ubicó: así un lugar que Google
 * no encuentra no se vuelve a buscar cada minuto, y si la organización
 * cambia la dirección o el lugar, se vuelve a ubicar. Las columnas
 * latitud y longitud las leen actividad-service, organizacion-service y
 * persona-mayor-service para pintar los mapas.
 *
 * Sin clave de Google (google.places.api-key) no hace nada.
 */
@Component
public class UbicadorAutomatico {

    private static final Logger log = LoggerFactory.getLogger(UbicadorAutomatico.class);

    /** Máximo de búsquedas por vuelta, para no gastar el cupo de golpe. */
    static final int MAX_POR_VUELTA = 20;

    private final NamedParameterJdbcTemplate jdbc;
    private final BuscadorLugares buscador;

    private boolean columnasListas = false;

    public UbicadorAutomatico(NamedParameterJdbcTemplate jdbc, BuscadorLugares buscador) {
        this.jdbc = jdbc;
        this.buscador = buscador;
    }

    @Scheduled(initialDelay = 20_000, fixedDelay = 60_000)
    public void ubicarPendientes() {
        if (!buscador.disponible()) {
            return;
        }

        try {
            prepararColumnas();
            // Primero las organizaciones: sus sedes sirven de respaldo a las actividades.
            int organizaciones = ubicarOrganizaciones();
            int actividades = ubicarActividades();

            if (organizaciones + actividades > 0) {
                log.info("Ubicados en el mapa: {} organizaciones y {} actividades", organizaciones, actividades);
            }
        } catch (RestClientException e) {
            // Google no responde: se intenta en la siguiente vuelta.
            log.warn("No se pudo ubicar en el mapa (Google Places): {}", e.getMessage());
        }
    }

    /** Las columnas existen aunque los otros servicios aún no hayan arrancado con el código nuevo. */
    private void prepararColumnas() {
        if (columnasListas) {
            return;
        }

        for (String tabla : List.of("organizacion", "actividad")) {
            jdbc.getJdbcTemplate().execute("ALTER TABLE " + tabla + " ADD COLUMN IF NOT EXISTS latitud double precision");
            jdbc.getJdbcTemplate().execute("ALTER TABLE " + tabla + " ADD COLUMN IF NOT EXISTS longitud double precision");
            jdbc.getJdbcTemplate().execute("ALTER TABLE " + tabla + " ADD COLUMN IF NOT EXISTS ubicacion_buscada varchar(255)");
        }

        columnasListas = true;
    }

    int ubicarOrganizaciones() {
        List<Map<String, Object>> pendientes = jdbc.queryForList("""
                SELECT id_organizacion, direccion
                FROM organizacion
                WHERE direccion IS NOT NULL AND trim(direccion) <> ''
                  AND (ubicacion_buscada IS NULL OR ubicacion_buscada <> direccion)
                ORDER BY id_organizacion
                LIMIT :limite
                """, Map.of("limite", MAX_POR_VUELTA));

        for (Map<String, Object> fila : pendientes) {
            String direccion = (String) fila.get("direccion");
            LugarEncontrado lugar = primero(direccion);

            // "AND direccion = :texto": si la cambiaron mientras se buscaba, se vuelve a ubicar en la siguiente vuelta.
            jdbc.update("""
                    UPDATE organizacion
                    SET latitud = :latitud, longitud = :longitud, ubicacion_buscada = :texto
                    WHERE id_organizacion = :id AND direccion = :texto
                    """, parametros(fila.get("id_organizacion"), direccion, lugar, null, null));
        }

        return pendientes.size();
    }

    /** Solo las actividades de hoy en adelante (o sin fecha): las pasadas no salen en los mapas. */
    int ubicarActividades() {
        List<Map<String, Object>> pendientes = jdbc.queryForList("""
                SELECT a.id_actividad, a.lugar, o.latitud AS latitud_sede, o.longitud AS longitud_sede
                FROM actividad a
                LEFT JOIN organizacion o ON o.id_organizacion = a.id_organizacion
                WHERE a.lugar IS NOT NULL AND trim(a.lugar) <> ''
                  AND (a.fecha IS NULL OR a.fecha >= CURRENT_DATE)
                  AND (a.ubicacion_buscada IS NULL OR a.ubicacion_buscada <> a.lugar)
                ORDER BY a.fecha, a.id_actividad
                LIMIT :limite
                """, Map.of("limite", MAX_POR_VUELTA));

        for (Map<String, Object> fila : pendientes) {
            String lugarEscrito = (String) fila.get("lugar");
            LugarEncontrado lugar = primero(lugarEscrito);

            jdbc.update("""
                    UPDATE actividad
                    SET latitud = :latitud, longitud = :longitud, ubicacion_buscada = :texto
                    WHERE id_actividad = :id AND lugar = :texto
                    """, parametros(fila.get("id_actividad"), lugarEscrito, lugar,
                    (Number) fila.get("latitud_sede"), (Number) fila.get("longitud_sede")));
        }

        return pendientes.size();
    }

    /** El resultado más relevante de Google, o null si no encontró nada. */
    private LugarEncontrado primero(String texto) {
        List<LugarEncontrado> lugares = buscador.buscar(texto);
        return lugares.isEmpty() ? null : lugares.get(0);
    }

    /** Si no se encontró el lugar, se usa la sede (si la hay); si tampoco, queda sin punto. */
    private MapSqlParameterSource parametros(Object id, String texto, LugarEncontrado lugar,
                                             Number latitudRespaldo, Number longitudRespaldo) {
        Double latitud = lugar != null ? Double.valueOf(lugar.latitud())
                : latitudRespaldo != null ? latitudRespaldo.doubleValue() : null;
        Double longitud = lugar != null ? Double.valueOf(lugar.longitud())
                : longitudRespaldo != null ? longitudRespaldo.doubleValue() : null;

        return new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("texto", texto)
                .addValue("latitud", latitud, java.sql.Types.DOUBLE)
                .addValue("longitud", longitud, java.sql.Types.DOUBLE);
    }
}
