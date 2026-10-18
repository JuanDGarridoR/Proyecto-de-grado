package com.proyectogrado.auth_backend.service;

import com.proyectogrado.auth_backend.model.Sesion;
import com.proyectogrado.auth_backend.repository.SesionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Sesiones revocables. El token de acceso (JWT) dura poco (jwt.expiration,
 * 15 minutos) y el gateway lo valida sin consultar la base. Para no pedirle
 * a la persona que vuelva a entrar cada 15 minutos, al iniciar sesión
 * recibe también un token de renovación, con el que el navegador pide
 * tokens de acceso nuevos.
 *
 * El token de renovación sí se revisa en la base en cada uso, así que se
 * puede revocar: al cerrar sesión, al cambiar o restablecer la contraseña y
 * al eliminar la cuenta. Desde ese momento, quien lo tenga solo conserva
 * el token de acceso que ya tenía, como máximo 15 minutos más.
 *
 * Vive en la base (y no en memoria) porque auth-service corre en dos réplicas.
 */
@Service
public class SesionService {

    /** Tiempo sin usar la sesión tras el que vence y hay que volver a entrar. */
    public static final Duration DURACION_SIN_USO = Duration.ofDays(30);

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private final SesionRepository sesionRepository;
    private final SecureRandom random = new SecureRandom();

    public SesionService(SesionRepository sesionRepository) {
        this.sesionRepository = sesionRepository;
    }

    /** Abre una sesión y devuelve su token de renovación (solo se ve esta vez). */
    @Transactional
    public String abrir(Integer idUsuario) {
        LocalDateTime ahora = LocalDateTime.now(ZONA);

        // De paso se limpian las sesiones vencidas de la persona.
        sesionRepository.deleteByIdUsuarioAndExpiraBefore(idUsuario, ahora);

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        sesionRepository.save(new Sesion(idUsuario, hash(token), ahora, ahora.plus(DURACION_SIN_USO)));
        return token;
    }

    /**
     * Dueño de la sesión, si el token corresponde a una sesión abierta y sin
     * vencer. Usarla corre su vencimiento.
     */
    @Transactional
    public Optional<Integer> usuarioDe(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        Optional<Sesion> encontrada = sesionRepository.findByHashToken(hash(token));
        if (encontrada.isEmpty()) {
            return Optional.empty();
        }

        Sesion sesion = encontrada.get();
        LocalDateTime ahora = LocalDateTime.now(ZONA);

        if (!ahora.isBefore(sesion.getExpira())) {
            sesionRepository.delete(sesion);
            return Optional.empty();
        }

        sesion.setExpira(ahora.plus(DURACION_SIN_USO));
        sesionRepository.save(sesion);
        return Optional.of(sesion.getIdUsuario());
    }

    /** Cierra la sesión de ese token (al cerrar sesión en el navegador). */
    @Transactional
    public void cerrar(String token) {
        if (token != null && !token.isBlank()) {
            sesionRepository.deleteByHashToken(hash(token));
        }
    }

    /** Cierra todas las sesiones de la persona (al restablecer la contraseña o eliminar la cuenta). */
    @Transactional
    public void cerrarTodas(Integer idUsuario) {
        sesionRepository.deleteByIdUsuario(idUsuario);
    }

    /**
     * Cierra todas las sesiones de la persona menos la de ese token (al
     * cambiar la contraseña: quien la cambió sigue adentro). Sin token, las
     * cierra todas.
     */
    @Transactional
    public void cerrarTodasMenos(Integer idUsuario, String tokenQueSeConserva) {
        if (tokenQueSeConserva == null || tokenQueSeConserva.isBlank()) {
            sesionRepository.deleteByIdUsuario(idUsuario);
        } else {
            sesionRepository.deleteByIdUsuarioAndHashTokenNot(idUsuario, hash(tokenQueSeConserva));
        }
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }
}
