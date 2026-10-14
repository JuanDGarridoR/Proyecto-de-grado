package com.proyectogrado.organizacion_service.controller;

import com.proyectogrado.organizacion_service.model.UsuarioLookup;
import com.proyectogrado.organizacion_service.repository.UsuarioLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Personas mayores que eliminaron su cuenta (RF-10): la organización ve por
 * qué se retiraron las suyas. La consulta usa to_regclass, que solo existe en
 * PostgreSQL, así que aquí se simula el JdbcTemplate; la fila se convierte con
 * el mismo RowMapper del controlador.
 */
class RetiroControllerTest {

    private NamedParameterJdbcTemplate jdbc;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        UsuarioLookupRepository usuarioLookupRepository = mock(UsuarioLookupRepository.class);
        jdbc = mock(NamedParameterJdbcTemplate.class);

        UsuarioLookup cuenta = mock(UsuarioLookup.class);
        when(cuenta.getIdOrganizacion()).thenReturn(5);
        when(usuarioLookupRepository.findById(30)).thenReturn(Optional.of(cuenta));

        UsuarioLookup personaMayor = mock(UsuarioLookup.class);
        when(personaMayor.getIdOrganizacion()).thenReturn(null);
        when(usuarioLookupRepository.findById(10)).thenReturn(Optional.of(personaMayor));

        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Boolean.class))).thenReturn(true);

        ResultSet fila = mock(ResultSet.class);
        when(fila.getString("nombre")).thenReturn("Rosa Díaz");
        when(fila.getString("razon")).thenReturn("FALLECIMIENTO");
        when(fila.getString("comentario")).thenReturn("Falleció en septiembre");
        when(fila.getTimestamp("fecha")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 10, 2, 9, 30)));
        when(jdbc.query(anyString(), any(SqlParameterSource.class), ArgumentMatchers.<RowMapper<Object>>any()))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(2);
                    return List.of(mapper.mapRow(fila, 0));
                });

        mockMvc = MockMvcBuilders.standaloneSetup(new RetiroController(usuarioLookupRepository, jdbc)).build();
    }

    @Test
    void laOrganizacionVeLaRazonDeRetiroDeSusPersonasMayores() throws Exception {
        mockMvc.perform(get("/api/organizacion/retiros").header("X-User-Id", 30))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Rosa Díaz"))
                .andExpect(jsonPath("$[0].razon").value("FALLECIMIENTO"))
                .andExpect(jsonPath("$[0].comentario").value("Falleció en septiembre"));

        // Solo los retiros de su organización
        ArgumentCaptor<SqlParameterSource> parametros = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(anyString(), parametros.capture(), ArgumentMatchers.<RowMapper<Object>>any());
        assertEquals(5, parametros.getValue().getValue("org"));
    }

    @Test
    void siAunNoHayTablaDeRetirosLaListaQuedaVacia() throws Exception {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Boolean.class))).thenReturn(false);

        mockMvc.perform(get("/api/organizacion/retiros").header("X-User-Id", 30))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(jdbc, never()).query(anyString(), any(SqlParameterSource.class), ArgumentMatchers.<RowMapper<Object>>any());
    }

    @Test
    void quienNoEsOrganizacionNoVeRetiros() throws Exception {
        mockMvc.perform(get("/api/organizacion/retiros").header("X-User-Id", 10))
                .andExpect(status().isNotFound());
    }
}
