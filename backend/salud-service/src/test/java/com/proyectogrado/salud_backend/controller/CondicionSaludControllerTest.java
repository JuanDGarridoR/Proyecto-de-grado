package com.proyectogrado.salud_backend.controller;

import com.proyectogrado.salud_backend.model.CondicionSalud;
import com.proyectogrado.salud_backend.model.PersonaMayorCondicionSalud;
import com.proyectogrado.salud_backend.model.SeveridadCondicion;
import com.proyectogrado.salud_backend.model.TipoCondicionSalud;
import com.proyectogrado.salud_backend.repository.CondicionSaludRepository;
import com.proyectogrado.salud_backend.repository.PersonaMayorCondicionSaludRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * Datos de salud de la persona mayor: registro de enfermedades, alergias y
 * discapacidades del catálogo, edición, borrado y las reglas de "Otra" y
 * de no repetir una misma condición.
 */
class CondicionSaludControllerTest {

    private static final int PERSONA_MAYOR = 10;

    private CondicionSaludRepository condicionSaludRepository;
    private PersonaMayorCondicionSaludRepository personaMayorCondicionSaludRepository;
    private MockMvc mockMvc;

    private CondicionSalud hipertension;
    private CondicionSalud otraAlergia;

    @BeforeEach
    void setUp() {
        condicionSaludRepository = mock(CondicionSaludRepository.class);
        personaMayorCondicionSaludRepository = mock(PersonaMayorCondicionSaludRepository.class);

        hipertension = condicion(1, TipoCondicionSalud.ENFERMEDAD, "Hipertensión arterial", "Cardiovascular", false);
        otraAlergia = condicion(2, TipoCondicionSalud.ALERGIA, "Otra", "Otra", true);
        when(condicionSaludRepository.findById(1)).thenReturn(Optional.of(hipertension));
        when(condicionSaludRepository.findById(2)).thenReturn(Optional.of(otraAlergia));

        when(personaMayorCondicionSaludRepository.save(any(PersonaMayorCondicionSalud.class))).thenAnswer(inv -> {
            PersonaMayorCondicionSalud registro = inv.getArgument(0);
            if (registro.getIdPersonaMayorCondicionSalud() == null) {
                registro.setIdPersonaMayorCondicionSalud(100);
            }
            return registro;
        });

        mockMvc = MockMvcBuilders.standaloneSetup(
                new CondicionSaludController(condicionSaludRepository, personaMayorCondicionSaludRepository)
        ).build();
    }

    private CondicionSalud condicion(int id, TipoCondicionSalud tipo, String nombre, String categoria, boolean esOtra) {
        CondicionSalud condicion = new CondicionSalud(tipo, nombre, categoria, esOtra);
        condicion.setIdCondicionSalud(id);
        return condicion;
    }

    private PersonaMayorCondicionSalud registroDe(int idPersonaMayor) {
        PersonaMayorCondicionSalud registro = new PersonaMayorCondicionSalud();
        registro.setIdPersonaMayorCondicionSalud(100);
        registro.setIdPersonaMayor(idPersonaMayor);
        registro.setCondicion(hipertension);
        registro.setSeveridad(SeveridadCondicion.LEVE);
        return registro;
    }

    @Test
    void elCatalogoTraeTipoCategoriaYSiEsOtra() throws Exception {
        when(condicionSaludRepository.findAllByOrderByTipoAscCategoriaAscNombreAsc())
                .thenReturn(List.of(hipertension, otraAlergia));

        mockMvc.perform(get("/api/persona-mayor/condiciones-salud/catalogo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tipo").value("ENFERMEDAD"))
                .andExpect(jsonPath("$[0].categoria").value("Cardiovascular"))
                .andExpect(jsonPath("$[1].esOtra").value(true));
    }

