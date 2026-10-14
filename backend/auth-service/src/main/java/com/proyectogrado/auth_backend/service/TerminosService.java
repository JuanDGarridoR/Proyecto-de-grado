package com.proyectogrado.auth_backend.service;

import com.proyectogrado.auth_backend.model.AceptacionTerminos;
import com.proyectogrado.auth_backend.repository.AceptacionTerminosRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Constancias de aceptación de los términos y condiciones (ver
 * AceptacionTerminos). La versión vigente sale de terminos.version: hay que
 * cambiarla cada vez que cambie el texto de los términos en el frontend.
 */
@Service
public class TerminosService {

    private static final Logger log = LoggerFactory.getLogger(TerminosService.class);

    /** La constancia se guarda en hora de Colombia, sin importar la zona del servidor. */
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private final AceptacionTerminosRepository repository;
    private final String versionVigente;

    public TerminosService(
            AceptacionTerminosRepository repository,
            @Value("${terminos.version}") String versionVigente
    ) {
        this.repository = repository;
        this.versionVigente = versionVigente;
    }

    /** Guarda que el usuario aceptó la versión vigente, con la fecha y hora de ahora. */
    public AceptacionTerminos registrarAceptacion(Integer idUsuario) {
        return repository.save(new AceptacionTerminos(idUsuario, LocalDateTime.now(ZONA), versionVigente));
    }

    /**
     * Al arrancar, crea la constancia de las cuentas que existían antes de
     * esta tabla. Solo toca las que no tienen ninguna, así que después de la
     * primera vez no hace nada.
     */
    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void registrarCuentasAnteriores() {
        int creadas = repository.registrarCuentasAnteriores();
        if (creadas > 0) {
            log.info("Constancias de términos creadas para {} cuentas anteriores", creadas);
        }
    }
}
