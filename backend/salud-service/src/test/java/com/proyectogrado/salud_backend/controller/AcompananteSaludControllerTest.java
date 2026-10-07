package com.proyectogrado.salud_backend.controller;

import com.proyectogrado.salud_backend.model.CitaMedica;
import com.proyectogrado.salud_backend.model.Medicamento;
import com.proyectogrado.salud_backend.repository.CitaMedicaRepository;
import com.proyectogrado.salud_backend.repository.CondicionSaludRepository;
import com.proyectogrado.salud_backend.repository.MedicamentoRepository;
import com.proyectogrado.salud_backend.repository.PersonaMayorCondicionSaludRepository;
import com.proyectogrado.salud_backend.repository.RelacionAcompananteLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El acompañante gestiona los medicamentos, las citas médicas y los datos
 * de salud de las personas mayores que acompaña, y solo de ellas.
 */
class AcompananteSaludControllerTest {

    private static final int ACOMPANANTE = 20;
    private static final int PERSONA_MAYOR = 10;
    private static final int OTRA_PERSONA_MAYOR = 11;

    private MedicamentoRepository medicamentoRepository;
    private CitaMedicaRepository citaMedicaRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RelacionAcompananteLookupRepository relaciones = mock(RelacionAcompananteLookupRepository.class);
        when(relaciones.existsById_IdPersonaMayorAndId_IdAcompananteAndEstado(PERSONA_MAYOR, ACOMPANANTE, "ACEPTADA"))
                .thenReturn(true);

        medicamentoRepository = mock(MedicamentoRepository.class);
        when(medicamentoRepository.save(any(Medicamento.class))).thenAnswer(inv -> {
            Medicamento medicamento = inv.getArgument(0);
            medicamento.setIdMedicamento(1);
            return medicamento;
        });

        citaMedicaRepository = mock(CitaMedicaRepository.class);
        when(citaMedicaRepository.save(any(CitaMedica.class))).thenAnswer(inv -> inv.getArgument(0));

        AcompananteSaludController controller = new AcompananteSaludController(
                relaciones,
                new MedicamentoController(medicamentoRepository),
                new CitaMedicaController(citaMedicaRepository),
                new CondicionSaludController(mock(CondicionSaludRepository.class),
                        mock(PersonaMayorCondicionSaludRepository.class))
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void registraUnMedicamentoParaLaPersonaQueAcompana() throws Exception {
        mockMvc.perform(post("/api/acompanante/personas-mayores/{id}/medicamentos", PERSONA_MAYOR)
                        .header("X-User-Id", ACOMPANANTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "Losartán", "dosis": "50mg", "intervaloHoras": 12, "hora": "08:00"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Losartán"));

        // Queda a nombre de la persona mayor, no del acompañante.
        ArgumentCaptor<Medicamento> guardado = ArgumentCaptor.forClass(Medicamento.class);
        verify(medicamentoRepository).save(guardado.capture());
        assertEquals(PERSONA_MAYOR, guardado.getValue().getIdPersonaMayor());
    }

    @Test
    void registraUnaCitaParaLaPersonaQueAcompana() throws Exception {
        String manana = LocalDate.now().plusDays(1).toString();

        mockMvc.perform(post("/api/acompanante/personas-mayores/{id}/citas-medicas", PERSONA_MAYOR)
                        .header("X-User-Id", ACOMPANANTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo": "Control de cardiología", "lugar": "USS Usme", "fecha": "%s", "hora": "10:00"}
                                """.formatted(manana)))
                .andExpect(status().isCreated());

        ArgumentCaptor<CitaMedica> guardada = ArgumentCaptor.forClass(CitaMedica.class);
        verify(citaMedicaRepository).save(guardada.capture());
        assertEquals(PERSONA_MAYOR, guardada.getValue().getIdPersonaMayor());
    }

    @Test
    void listaLosMedicamentosDeLaPersonaQueAcompana() throws Exception {
        when(medicamentoRepository.findByIdPersonaMayor(PERSONA_MAYOR)).thenReturn(List.of());

        mockMvc.perform(get("/api/acompanante/personas-mayores/{id}/medicamentos", PERSONA_MAYOR)
                        .header("X-User-Id", ACOMPANANTE))
                .andExpect(status().isOk());
    }

    @Test
    void noGestionaNadaDeUnaPersonaQueNoAcompana() throws Exception {
        mockMvc.perform(post("/api/acompanante/personas-mayores/{id}/medicamentos", OTRA_PERSONA_MAYOR)
                        .header("X-User-Id", ACOMPANANTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Losartán\", \"intervaloHoras\": 12}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/acompanante/personas-mayores/{id}/citas-medicas", OTRA_PERSONA_MAYOR)
                        .header("X-User-Id", ACOMPANANTE))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/acompanante/personas-mayores/{id}/condiciones-salud", OTRA_PERSONA_MAYOR)
                        .header("X-User-Id", ACOMPANANTE))
                .andExpect(status().isForbidden());

        verify(medicamentoRepository, never()).save(any());
    }

    @Test
    void noBorraUnMedicamentoDeOtraPersonaAunqueAcompaneAAlguien() throws Exception {
        Medicamento ajeno = new Medicamento();
        ajeno.setIdMedicamento(5);
        ajeno.setIdPersonaMayor(OTRA_PERSONA_MAYOR);
        when(medicamentoRepository.findById(5)).thenReturn(Optional.of(ajeno));

        mockMvc.perform(delete("/api/acompanante/personas-mayores/{id}/medicamentos/5", PERSONA_MAYOR)
                        .header("X-User-Id", ACOMPANANTE))
                .andExpect(status().isForbidden());

        verify(medicamentoRepository, never()).delete(any());
    }
}
