package com.proyectogrado.acompanante_service.controller;

import com.proyectogrado.acompanante_service.model.AcompananteInfoLookup;
import com.proyectogrado.acompanante_service.model.CitaMedicaLookup;
import com.proyectogrado.acompanante_service.model.MedicamentoLookup;
import com.proyectogrado.acompanante_service.model.PersonaMayorAcompanante;
import com.proyectogrado.acompanante_service.model.SignoVitalLookup;
import com.proyectogrado.acompanante_service.model.UsuarioLookup;
import com.proyectogrado.acompanante_service.repository.AcompananteInfoLookupRepository;
import com.proyectogrado.acompanante_service.repository.CitaMedicaLookupRepository;
import com.proyectogrado.acompanante_service.repository.MedicamentoLookupRepository;
import com.proyectogrado.acompanante_service.repository.PersonaMayorAcompananteRepository;
import com.proyectogrado.acompanante_service.repository.SignoVitalLookupRepository;
import com.proyectogrado.acompanante_service.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seguimiento del acompañante (RF-13 y RF-57): con un vínculo aceptado ve
 * los signos vitales, medicamentos, citas y demás contactos de la persona
 * mayor, que vienen de varios módulos; sin ese vínculo no ve nada.
 */
class AcompananteSeguimientoControllerTest {

    private static final int ACOMPANANTE = 20;
    private static final int PERSONA_MAYOR = 10;

    private PersonaMayorAcompananteRepository relacionRepository;
    private UsuarioLookupRepository usuarioLookupRepository;
    private AcompananteInfoLookupRepository acompananteInfoLookupRepository;
    private MedicamentoLookupRepository medicamentoLookupRepository;
    private SignoVitalLookupRepository signoVitalLookupRepository;
    private CitaMedicaLookupRepository citaMedicaLookupRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        relacionRepository = mock(PersonaMayorAcompananteRepository.class);
        usuarioLookupRepository = mock(UsuarioLookupRepository.class);
        acompananteInfoLookupRepository = mock(AcompananteInfoLookupRepository.class);
        medicamentoLookupRepository = mock(MedicamentoLookupRepository.class);
        signoVitalLookupRepository = mock(SignoVitalLookupRepository.class);
        citaMedicaLookupRepository = mock(CitaMedicaLookupRepository.class);

        PersonaMayorAcompanante vinculo = new PersonaMayorAcompanante(PERSONA_MAYOR, ACOMPANANTE);
        vinculo.setEstado("ACEPTADA");
        when(relacionRepository.findById_IdAcompananteAndEstado(ACOMPANANTE, "ACEPTADA")).thenReturn(List.of(vinculo));

