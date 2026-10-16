package com.proyectogrado.analitica_service.lugares;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las sedes de las organizaciones y las actividades próximas se ubican
 * solas en el mapa a partir de su dirección o su lugar, sobre H2 en modo
 * PostgreSQL. Google se reemplaza por un mock: no gasta cupo ni pide clave.
 */
class UbicadorAutomaticoTest {

    private static final LugarEncontrado PARQUE_LA_FLORA =
            new LugarEncontrado("Parque La Flora", "Usme, Bogotá", 4.528, -74.085);
    private static final LugarEncontrado SEDE_USME_CENTRO =
            new LugarEncontrado(null, "Cl. 91 Sur #3-15, Usme, Bogotá", 4.4805, -74.1255);

    private NamedParameterJdbcTemplate jdbc;
    private BuscadorLugares buscador;
    private UbicadorAutomatico ubicador;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:ubicador-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new NamedParameterJdbcTemplate(dataSource);

        // Tablas como las crean los otros servicios, todavía sin columnas de ubicación.
        jdbc.getJdbcTemplate().execute(
                "CREATE TABLE organizacion (id_organizacion INTEGER PRIMARY KEY, nombre VARCHAR(255), direccion VARCHAR(255))");
        jdbc.getJdbcTemplate().execute(
                "CREATE TABLE actividad (id_actividad INTEGER PRIMARY KEY, id_organizacion INTEGER,"
                        + " nombre VARCHAR(255), fecha DATE, lugar VARCHAR(255))");

        jdbc.getJdbcTemplate().execute(
                "INSERT INTO organizacion VALUES (5, 'Fundación Entrenubes', 'Calle 91 Sur # 3-15, Usme Centro')");
        jdbc.getJdbcTemplate().execute(
                "INSERT INTO actividad VALUES"
                        + " (1, 5, 'Caminata', CURRENT_DATE + 3, 'Parque La Flora'),"
                        + " (2, 5, 'Bingo', CURRENT_DATE + 5, 'Salón principal'),"
                        + " (3, 5, 'Yoga', CURRENT_DATE - 10, 'Parque La Flora')");

        // Personas mayores (la tabla aún sin las columnas de barrio)
        jdbc.getJdbcTemplate().execute(
                "CREATE TABLE usuario (id_usuario INTEGER PRIMARY KEY, nombre_usuario VARCHAR(255), direccion VARCHAR(255))");
        jdbc.getJdbcTemplate().execute("CREATE TABLE persona_mayor (id_usuario INTEGER PRIMARY KEY)");
        jdbc.getJdbcTemplate().execute(
                "INSERT INTO usuario VALUES"
                        + " (20, 'Con barrio escrito', 'Calle 80 Sur # 5-20, Yomasa'),"
                        + " (21, 'Google da el barrio', 'Calle 91 Sur # 3-15'),"
                        + " (22, 'Google solo da la localidad', 'Carrera 4 Este # 76-20 Sur'),"
                        + " (23, 'Google no la encuentra', 'Avenida que no existe 123')");
        jdbc.getJdbcTemplate().execute("INSERT INTO persona_mayor VALUES (20), (21), (22), (23)");

        buscador = mock(BuscadorLugares.class);
        when(buscador.disponible()).thenReturn(true);
        when(buscador.buscar(anyString())).thenReturn(List.of());
        when(buscador.buscar("Calle 91 Sur # 3-15, Usme Centro")).thenReturn(List.of(SEDE_USME_CENTRO));
        when(buscador.buscar("Parque La Flora")).thenReturn(List.of(PARQUE_LA_FLORA));
        when(buscador.buscar("Calle 91 Sur # 3-15")).thenReturn(List.of(
                new LugarEncontrado(null, "Cl. 91 Sur #3b Este, Bogotá", 4.5105, -74.1158, "Chuniza")));
        // Sin barrio, pero a pocos metros del centro de Yomasa
        when(buscador.buscar("Carrera 4 Este # 76-20 Sur")).thenReturn(List.of(
                new LugarEncontrado(null, "Cra. 10 Este #76-20, Bogotá", 4.5182, -74.1080, null)));

