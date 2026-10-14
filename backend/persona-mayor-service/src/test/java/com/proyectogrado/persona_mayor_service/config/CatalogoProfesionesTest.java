package com.proyectogrado.persona_mayor_service.config;

import com.proyectogrado.persona_mayor_service.model.Gusto;
import com.proyectogrado.persona_mayor_service.repository.GustoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Profesiones del catálogo de intereses (RF-71): al arrancar se agregan las
 * que falten, en la categoría PROFESION, sin duplicar las que ya existen.
 */
class CatalogoProfesionesTest {

    @Test
    void soloAgregaLasProfesionesQueFaltan() {
        GustoRepository repository = mock(GustoRepository.class);
        when(repository.existsByNombre(anyString())).thenReturn(false);
        when(repository.existsByNombre("Docencia")).thenReturn(true);

        new CatalogoProfesiones(repository).agregarFaltantes();

        ArgumentCaptor<Gusto> guardadas = ArgumentCaptor.forClass(Gusto.class);
        verify(repository, times(CatalogoProfesiones.PROFESIONES.size() - 1)).save(guardadas.capture());
        assertTrue(guardadas.getAllValues().stream().allMatch(g -> "PROFESION".equals(g.getCategoria())));
        assertTrue(guardadas.getAllValues().stream().noneMatch(g -> "Docencia".equals(g.getNombre())));
    }

    @Test
    void siYaEstanTodasNoHaceNada() {
        GustoRepository repository = mock(GustoRepository.class);
        when(repository.existsByNombre(anyString())).thenReturn(true);

        new CatalogoProfesiones(repository).agregarFaltantes();

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
        assertEquals(20, CatalogoProfesiones.PROFESIONES.size());
    }
}
