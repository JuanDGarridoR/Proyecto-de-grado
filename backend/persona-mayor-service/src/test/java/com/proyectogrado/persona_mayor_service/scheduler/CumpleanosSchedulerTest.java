package com.proyectogrado.persona_mayor_service.scheduler;

import com.proyectogrado.persona_mayor_service.client.MensajeriaClient;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorAcompanante;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorLookup;
import com.proyectogrado.persona_mayor_service.model.PersonaMayorOrganizacion;
import com.proyectogrado.persona_mayor_service.model.UsuarioLookup;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorAcompananteRepository;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorLookupRepository;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorOrganizacionRepository;
import com.proyectogrado.persona_mayor_service.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifica que el día del cumpleaños de una persona mayor VITA+ la
 * felicite y se les recuerde a su acompañante y a su organización, que
 * VITA+ también felicite a acompañantes y voluntarios en su cumpleaños, y
 * que los nacidos un 29 de febrero reciban los mensajes el 28 en años no
 * bisiestos.
 */
class CumpleanosSchedulerTest {

    private static final int ID_PERSONA = 10;
    private static final int ID_ACOMPANANTE = 20;
    private static final int ID_ORGANIZACION = 30;
    private static final int ID_USUARIO_ORGANIZACION = 31;
    private static final String NOMBRE_PERSONA = "Persona mayor de prueba";
    private static final String NOMBRE_ACOMPANANTE = "Acompañante de prueba";
    private static final String NOMBRE_ORGANIZACION = "Organización de prueba";
    private static final String CEL_PERSONA = "+570000000001";
    private static final String CEL_ACOMPANANTE = "+570000000002";
    private static final String CEL_ORGANIZACION = "+570000000003";
    private static final int ID_VOLUNTARIO = 40;
    private static final String NOMBRE_VOLUNTARIO = "Voluntario de prueba";
    private static final String CEL_VOLUNTARIO = "+570000000004";

    private record Envio(String celular, String mensaje) {
    }

    private final List<Envio> envios = new ArrayList<>();
    private LocalDate fechaNacimiento;
    private LocalDate fechaNacimientoAcompanante;
    private LocalDate fechaNacimientoVoluntario;
    private CumpleanosScheduler scheduler;

    @BeforeEach
    void setUp() {
        PersonaMayorLookup persona = mock(PersonaMayorLookup.class);
        when(persona.getIdUsuario()).thenReturn(ID_PERSONA);

        // Simula la consulta por mes y días de la BD
        PersonaMayorLookupRepository personaRepo = mock(PersonaMayorLookupRepository.class);
        when(personaRepo.findCumpleanos(anyInt(), anyCollection())).thenAnswer(inv ->
                cumpleEn(fechaNacimiento, inv.getArgument(0), inv.getArgument(1))
                        ? List.of(persona)
                        : List.of());

        PersonaMayorAcompananteRepository acompRepo = mock(PersonaMayorAcompananteRepository.class);
        when(acompRepo.findById_IdPersonaMayorAndEstado(ID_PERSONA, "ACEPTADA"))
                .thenReturn(List.of(new PersonaMayorAcompanante(ID_PERSONA, ID_ACOMPANANTE)));

        PersonaMayorOrganizacionRepository orgRepo = mock(PersonaMayorOrganizacionRepository.class);
        when(orgRepo.findById_IdPersonaMayorAndEstado(ID_PERSONA, "ACEPTADA"))
                .thenReturn(List.of(new PersonaMayorOrganizacion(ID_PERSONA, ID_ORGANIZACION)));

        UsuarioLookupRepository usuarioRepo = mock(UsuarioLookupRepository.class);
        UsuarioLookup usuarioPersona = usuario(ID_PERSONA, NOMBRE_PERSONA, CEL_PERSONA);
        UsuarioLookup usuarioAcompanante = usuario(ID_ACOMPANANTE, NOMBRE_ACOMPANANTE, CEL_ACOMPANANTE);
        UsuarioLookup usuarioOrganizacion = usuario(ID_USUARIO_ORGANIZACION, NOMBRE_ORGANIZACION, CEL_ORGANIZACION);
        when(usuarioRepo.findById(ID_PERSONA)).thenReturn(Optional.of(usuarioPersona));
        when(usuarioRepo.findById(ID_ACOMPANANTE)).thenReturn(Optional.of(usuarioAcompanante));
        when(usuarioRepo.findByIdOrganizacion(ID_ORGANIZACION)).thenReturn(List.of(usuarioOrganizacion));

        // Simula la consulta de acompañantes y voluntarios que cumplen años
        UsuarioLookup usuarioVoluntario = usuario(ID_VOLUNTARIO, NOMBRE_VOLUNTARIO, CEL_VOLUNTARIO);
        when(usuarioRepo.findCumpleanosAcompanantesYVoluntarios(anyInt(), anyCollection())).thenAnswer(inv -> {
            List<UsuarioLookup> cumplen = new ArrayList<>();
            if (cumpleEn(fechaNacimientoAcompanante, inv.getArgument(0), inv.getArgument(1))) {
                cumplen.add(usuarioAcompanante);
            }
            if (cumpleEn(fechaNacimientoVoluntario, inv.getArgument(0), inv.getArgument(1))) {
                cumplen.add(usuarioVoluntario);
            }
            return cumplen;
        });

        MensajeriaClient mensajeriaClient = mock(MensajeriaClient.class);
        when(mensajeriaClient.enviarMensaje(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            envios.add(new Envio(inv.getArgument(0), inv.getArgument(1)));
            return true;
        });

        scheduler = new CumpleanosScheduler(personaRepo, acompRepo, orgRepo, usuarioRepo, mensajeriaClient);
    }

