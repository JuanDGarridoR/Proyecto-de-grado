package com.proyectogrado.actividad_service.controller;

import com.proyectogrado.actividad_service.config.ZonaHoraria;
import com.proyectogrado.actividad_service.model.Actividad;
import com.proyectogrado.actividad_service.model.Participacion;
import com.proyectogrado.actividad_service.model.PersonaMayorAcompananteId;
import com.proyectogrado.actividad_service.model.PersonaMayorAcompananteLookup;
import com.proyectogrado.actividad_service.model.PersonaMayorOrganizacionId;
import com.proyectogrado.actividad_service.model.PersonaMayorOrganizacionLookup;
import com.proyectogrado.actividad_service.model.UsuarioLookup;
import com.proyectogrado.actividad_service.repository.AcompananteLookupRepository;
import com.proyectogrado.actividad_service.repository.ActividadRepository;
import com.proyectogrado.actividad_service.repository.OrganizacionLookupRepository;
import com.proyectogrado.actividad_service.repository.ParticipacionRepository;
import com.proyectogrado.actividad_service.repository.PersonaMayorAcompananteLookupRepository;
import com.proyectogrado.actividad_service.repository.PersonaMayorLookupRepository;
import com.proyectogrado.actividad_service.repository.PersonaMayorOrganizacionLookupRepository;
import com.proyectogrado.actividad_service.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Actividades y participación (RF-28 a RF-31 y RF-03): la organización crea,
 * edita y borra sus actividades y registra la asistencia; la persona mayor se
 * inscribe en las de sus organizaciones; cada rol ve solo lo que le
 * corresponde; voluntarios y personas mayores proponen actividades.
 */
class ActividadControllerTest {

    private static final int CUENTA_ORGANIZACION = 30;
    private static final int ORGANIZACION = 5;
    private static final int PERSONA_MAYOR = 10;
    private static final int ACOMPANANTE = 20;
    private static final int VOLUNTARIO = 40;

    private ActividadRepository actividadRepository;
    private ParticipacionRepository participacionRepository;
    private UsuarioLookupRepository usuarioLookupRepository;
    private PersonaMayorLookupRepository personaMayorLookupRepository;
    private AcompananteLookupRepository acompananteLookupRepository;
    private PersonaMayorOrganizacionLookupRepository personaMayorOrganizacionRepository;
    private PersonaMayorAcompananteLookupRepository personaMayorAcompananteRepository;
    private MockMvc mockMvc;

    private final LocalDate manana = ZonaHoraria.ahora().toLocalDate().plusDays(1);

