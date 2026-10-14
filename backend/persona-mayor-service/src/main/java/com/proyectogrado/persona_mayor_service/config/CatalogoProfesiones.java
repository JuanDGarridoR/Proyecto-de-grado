package com.proyectogrado.persona_mayor_service.config;

import com.proyectogrado.persona_mayor_service.model.Gusto;
import com.proyectogrado.persona_mayor_service.repository.GustoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Profesiones u oficios del catálogo de intereses (categoría PROFESION): a
 * qué se dedica o se dedicó la persona. Están en la misma tabla gusto que
 * los gustos, talentos y pasatiempos, y se marcan desde la misma pantalla.
 *
 * Al arrancar se agregan las que falten, así llegan a cualquier base sin
 * correr scripts. Las que ya existen no se tocan. El recomendador de
 * organizaciones (analitica-service, Oficios) usa estos mismos nombres.
 */
@Component
public class CatalogoProfesiones {

    private static final Logger log = LoggerFactory.getLogger(CatalogoProfesiones.class);

    public static final String CATEGORIA = "PROFESION";

    public static final List<String> PROFESIONES = List.of(
            "Labores del hogar",
            "Agricultura o trabajo del campo",
            "Construcción",
            "Comercio o ventas",
            "Conducción o transporte",
            "Modistería o sastrería",
            "Docencia",
            "Salud (enfermería o medicina)",
            "Cocina",
            "Servicios generales o aseo",
            "Oficina o administración",
            "Vigilancia o seguridad",
            "Mecánica",
            "Carpintería o ebanistería",
            "Peluquería o belleza",
            "Artesanía",
            "Fuerza pública (militar o policía)",
            "Ingeniería o trabajo técnico",
            "Trabajo social o psicología",
            "Estudiante"
    );

    private final GustoRepository gustoRepository;

    public CatalogoProfesiones(GustoRepository gustoRepository) {
        this.gustoRepository = gustoRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void agregarFaltantes() {
        int agregadas = 0;
        for (String profesion : PROFESIONES) {
            if (!gustoRepository.existsByNombre(profesion)) {
                gustoRepository.save(new Gusto(profesion, CATEGORIA));
                agregadas++;
            }
        }

        if (agregadas > 0) {
            log.info("Se agregaron {} profesiones al catálogo de intereses", agregadas);
        }
    }
}