        ubicador = new UbicadorAutomatico(jdbc, buscador);
    }

    private Map<String, Object> fila(String tabla, String columnaId, int id) {
        return jdbc.queryForMap("SELECT latitud, longitud, ubicacion_buscada FROM " + tabla
                + " WHERE " + columnaId + " = :id", Map.of("id", id));
    }

    @Test
    void laSedeDeLaOrganizacionSeUbicaConSuDireccion() {
        ubicador.ubicarPendientes();

        Map<String, Object> sede = fila("organizacion", "id_organizacion", 5);
        assertEquals(4.4805, sede.get("latitud"));
        assertEquals(-74.1255, sede.get("longitud"));
        assertEquals("Calle 91 Sur # 3-15, Usme Centro", sede.get("ubicacion_buscada"));
    }

    @Test
    void laActividadSeUbicaPorElNombreDelLugar() {
        ubicador.ubicarPendientes();

        Map<String, Object> caminata = fila("actividad", "id_actividad", 1);
        assertEquals(4.528, caminata.get("latitud"));
        assertEquals(-74.085, caminata.get("longitud"));
    }

    @Test
    void siElLugarNoApareceLaActividadQuedaEnLaSedeDeSuOrganizacion() {
        ubicador.ubicarPendientes();

        Map<String, Object> bingo = fila("actividad", "id_actividad", 2);
        assertEquals(4.4805, bingo.get("latitud"));
        assertEquals(-74.1255, bingo.get("longitud"));
        assertEquals("Salón principal", bingo.get("ubicacion_buscada"));
    }

    @Test
    void lasActividadesQueYaPasaronNoSeUbican() {
        ubicador.ubicarPendientes();

        assertNull(fila("actividad", "id_actividad", 3).get("latitud"));
    }

    @Test
    void loYaUbicadoNoSeVuelveABuscarHastaQueCambieElTexto() {
        ubicador.ubicarPendientes();
        ubicador.ubicarPendientes();

        verify(buscador, times(1)).buscar("Parque La Flora");
        verify(buscador, times(1)).buscar("Salón principal");

        // La organización cambia el lugar de la caminata: se vuelve a ubicar.
        jdbc.getJdbcTemplate().execute("UPDATE actividad SET lugar = 'Parque Entrenubes' WHERE id_actividad = 1");
        when(buscador.buscar("Parque Entrenubes")).thenReturn(List.of(
                new LugarEncontrado("Parque Entrenubes", "Usme, Bogotá", 4.49, -74.10)));

        ubicador.ubicarPendientes();

        assertEquals(4.49, fila("actividad", "id_actividad", 1).get("latitud"));
    }

    private Object barrio(int idPersona) {
        return jdbc.queryForObject("SELECT barrio FROM persona_mayor WHERE id_usuario = :id",
                Map.of("id", idPersona), Object.class);
    }

    @Test
    void siLaDireccionTraeElBarrioSeUsaSinPreguntarleAGoogle() {
        ubicador.ubicarPendientes();

        assertEquals("Yomasa", barrio(20));
        verify(buscador, never()).buscar("Calle 80 Sur # 5-20, Yomasa");
    }

    @Test
    void siNoTraeBarrioSeUsaElQueLeAsignaGoogle() {
        ubicador.ubicarPendientes();

        assertEquals("Chuniza", barrio(21));
    }

    @Test
    void siGoogleSoloDaLaLocalidadSeUsaElBarrioConocidoMasCercano() {
        ubicador.ubicarPendientes();

        assertEquals("Yomasa", barrio(22));
    }

    @Test
    void siGoogleNoLaEncuentraQuedaSinBarrioYNoSeVuelveABuscar() {
        ubicador.ubicarPendientes();
        ubicador.ubicarPendientes();

        assertNull(barrio(23));
        verify(buscador, times(1)).buscar("Avenida que no existe 123");
    }

    @Test
    void deLasPersonasMayoresSoloSeGuardaElBarrioNuncaCoordenadas() {
        ubicador.ubicarPendientes();

        List<String> columnas = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'persona_mayor'",
                Map.of(), String.class);
        assertEquals(List.of("barrio", "barrio_buscado", "id_usuario"), columnas.stream().sorted().toList());
    }

    @Test
    void sinClaveDeGoogleSoloSeSacaElBarrioDelTexto() {
        when(buscador.disponible()).thenReturn(false);

        ubicador.ubicarPendientes();

        verify(buscador, never()).buscar(anyString());
        assertEquals("Yomasa", barrio(20));
        // Las que necesitan Google quedan pendientes para cuando haya clave
        assertNull(jdbc.queryForObject("SELECT barrio_buscado FROM persona_mayor WHERE id_usuario = 21",
                Map.of(), Object.class));
    }
}