    @BeforeEach
    void setUp() {
        actividadRepository = mock(ActividadRepository.class);
        participacionRepository = mock(ParticipacionRepository.class);
        usuarioLookupRepository = mock(UsuarioLookupRepository.class);
        personaMayorLookupRepository = mock(PersonaMayorLookupRepository.class);
        acompananteLookupRepository = mock(AcompananteLookupRepository.class);
        personaMayorOrganizacionRepository = mock(PersonaMayorOrganizacionLookupRepository.class);
        personaMayorAcompananteRepository = mock(PersonaMayorAcompananteLookupRepository.class);

        UsuarioLookup cuentaOrganizacion = mock(UsuarioLookup.class);
        when(cuentaOrganizacion.getIdOrganizacion()).thenReturn(ORGANIZACION);
        when(usuarioLookupRepository.findById(CUENTA_ORGANIZACION)).thenReturn(Optional.of(cuentaOrganizacion));

        // Un mock devolvería 0 como organización; la persona mayor no tiene.
        UsuarioLookup personaMayor = mock(UsuarioLookup.class);
        when(personaMayor.getIdOrganizacion()).thenReturn(null);
        when(personaMayor.getNombreUsuario()).thenReturn("Rosa Díaz");
        when(personaMayor.getCelular()).thenReturn("+573001110000");
        when(usuarioLookupRepository.findById(PERSONA_MAYOR)).thenReturn(Optional.of(personaMayor));
        when(personaMayorLookupRepository.existsById(PERSONA_MAYOR)).thenReturn(true);
        when(acompananteLookupRepository.existsById(ACOMPANANTE)).thenReturn(true);
        when(usuarioLookupRepository.tieneRol(VOLUNTARIO, "VOLUNTARIO")).thenReturn(true);

        // La persona mayor tiene vínculo aceptado con la organización 5.
        PersonaMayorOrganizacionLookup vinculo = mock(PersonaMayorOrganizacionLookup.class);
        when(vinculo.getId()).thenReturn(new PersonaMayorOrganizacionId(PERSONA_MAYOR, ORGANIZACION));
        when(personaMayorOrganizacionRepository.findById_IdPersonaMayorAndEstado(PERSONA_MAYOR, "ACEPTADA"))
                .thenReturn(List.of(vinculo));

        when(actividadRepository.save(any(Actividad.class))).thenAnswer(inv -> {
            Actividad actividad = inv.getArgument(0);
            if (actividad.getIdActividad() == null) {
                actividad.setIdActividad(100);
            }
            return actividad;
        });

        mockMvc = MockMvcBuilders.standaloneSetup(new ActividadController(
                actividadRepository, participacionRepository, usuarioLookupRepository,
                personaMayorLookupRepository, acompananteLookupRepository,
                personaMayorOrganizacionRepository, personaMayorAcompananteRepository,
                mock(OrganizacionLookupRepository.class))).build();
    }

    private Actividad actividad(int id, LocalDate fecha, Integer cupos) {
        Actividad actividad = new Actividad();
        actividad.setIdActividad(id);
        actividad.setIdOrganizacion(ORGANIZACION);
        actividad.setNombre("Yoga en el parque");
        actividad.setFecha(fecha);
        actividad.setHora("09:00");
        actividad.setLugar("Parque Entrenubes");
        actividad.setCupos(cupos);
        return actividad;
    }

    private String formulario(String nombre, LocalDate fecha, Integer cupos) {
        return """
                {"nombre": "%s", "descripcion": "Estiramientos suaves", "fecha": "%s", "hora": "09:00",
                 "lugar": "Parque Entrenubes", "tipo": "Recreativa", "cupos": %s, "responsable": "Laura"}
                """.formatted(nombre, fecha, cupos);
    }

