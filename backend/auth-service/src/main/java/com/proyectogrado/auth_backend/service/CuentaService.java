package com.proyectogrado.auth_backend.service;

import com.proyectogrado.auth_backend.model.RetiroCuenta;
import com.proyectogrado.auth_backend.model.Usuario;
import com.proyectogrado.auth_backend.repository.RetiroCuentaRepository;
import com.proyectogrado.auth_backend.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Eliminación, inactivación y reactivación de la cuenta, para cualquier rol.
 *
 * Al eliminar no se mira el rol del usuario:
 * borra todo lo que pueda referenciar su id_usuario en cada tabla (si no
 * aplica a su rol, el DELETE simplemente no afecta filas).
 *
 * Se hace con SQL nativo porque varias de estas tablas pertenecen a otros
 * servicios y auth-service no tiene entidades para ellas. Como todos
 * comparten la misma base de datos, todo corre en una sola transacción:
 * o se borra todo, o no se borra nada.
 */
@Service
public class CuentaService {

    @PersistenceContext
    private EntityManager entityManager;

    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");

    private final UsuarioRepository usuarioRepository;
    private final RetiroCuentaRepository retiroCuentaRepository;

    public CuentaService(UsuarioRepository usuarioRepository, RetiroCuentaRepository retiroCuentaRepository) {
        this.usuarioRepository = usuarioRepository;
        this.retiroCuentaRepository = retiroCuentaRepository;
    }

    /**
     * Guarda la razón del retiro (ver RetiroCuenta) y borra la cuenta, todo
     * en la misma transacción.
     */
    @Transactional
    public void eliminarCuenta(Integer idUsuario, String razon, String comentario) {

        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        registrarRetiro(usuario, razon, comentario);

        Integer idOrganizacion = usuario.getIdOrganizacion();

        // Datos como persona mayor
        ejecutar("DELETE FROM participacion WHERE id_persona_mayor = :id", idUsuario);

        // Propuestas de actividades de la persona mayor que nadie ve
        // (pendientes o rechazadas). Las aceptadas son actividades de la
        // organización y se quedan.
        if (existeColumna("actividad", "id_persona_mayor_proponente")) {
            ejecutar("DELETE FROM actividad WHERE id_persona_mayor_proponente = :id"
                    + " AND estado IN ('PENDIENTE', 'RECHAZADA')", idUsuario);
        }
        ejecutar("DELETE FROM medicamento WHERE id_persona_mayor = :id", idUsuario);
        ejecutarSiExisteTabla("persona_mayor_condicion_salud",
                "DELETE FROM persona_mayor_condicion_salud WHERE id_persona_mayor = :id", idUsuario);
        ejecutar("DELETE FROM persona_mayor_gusto WHERE id_persona_mayor = :id", idUsuario);
        ejecutar("DELETE FROM persona_mayor_organizacion WHERE id_persona_mayor = :id", idUsuario);

        // Vínculos persona mayor - acompañante, sea cual sea su lado
        ejecutar("DELETE FROM persona_mayor_acompanante"
                + " WHERE id_persona_mayor = :id OR id_acompanante = :id", idUsuario);

        // Vínculos y solicitudes como voluntario
        ejecutarSiExisteTabla("voluntario_organizacion",
                "DELETE FROM voluntario_organizacion WHERE id_voluntario = :id", idUsuario);

        // Propuestas de actividades del voluntario que nadie ve (pendientes o
        // rechazadas). Las aceptadas son actividades de la organización y se quedan.
        if (existeColumna("actividad", "id_voluntario")) {
            ejecutar("DELETE FROM actividad WHERE id_voluntario = :id"
                    + " AND estado IN ('PENDIENTE', 'RECHAZADA')", idUsuario);
        }

        // Preferencias de notificación (tabla de messaging-service)
        ejecutarSiExisteTabla("preferencia_notificacion",
                "DELETE FROM preferencia_notificacion WHERE id_usuario = :id", idUsuario);

        // Perfiles de rol
        ejecutar("DELETE FROM persona_mayor WHERE id_usuario = :id", idUsuario);
        ejecutar("DELETE FROM acompanante WHERE id_usuario = :id", idUsuario);
        ejecutar("DELETE FROM voluntario WHERE id_usuario = :id", idUsuario);

        ejecutar("DELETE FROM usuario_rol WHERE id_usuario = :id", idUsuario);
        ejecutar("DELETE FROM usuario WHERE id_usuario = :id", idUsuario);

        // Si era el último usuario de una organización, la organización
        // queda huérfana: se borra junto con sus actividades.
        if (idOrganizacion != null) {
            eliminarOrganizacionSinUsuarios(idOrganizacion);
        }
    }

