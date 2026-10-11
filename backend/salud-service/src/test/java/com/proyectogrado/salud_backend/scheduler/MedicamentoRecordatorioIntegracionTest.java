package com.proyectogrado.salud_backend.scheduler;

import com.proyectogrado.salud_backend.client.MensajeriaClient;
import com.proyectogrado.salud_backend.config.ZonaHoraria;
import com.proyectogrado.salud_backend.controller.MedicamentoController;
import com.proyectogrado.salud_backend.model.Medicamento;
import com.proyectogrado.salud_backend.repository.MedicamentoRepository;
import com.proyectogrado.salud_backend.repository.RelacionAcompananteLookupRepository;
import com.proyectogrado.salud_backend.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Del registro del medicamento a sus recordatorios, sobre una base H2 en
 * memoria (RF-54): la persona mayor registra el medicamento y el scheduler,
 * con las consultas reales, avisa 15 minutos antes y a la hora exacta a ella
 * y a su acompañante, sin repetir avisos. Cada consulta corre en su propia
 * transacción, como en producción; mensajeria-service está simulado.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MedicamentoRecordatorioIntegracionTest {

    @Autowired
    private MedicamentoRepository medicamentoRepository;

    @Autowired
    private RelacionAcompananteLookupRepository relacionRepository;

    @Autowired
    private UsuarioLookupRepository usuarioLookupRepository;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private final List<String> enviados = new ArrayList<>();
    private MedicamentoRecordatorioScheduler scheduler;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("insert into usuario (id_usuario, nombre_usuario, celular) values (10, 'Rosa Díaz', '+573001110000')");
        jdbc.update("insert into usuario (id_usuario, nombre_usuario, celular) values (20, 'Carlos Díaz', '+573002220000')");
        jdbc.update("insert into persona_mayor_acompanante (id_persona_mayor, id_acompanante, estado)"
                + " values (10, 20, 'ACEPTADA')");

        MensajeriaClient mensajeriaClient = mock(MensajeriaClient.class);
        when(mensajeriaClient.enviarMensaje(anyString(), anyString(), eq("MEDICAMENTO"))).thenAnswer(inv -> {
            enviados.add(inv.getArgument(0) + " | " + inv.getArgument(1));
            return true;
        });

        scheduler = new MedicamentoRecordatorioScheduler(
                medicamentoRepository, relacionRepository, usuarioLookupRepository, mensajeriaClient);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("delete from medicamento");
        jdbc.update("delete from persona_mayor_acompanante");
        jdbc.update("delete from usuario");
    }

    @Test
    void unMedicamentoRegistradoGeneraSusRecordatoriosSinRepetirlos() throws Exception {
        LocalDate manana = ZonaHoraria.hoy().plusDays(1);
        LocalDateTime toma = manana.atTime(8, 0);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new MedicamentoController(medicamentoRepository)).build();
        mockMvc.perform(post("/api/persona-mayor/medicamentos")
                        .header("X-User-Id", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "Losartán", "dosis": "50mg", "intervaloHoras": 12,
                                 "hora": "08:00", "fechaInicio": "%s"}
                                """.formatted(manana)))
                .andExpect(status().isCreated());

        // 16 minutos antes todavía no toca ningún aviso.
        scheduler.revisarRecordatorios(toma.minusMinutes(16));
        assertEquals(0, enviados.size(), enviados.toString());

        // 15 minutos antes: aviso previo a la persona mayor y a su acompañante.
        scheduler.revisarRecordatorios(toma.minusMinutes(15));
        assertEquals(2, enviados.size(), enviados.toString());
        assertTrue(enviados.get(0).startsWith("+573001110000 | En 15 minutos"), enviados.get(0));
        assertTrue(enviados.get(0).endsWith("te toca tomar Losartán (50mg)."), enviados.get(0));
        assertTrue(enviados.get(1).startsWith("+573002220000 | En 15 minutos"), enviados.get(1));
        assertTrue(enviados.get(1).endsWith("Rosa Díaz debe tomar Losartán (50mg)."), enviados.get(1));

        // El minuto siguiente no se repite.
        scheduler.revisarRecordatorios(toma.minusMinutes(14));
        assertEquals(2, enviados.size(), enviados.toString());

        // A la hora exacta se avisa otra vez y la próxima toma avanza 12 horas en la base.
        scheduler.revisarRecordatorios(toma);
        assertEquals(4, enviados.size(), enviados.toString());
        assertEquals("+573001110000 | Es hora de tomar Losartán (50mg).", enviados.get(2));
        assertEquals("+573002220000 | Es hora de que Rosa Díaz tome Losartán (50mg).", enviados.get(3));

        Medicamento enLaBase = medicamentoRepository.findByIdPersonaMayor(10).get(0);
        assertEquals(toma, enLaBase.getUltimaToma());
        assertEquals(toma.plusHours(12), enLaBase.getProximaToma());

        // Si el scheduler vuelve a correr en el mismo minuto, no se repite.
        scheduler.revisarRecordatorios(toma.plusSeconds(20));
        assertEquals(4, enviados.size(), enviados.toString());
    }
}
