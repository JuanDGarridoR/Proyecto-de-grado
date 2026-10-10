package com.proyectogrado.auth_backend.security;

import com.proyectogrado.auth_backend.model.Usuario;
import com.proyectogrado.auth_backend.repository.UsuarioRepository;
import com.proyectogrado.auth_backend.repository.UsuarioRolRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Carga los usuarios para Spring Security. Un usuario se puede identificar
 * por correo o por celular, porque quien se registra con OTP puede no
 * tener correo.
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioRolRepository usuarioRolRepository;

    public UsuarioDetailsService(
            UsuarioRepository usuarioRepository,
            UsuarioRolRepository usuarioRolRepository
    ) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioRolRepository = usuarioRolRepository;
    }

    /** Busca primero por correo y, si no lo encuentra, por celular. */
    @Override
    public UserDetails loadUserByUsername(String identificador)
            throws UsernameNotFoundException {

        Usuario usuario = usuarioRepository.findByCorreo(identificador)
                .or(() -> usuarioRepository.findByCelular(identificador))
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Usuario no encontrado: " + identificador
                        )
                );

        return construirUserDetails(usuario);
    }

    /** Lo usa JwtAuthenticationFilter, ya que el token trae el id del usuario. */
    public UserDetails loadUserById(Integer idUsuario)
            throws UsernameNotFoundException {

        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Usuario no encontrado con ID: " + idUsuario
                        )
                );

        return construirUserDetails(usuario);
    }

    /**
     * Los roles quedan como ROLE_NOMBRE. Spring exige una contraseña, así que a
     * quien entra solo con OTP se le pone un texto fijo que no sirve para
     * iniciar sesión.
     */
    private UserDetails construirUserDetails(Usuario usuario) {

        String[] authorities = usuarioRolRepository
                .findByUsuario_IdUsuario(usuario.getIdUsuario())
                .stream()
                .map(usuarioRol ->
                        "ROLE_" + usuarioRol.getRol().getNombre()
                )
                .toArray(String[]::new);

        String username = usuario.getCorreo() != null
                ? usuario.getCorreo()
                : usuario.getCelular();

        return User.builder()
                .username(username)
                .password(
                        usuario.getContrasenaHash() != null
                                ? usuario.getContrasenaHash()
                                : "SIN_CONTRASENA_LOGIN_POR_OTP"
                )
                .authorities(authorities)
                // Una cuenta inactiva sigue pudiendo usar su sesión para reactivarse.
                .build();
    }
}