        mockMvc = MockMvcBuilders.standaloneSetup(new AcompananteSeguimientoController(
                relacionRepository, usuarioLookupRepository, acompananteInfoLookupRepository,
                medicamentoLookupRepository, signoVitalLookupRepository, citaMedicaLookupRepository)).build();
    }

    @Test
    void conVinculoAceptadoVeLosUltimosSignosVitales() throws Exception {
        SignoVitalLookup medicion = mock(SignoVitalLookup.class);
        when(medicion.getIdSignoVital()).thenReturn(3);
        when(medicion.getFechaHora()).thenReturn(LocalDateTime.of(2026, 10, 1, 8, 30));
        when(medicion.getPresionSistolica()).thenReturn(150);
        when(medicion.getPresionDiastolica()).thenReturn(95);
        when(medicion.getSaturacionOxigeno()).thenReturn(93);
        when(signoVitalLookupRepository.findTop10ByIdPersonaMayorOrderByFechaHoraDesc(PERSONA_MAYOR))
                .thenReturn(List.of(medicion));

        mockMvc.perform(get("/api/acompanante/seguimiento/" + PERSONA_MAYOR + "/signos-vitales")
                        .header("X-User-Id", ACOMPANANTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fechaHora").value("2026-10-01T08:30"))
                .andExpect(jsonPath("$[0].presionSistolica").value(150))
                .andExpect(jsonPath("$[0].saturacionOxigeno").value(93));
    }

    @Test
    void conVinculoAceptadoVeLosMedicamentosConSuProximaToma() throws Exception {
        MedicamentoLookup medicamento = mock(MedicamentoLookup.class);
        when(medicamento.getNombre()).thenReturn("Losartán");
        when(medicamento.getDosis()).thenReturn("50mg");
        when(medicamento.getHora()).thenReturn(LocalTime.of(8, 0));
        when(medicamento.getProximaToma()).thenReturn(LocalDateTime.of(2026, 10, 5, 20, 0));
        when(medicamento.getActivo()).thenReturn(true);
        when(medicamentoLookupRepository.findByIdPersonaMayor(PERSONA_MAYOR)).thenReturn(List.of(medicamento));

        mockMvc.perform(get("/api/acompanante/seguimiento/" + PERSONA_MAYOR + "/medicamentos")
                        .header("X-User-Id", ACOMPANANTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Losartán"))
                .andExpect(jsonPath("$[0].hora").value("08:00"))
                .andExpect(jsonPath("$[0].proximaToma").value("2026-10-05T20:00"));
    }

    @Test
    void conVinculoAceptadoVeLasCitasMedicas() throws Exception {
        CitaMedicaLookup cita = mock(CitaMedicaLookup.class);
        when(cita.getTitulo()).thenReturn("Control de tensión");
        when(cita.getLugar()).thenReturn("USS Usme");
        when(cita.getFecha()).thenReturn(LocalDate.of(2026, 10, 20));
        when(cita.getHora()).thenReturn(LocalTime.of(7, 30));
        when(citaMedicaLookupRepository.findByIdPersonaMayorOrderByFechaAscHoraAsc(PERSONA_MAYOR))
                .thenReturn(List.of(cita));

        mockMvc.perform(get("/api/acompanante/seguimiento/" + PERSONA_MAYOR + "/citas-medicas")
                        .header("X-User-Id", ACOMPANANTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titulo").value("Control de tensión"))
                .andExpect(jsonPath("$[0].fecha").value("2026-10-20"))
                .andExpect(jsonPath("$[0].hora").value("07:30"));
    }

    @Test
    void conVinculoAceptadoVeLosDemasContactosDeLaPersonaMayor() throws Exception {
        PersonaMayorAcompanante otroVinculo = new PersonaMayorAcompanante(PERSONA_MAYOR, 21);
        otroVinculo.setEstado("ACEPTADA");
        when(relacionRepository.findById_IdPersonaMayorAndEstado(PERSONA_MAYOR, "ACEPTADA")).thenReturn(List.of(otroVinculo));

        UsuarioLookup vecina = mock(UsuarioLookup.class);
        when(vecina.getNombreUsuario()).thenReturn("Marta Gil");
        when(vecina.getCelular()).thenReturn("+573002221111");
        when(usuarioLookupRepository.findById(21)).thenReturn(Optional.of(vecina));
        AcompananteInfoLookup info = mock(AcompananteInfoLookup.class);
        when(info.getRelacion()).thenReturn("Vecina");
        when(acompananteInfoLookupRepository.findById(21)).thenReturn(Optional.of(info));

        mockMvc.perform(get("/api/acompanante/seguimiento/" + PERSONA_MAYOR + "/contactos")
                        .header("X-User-Id", ACOMPANANTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Marta Gil"))
                .andExpect(jsonPath("$[0].relacion").value("Vecina"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"signos-vitales", "medicamentos", "citas-medicas", "contactos"})
    void sinVinculoAceptadoNoVeNadaDeLaPersonaMayor(String seccion) throws Exception {
        // La persona 99 no está vinculada a este acompañante.
        mockMvc.perform(get("/api/acompanante/seguimiento/99/" + seccion).header("X-User-Id", ACOMPANANTE))
                .andExpect(status().isForbidden())
                .andExpect(content().string("No tienes autorización para consultar a esta persona mayor"));

        verify(signoVitalLookupRepository, never()).findTop10ByIdPersonaMayorOrderByFechaHoraDesc(anyInt());
        verify(medicamentoLookupRepository, never()).findByIdPersonaMayor(anyInt());
        verify(citaMedicaLookupRepository, never()).findByIdPersonaMayorOrderByFechaAscHoraAsc(anyInt());
    }
}
