package com.proyectogrado.persona_mayor_service.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * En el servidor este servicio corre en dos réplicas (ver docker-compose.yml)
 * y cada una tiene sus tareas programadas. Sin bloqueo, las dos enviarían el
 * mismo SMS de cumpleaños. Con ShedLock, la réplica que empieza una tarea
 * deja una marca en la tabla shedlock y la otra se salta esa ejecución.
 *
 * Se usa la hora de la base de datos (usingDbTime) para que no importe si
 * los relojes de las réplicas están un poco desfasados.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class BloqueoTareasConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // La tabla la comparten todos los servicios que usen ShedLock; cada
        // tarea se distingue por su nombre (ver @SchedulerLock).
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS shedlock (
                    name VARCHAR(64) NOT NULL PRIMARY KEY,
                    lock_until TIMESTAMP NOT NULL,
                    locked_at TIMESTAMP NOT NULL,
                    locked_by VARCHAR(255) NOT NULL
                )""");

        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbcTemplate)
                .usingDbTime()
                .build());
    }
}
