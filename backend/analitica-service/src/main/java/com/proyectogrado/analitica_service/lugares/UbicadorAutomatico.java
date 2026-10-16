package com.proyectogrado.analitica_service.lugares;

import com.proyectogrado.analitica_service.recomendacion.Barrios;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
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
 * De las personas mayores solo se guarda el BARRIO (persona_mayor.barrio),
 * nunca las coordenadas de su casa; con él el mapa de la organización las
 * agrupa. El barrio sale, en este orden:
 *   1. del texto de la dirección, si lo trae ("..., Yomasa"), sin Google;
 *   2. del barrio que Google le asigna a la dirección;
 *   3. del barrio conocido más cercano al punto que encontró Google (a
 *      menos de MAX_KM_BARRIO), porque en Bogotá Google a veces solo da la
 *      localidad ("Usme").
 *
 * ubicacion_buscada y barrio_buscado guardan el texto que se ubicó: así lo
 * que Google no encuentra no se vuelve a buscar cada minuto, y si alguien
 * cambia la dirección o el lugar, se vuelve a ubicar. Las columnas latitud
 * y longitud las leen actividad-service, organizacion-service y
 * persona-mayor-service para pintar los mapas.
 *
 * Sin clave de Google (google.places.api-key) solo saca el barrio del texto.
 */
@Component
public class UbicadorAutomatico {

    private static final Logger log = LoggerFactory.getLogger(UbicadorAutomatico.class);

    /** Máximo de búsquedas en Google por vuelta y por tipo, para no gastar el cupo de golpe. */
    static final int MAX_POR_VUELTA = 20;

    /** Personas revisadas por vuelta (sacar el barrio del texto no gasta Google). */
    static final int MAX_PERSONAS_POR_VUELTA = 500;

    /** Lo más lejos que puede quedar el barrio conocido más cercano. */
    static final double MAX_KM_BARRIO = 2.0;

    private final NamedParameterJdbcTemplate jdbc;
    private final BuscadorLugares buscador;

    private boolean columnasListas = false;

    public UbicadorAutomatico(NamedParameterJdbcTemplate jdbc, BuscadorLugares buscador) {
        this.jdbc = jdbc;
        this.buscador = buscador;
    }

    /** Al arrancar se crean las columnas: la red de la organización las lee aunque no haya clave de Google. */
    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        try {
            prepararColumnas();
        } catch (DataAccessException e) {
            log.warn("No se pudieron preparar las columnas de ubicación: {}", e.getMessage());
        }
    }

    @Scheduled(initialDelay = 20_000, fixedDelay = 60_000)
    public void ubicarPendientes() {
        try {
            prepararColumnas();
            int personas = ubicarBarriosPersonas();

            int organizaciones = 0;
            int actividades = 0;
            if (buscador.disponible()) {
                // Primero las organizaciones: sus sedes sirven de respaldo a las actividades.
                organizaciones = ubicarOrganizaciones();
                actividades = ubicarActividades();
            }

            if (personas + organizaciones + actividades > 0) {
                log.info("Ubicados en el mapa: {} organizaciones, {} actividades y {} personas mayores (por barrio)",
                        organizaciones, actividades, personas);
            }
        } catch (RestClientException e) {
            // Google no responde: se intenta en la siguiente vuelta.
            log.warn("No se pudo ubicar en el mapa (Google Places): {}", e.getMessage());
        }
    }

    /** Las columnas existen aunque los otros servicios aún no hayan arrancado con el código nuevo. */
    void prepararColumnas() {
        if (columnasListas) {
            return;
        }

        for (String tabla : List.of("organizacion", "actividad")) {
            jdbc.getJdbcTemplate().execute("ALTER TABLE " + tabla + " ADD COLUMN IF NOT EXISTS latitud double precision");
            jdbc.getJdbcTemplate().execute("ALTER TABLE " + tabla + " ADD COLUMN IF NOT EXISTS longitud double precision");
            jdbc.getJdbcTemplate().execute("ALTER TABLE " + tabla + " ADD COLUMN IF NOT EXISTS ubicacion_buscada varchar(255)");
        }
        jdbc.getJdbcTemplate().execute("ALTER TABLE persona_mayor ADD COLUMN IF NOT EXISTS barrio varchar(100)");
        jdbc.getJdbcTemplate().execute("ALTER TABLE persona_mayor ADD COLUMN IF NOT EXISTS barrio_buscado varchar(255)");

        columnasListas = true;
    }

    /**
     * Barrio de las personas mayores con dirección nueva o cambiada. Las que
     * necesitan Google y no se alcanzan a buscar (o no hay clave) quedan
     * pendientes para otra vuelta.
     */
    int ubicarBarriosPersonas() {
        List<Map<String, Object>> pendientes = jdbc.queryForList("""
                SELECT pm.id_usuario, u.direccion
                FROM persona_mayor pm
                JOIN usuario u ON u.id_usuario = pm.id_usuario
                WHERE u.direccion IS NOT NULL AND trim(u.direccion) <> ''
                  AND (pm.barrio_buscado IS NULL OR pm.barrio_buscado <> u.direccion)
                ORDER BY pm.id_usuario
                LIMIT :limite
                """, Map.of("limite", MAX_PERSONAS_POR_VUELTA));

        int ubicadas = 0;
        int busquedasGoogle = 0;

        for (Map<String, Object> fila : pendientes) {
            String direccion = (String) fila.get("direccion");
            Barrios.Barrio barrio = Barrios.detectar(direccion);

            if (barrio == null) {
                if (!buscador.disponible() || busquedasGoogle >= MAX_POR_VUELTA) {
                    continue;
                }
                busquedasGoogle++;
                barrio = barrioSegunGoogle(direccion);
            }

            jdbc.update("""
                    UPDATE persona_mayor SET barrio = :barrio, barrio_buscado = :texto
                    WHERE id_usuario = :id
                    """, new MapSqlParameterSource()
                    .addValue("id", fila.get("id_usuario"))
                    .addValue("texto", direccion)
                    .addValue("barrio", barrio != null ? barrio.nombre() : null));
            ubicadas++;
        }

        return ubicadas;
    }

    /** Barrio (de los conocidos) de una dirección según Google, o null si no se pudo saber. */
    private Barrios.Barrio barrioSegunGoogle(String direccion) {
        LugarEncontrado lugar = primero(direccion);
        if (lugar == null) {
            return null;
        }
        Barrios.Barrio porNombre = Barrios.porNombre(lugar.barrio());
        return porNombre != null
                ? porNombre
                : Barrios.masCercano(lugar.latitud(), lugar.longitud(), MAX_KM_BARRIO);
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