    @Test
    void felicitaALaPersonaMayorYRecuerdaAAcompananteYOrganizacion() {
        fechaNacimiento = LocalDate.of(1950, 10, 1);

        scheduler.revisarCumpleanos(LocalDate.of(2026, 10, 1));

        assertEquals(List.of(CEL_PERSONA, CEL_ACOMPANANTE, CEL_ORGANIZACION),
                envios.stream().map(Envio::celular).toList());
        assertTrue(envios.get(0).mensaje().contains("¡Feliz cumpleaños, " + NOMBRE_PERSONA + "!"));
        assertTrue(envios.get(1).mensaje().contains(NOMBRE_PERSONA));
        assertEquals("Hoy es el cumpleaños de " + NOMBRE_PERSONA
                + ". Un saludo tuyo puede alegrarle el día.", envios.get(1).mensaje());
        assertEquals(envios.get(1).mensaje(), envios.get(2).mensaje());
    }

    @Test
    void felicitaAAcompananteYVoluntarioEnSuCumpleanos() {
        fechaNacimiento = LocalDate.of(1950, 3, 15);
        fechaNacimientoAcompanante = LocalDate.of(1990, 10, 1);
        fechaNacimientoVoluntario = LocalDate.of(2000, 10, 1);

        scheduler.revisarCumpleanos(LocalDate.of(2026, 10, 1));

        assertEquals(List.of(CEL_ACOMPANANTE, CEL_VOLUNTARIO),
                envios.stream().map(Envio::celular).toList());
        assertTrue(envios.get(0).mensaje().contains("¡Feliz cumpleaños, " + NOMBRE_ACOMPANANTE + "!"));
        assertTrue(envios.get(1).mensaje().contains("¡Feliz cumpleaños, " + NOMBRE_VOLUNTARIO + "!"));
        assertTrue(envios.get(1).mensaje().contains("VITA+"));
    }

    @Test
    void noAvisaOtroDia() {
        fechaNacimiento = LocalDate.of(1950, 10, 1);

        scheduler.revisarCumpleanos(LocalDate.of(2026, 10, 2));

        assertEquals(0, envios.size());
    }

    @Test
    void nacidoEl29DeFebreroRecibeAvisoEl28EnAnoNoBisiesto() {
        fechaNacimiento = LocalDate.of(1948, 2, 29);

        // Felicitación + recordatorio al acompañante + recordatorio a la organización
        scheduler.revisarCumpleanos(LocalDate.of(2027, 2, 28));
        assertEquals(3, envios.size());

        // En año bisiesto los mensajes salen el 29, no el 28
        envios.clear();
        scheduler.revisarCumpleanos(LocalDate.of(2028, 2, 28));
        assertEquals(0, envios.size());
        scheduler.revisarCumpleanos(LocalDate.of(2028, 2, 29));
        assertEquals(3, envios.size());
    }

    private static boolean cumpleEn(LocalDate fecha, int mes, Collection<Integer> dias) {
        return fecha != null && fecha.getMonthValue() == mes && dias.contains(fecha.getDayOfMonth());
    }

    private static UsuarioLookup usuario(int id, String nombre, String celular) {
        UsuarioLookup usuario = mock(UsuarioLookup.class);
        when(usuario.getIdUsuario()).thenReturn(id);
        when(usuario.getNombreUsuario()).thenReturn(nombre);
        when(usuario.getCelular()).thenReturn(celular);
        return usuario;
    }
}
