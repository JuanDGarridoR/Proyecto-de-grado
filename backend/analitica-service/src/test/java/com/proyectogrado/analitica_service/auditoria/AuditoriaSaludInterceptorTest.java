package com.proyectogrado.analitica_service.auditoria;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Registro de auditoría de los datos de salud (RNF-25): queda quién
 * consultó o cambió los datos de salud de una persona mayor, y si se le
 * permitió. La propia persona viendo lo suyo no se registra.
 */
class AuditoriaSaludInterceptorTest {

    private JdbcTemplate jdbc;
    private AuditoriaSaludInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        interceptor = new AuditoriaSaludInterceptor(jdbc);
    }

    /** Simula que el servicio respondió esa petición con ese estado. */
    private void responder(String metodo, String ruta, Integer idUsuario, String rol, int estado) {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, ruta);
        if (idUsuario != null) {
            request.addHeader("X-User-Id", idUsuario);
        }
        if (rol != null) {
            request.addHeader("X-User-Rol", rol);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(estado);
        interceptor.afterCompletion(request, response, null, null);
    }

    /** Verifica el INSERT con estos valores (la fecha puede ser cualquiera). */
    private void seRegistro(Integer idUsuario, String rol, Integer idPersonaMayor, String recurso,
                            String accion, String metodo, String ruta, int estado) {
        verify(jdbc).update(anyString(), any(), eq(idUsuario), eq(rol), eq(idPersonaMayor), eq(recurso),
                eq(accion), eq(metodo), eq(ruta), eq(estado));
    }

    private void noSeRegistroNada() {
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void losReportesDeSaludYDePerfilesQuedanRegistradosSinUnaPersonaEnParticular() {
        responder("GET", "/api/analitica/salud", 30, "ORGANIZACION", 200);
        responder("GET", "/api/analitica/perfiles", 30, "ORGANIZACION", 200);

        seRegistro(30, "ORGANIZACION", null, "REPORTE_SALUD", "CONSULTA", "GET", "/api/analitica/salud", 200);
        seRegistro(30, "ORGANIZACION", null, "PERFILES", "CONSULTA", "GET", "/api/analitica/perfiles", 200);
    }

    @Test
    void losDemasReportesNoSeRegistran() {
        responder("GET", "/api/analitica/actividades", 30, "ORGANIZACION", 200);
        responder("GET", "/api/analitica/poblacion", 30, "ORGANIZACION", 200);

        noSeRegistroNada();
    }
}
