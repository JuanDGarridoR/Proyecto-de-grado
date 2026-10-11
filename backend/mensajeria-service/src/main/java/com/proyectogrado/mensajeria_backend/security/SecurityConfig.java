package com.proyectogrado.mensajeria_backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * mensajeria-service no valida tokens: quien decide si una petición puede
 * llegar aquí es el gateway. Los demás servicios lo llaman directamente.
 *
 * Sin esta clase, Spring Security activa su configuración por defecto y
 * responde 401 en todos los endpoints, incluido /api/otp/**.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
