package com.proyectogrado.auth_backend.controller;

import com.proyectogrado.auth_backend.dto.LoginResponse;
import com.proyectogrado.auth_backend.model.Acompanante;
import com.proyectogrado.auth_backend.model.PersonaMayor;
import com.proyectogrado.auth_backend.model.Rol;
import com.proyectogrado.auth_backend.model.Usuario;
import com.proyectogrado.auth_backend.model.UsuarioRol;
import com.proyectogrado.auth_backend.repository.AcompananteRepository;
import com.proyectogrado.auth_backend.repository.PersonaMayorRepository;
import com.proyectogrado.auth_backend.repository.RolRepository;
import com.proyectogrado.auth_backend.repository.UsuarioRepository;
import com.proyectogrado.auth_backend.repository.UsuarioRolRepository;
import com.proyectogrado.auth_backend.security.JwtService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Cambio de rol entre persona mayor y acompañante (RF-76).
 *
 * Una misma cuenta (un solo usuario: nombre, celular, dirección, foto...)
 * puede tener los dos roles; cada uno tiene su fila en usuario_rol y en su
 * tabla (persona_mayor o acompanante). El token lleva el rol con el que se
 * está usando la cuenta, así que cambiar de rol es pedir un token nuevo.
 *
 * Si el usuario aún no tiene el otro rol, al cambiar se le activa con su
 * misma información: no hay que llenar otro registro.
 */
@RestController
@RequestMapping("/api/auth/roles")
public class RolController {

    /** Roles entre los que se puede cambiar (y que se pueden activar desde la barra superior). */
    static final Set<String> ROLES_INTERCAMBIABLES = Set.of("PERSONA_MAYOR", "ACOMPANANTE");

    /** Roles de la cuenta, para saber si el óvalo de la barra superior ofrece cambiar o activar. */
    public record RolesResponse(List<String> roles) {
    }

    public record CambiarRolRequest(String rol) {
    }

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final PersonaMayorRepository personaMayorRepository;
    private final AcompananteRepository acompananteRepository;
    private final JwtService jwtService;

    public RolController(
            UsuarioRepository usuarioRepository,
            RolRepository rolRepository,
            UsuarioRolRepository usuarioRolRepository,
            PersonaMayorRepository personaMayorRepository,
            AcompananteRepository acompananteRepository,
            JwtService jwtService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.usuarioRolRepository = usuarioRolRepository;
        this.personaMayorRepository = personaMayorRepository;
        this.acompananteRepository = acompananteRepository;
        this.jwtService = jwtService;
    }

    @GetMapping
    public RolesResponse roles(@RequestHeader("Authorization") String authorizationHeader) {
        return new RolesResponse(rolesDe(idUsuarioDelToken(authorizationHeader)));
    }

    /**
     * Devuelve una sesión nueva (token) con el rol pedido. Solo entre persona
     * mayor y acompañante, y solo si la cuenta ya usa uno de los dos.
     */
    @PostMapping("/cambiar")
    @Transactional
    public ResponseEntity<LoginResponse> cambiar(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody CambiarRolRequest request
    ) {
        Integer idUsuario = idUsuarioDelToken(authorizationHeader);
        String rolNuevo = request == null || request.rol() == null ? "" : request.rol().trim().toUpperCase();

        if (!ROLES_INTERCAMBIABLES.contains(rolNuevo)) {
            return error(HttpStatus.BAD_REQUEST, "Solo se puede cambiar entre persona mayor y acompañante");
        }

        Usuario usuario = usuarioRepository.findById(idUsuario).orElse(null);
        if (usuario == null) {
            return error(HttpStatus.NOT_FOUND, "La cuenta no existe");
        }

        List<String> roles = rolesDe(idUsuario);
        if (roles.stream().noneMatch(ROLES_INTERCAMBIABLES::contains)) {
            return error(HttpStatus.FORBIDDEN, "Tu cuenta no puede cambiar a ese rol");
        }

        String mensaje = "Ahora estás como " + nombreDe(rolNuevo);

        if (!roles.contains(rolNuevo)) {
            activarRol(usuario, rolNuevo);
            mensaje = "Activaste el rol de " + nombreDe(rolNuevo) + " con tu misma información";
        }

        // La próxima vez que inicie sesión, entra con este rol.
        usuario.setUltimoRol(rolNuevo);
        usuarioRepository.save(usuario);

        return ResponseEntity.ok(new LoginResponse(
                jwtService.generarToken(idUsuario, rolNuevo),
                rolNuevo,
                mensaje,
                idUsuario,
                usuario.getNombreUsuario()
        ));
    }

    /** Le da a la cuenta el otro rol: su fila en usuario_rol y su perfil de rol. */
    private void activarRol(Usuario usuario, String nombreRol) {
        Rol rol = rolRepository.findByNombre(nombreRol)
                .orElseThrow(() -> new IllegalStateException("No existe el rol " + nombreRol));

        usuarioRolRepository.save(new UsuarioRol(usuario, rol));

        // Los datos personales viven en usuario: el perfil de rol solo enlaza la cuenta.
        if ("PERSONA_MAYOR".equals(nombreRol) && !personaMayorRepository.existsById(usuario.getIdUsuario())) {
            personaMayorRepository.save(new PersonaMayor(usuario));
        }

        if ("ACOMPANANTE".equals(nombreRol) && !acompananteRepository.existsById(usuario.getIdUsuario())) {
            acompananteRepository.save(new Acompanante(usuario));
        }
    }

    private List<String> rolesDe(Integer idUsuario) {
        return usuarioRolRepository.findByUsuario_IdUsuario(idUsuario).stream()
                .filter(usuarioRol -> usuarioRol.getRol() != null)
                .map(usuarioRol -> usuarioRol.getRol().getNombre())
                .toList();
    }

    private static String nombreDe(String rol) {
        return "PERSONA_MAYOR".equals(rol) ? "persona mayor" : "acompañante";
    }

    private static ResponseEntity<LoginResponse> error(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(new LoginResponse(null, null, mensaje, null, null));
    }

    private Integer idUsuarioDelToken(String authorizationHeader) {
        return jwtService.extraerIdUsuario(authorizationHeader.substring(7));
    }
}
