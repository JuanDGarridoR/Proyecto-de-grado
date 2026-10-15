package com.proyectogrado.persona_mayor_service.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Activa la caché en memoria (Caffeine). Qué se guarda y por cuánto tiempo
 * está en application.properties (spring.cache.*) y en los @Cacheable.
 * Va aparte de la clase principal para que las pruebas de repositorios
 * (@DataJpaTest), que no cargan la caché, no la exijan.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
