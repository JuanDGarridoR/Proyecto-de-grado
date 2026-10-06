package com.proyectogrado.auth_backend.repository;

import com.proyectogrado.auth_backend.model.Rol;
import com.proyectogrado.auth_backend.model.Usuario;
import com.proyectogrado.auth_backend.model.UsuarioRol;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistencia de las cuentas en una base H2 en memoria (BD-01 a BD-03):
 * inserción, consulta, actualización, borrado, llaves únicas y llaves
 * foráneas de usuario, rol y usuario_rol. Cada prueba se deshace al terminar.
 */
@DataJpaTest
class UsuarioRepositoryTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private UsuarioRolRepository usuarioRolRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Rol personaMayor;

    @BeforeEach
    void setUp() {
        personaMayor = rolRepository.saveAndFlush(new Rol("PERSONA_MAYOR"));
    }

    private Usuario usuario(String nombre, String celular, String correo) {
        Usuario usuario = new Usuario();
        usuario.setNombreUsuario(nombre);
        usuario.setCelular(celular);
        usuario.setCorreo(correo);
        usuario.setFechaNacimiento(LocalDate.of(1952, 5, 20));
        usuario.setGenero("Masculino");
        usuario.setDireccion("Calle 76 Sur # 14-30");
        return usuario;
    }

    @Test
    void unUsuarioGuardadoSeRecuperaConLosMismosDatos() {
        Usuario guardado = usuarioRepository.saveAndFlush(usuario("Luis Peña", "+573001230001", "luis@vitamas.co"));
        // Se vacía la caché de JPA para que la consulta vaya a la base.
        entityManager.clear();

        Usuario leido = usuarioRepository.findByCorreo("luis@vitamas.co").orElseThrow();

        assertEquals(guardado.getIdUsuario(), leido.getIdUsuario());
        assertEquals("Luis Peña", leido.getNombreUsuario());
        assertEquals("+573001230001", leido.getCelular());
        assertEquals(LocalDate.of(1952, 5, 20), leido.getFechaNacimiento());
        // Valores que pone la entidad al insertarse.
        assertNotNull(leido.getFechaCreacion());
        assertTrue(leido.getActivo());
        assertTrue(usuarioRepository.existsByCelular("+573001230001"));
    }

    @Test
    void losCambiosDeUnUsuarioQuedanGuardados() {
        Usuario guardado = usuarioRepository.saveAndFlush(usuario("Luis Peña", "+573001230001", "luis@vitamas.co"));
        entityManager.clear();

        Usuario editado = usuarioRepository.findById(guardado.getIdUsuario()).orElseThrow();
        editado.setNombreUsuario("Luis Alberto Peña");
        editado.setDireccion("Carrera 1 # 90-12 Sur");
        usuarioRepository.saveAndFlush(editado);
        entityManager.clear();

        Usuario leido = usuarioRepository.findById(guardado.getIdUsuario()).orElseThrow();
        assertEquals("Luis Alberto Peña", leido.getNombreUsuario());
        assertEquals("Carrera 1 # 90-12 Sur", leido.getDireccion());
    }

    @Test
    void noSePuedenRegistrarDosCuentasConElMismoCelular() {
        usuarioRepository.saveAndFlush(usuario("Luis Peña", "+573001230001", "luis@vitamas.co"));

        assertThrows(DataIntegrityViolationException.class, () ->
                usuarioRepository.saveAndFlush(usuario("Otro Luis", "+573001230001", "otro@vitamas.co")));
    }

    @Test
    void noSePuedenRegistrarDosCuentasConElMismoCorreo() {
        usuarioRepository.saveAndFlush(usuario("Luis Peña", "+573001230001", "luis@vitamas.co"));

        assertThrows(DataIntegrityViolationException.class, () ->
                usuarioRepository.saveAndFlush(usuario("Otro Luis", "+573001230002", "luis@vitamas.co")));
    }

    @Test
    void noSePuedeRepetirUnRolDelCatalogo() {
        assertThrows(DataIntegrityViolationException.class, () ->
                rolRepository.saveAndFlush(new Rol("PERSONA_MAYOR")));
    }

    @Test
    void losRolesDelUsuarioSeLeenConSuNombre() {
        Usuario guardado = usuarioRepository.saveAndFlush(usuario("Luis Peña", "+573001230001", "luis@vitamas.co"));
        usuarioRolRepository.saveAndFlush(new UsuarioRol(guardado, personaMayor));
        entityManager.clear();

        List<UsuarioRol> roles = usuarioRolRepository.findByUsuario_IdUsuario(guardado.getIdUsuario());

        assertEquals(1, roles.size());
        assertEquals("PERSONA_MAYOR", roles.get(0).getRol().getNombre());
    }

    @Test
    void unRolAsignadoTieneQueReferirAUnUsuarioQueExista() {
        // id_usuario 9999 no existe: la llave foránea lo impide.
        assertThrows(PersistenceException.class, () -> entityManager.getEntityManager()
                .createNativeQuery("insert into usuario_rol (id_usuario, id_rol) values (9999, :rol)")
                .setParameter("rol", personaMayor.getIdRol())
                .executeUpdate());
    }

    @Test
    void noSeBorraUnUsuarioQueTodaviaTieneRolesAsignados() {
        Usuario guardado = usuarioRepository.saveAndFlush(usuario("Luis Peña", "+573001230001", "luis@vitamas.co"));
        usuarioRolRepository.saveAndFlush(new UsuarioRol(guardado, personaMayor));

        assertThrows(PersistenceException.class, () -> entityManager.getEntityManager()
                .createNativeQuery("delete from usuario where id_usuario = :id")
                .setParameter("id", guardado.getIdUsuario())
                .executeUpdate());
    }

    @Test
    void alBorrarPrimeroSusRolesElUsuarioSeEliminaPorCompleto() {
        Usuario guardado = usuarioRepository.saveAndFlush(usuario("Luis Peña", "+573001230001", "luis@vitamas.co"));
        usuarioRolRepository.saveAndFlush(new UsuarioRol(guardado, personaMayor));
        Integer id = guardado.getIdUsuario();

        usuarioRolRepository.deleteAll(usuarioRolRepository.findByUsuario_IdUsuario(id));
        usuarioRepository.deleteById(id);
        usuarioRepository.flush();
        entityManager.clear();

        assertFalse(usuarioRepository.existsById(id));
        assertTrue(usuarioRolRepository.findByUsuario_IdUsuario(id).isEmpty());
    }
}
