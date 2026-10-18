
package com.proyectogrado.auth_backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuración de Spring Security de auth-service: qué rutas son públicas,
 * cuáles exigen token y cómo se guardan las contraseñas (hash BCrypt).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UsuarioDetailsService usuarioDetailsService;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            UsuarioDetailsService usuarioDetailsService
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.usuarioDetailsService = usuarioDetailsService;
    }

    /** Hash de contraseñas. Lo usan el registro, el login y el cambio de contraseña. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Proveedor estándar de Spring Security para usuario y contraseña. El
     * login de la aplicación no pasa por aquí: AuthService compara la
     * contraseña directamente con el PasswordEncoder.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(usuarioDetailsService);

        provider.setPasswordEncoder(passwordEncoder());

        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {

        return config.getAuthenticationManager();
    }

    /** Reglas de acceso a las rutas de auth-service. */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http

                // La API no usa cookies de sesión sino el token, así que la
                // protección CSRF no aplica.
                .csrf(csrf -> csrf.disable())

                // No se guarda sesión en el servidor: cada petición trae su token.
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // Sin token válido (por ejemplo, vencido) se responde 401, no
                // el 403 por defecto: con 401 el frontend sabe que debe
                // renovar el token y repetir la petición.
                .exceptionHandling(excepciones -> excepciones
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )

                .authorizeHttpRequests(auth -> auth

                        // Rutas públicas: login, registro, recuperación de
                        // contraseña, la consulta de si un celular tiene
                        // cuenta y renovar o cerrar la sesión (se llaman
                        // cuando el token de acceso ya venció).
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/login-otp",
                                "/api/auth/registro",
                                "/api/auth/celular-existe",
                                "/api/auth/restablecer-contrasena",
                                "/api/auth/sesion/renovar",
                                "/api/auth/sesion/cerrar",
                                "/error"
                        )
                        .permitAll()

                        // Health check de Docker. No sale a internet: el
                        // gateway solo enruta /api/**.
                        .requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()

                        // Todo lo demás exige un token válido.
                        .requestMatchers("/api/auth/**")
                        .authenticated()

                        .requestMatchers("/api/persona-mayor/**")
                        .authenticated()

                        .requestMatchers("/api/organizacion/**")
                        .authenticated()

                        .requestMatchers("/api/acompanante/**")
                        .authenticated()

                        .anyRequest()
                        .authenticated()
                )

                .authenticationProvider(authenticationProvider())

                // El token se revisa antes del filtro de usuario y contraseña de Spring.
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}