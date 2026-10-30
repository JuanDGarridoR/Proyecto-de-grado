package com.proyectogrado.persona_mayor_service.config;

import com.proyectogrado.persona_mayor_service.model.Gusto;
import com.proyectogrado.persona_mayor_service.repository.GustoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opciones casi repetidas del catálogo de intereses sobre una base H2: se
 * juntan con la del catálogo sin que nadie pierda lo que había marcado.
 */
@DataJpaTest
class CatalogoInteresesFusionTest {

    @Autowired
    private GustoRepository gustoRepository;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private int tejido;
    private int costura;
    private int costuraOTejido;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        // voluntario_gusto es de voluntario-service: aquí no la crea Hibernate.
        jdbc.execute("CREATE TABLE IF NOT EXISTS voluntario_gusto (id_voluntario INTEGER, id_gusto INTEGER)");

        tejido = gustoRepository.save(new Gusto("Tejido", "TALENTO")).getIdGusto();
        costura = gustoRepository.save(new Gusto("Costura", "TALENTO")).getIdGusto();
        costuraOTejido = gustoRepository.save(new Gusto("Costura o tejido", "TALENTO")).getIdGusto();

        // La 10 tenía Tejido y Costura; la 11, Tejido y ya la nueva; el voluntario 50, Costura.
        marcar("persona_mayor_gusto", "id_persona_mayor", 10, tejido);
        marcar("persona_mayor_gusto", "id_persona_mayor", 10, costura);
        marcar("persona_mayor_gusto", "id_persona_mayor", 11, tejido);
        marcar("persona_mayor_gusto", "id_persona_mayor", 11, costuraOTejido);
        marcar("voluntario_gusto", "id_voluntario", 50, costura);
    }

    private void marcar(String tabla, String columna, int persona, int gusto) {
        jdbc.update("INSERT INTO " + tabla + " (" + columna + ", id_gusto) VALUES (?, ?)", persona, gusto);
    }

    private List<Integer> conGusto(String tabla, String columna, int gusto) {
        return jdbc.queryForList("SELECT " + columna + " FROM " + tabla + " WHERE id_gusto = ? ORDER BY 1",
                Integer.class, gusto);
    }

    @Test
    void quienTeniaLaRepetidaQuedaConLaDelCatalogoSinDuplicados() {
        new CatalogoIntereses(gustoRepository, jdbc).fusionarRepetidas();

        assertFalse(gustoRepository.existsByNombre("Tejido"));
        assertFalse(gustoRepository.existsByNombre("Costura"));
        assertTrue(gustoRepository.existsByNombre("Costura o tejido"));

        assertEquals(List.of(10, 11), conGusto("persona_mayor_gusto", "id_persona_mayor", costuraOTejido));
        assertEquals(List.of(50), conGusto("voluntario_gusto", "id_voluntario", costuraOTejido));
        assertEquals(0, conGusto("persona_mayor_gusto", "id_persona_mayor", tejido).size());

        // Correrla otra vez no cambia nada.
        new CatalogoIntereses(gustoRepository, jdbc).fusionarRepetidas();
        assertEquals(List.of(10, 11), conGusto("persona_mayor_gusto", "id_persona_mayor", costuraOTejido));
    }
}
