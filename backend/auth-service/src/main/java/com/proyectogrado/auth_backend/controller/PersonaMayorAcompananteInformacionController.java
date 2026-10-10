package com.proyectogrado.auth_backend.controller;

import com.proyectogrado.auth_backend.dto.ActualizarInformacionRequest;
import com.proyectogrado.auth_backend.dto.InformacionUsuarioResponse;
import com.proyectogrado.auth_backend.model.PersonaMayor;
import com.proyectogrado.auth_backend.model.Usuario;
import com.proyectogrado.auth_backend.repository.PersonaMayorRepository;
import com.proyectogrado.auth_backend.repository.UsuarioRepository;
import com.proyectogrado.auth_backend.security.JwtService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.proyectogrado.auth_backend.controller.UsuarioInformacionController.textoOpcional;
import static com.proyectogrado.auth_backend.controller.UsuarioInformacionController.validarDatosDeSalud;

/**
 * Perfil de una persona mayor consultado y editado por su acompañante:
 * nombre, fecha de nacimiento, género, dirección, EPS, IPS, dirección de
 * la IPS y si vive sola. Solo si el vínculo entre los dos está ACEPTADO.
 *
 * El correo y el celular no se editan aquí: son los datos con los que la
 * persona mayor inicia sesión, y solo ella los cambia.
 */
@RestController
@RequestMapping("/api/acompanante/personas-mayores/{idPersonaMayor}/informacion")
public class PersonaMayorAcompananteInformacionController {

    private final UsuarioRepository usuarioRepository;
    private final PersonaMayorRepository personaMayorRepository;
    private final JwtService jwtService;

    public PersonaMayorAcompananteInformacionController(
            UsuarioRepository usuarioRepository,
            PersonaMayorRepository personaMayorRepository,
            JwtService jwtService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.personaMayorRepository = personaMayorRepository;
        this.jwtService = jwtService;
    }

    @GetMapping
    public ResponseEntity<?> obtener(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Integer idPersonaMayor
    ) {
        if (!puedeGestionar(authorizationHeader, idPersonaMayor)) {
            return sinAcceso();
        }

        Usuario usuario = usuarioRepository.findById(idPersonaMayor).orElse(null);
        PersonaMayor personaMayor = personaMayorRepository.findById(idPersonaMayor).orElse(null);
        if (usuario == null || personaMayor == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(aRespuesta(usuario, personaMayor));
    }

    /** Guarda los cambios del perfil. Si llega un correo, se ignora. */
    @PutMapping
    public ResponseEntity<?> actualizar(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Integer idPersonaMayor,
            @RequestBody ActualizarInformacionRequest request
    ) {
        if (!puedeGestionar(authorizationHeader, idPersonaMayor)) {
            return sinAcceso();
        }

        Usuario usuario = usuarioRepository.findById(idPersonaMayor).orElse(null);
        PersonaMayor personaMayor = personaMayorRepository.findById(idPersonaMayor).orElse(null);
        if (usuario == null || personaMayor == null) {
            return ResponseEntity.notFound().build();
        }

        if (request.getNombre() == null || request.getNombre().isBlank()) {
            return ResponseEntity.badRequest().body("El nombre es obligatorio");
        }

        String eps = textoOpcional(request.getEps());
        String ips = textoOpcional(request.getIps());
        String direccionIps = textoOpcional(request.getDireccionIps());

        String errorSalud = validarDatosDeSalud(eps, ips, direccionIps);
        if (errorSalud != null) {
            return ResponseEntity.badRequest().body(errorSalud);
        }

        usuario.setNombreUsuario(request.getNombre().trim());
        usuario.setFechaNacimiento(request.getFechaNacimiento());
        usuario.setGenero(textoOpcional(request.getGenero()));
        usuario.setDireccion(textoOpcional(request.getDireccion()));
        usuario = usuarioRepository.save(usuario);

        personaMayor.setEps(eps);
        personaMayor.setIps(ips);
        personaMayor.setDireccionIps(direccionIps);
        personaMayor.setViveSolo(request.getViveSolo());
        personaMayor = personaMayorRepository.save(personaMayor);

        return ResponseEntity.ok(aRespuesta(usuario, personaMayor));
    }

    /**
     * Si quien llama acompaña a la persona mayor. Como en el resto de
     * auth-service, el usuario se saca del token.
     */
    private boolean puedeGestionar(String authorizationHeader, Integer idPersonaMayor) {
        Integer idAcompanante = jwtService.extraerIdUsuario(authorizationHeader.substring(7));
        return personaMayorRepository.esAcompananteAceptado(idPersonaMayor, idAcompanante);
    }

    private ResponseEntity<String> sinAcceso() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("No acompañas a esta persona mayor");
    }

    /** tieneContrasena no aplica aquí: el acompañante no cambia la contraseña. */
    private InformacionUsuarioResponse aRespuesta(Usuario usuario, PersonaMayor personaMayor) {
        return new InformacionUsuarioResponse(
                usuario.getIdUsuario(),
                usuario.getNombreUsuario(),
                usuario.getCelular(),
                usuario.getCorreo(),
                usuario.getFechaNacimiento(),
                usuario.getGenero(),
                usuario.getDireccion(),
                personaMayor.getEps(),
                personaMayor.getIps(),
                personaMayor.getDireccionIps(),
                personaMayor.getViveSolo(),
                false,
                !Boolean.FALSE.equals(usuario.getActivo())
        );
    }
}
