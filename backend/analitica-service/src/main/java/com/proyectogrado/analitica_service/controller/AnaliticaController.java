package com.proyectogrado.analitica_service.controller;

import com.proyectogrado.analitica_service.dto.AnaliticaDtos.ActividadAnalitica;
import com.proyectogrado.analitica_service.dto.AnaliticaDtos.InteresConteo;
import com.proyectogrado.analitica_service.dto.AnaliticaDtos.MedicionAnalitica;
import com.proyectogrado.analitica_service.dto.AnaliticaDtos.PersonaAnalitica;
import com.proyectogrado.analitica_service.dto.AnaliticaDtos.PersonaPoblacion;
import com.proyectogrado.analitica_service.dto.AnaliticaDtos.PoblacionAnalitica;
import com.proyectogrado.analitica_service.dto.AnaliticaDtos.SaludAnalitica;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Datos de los reportes de analítica de la organización (el frontend arma
 * con ellos los indicadores y las gráficas; ver también PdfController).
 *
 * Solo lectura. Las tablas son de otros servicios (actividad, salud,
 * persona mayor), pero todos comparten la base, así que se consultan
 * directamente con SQL en vez de pedirle a cada servicio sus datos.
 * Todo se limita a las personas mayores con asociación ACEPTADA a la
 * organización del usuario.
 */
@RestController
@RequestMapping("/api/analitica")
public class AnaliticaController {

