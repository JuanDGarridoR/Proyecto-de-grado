package com.proyectogrado.salud_backend.controller;

import com.jayway.jsonpath.JsonPath;
import com.proyectogrado.salud_backend.config.ZonaHoraria;
import com.proyectogrado.salud_backend.model.Medicamento;
import com.proyectogrado.salud_backend.repository.MedicamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Medicamentos de la persona mayor (RF-34 a RF-36): registro con cálculo de
 * la próxima toma, consulta, edición y borrado, cada persona solo los suyos.
 */
class MedicamentoControllerTest {

    private static final int PERSONA_MAYOR = 10;

    private MedicamentoRepository medicamentoRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        medicamentoRepository = mock(MedicamentoRepository.class);
        when(medicamentoRepository.save(any(Medicamento.class))).thenAnswer(inv -> {
            Medicamento medicamento = inv.getArgument(0);
            if (medicamento.getIdMedicamento() == null) {
                medicamento.setIdMedicamento(1);
            }
            return medicamento;
        });

        mockMvc = MockMvcBuilders.standaloneSetup(new MedicamentoController(medicamentoRepository)).build();
    }

    private Medicamento medicamentoDe(int idPersonaMayor) {
        Medicamento medicamento = new Medicamento();
        medicamento.setIdMedicamento(1);
        medicamento.setIdPersonaMayor(idPersonaMayor);
        medicamento.setNombre("Losartán");
        medicamento.setDosis("50mg");
        medicamento.setIntervaloHoras(12);
        medicamento.setHora(LocalTime.of(8, 0));
        medicamento.setActivo(true);
        return medicamento;
    }

    private String formulario(String nombre, int intervalo, String hora, LocalDate fechaInicio) {
        return """
                {"nombre": "%s", "dosis": "50mg", "frecuencia": "Después del desayuno",
                 "intervaloHoras": %d, "hora": "%s", "fechaInicio": "%s"}
                """.formatted(nombre, intervalo, hora, fechaInicio);
    }

    @Test
    void registrarUnMedicamentoCalculaSuPrimeraToma() throws Exception {
        LocalDate manana = ZonaHoraria.hoy().plusDays(1);

        mockMvc.perform(post("/api/persona-mayor/medicamentos")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formulario("Losartán", 12, "08:00", manana)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Losartán"))
                .andExpect(jsonPath("$.hora").value("08:00"))
                .andExpect(jsonPath("$.proximaToma").value(manana + "T08:00"));

        ArgumentCaptor<Medicamento> guardado = ArgumentCaptor.forClass(Medicamento.class);
        verify(medicamentoRepository).save(guardado.capture());
        assertEquals(PERSONA_MAYOR, guardado.getValue().getIdPersonaMayor());
        assertNull(guardado.getValue().getUltimoRecordatorioEnviado());
    }

    @Test
    void siLaHoraDeInicioYaPasoLaProximaTomaEsLaSiguientePendiente() throws Exception {
        LocalDate haceTresDias = ZonaHoraria.hoy().minusDays(3);

        MvcResult resultado = mockMvc.perform(post("/api/persona-mayor/medicamentos")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formulario("Metformina", 8, "06:00", haceTresDias)))
                .andExpect(status().isCreated())
                .andReturn();

        String texto = JsonPath.read(resultado.getResponse().getContentAsString(), "$.proximaToma");
        LocalDateTime proximaToma = LocalDateTime.parse(texto);
        LocalDateTime ahora = ZonaHoraria.ahora();

        // Queda en el futuro, a menos de un intervalo, y en el horario 6:00, 14:00 o 22:00.
        assertTrue(proximaToma.isAfter(ahora), texto);
        assertTrue(proximaToma.isBefore(ahora.plusHours(8).plusMinutes(1)), texto);
        assertEquals(6, proximaToma.getHour() % 8, texto);
    }

    @Test
    void laPersonaMayorSoloVeSusMedicamentos() throws Exception {
        when(medicamentoRepository.findByIdPersonaMayor(PERSONA_MAYOR)).thenReturn(List.of(medicamentoDe(PERSONA_MAYOR)));

        mockMvc.perform(get("/api/persona-mayor/medicamentos").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Losartán"))
                .andExpect(jsonPath("$[0].dosis").value("50mg"));

        verify(medicamentoRepository).findByIdPersonaMayor(PERSONA_MAYOR);
    }

    @Test
    void editarUnMedicamentoPropioGuardaLosCambios() throws Exception {
        Medicamento medicamento = medicamentoDe(PERSONA_MAYOR);
        when(medicamentoRepository.findById(1)).thenReturn(Optional.of(medicamento));

        mockMvc.perform(put("/api/persona-mayor/medicamentos/1")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formulario("Losartán", 24, "09:30", ZonaHoraria.hoy().plusDays(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervaloHoras").value(24))
                .andExpect(jsonPath("$.hora").value("09:30"));

        assertEquals(24, medicamento.getIntervaloHoras());
        verify(medicamentoRepository).save(medicamento);
    }

    @Test
    void nadiePuedeEditarNiBorrarLosMedicamentosDeOtraPersona() throws Exception {
        when(medicamentoRepository.findById(1)).thenReturn(Optional.of(medicamentoDe(99)));

        mockMvc.perform(put("/api/persona-mayor/medicamentos/1")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formulario("Otro", 8, "08:00", ZonaHoraria.hoy())))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/persona-mayor/medicamentos/1").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isForbidden());

        verify(medicamentoRepository, never()).save(any());
        verify(medicamentoRepository, never()).delete(any());
    }

    @Test
    void borrarUnMedicamentoPropioLoElimina() throws Exception {
        Medicamento medicamento = medicamentoDe(PERSONA_MAYOR);
        when(medicamentoRepository.findById(1)).thenReturn(Optional.of(medicamento));

        mockMvc.perform(delete("/api/persona-mayor/medicamentos/1").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isNoContent());

        verify(medicamentoRepository).delete(medicamento);
    }

    /**
     * Hallazgo del QC (CP-GMI-002): el controlador no valida los campos
     * obligatorios y guarda un medicamento sin nombre.
     */
    @Test
    @Disabled("Hallazgo CP-GMI-002: se registra un medicamento sin nombre")
    void registrarUnMedicamentoSinNombreSeRechaza() throws Exception {
        mockMvc.perform(post("/api/persona-mayor/medicamentos")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formulario("", 8, "08:00", ZonaHoraria.hoy())))
                .andExpect(status().isBadRequest());

        verify(medicamentoRepository, never()).save(any());
    }

    /** Hallazgo del QC (CP-GMI-006): la edición tampoco valida los datos. */
    @Test
    @Disabled("Hallazgo CP-GMI-006: se guarda un medicamento con intervalo negativo y sin nombre")
    void editarConDatosInvalidosSeRechaza() throws Exception {
        when(medicamentoRepository.findById(1)).thenReturn(Optional.of(medicamentoDe(PERSONA_MAYOR)));

        mockMvc.perform(put("/api/persona-mayor/medicamentos/1")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formulario(" ", -8, "08:00", ZonaHoraria.hoy())))
                .andExpect(status().isBadRequest());

        verify(medicamentoRepository, never()).save(any());
    }
}
