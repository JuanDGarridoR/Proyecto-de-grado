package com.proyectogrado.auth_backend.service;

import com.proyectogrado.auth_backend.model.Usuario;
import com.proyectogrado.auth_backend.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Eliminación de cuenta, para cualquier rol. No mira el rol del usuario:
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

    private final UsuarioRepository usuarioRepository;

    public CuentaService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public void eliminarCuenta(Integer idUsuario) {

        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

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
     * Ejecuta el DELETE solo si la tabla existe. voluntario_organizacion y
     * preferencia_notificacion las crean voluntario-service y
     * messaging-service al arrancar; si nunca han arrancado, la tabla no
     * existe y el DELETE abortaría todo.
     */
    private void ejecutarSiExisteTabla(String tabla, String sql, Integer id) {
        Object existe = entityManager
                .createNativeQuery("SELECT to_regclass(:tabla) IS NOT NULL")
                .setParameter("tabla", tabla)
                .getSingleResult();

        if (Boolean.TRUE.equals(existe)) {
            ejecutar(sql, id);
        }
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
