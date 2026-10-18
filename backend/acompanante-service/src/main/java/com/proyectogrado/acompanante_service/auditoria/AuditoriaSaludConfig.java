package com.proyectogrado.acompanante_service.auditoria;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Activa el registro de auditoría de los datos de salud en todas las rutas (ver AuditoriaSaludInterceptor). */
@Configuration
public class AuditoriaSaludConfig implements WebMvcConfigurer {

    private final AuditoriaSaludInterceptor interceptor;

    public AuditoriaSaludConfig(AuditoriaSaludInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }
}
