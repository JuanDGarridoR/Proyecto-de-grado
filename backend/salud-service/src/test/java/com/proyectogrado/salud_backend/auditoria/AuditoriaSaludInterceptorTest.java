package com.proyectogrado.salud_backend.auditoria;

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
    void queUnAcompananteConsulteLosMedicamentosQuedaRegistrado() {
        responder("GET", "/api/acompanante/personas-mayores/10/medicamentos", 20, "ACOMPANANTE", 200);

        seRegistro(20, "ACOMPANANTE", 10, "MEDICAMENTOS", "CONSULTA", "GET",
                "/api/acompanante/personas-mayores/10/medicamentos", 200);
    }

    @Test
    void editarUnaCitaOAcompanarlaQuedaRegistradoComoEdicion() {
        responder("PUT", "/api/acompanante/personas-mayores/10/citas-medicas/5/acompanamiento", 20, "ACOMPANANTE", 200);

        seRegistro(20, "ACOMPANANTE", 10, "CITAS_MEDICAS", "EDICION", "PUT",
                "/api/acompanante/personas-mayores/10/citas-medicas/5/acompanamiento", 200);
    }

    @Test
    void unIntentoSinVinculoQuedaRegistradoConSu403() {
        responder("DELETE", "/api/acompanante/personas-mayores/10/condiciones-salud/3", 99, "ACOMPANANTE", 403);

        seRegistro(99, "ACOMPANANTE", 10, "CONDICIONES_SALUD", "BORRADO", "DELETE",
                "/api/acompanante/personas-mayores/10/condiciones-salud/3", 403);
    }

    @Test
    void queLaOrganizacionRegistreSignosVitalesQuedaRegistrado() {
        responder("POST", "/api/organizacion/signos-vitales/10", 30, "ORGANIZACION", 201);

        seRegistro(30, "ORGANIZACION", 10, "SIGNOS_VITALES", "CREACION", "POST",
                "/api/organizacion/signos-vitales/10", 201);
    }

    @Test
    void laPersonaMayorViendoLoSuyoNoSeRegistra() {
        responder("GET", "/api/persona-mayor/medicamentos", 10, "PERSONA_MAYOR", 200);
        responder("GET", "/api/acompanante/personas-mayores/10/medicamentos", 10, "ACOMPANANTE", 200);

        noSeRegistroNada();
    }

    @Test
    void sinUsuarioOConElPreflightDeCorsNoSeRegistraNada() {
        responder("GET", "/api/acompanante/personas-mayores/10/medicamentos", null, null, 401);
        responder("OPTIONS", "/api/acompanante/personas-mayores/10/medicamentos", 20, "ACOMPANANTE", 200);

        noSeRegistroNada();
    }
}
