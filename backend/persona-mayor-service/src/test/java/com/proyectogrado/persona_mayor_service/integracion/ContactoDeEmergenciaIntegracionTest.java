package com.proyectogrado.persona_mayor_service.integracion;

import com.proyectogrado.persona_mayor_service.client.MensajeriaClient;
import com.proyectogrado.persona_mayor_service.controller.EmergenciaController;
import com.proyectogrado.persona_mayor_service.controller.PersonaMayorAcompananteController;
import com.proyectogrado.persona_mayor_service.repository.AcompananteLookupRepository;
import com.proyectogrado.persona_mayor_service.repository.EmergenciaRepository;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorAcompananteRepository;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorOrganizacionRepository;
import com.proyectogrado.persona_mayor_service.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo completo de un contacto de emergencia sobre una base H2 en memoria
 * (RF-53): la persona mayor ya registrada agrega a su acompañante, él acepta,
 * aparece en sus contactos y le llega la alerta del botón de emergencia. Los
 * controladores usan los repositorios reales; solo mensajeria-service está
 * simulado.
 */
@DataJpaTest
class ContactoDeEmergenciaIntegracionTest {

    @Autowired
    private PersonaMayorAcompananteRepository relacionRepository;

    @Autowired
    private UsuarioLookupRepository usuarioLookupRepository;

    @Autowired
    private AcompananteLookupRepository acompananteLookupRepository;

    @Autowired
    private PersonaMayorOrganizacionRepository organizacionRepository;

    @Autowired
    private EmergenciaRepository emergenciaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final MensajeriaClient mensajeriaClient = mock(MensajeriaClient.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Lo que deja auth-service al registrar a dos personas mayores y a un acompañante.
        sql("insert into usuario (id_usuario, nombre_usuario, celular) values (10, 'Rosa Díaz', '+573001110000')");
        sql("insert into persona_mayor (id_usuario) values (10)");
        sql("insert into usuario (id_usuario, nombre_usuario, celular) values (11, 'Luis Gómez', '+573001110001')");
        sql("insert into persona_mayor (id_usuario) values (11)");
        sql("insert into usuario (id_usuario, nombre_usuario, celular) values (20, 'Carlos Díaz', '+573002220000')");
        sql("insert into acompanante (id_usuario) values (20)");

        mockMvc = MockMvcBuilders.standaloneSetup(
                new PersonaMayorAcompananteController(relacionRepository, usuarioLookupRepository, acompananteLookupRepository),
                new EmergenciaController(relacionRepository, organizacionRepository, usuarioLookupRepository, mensajeriaClient,
                        emergenciaRepository)
        ).build();
    }

    private void sql(String sentencia) {
        entityManager.getEntityManager().createNativeQuery(sentencia).executeUpdate();
    }

    private Object consultar(String sentencia) {
        return entityManager.getEntityManager().createNativeQuery(sentencia).getSingleResult();
    }

    /** acompanante-service acepta la solicitud actualizando la misma tabla. */
    private void elAcompananteAcepta(int idPersonaMayor) {
        sql("update persona_mayor_acompanante set estado = 'ACEPTADA'"
                + " where id_persona_mayor = " + idPersonaMayor + " and id_acompanante = 20");
        entityManager.clear();
    }

    private void agregarContacto(int idPersonaMayor, String parentesco) throws Exception {
        mockMvc.perform(post("/api/persona-mayor/acompanantes")
                        .header("X-User-Id", idPersonaMayor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"celular\": \"+573002220000\", \"relacion\": \"" + parentesco + "\"}"))
                .andExpect(status().isOk());
        entityManager.clear();
    }

    @Test
    void unContactoAgregadoYAceptadoQuedaGuardadoYRecibeLaAlertaDeEmergencia() throws Exception {
        agregarContacto(10, "Hijo");

        // Queda guardada la solicitud pendiente y el parentesco.
        assertEquals("PENDIENTE", consultar("select estado from persona_mayor_acompanante"
                + " where id_persona_mayor = 10 and id_acompanante = 20"));
        assertEquals("Hijo", consultar("select relacion from acompanante where id_usuario = 20"));

        // Mientras no acepte, no es su contacto ni recibe alertas.
        mockMvc.perform(get("/api/persona-mayor/acompanantes").header("X-User-Id", 10))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(post("/api/persona-mayor/emergencia").header("X-User-Id", 10))
                .andExpect(status().isBadRequest());
        verify(mensajeriaClient, never()).enviarMensaje(anyString(), anyString(), anyString());

        elAcompananteAcepta(10);

        mockMvc.perform(get("/api/persona-mayor/acompanantes").header("X-User-Id", 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Carlos Díaz"))
                .andExpect(jsonPath("$[0].celular").value("+573002220000"))
                .andExpect(jsonPath("$[0].relacion").value("Hijo"));

        when(mensajeriaClient.enviarMensaje(anyString(), anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/api/persona-mayor/emergencia").header("X-User-Id", 10))
                .andExpect(status().isOk())
                .andExpect(content().string("Alerta de emergencia enviada a 1 acompañante(s) y 0 organización(es)"));

        verify(mensajeriaClient).enviarMensaje(eq("+573002220000"), contains("Rosa Díaz"), eq("EMERGENCIA"));
    }

    @Test
    void alQuitarElContactoDejaDeRecibirLasAlertas() throws Exception {
        agregarContacto(10, "Hijo");
        elAcompananteAcepta(10);

        mockMvc.perform(delete("/api/persona-mayor/acompanantes/20").header("X-User-Id", 10))
                .andExpect(status().isOk());
        entityManager.clear();

        assertEquals(0L, ((Number) consultar("select count(*) from persona_mayor_acompanante")).longValue());
        mockMvc.perform(post("/api/persona-mayor/emergencia").header("X-User-Id", 10))
                .andExpect(status().isBadRequest());
    }

    /**
     * Hallazgo del QC (CP-GCE-003): el parentesco se guarda en la tabla
     * acompanante (uno por acompañante) y no en el vínculo, así que si el
     * mismo acompañante cuida a dos personas, la segunda cambia lo que ve la
     * primera.
     */
    @Test
    @Disabled("Hallazgo CP-GCE-003: el parentesco es uno por acompañante y no uno por vínculo")
    void cadaPersonaMayorConservaElParentescoQueLeDioASuContacto() throws Exception {
        agregarContacto(10, "Hijo");
        elAcompananteAcepta(10);
        agregarContacto(11, "Sobrino");
        elAcompananteAcepta(11);

        mockMvc.perform(get("/api/persona-mayor/acompanantes").header("X-User-Id", 10))
                .andExpect(jsonPath("$[0].relacion").value("Hijo"));
    }
}
