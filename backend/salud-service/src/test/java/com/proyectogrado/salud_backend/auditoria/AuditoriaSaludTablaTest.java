package com.proyectogrado.salud_backend.auditoria;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * El SQL de la auditoría contra una base H2 en modo PostgreSQL: la tabla se
 * crea (dos veces no falla, como al arrancar varias réplicas) y el registro
 * queda guardado con todos sus datos.
 */
class AuditoriaSaludTablaTest {

    @Test
    void laTablaSeCreaYElRegistroQuedaGuardado() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:auditoria;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""));
        AuditoriaSaludInterceptor interceptor = new AuditoriaSaludInterceptor(jdbc);
        interceptor.crearTablaSiFalta();
        interceptor.crearTablaSiFalta();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/organizacion/signos-vitales/10");
        request.addHeader("X-User-Id", "30");
        request.addHeader("X-User-Rol", "ORGANIZACION");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        interceptor.afterCompletion(request, response, null, null);

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM auditoria_salud");
        assertEquals(30, fila.get("id_usuario"));
        assertEquals("ORGANIZACION", fila.get("rol"));
        assertEquals(10, fila.get("id_persona_mayor"));
        assertEquals("SIGNOS_VITALES", fila.get("recurso"));
        assertEquals("CONSULTA", fila.get("accion"));
        assertEquals(200, fila.get("estado"));
        assertNotNull(fila.get("fecha"));
    }
}