    private static final DateTimeFormatter FORMATO_FECHA_HORA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    /** Subconsulta con las personas mayores vinculadas a la organización; la usan todas las consultas. */
    private static final String PERSONAS_ASOCIADAS = """
            SELECT po.id_persona_mayor
              FROM persona_mayor_organizacion po
             WHERE po.id_organizacion = :org
               AND po.estado = 'ACEPTADA'
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final OrganizacionActual organizacionActual;

    public AnaliticaController(NamedParameterJdbcTemplate jdbc, OrganizacionActual organizacionActual) {
        this.jdbc = jdbc;
        this.organizacionActual = organizacionActual;
    }

    /**
     * Actividades y participación: una fila por actividad de la organización
     * con fecha dentro del período. Sin fechas, trae todas las que tienen fecha.
     */
    @GetMapping("/actividades")
    public ResponseEntity<?> actividades(
            @RequestHeader("X-User-Id") Integer idUsuario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta
    ) {
        Integer idOrganizacion = obtenerIdOrganizacion(idUsuario);
        if (idOrganizacion == null) {
            return sinOrganizacion();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("org", idOrganizacion)
                .addValue("desde", Date.valueOf(desde != null ? desde : LocalDate.of(1900, 1, 1)))
                .addValue("hasta", Date.valueOf(hasta != null ? hasta : LocalDate.of(2999, 12, 31)));

        List<ActividadAnalitica> filas = jdbc.query("""
                SELECT a.id_actividad, a.nombre, a.tipo, a.fecha, a.cupos,
                       COUNT(p.id_persona_mayor)                                  AS inscritos,
                       COUNT(p.id_persona_mayor) FILTER (WHERE p.asistio = TRUE)  AS asistentes,
                       COUNT(p.id_persona_mayor) FILTER (WHERE p.asistio IS NOT NULL) AS con_registro
                  FROM actividad a
                  LEFT JOIN participacion p ON p.id_actividad = a.id_actividad
                 WHERE a.id_organizacion = :org
                   AND a.fecha BETWEEN :desde AND :hasta
                   -- Sin propuestas de voluntarios pendientes o rechazadas
                   AND (a.estado IS NULL OR a.estado = 'ACEPTADA')
                 GROUP BY a.id_actividad, a.nombre, a.tipo, a.fecha, a.cupos
                 ORDER BY a.fecha, a.id_actividad
                """, params, (rs, i) -> new ActividadAnalitica(
                rs.getInt("id_actividad"),
                rs.getString("nombre"),
                rs.getString("tipo"),
                fecha(rs, "fecha"),
                entero(rs, "cupos"),
                rs.getLong("inscritos"),
                rs.getLong("asistentes"),
                rs.getLong("con_registro")
        ));

        return ResponseEntity.ok(filas);
    }

    /**
     * Salud de la población: las personas vinculadas y todas sus mediciones
     * de signos vitales. El frontend filtra por período para las tendencias
     * y usa la última medición de cada persona para el estado actual.
     */
    @GetMapping("/salud")
    public ResponseEntity<?> salud(@RequestHeader("X-User-Id") Integer idUsuario) {
        Integer idOrganizacion = obtenerIdOrganizacion(idUsuario);
        if (idOrganizacion == null) {
            return sinOrganizacion();
        }

        MapSqlParameterSource params = new MapSqlParameterSource("org", idOrganizacion);

        List<PersonaAnalitica> personas = jdbc.query("""
                SELECT u.id_usuario, u.nombre_usuario
                  FROM usuario u
                 WHERE u.id_usuario IN (%s)
                 ORDER BY u.nombre_usuario
                """.formatted(PERSONAS_ASOCIADAS), params,
                (rs, i) -> new PersonaAnalitica(rs.getInt("id_usuario"), rs.getString("nombre_usuario")));

        List<MedicionAnalitica> mediciones = jdbc.query("""
                SELECT s.id_persona_mayor, s.fecha_hora,
                       s.presion_sistolica, s.presion_diastolica, s.frecuencia_cardiaca,
                       s.temperatura, s.saturacion_oxigeno, s.frecuencia_respiratoria, s.peso
                  FROM signo_vital s
                 WHERE s.id_persona_mayor IN (%s)
                 ORDER BY s.fecha_hora
                """.formatted(PERSONAS_ASOCIADAS), params,
                (rs, i) -> new MedicionAnalitica(
                        rs.getInt("id_persona_mayor"),
                        fechaHora(rs, "fecha_hora"),
                        entero(rs, "presion_sistolica"),
                        entero(rs, "presion_diastolica"),
                        entero(rs, "frecuencia_cardiaca"),
                        decimal(rs, "temperatura"),
                        entero(rs, "saturacion_oxigeno"),
                        entero(rs, "frecuencia_respiratoria"),
                        decimal(rs, "peso")
                ));

        return ResponseEntity.ok(new SaludAnalitica(personas, mediciones));
    }

    /**
     * Perfil de la población: fecha de nacimiento, género y EPS de cada
     * persona vinculada, y cuántas personas tienen marcado cada gusto.
     */
    @GetMapping("/poblacion")
    public ResponseEntity<?> poblacion(@RequestHeader("X-User-Id") Integer idUsuario) {
        Integer idOrganizacion = obtenerIdOrganizacion(idUsuario);
        if (idOrganizacion == null) {
            return sinOrganizacion();
        }

        MapSqlParameterSource params = new MapSqlParameterSource("org", idOrganizacion);

        // La fecha de nacimiento y el género pueden estar en persona_mayor (los
        // guarda /api/persona-mayor/perfil) o en usuario (registro y "Mi
        // información"): se usa el primero que exista.
        List<PersonaPoblacion> personas = jdbc.query("""
                SELECT u.id_usuario, u.nombre_usuario,
                       COALESCE(pm.fecha_nacimiento, u.fecha_nacimiento) AS fecha_nacimiento,
                       COALESCE(NULLIF(pm.genero, ''), NULLIF(u.genero, '')) AS genero,
                       NULLIF(TRIM(pm.eps), '') AS eps
                  FROM usuario u
                  LEFT JOIN persona_mayor pm ON pm.id_usuario = u.id_usuario
                 WHERE u.id_usuario IN (%s)
                 ORDER BY u.nombre_usuario
                """.formatted(PERSONAS_ASOCIADAS), params,
                (rs, i) -> new PersonaPoblacion(
                        rs.getInt("id_usuario"),
                        rs.getString("nombre_usuario"),
                        fecha(rs, "fecha_nacimiento"),
                        rs.getString("genero"),
                        rs.getString("eps")
                ));

        List<InteresConteo> intereses = jdbc.query("""
                SELECT g.nombre, g.categoria, COUNT(DISTINCT pg.id_persona_mayor) AS personas
                  FROM persona_mayor_gusto pg
                  JOIN gusto g ON g.id_gusto = pg.id_gusto
                 WHERE pg.id_persona_mayor IN (%s)
                 GROUP BY g.nombre, g.categoria
                 ORDER BY personas DESC, g.nombre
                """.formatted(PERSONAS_ASOCIADAS), params,
                (rs, i) -> new InteresConteo(
                        rs.getString("nombre"),
                        rs.getString("categoria"),
                        rs.getLong("personas")
                ));

        Long conIntereses = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT pg.id_persona_mayor)
                  FROM persona_mayor_gusto pg
                 WHERE pg.id_persona_mayor IN (%s)
                """.formatted(PERSONAS_ASOCIADAS), params, Long.class);

        // Acompañantes distintos con vínculo aceptado a alguna persona de la organización.
        Long acompanantes = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT pa.id_acompanante)
                  FROM persona_mayor_acompanante pa
                 WHERE pa.estado = 'ACEPTADA'
                   AND pa.id_persona_mayor IN (%s)
                """.formatted(PERSONAS_ASOCIADAS), params, Long.class);

        return ResponseEntity.ok(new PoblacionAnalitica(
                personas, intereses, conIntereses != null ? conIntereses : 0,
                acompanantes != null ? acompanantes : 0));
    }

    /** Organización de la cuenta, o null si el usuario no es una organización. */
    private Integer obtenerIdOrganizacion(Integer idUsuario) {
        OrganizacionActual.Organizacion organizacion = organizacionActual.de(idUsuario);
        return organizacion != null ? organizacion.id() : null;
    }

    private ResponseEntity<String> sinOrganizacion() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Solo una organización puede ver la analítica");
    }

    // Lectores de columnas que pueden venir en null (getInt, por ejemplo,
    // devolvería 0 en lugar de null).

    private static Integer entero(ResultSet rs, String columna) throws SQLException {
        Number valor = (Number) rs.getObject(columna);
        return valor != null ? valor.intValue() : null;
    }

    private static Double decimal(ResultSet rs, String columna) throws SQLException {
        Number valor = (Number) rs.getObject(columna);
        return valor != null ? valor.doubleValue() : null;
    }

    private static String fecha(ResultSet rs, String columna) throws SQLException {
        Date valor = rs.getDate(columna);
        return valor != null ? valor.toLocalDate().toString() : null;
    }

    private static String fechaHora(ResultSet rs, String columna) throws SQLException {
        Timestamp valor = rs.getTimestamp(columna);
        return valor != null ? valor.toLocalDateTime().format(FORMATO_FECHA_HORA) : null;
    }
}
