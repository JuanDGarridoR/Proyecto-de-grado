package com.proyectogrado.persona_mayor_service.config;

import com.proyectogrado.persona_mayor_service.model.Gusto;
import com.proyectogrado.persona_mayor_service.repository.GustoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Opciones base del catálogo de intereses (RF-16 y RF-71): al arrancar se
 * agregan las que falten, cada una en su categoría, sin duplicar las que
 * ya existen.
 */
class CatalogoInteresesTest {

    private static int totalOpciones() {
        return CatalogoIntereses.POR_CATEGORIA.values().stream().mapToInt(List::size).sum();
    }

    @Test
    void soloAgregaLasOpcionesQueFaltanCadaUnaEnSuCategoria() {
        GustoRepository repository = mock(GustoRepository.class);
        when(repository.existsByNombreIgnoreCase(anyString())).thenReturn(false);
        when(repository.existsByNombreIgnoreCase("Docencia")).thenReturn(true);

        new CatalogoIntereses(repository, mock(JdbcTemplate.class)).agregarFaltantes();

        ArgumentCaptor<Gusto> guardadas = ArgumentCaptor.forClass(Gusto.class);
        verify(repository, times(totalOpciones() - 1)).save(guardadas.capture());
        assertTrue(guardadas.getAllValues().stream().noneMatch(g -> "Docencia".equals(g.getNombre())));

        for (Gusto gusto : guardadas.getAllValues()) {
            assertTrue(CatalogoIntereses.POR_CATEGORIA.get(gusto.getCategoria()).contains(gusto.getNombre()),
                    gusto.getNombre() + " quedó en la categoría equivocada");
        }
    }

    @Test
    void siYaEstanTodasNoHaceNada() {
        GustoRepository repository = mock(GustoRepository.class);
        when(repository.existsByNombreIgnoreCase(anyString())).thenReturn(true);

        new CatalogoIntereses(repository, mock(JdbcTemplate.class)).agregarFaltantes();

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    /** Cada repetida se junta con una opción que sí está en el catálogo, y ninguna repetida se vuelve a crear. */
    @Test
    void lasFusionesApuntanAOpcionesDelCatalogo() {
        Set<String> catalogo = new HashSet<>();
        CatalogoIntereses.POR_CATEGORIA.values().forEach(catalogo::addAll);

        CatalogoIntereses.FUSIONES.forEach((vieja, nueva) -> {
            assertTrue(catalogo.contains(nueva), nueva + " no está en el catálogo");
            assertTrue(!catalogo.contains(vieja), vieja + " está en el catálogo y se volvería a crear");
        });
    }

    /** El nombre es único en la tabla gusto: uno repetido en dos categorías solo quedaría en la primera. */
    @Test
    void ningunNombreSeRepiteNiSePasaDelLargoMaximo() {
        Set<String> vistos = new HashSet<>();
        for (List<String> opciones : CatalogoIntereses.POR_CATEGORIA.values()) {
            for (String nombre : opciones) {
                assertTrue(vistos.add(nombre.toLowerCase(Locale.ROOT)), "Repetido: " + nombre);
                assertTrue(nombre.length() <= 60, "Muy largo: " + nombre);
            }
        }
        assertEquals(4, CatalogoIntereses.POR_CATEGORIA.size());
    }
}
