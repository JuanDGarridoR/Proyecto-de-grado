package com.proyectogrado.persona_mayor_service.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las tareas programadas (CumpleanosScheduler) solo si
 * tareas.programadas.activas es true, que es lo normal.
 *
 * En el servidor este servicio corre en dos réplicas; si las dos corrieran
 * la tarea de las 8 a. m., cada persona recibiría dos felicitaciones. Por
 * eso docker-compose.yml la apaga en persona-mayor-service-2. Si la primera
 * réplica está caída justo a esa hora, ese día no salen los mensajes; se
 * acepta porque se reinicia sola y es una sola tarea no crítica.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "tareas.programadas.activas", havingValue = "true", matchIfMissing = true)
public class TareasProgramadasConfig {
}