    /**
     * Una fila por cada organización de la persona mayor (con su nombre, para
     * que la organización sepa quién se fue y por qué). Si no era persona
     * mayor o no tenía organizaciones, una sola fila anónima.
     */
    private void registrarRetiro(Usuario usuario, String razon, String comentario) {
        LocalDateTime ahora = LocalDateTime.now(COLOMBIA);
        String rol = rolDe(usuario.getIdUsuario());

        @SuppressWarnings("unchecked")
        List<Number> organizaciones = "PERSONA_MAYOR".equals(rol)
                ? entityManager.createNativeQuery("""
                        SELECT id_organizacion FROM persona_mayor_organizacion
                         WHERE id_persona_mayor = :id
                           AND estado IN ('ACEPTADA', 'CUENTA_INACTIVA')
                        """)
                        .setParameter("id", usuario.getIdUsuario())
                        .getResultList()
                : List.of();

        if (organizaciones.isEmpty()) {
            retiroCuentaRepository.save(new RetiroCuenta(null, null, rol, razon, comentario, ahora));
            return;
        }

        for (Number idOrganizacion : organizaciones) {
            retiroCuentaRepository.save(new RetiroCuenta(idOrganizacion.intValue(),
                    usuario.getNombreUsuario(), rol, razon, comentario, ahora));
        }
    }

    /** Primer rol del usuario (PERSONA_MAYOR, ACOMPANANTE...), o null si no tiene. */
    private String rolDe(Integer idUsuario) {
        List<?> roles = entityManager.createNativeQuery("""
                        SELECT r.nombre FROM usuario_rol ur
                          JOIN rol r ON r.id_rol = ur.id_rol
                         WHERE ur.id_usuario = :id
                         ORDER BY r.id_rol
                        """)
                .setParameter("id", idUsuario)
                .getResultList();

        return roles.isEmpty() ? null : String.valueOf(roles.get(0));
    }

    // =========================================================
    // INACTIVAR Y REACTIVAR LA CUENTA
    // =========================================================

    /*
     * Una cuenta inactiva puede entrar, pero nadie más la ve. En vez de tocar
     * cada consulta de cada servicio (todas filtran los vínculos por
     * estado = 'ACEPTADA' o 'PENDIENTE'), sus vínculos pasan a un estado que
     * nadie lista:
     *   ACEPTADA  -> CUENTA_INACTIVA
     *   PENDIENTE -> PENDIENTE_CUENTA_INACTIVA
     * Al reactivarla vuelven a su estado, pero solo los vínculos cuya otra
     * parte también está activa (si las dos se inactivaron, el vínculo
     * vuelve cuando se reactiva la segunda). Los vínculos que el usuario
     * inactivó a mano (INACTIVA) no se tocan.
     */

    public static final String CUENTA_INACTIVA = "CUENTA_INACTIVA";
    public static final String PENDIENTE_CUENTA_INACTIVA = "PENDIENTE_CUENTA_INACTIVA";

    private static final String OCULTAR = """
            UPDATE %s SET estado = CASE estado
                    WHEN 'ACEPTADA' THEN 'CUENTA_INACTIVA'
                    ELSE 'PENDIENTE_CUENTA_INACTIVA' END
             WHERE estado IN ('ACEPTADA', 'PENDIENTE') AND (%s)
            """;

    private static final String RESTAURAR = """
            UPDATE %s v SET estado = CASE v.estado
                    WHEN 'CUENTA_INACTIVA' THEN 'ACEPTADA'
                    ELSE 'PENDIENTE' END
             WHERE v.estado IN ('CUENTA_INACTIVA', 'PENDIENTE_CUENTA_INACTIVA') AND (%s)
            """;

    // La otra parte del vínculo está activa (activo null cuenta como activo).
    private static final String PERSONA_ACTIVA =
            "EXISTS (SELECT 1 FROM usuario u WHERE u.id_usuario = v.%s AND COALESCE(u.activo, TRUE))";
    private static final String ORGANIZACION_ACTIVA =
            "EXISTS (SELECT 1 FROM usuario u WHERE u.id_organizacion = v.id_organizacion AND COALESCE(u.activo, TRUE))";

    @Transactional
    public void inactivarCuenta(Integer idUsuario) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        usuario.setActivo(false);
        usuarioRepository.save(usuario);

        ocultar("persona_mayor_organizacion", "id_persona_mayor = :id", idUsuario);
        ocultar("persona_mayor_acompanante", "id_persona_mayor = :id OR id_acompanante = :id", idUsuario);
        ocultarSiExisteTabla("voluntario_organizacion", "id_voluntario = :id", idUsuario);

