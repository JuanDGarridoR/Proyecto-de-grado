package com.proyectogrado.analitica_service.controller;

import com.proyectogrado.analitica_service.dto.RecursosDtos.ArticuloDonado;
import com.proyectogrado.analitica_service.dto.RecursosDtos.ArticuloInventario;
import com.proyectogrado.analitica_service.dto.RecursosDtos.DonacionAnalitica;
import com.proyectogrado.analitica_service.dto.RecursosDtos.DonacionesAnalitica;
import com.proyectogrado.analitica_service.dto.RecursosDtos.InventarioAnalitica;
import com.proyectogrado.analitica_service.dto.RecursosDtos.MovimientoInventario;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reportes de recursos de la organización: las donaciones que le programan
 * acompañantes y voluntarios, y su inventario (lo que tiene hoy y sus
 * entradas y salidas). Solo lee las tablas de donacion-service.
 *
 * Como el reporte de retiros, devuelve todo el historial y el frontend lo
 * filtra por período: así cambiar el período no vuelve a consultar.
 */
@RestController
@RequestMapping("/api/analitica")
public class RecursosController {

    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final NamedParameterJdbcTemplate jdbc;
    private final OrganizacionActual organizacionActual;

    public RecursosController(NamedParameterJdbcTemplate jdbc, OrganizacionActual organizacionActual) {
        this.jdbc = jdbc;
        this.organizacionActual = organizacionActual;
    }

    /** Donaciones de la organización con sus artículos, de la más reciente a la más antigua. */
    @GetMapping("/donaciones")
    public ResponseEntity<?> donaciones(@RequestHeader("X-User-Id") Integer idUsuario) {
        OrganizacionActual.Organizacion organizacion = organizacionActual.de(idUsuario);
        if (organizacion == null) {
            return sinOrganizacion();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("org", organizacion.id());

        Map<Integer, List<ArticuloDonado>> articulos = new LinkedHashMap<>();
        jdbc.query("""
                SELECT a.id_donacion, a.categoria, a.descripcion, a.cantidad, a.unidad, a.cantidad_recibida
                  FROM donacion_articulo a
                  JOIN donacion d ON d.id_donacion = a.id_donacion
                 WHERE d.id_organizacion = :org
                 ORDER BY a.id_articulo
                """, params, rs -> {
            articulos.computeIfAbsent(rs.getInt("id_donacion"), k -> new ArrayList<>()).add(new ArticuloDonado(
                    rs.getString("categoria"),
                    rs.getString("descripcion"),
                    rs.getDouble("cantidad"),
                    rs.getString("unidad"),
                    decimal(rs, "cantidad_recibida")));
        });

        List<DonacionAnalitica> donaciones = jdbc.query("""
                SELECT d.id_donacion, d.fecha_entrega, d.estado, d.rol_donante, u.nombre_usuario
                  FROM donacion d
                  LEFT JOIN usuario u ON u.id_usuario = d.id_donante
                 WHERE d.id_organizacion = :org
                 ORDER BY d.fecha_entrega DESC, d.id_donacion DESC
                """, params, (rs, i) -> new DonacionAnalitica(
                rs.getInt("id_donacion"),
                fecha(rs, "fecha_entrega"),
                rs.getString("estado"),
                rs.getString("rol_donante"),
                rs.getString("nombre_usuario"),
                articulos.getOrDefault(rs.getInt("id_donacion"), List.of())));

        return ResponseEntity.ok(new DonacionesAnalitica(donaciones));
    }

    /** Artículos del inventario como están hoy y todos sus movimientos, del más antiguo al más reciente. */
    @GetMapping("/inventario")
    public ResponseEntity<?> inventario(@RequestHeader("X-User-Id") Integer idUsuario) {
        OrganizacionActual.Organizacion organizacion = organizacionActual.de(idUsuario);
        if (organizacion == null) {
            return sinOrganizacion();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("org", organizacion.id());

        List<ArticuloInventario> articulos = jdbc.query("""
                SELECT id_inventario, nombre, categoria, unidad, cantidad, cantidad_minima, fecha_vencimiento
                  FROM inventario_articulo
                 WHERE id_organizacion = :org
                 ORDER BY nombre
                """, params, (rs, i) -> new ArticuloInventario(
                rs.getInt("id_inventario"),
                rs.getString("nombre"),
                rs.getString("categoria"),
                rs.getString("unidad"),
                rs.getDouble("cantidad"),
                decimal(rs, "cantidad_minima"),
                fecha(rs, "fecha_vencimiento")));

        List<MovimientoInventario> movimientos = jdbc.query("""
                SELECT m.id_inventario, m.tipo, m.cantidad, m.motivo, m.fecha
                  FROM inventario_movimiento m
                  JOIN inventario_articulo a ON a.id_inventario = m.id_inventario
                 WHERE a.id_organizacion = :org
                 ORDER BY m.fecha, m.id_movimiento
                """, params, (rs, i) -> new MovimientoInventario(
                rs.getInt("id_inventario"),
                rs.getString("tipo"),
                rs.getDouble("cantidad"),
                rs.getString("motivo"),
                fechaHora(rs, "fecha")));

        return ResponseEntity.ok(new InventarioAnalitica(articulos, movimientos));
    }

    private ResponseEntity<String> sinOrganizacion() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Solo una organización puede ver la analítica");
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