    @Test
    void laOrganizacionCreaUnaActividadConDatosValidos() throws Exception {
        mockMvc.perform(post("/api/actividades")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formulario("Yoga en el parque", manana, 20)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idActividad").value(100))
                .andExpect(jsonPath("$.idOrganizacion").value(ORGANIZACION))
                .andExpect(jsonPath("$.fecha").value(manana.toString()))
                .andExpect(jsonPath("$.cupos").value(20));
    }

    @Test
    void unaActividadSinNombreNoSeCrea() throws Exception {
        mockMvc.perform(post("/api/actividades")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formulario(" ", manana, 20)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("El nombre es obligatorio"));

        verify(actividadRepository, never()).save(any());
    }

    @Test
    void unaActividadConFechaPasadaOSinCuposNoSeCrea() throws Exception {
        mockMvc.perform(post("/api/actividades")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formulario("Yoga", manana.minusDays(3), 20)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("No se puede crear una actividad con una fecha anterior a hoy"));

        mockMvc.perform(post("/api/actividades")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formulario("Yoga", manana, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Los cupos deben ser mayores a 0"));

        verify(actividadRepository, never()).save(any());
    }

    @Test
    void soloUnaOrganizacionCreaActividades() throws Exception {
        mockMvc.perform(post("/api/actividades")
                        .header("X-User-Id", PERSONA_MAYOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formulario("Yoga", manana, 20)))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Solo una organización puede crear actividades"));

        verify(actividadRepository, never()).save(any());
    }

    @Test
    void laOrganizacionVeSusActividadesSinLasPropuestasPendientes() throws Exception {
        Actividad propuesta = actividad(2, manana, null);
        propuesta.setIdVoluntario(VOLUNTARIO);
        propuesta.setEstado(Actividad.PENDIENTE);
        when(actividadRepository.findByIdOrganizacion(ORGANIZACION))
                .thenReturn(List.of(actividad(1, manana, 20), propuesta));

        mockMvc.perform(get("/api/actividades/mias").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idActividad").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Yoga en el parque"));
    }

    @Test
    void cadaRolVeLasActividadesQueLeCorresponden() throws Exception {
        when(actividadRepository.findByIdOrganizacion(ORGANIZACION)).thenReturn(List.of(actividad(1, manana, 20)));

        // Persona mayor: las de las organizaciones con las que tiene vínculo.
        mockMvc.perform(get("/api/actividades").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Acompañante: las de las organizaciones de la persona que acompaña.
        PersonaMayorAcompananteLookup acompana = mock(PersonaMayorAcompananteLookup.class);
        when(acompana.getId()).thenReturn(new PersonaMayorAcompananteId(PERSONA_MAYOR, ACOMPANANTE));
        when(personaMayorAcompananteRepository.findById_IdAcompananteAndEstado(ACOMPANANTE, "ACEPTADA"))
                .thenReturn(List.of(acompana));
        mockMvc.perform(get("/api/actividades").header("X-User-Id", ACOMPANANTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Un usuario sin ninguno de esos roles no consulta actividades.
        mockMvc.perform(get("/api/actividades").header("X-User-Id", 77))
                .andExpect(status().isForbidden());
    }

    @Test
    void laOrganizacionEditaSuActividad() throws Exception {
        Actividad guardada = actividad(1, manana, 20);
        when(actividadRepository.findById(1)).thenReturn(Optional.of(guardada));

        mockMvc.perform(put("/api/actividades/1")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formulario("Yoga y meditación", manana, 25)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Yoga y meditación"))
                .andExpect(jsonPath("$.cupos").value(25));

        assertEquals("Yoga y meditación", guardada.getNombre());
    }

    @Test
    void editarConDatosInvalidosSeRechaza() throws Exception {
        when(actividadRepository.findById(1)).thenReturn(Optional.of(actividad(1, manana, 20)));

        mockMvc.perform(put("/api/actividades/1")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formulario("", manana, 25)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("El nombre es obligatorio"));

        mockMvc.perform(put("/api/actividades/1")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formulario("Yoga", manana, -1)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Los cupos deben ser mayores a 0"));

        verify(actividadRepository, never()).save(any());
    }

    @Test
    void unaOrganizacionNoEditaActividadesDeOtra() throws Exception {
        Actividad deOtra = actividad(1, manana, 20);
        deOtra.setIdOrganizacion(6);
        when(actividadRepository.findById(1)).thenReturn(Optional.of(deOtra));

        mockMvc.perform(put("/api/actividades/1")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formulario("Otra", manana, 10)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/actividades/1").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isForbidden());

        verify(actividadRepository, never()).deleteById(anyInt());
    }

    @Test
    void borrarUnaActividadQuitaPrimeroSusInscripciones() throws Exception {
        when(actividadRepository.findById(1)).thenReturn(Optional.of(actividad(1, manana, 20)));
        List<Participacion> inscripciones = List.of(new Participacion(PERSONA_MAYOR, 1));
        when(participacionRepository.findById_IdActividad(1)).thenReturn(inscripciones);

        mockMvc.perform(delete("/api/actividades/1").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isNoContent());

        InOrder orden = inOrder(participacionRepository, actividadRepository);
        orden.verify(participacionRepository).deleteAllInBatch(inscripciones);
        orden.verify(actividadRepository).deleteById(1);
    }

    @Test
    void laPersonaMayorSeInscribeSiHayCupo() throws Exception {
        when(actividadRepository.findById(1)).thenReturn(Optional.of(actividad(1, manana, 2)));
        when(participacionRepository.findById_IdPersonaMayorAndId_IdActividad(PERSONA_MAYOR, 1)).thenReturn(Optional.empty());
        when(participacionRepository.findById_IdActividad(1)).thenReturn(List.of(new Participacion(11, 1)));

        mockMvc.perform(post("/api/actividades/1/inscribirse").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isCreated());

        ArgumentCaptor<Participacion> inscripcion = ArgumentCaptor.forClass(Participacion.class);
        verify(participacionRepository).save(inscripcion.capture());
        assertEquals(PERSONA_MAYOR, inscripcion.getValue().getId().getIdPersonaMayor());
        assertNull(inscripcion.getValue().getAsistio());
    }

    @Test
    void sinCuposLaInscripcionSeRechaza() throws Exception {
        when(actividadRepository.findById(1)).thenReturn(Optional.of(actividad(1, manana, 1)));
        when(participacionRepository.findById_IdPersonaMayorAndId_IdActividad(PERSONA_MAYOR, 1)).thenReturn(Optional.empty());
        when(participacionRepository.findById_IdActividad(1)).thenReturn(List.of(new Participacion(11, 1)));

        mockMvc.perform(post("/api/actividades/1/inscribirse").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isConflict())
                .andExpect(content().string("La actividad ya alcanzó el límite de cupos"));

        verify(participacionRepository, never()).save(any());
    }

    @Test
    void noSePuedeInscribirDosVecesNiEnUnaActividadPasada() throws Exception {
        when(actividadRepository.findById(1)).thenReturn(Optional.of(actividad(1, manana, 20)));
        when(participacionRepository.findById_IdPersonaMayorAndId_IdActividad(PERSONA_MAYOR, 1))
                .thenReturn(Optional.of(new Participacion(PERSONA_MAYOR, 1)));
        mockMvc.perform(post("/api/actividades/1/inscribirse").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isConflict())
                .andExpect(content().string("Ya estás inscrito en esta actividad"));

        when(actividadRepository.findById(2)).thenReturn(Optional.of(actividad(2, manana.minusDays(5), 20)));
        mockMvc.perform(post("/api/actividades/2/inscribirse").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isConflict())
                .andExpect(content().string("No puedes inscribirte en una actividad que ya pasó"));

        verify(participacionRepository, never()).save(any());
    }

    @Test
    void noSePuedeInscribirEnActividadesDeUnaOrganizacionAjena() throws Exception {
        Actividad deOtra = actividad(1, manana, 20);
        deOtra.setIdOrganizacion(6);
        when(actividadRepository.findById(1)).thenReturn(Optional.of(deOtra));

        mockMvc.perform(post("/api/actividades/1/inscribirse").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isForbidden());
    }

    @Test
    void unaPropuestaPendienteNoExisteParaLaPersonaMayor() throws Exception {
        Actividad propuesta = actividad(3, manana, 20);
        propuesta.setIdVoluntario(VOLUNTARIO);
        propuesta.setEstado(Actividad.PENDIENTE);
        when(actividadRepository.findById(3)).thenReturn(Optional.of(propuesta));

        mockMvc.perform(post("/api/actividades/3/inscribirse").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isNotFound());
    }

    @Test
    void laPersonaMayorVeEnCualesActividadesEstaInscrita() throws Exception {
        when(actividadRepository.findByIdOrganizacion(ORGANIZACION))
                .thenReturn(List.of(actividad(1, manana, 20), actividad(2, manana, 20)));
        when(participacionRepository.findById_IdPersonaMayor(PERSONA_MAYOR)).thenReturn(List.of(new Participacion(PERSONA_MAYOR, 2)));

        mockMvc.perform(get("/api/actividades/disponibles").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].inscrito").value(false))
                .andExpect(jsonPath("$[1].inscrito").value(true));
    }

    @Test
    void laOrganizacionRegistraLaAsistenciaDeUnInscrito() throws Exception {
        when(actividadRepository.findById(1)).thenReturn(Optional.of(actividad(1, manana, 20)));
        Participacion inscripcion = new Participacion(PERSONA_MAYOR, 1);
        when(participacionRepository.findById_IdPersonaMayorAndId_IdActividad(PERSONA_MAYOR, 1))
                .thenReturn(Optional.of(inscripcion));

        mockMvc.perform(put("/api/actividades/1/participantes/" + PERSONA_MAYOR + "/asistencia")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"asistio\": true}"))
                .andExpect(status().isOk());

        assertTrue(inscripcion.getAsistio());
        verify(participacionRepository).save(inscripcion);
    }

    @Test
    void noSeRegistraAsistenciaDeQuienNoEstaInscrito() throws Exception {
        when(actividadRepository.findById(1)).thenReturn(Optional.of(actividad(1, manana, 20)));
        when(participacionRepository.findById_IdPersonaMayorAndId_IdActividad(PERSONA_MAYOR, 1)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/actividades/1/participantes/" + PERSONA_MAYOR + "/asistencia")
                        .header("X-User-Id", CUENTA_ORGANIZACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"asistio\": true}"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("La persona mayor no está inscrita en esta actividad"));
    }

    @Test
    void laOrganizacionConsultaLosParticipantesConSuAsistencia() throws Exception {
        when(actividadRepository.findById(1)).thenReturn(Optional.of(actividad(1, manana, 20)));
        Participacion asistio = new Participacion(PERSONA_MAYOR, 1);
        asistio.setAsistio(true);
        when(participacionRepository.findById_IdActividad(1)).thenReturn(List.of(asistio));

        mockMvc.perform(get("/api/actividades/1/participantes").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Rosa Díaz"))
                .andExpect(jsonPath("$[0].asistio").value(true));

        // Otro rol no ve la lista de participantes.
        mockMvc.perform(get("/api/actividades/1/participantes").header("X-User-Id", PERSONA_MAYOR))
                .andExpect(status().isForbidden());
    }

    @Test
    void unVoluntarioVinculadoProponeUnaActividadQueQuedaPendiente() throws Exception {
        when(usuarioLookupRepository.voluntarioVinculado(VOLUNTARIO, ORGANIZACION)).thenReturn(true);

        mockMvc.perform(post("/api/actividades/propuestas")
                        .header("X-User-Id", VOLUNTARIO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formulario("Taller de memoria", manana, 15)
                                .replace("{", "{\"idOrganizacion\": " + ORGANIZACION + ", ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.idVoluntario").value(VOLUNTARIO));
    }

    @Test
    void noSeProponeAUnaOrganizacionALaQueNoSeEstaVinculado() throws Exception {
        when(usuarioLookupRepository.voluntarioVinculado(VOLUNTARIO, ORGANIZACION)).thenReturn(false);

        mockMvc.perform(post("/api/actividades/propuestas")
                        .header("X-User-Id", VOLUNTARIO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formulario("Taller de memoria", manana, 15)
                                .replace("{", "{\"idOrganizacion\": " + ORGANIZACION + ", ")))
                .andExpect(status().isForbidden());

        verify(actividadRepository, never()).save(any());
    }

    @Test
    void alAceptarUnaPropuestaQuedaVisibleParaLasPersonasMayores() throws Exception {
        Actividad propuesta = actividad(3, manana, 15);
        propuesta.setIdPersonaMayorProponente(PERSONA_MAYOR);
        propuesta.setEstado(Actividad.PENDIENTE);
        when(actividadRepository.findById(3)).thenReturn(Optional.of(propuesta));

        mockMvc.perform(put("/api/actividades/propuestas/3/aceptar").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACEPTADA"));

        assertTrue(propuesta.esVisible());

        // Ya respondida, no se puede volver a responder.
        mockMvc.perform(put("/api/actividades/propuestas/3/rechazar").header("X-User-Id", CUENTA_ORGANIZACION))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Esta propuesta ya fue respondida"));
    }
}