        if (usuario.getIdOrganizacion() != null) {
            ocultar("persona_mayor_organizacion", "id_organizacion = :id", usuario.getIdOrganizacion());
            ocultarSiExisteTabla("voluntario_organizacion", "id_organizacion = :id", usuario.getIdOrganizacion());
        }
    }

    @Transactional
    public void reactivarCuenta(Integer idUsuario) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        usuario.setActivo(true);
        usuarioRepository.saveAndFlush(usuario);

        restaurar("persona_mayor_organizacion",
                "v.id_persona_mayor = :id AND " + ORGANIZACION_ACTIVA, idUsuario);
        restaurar("persona_mayor_acompanante",
                "(v.id_persona_mayor = :id AND " + PERSONA_ACTIVA.formatted("id_acompanante") + ")"
                        + " OR (v.id_acompanante = :id AND " + PERSONA_ACTIVA.formatted("id_persona_mayor") + ")",
                idUsuario);
        if (existeTabla("voluntario_organizacion")) {
            restaurar("voluntario_organizacion",
                    "v.id_voluntario = :id AND " + ORGANIZACION_ACTIVA, idUsuario);
        }

        if (usuario.getIdOrganizacion() != null) {
            restaurar("persona_mayor_organizacion",
                    "v.id_organizacion = :id AND " + PERSONA_ACTIVA.formatted("id_persona_mayor"),
                    usuario.getIdOrganizacion());
            if (existeTabla("voluntario_organizacion")) {
                restaurar("voluntario_organizacion",
                        "v.id_organizacion = :id AND " + PERSONA_ACTIVA.formatted("id_voluntario"),
                        usuario.getIdOrganizacion());
            }
        }
    }

    private void ocultar(String tabla, String condicion, Integer id) {
        ejecutar(OCULTAR.formatted(tabla, condicion), id);
    }

    private void ocultarSiExisteTabla(String tabla, String condicion, Integer id) {
        if (existeTabla(tabla)) {
            ocultar(tabla, condicion, id);
        }
    }

    private void restaurar(String tabla, String condicion, Integer id) {
        ejecutar(RESTAURAR.formatted(tabla, condicion), id);
    }

    /** Borra la organización, sus actividades y sus vínculos si ya no le queda ningún usuario. */
    private void eliminarOrganizacionSinUsuarios(Integer idOrganizacion) {

        Number usuariosRestantes = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM usuario WHERE id_organizacion = :id")
                .setParameter("id", idOrganizacion)
                .getSingleResult();

        if (usuariosRestantes.longValue() > 0) {
            return;
        }

        ejecutar("DELETE FROM participacion WHERE id_actividad IN"
                + " (SELECT id_actividad FROM actividad WHERE id_organizacion = :id)", idOrganizacion);
        ejecutar("DELETE FROM actividad WHERE id_organizacion = :id", idOrganizacion);
        ejecutar("DELETE FROM persona_mayor_organizacion WHERE id_organizacion = :id", idOrganizacion);
        ejecutarSiExisteTabla("voluntario_organizacion",
                "DELETE FROM voluntario_organizacion WHERE id_organizacion = :id", idOrganizacion);
        ejecutar("DELETE FROM organizacion WHERE id_organizacion = :id", idOrganizacion);
    }

    /**
     * Ejecuta el DELETE solo si la tabla existe. voluntario_organizacion,
     * preferencia_notificacion y persona_mayor_condicion_salud las crean
     * voluntario-service, messaging-service y salud-service al arrancar; si
     * nunca han arrancado, la tabla no existe y el DELETE abortaría todo.
     */
    private void ejecutarSiExisteTabla(String tabla, String sql, Integer id) {
        if (existeTabla(tabla)) {
            ejecutar(sql, id);
        }
    }

    private boolean existeTabla(String tabla) {
        Object existe = entityManager
                .createNativeQuery("SELECT to_regclass(:tabla) IS NOT NULL")
                .setParameter("tabla", tabla)
                .getSingleResult();

        return Boolean.TRUE.equals(existe);
    }

    // id_voluntario, id_persona_mayor_proponente y estado los agrega actividad-service al arrancar.
    private boolean existeColumna(String tabla, String columna) {
        Object existe = entityManager
                .createNativeQuery("SELECT EXISTS (SELECT 1 FROM information_schema.columns"
                        + " WHERE table_name = :tabla AND column_name = :columna)")
                .setParameter("tabla", tabla)
                .setParameter("columna", columna)
                .getSingleResult();

        return Boolean.TRUE.equals(existe);
    }

    private void ejecutar(String sql, Integer id) {
        entityManager.createNativeQuery(sql)
                .setParameter("id", id)
                .executeUpdate();
    }
}
