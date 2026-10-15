package com.proyectogrado.persona_mayor_service.config;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Con dos réplicas, solo una corre cada tarea programada: la segunda que
 * intenta tomar la misma tarea no consigue el bloqueo y se la salta.
 * Usa una base H2 en memoria, que hace de la base compartida.
 */
class BloqueoTareasConfigTest {

    private final DriverManagerDataSource baseCompartida = new DriverManagerDataSource(
            "jdbc:h2:mem:shedlock;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");

    private LockConfiguration cumpleanos() {
        return new LockConfiguration(Instant.now(), "persona-mayor.cumpleanos",
                Duration.ofMinutes(30), Duration.ofMinutes(5));
    }

    @Test
    void laSegundaReplicaNoCorreLaTareaQueYaTomoLaPrimera() {
        LockProvider replica1 = new BloqueoTareasConfig().lockProvider(baseCompartida);
        LockProvider replica2 = new BloqueoTareasConfig().lockProvider(baseCompartida);

        Optional<SimpleLock> deLaPrimera = replica1.lock(cumpleanos());
        Optional<SimpleLock> deLaSegunda = replica2.lock(cumpleanos());

        assertTrue(deLaPrimera.isPresent());
        assertFalse(deLaSegunda.isPresent());
    }
}
