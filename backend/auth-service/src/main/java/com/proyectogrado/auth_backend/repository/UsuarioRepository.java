package com.proyectogrado.auth_backend.repository;

import com.proyectogrado.auth_backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

/**
 * Acceso a la tabla usuario. El correo y el celular sirven para encontrar
 * a la persona al iniciar sesión.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    Optional<Usuario> findByCelular(String celular);

    boolean existsByCelular(String celular);

    List<Usuario> findByIdOrganizacion(Integer idOrganizacion);

    /**
     * Suma una contraseña incorrecta. Si con ella llega al máximo, bloquea la
     * cuenta hasta la hora indicada y deja el contador en 0. Es una sola
     * sentencia para que dos intentos a la vez (uno en cada réplica) no se
     * pisen: los dos cuentan.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Usuario u
               SET u.intentosFallidos = CASE WHEN COALESCE(u.intentosFallidos, 0) + 1 >= :maximo
                                             THEN 0 ELSE COALESCE(u.intentosFallidos, 0) + 1 END,
                   u.bloqueadoHasta = CASE WHEN COALESCE(u.intentosFallidos, 0) + 1 >= :maximo
                                           THEN :bloqueadoHasta ELSE u.bloqueadoHasta END
             WHERE u.idUsuario = :idUsuario
            """)
    int registrarIntentoFallido(
            @Param("idUsuario") Integer idUsuario,
            @Param("maximo") int maximo,
            @Param("bloqueadoHasta") LocalDateTime bloqueadoHasta
    );

    /**
     * Pasa las fotos guardadas con la URL pública de antes a su ruta en el
     * bucket (quita el comienzo hasta la posición "desde"). Devuelve cuántas cambió.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Usuario u SET u.fotoPerfil = SUBSTRING(u.fotoPerfil, :desde) WHERE u.fotoPerfil LIKE :patron")
    int quitarPrefijoFotoPerfil(@Param("patron") String patron, @Param("desde") int desde);

    /** Borra los intentos fallidos y el bloqueo (al entrar o al restablecer la contraseña). */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Usuario u SET u.intentosFallidos = 0, u.bloqueadoHasta = NULL WHERE u.idUsuario = :idUsuario")
    int reiniciarIntentosFallidos(@Param("idUsuario") Integer idUsuario);
}