    @Test
    void listaSoloLasDeLaPersonaAutenticada() throws Exception {
        when(personaMayorCondicionSaludRepository.findByIdPersonaMayor(PERSONA_MAYOR))
                .thenReturn(List.of(registroDe(PERSONA_MAYOR)));

        mockMvc.perform(get("/api/persona-mayor/condiciones-salud").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Hipertensión arterial"))
                .andExpect(jsonPath("$[0].severidad").value("LEVE"));
    }

    @Test
    void registraUnaCondicionConSeveridadYDetalle() throws Exception {
        mockMvc.perform(post("/api/persona-mayor/condiciones-salud")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idCondicionSalud": 1, "severidad": "moderada", "detalle": "  Desde 2015  "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("ENFERMEDAD"))
                .andExpect(jsonPath("$.severidad").value("MODERADA"))
                .andExpect(jsonPath("$.detalle").value("Desde 2015"));

        ArgumentCaptor<PersonaMayorCondicionSalud> guardado = ArgumentCaptor.forClass(PersonaMayorCondicionSalud.class);
        verify(personaMayorCondicionSaludRepository).save(guardado.capture());
        assertEquals(PERSONA_MAYOR, guardado.getValue().getIdPersonaMayor());
    }

    @Test
    void laSeveridadEsOpcional() throws Exception {
        mockMvc.perform(post("/api/persona-mayor/condiciones-salud")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCondicionSalud\": 1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.severidad").doesNotExist());
    }

    @Test
    void noSeRegistraDosVecesLaMismaCondicion() throws Exception {
        when(personaMayorCondicionSaludRepository
                .existsByIdPersonaMayorAndCondicion_IdCondicionSalud(PERSONA_MAYOR, 1)).thenReturn(true);

        mockMvc.perform(post("/api/persona-mayor/condiciones-salud")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCondicionSalud\": 1}"))
                .andExpect(status().isConflict());

        verify(personaMayorCondicionSaludRepository, never()).save(any());
    }

    @Test
    void laOpcionOtraExigeElDetalle() throws Exception {
        mockMvc.perform(post("/api/persona-mayor/condiciones-salud")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCondicionSalud\": 2, \"detalle\": \"   \"}"))
                .andExpect(status().isBadRequest());

        verify(personaMayorCondicionSaludRepository, never()).save(any());
    }

    @Test
    void laOpcionOtraSePuedeRegistrarVariasVeces() throws Exception {
        when(personaMayorCondicionSaludRepository
                .existsByIdPersonaMayorAndCondicion_IdCondicionSalud(PERSONA_MAYOR, 2)).thenReturn(true);

        mockMvc.perform(post("/api/persona-mayor/condiciones-salud")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCondicionSalud\": 2, \"detalle\": \"Fresas\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.detalle").value("Fresas"));
    }

    @Test
    void rechazaUnaSeveridadQueNoExiste() throws Exception {
        mockMvc.perform(post("/api/persona-mayor/condiciones-salud")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCondicionSalud\": 1, \"severidad\": \"GRAVISIMA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rechazaUnaCondicionQueNoEstaEnElCatalogo() throws Exception {
        when(condicionSaludRepository.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/persona-mayor/condiciones-salud")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCondicionSalud\": 99}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editaLaSeveridadYPuedeQuitarla() throws Exception {
        PersonaMayorCondicionSalud registro = registroDe(PERSONA_MAYOR);
        when(personaMayorCondicionSaludRepository.findById(100)).thenReturn(Optional.of(registro));

        mockMvc.perform(put("/api/persona-mayor/condiciones-salud/100")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"severidad\": \"\", \"detalle\": \"Controlada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detalle").value("Controlada"));

        assertNull(registro.getSeveridad());
    }

    @Test
    void nadieEditaNiBorraLosDatosDeOtraPersona() throws Exception {
        when(personaMayorCondicionSaludRepository.findById(100)).thenReturn(Optional.of(registroDe(99)));

        mockMvc.perform(put("/api/persona-mayor/condiciones-salud/100")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"severidad\": \"LEVE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/persona-mayor/condiciones-salud/100").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isForbidden());

        verify(personaMayorCondicionSaludRepository, never()).delete(any());
    }

    @Test
    void borraUnRegistroPropio() throws Exception {
        PersonaMayorCondicionSalud registro = registroDe(PERSONA_MAYOR);
        when(personaMayorCondicionSaludRepository.findById(100)).thenReturn(Optional.of(registro));

        mockMvc.perform(delete("/api/persona-mayor/condiciones-salud/100").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isNoContent());

        verify(personaMayorCondicionSaludRepository).delete(registro);
    }
}
