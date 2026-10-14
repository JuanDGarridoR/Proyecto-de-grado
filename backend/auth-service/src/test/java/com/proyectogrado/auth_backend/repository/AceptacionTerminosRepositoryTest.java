package com.proyectogrado.auth_backend.repository;

import com.proyectogrado.auth_backend.model.AceptacionTerminos;
import com.proyectogrado.auth_backend.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Constancias de aceptación de los términos (RF-69) en una base H2 en
 * memoria: las cuentas anteriores reciben la suya una sola vez, con la fecha
 * de creación de la cuenta, y la constancia sobrevive al borrar la cuenta.
 */
@DataJpaTest
class AceptacionTerminosRepositoryTest {

    @Autowired
    private AceptacionTerminosRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Usuario usuario(String nombre, String celular) {
        Usuario usuario = new Usuario();
        usuario.setNombreUsuario(nombre);
        usuario.setCelular(celular);
        return usuarioRepository.saveAndFlush(usuario);
    }

    @Test
    void lasCuentasAnterioresRecibenSuConstanciaUnaSolaVez() {
        Usuario anterior = usuario("Rosa Díaz", "+573001230011");
        Usuario nuevo = usuario("Luis Peña", "+573001230012");
        repository.saveAndFlush(new AceptacionTerminos(nuevo.getIdUsuario(), LocalDateTime.now(), "2026-10"));

        assertEquals(1, repository.registrarCuentasAnteriores());
        // La segunda vez no hay nada que hacer: no se duplican constancias.
        assertEquals(0, repository.registrarCuentasAnteriores());
        entityManager.clear();

        AceptacionTerminos constancia = repository.findAll().stream()
                .filter(a -> a.getIdUsuario().equals(anterior.getIdUsuario()))
                .findFirst().orElseThrow();

        assertTrue(constancia.isAcepto());
        assertEquals(AceptacionTerminos.REGISTRO_ANTERIOR, constancia.getOrigen());
        assertNull(constancia.getVersionTerminos());
        assertEquals(anterior.getFechaCreacion().withNano(0), constancia.getFechaAceptacion().withNano(0));
    }

    @Test
    void laConstanciaSeConservaAlBorrarLaCuenta() {
        Usuario usuario = usuario("Ana Rojas", "+573001230013");
        repository.saveAndFlush(new AceptacionTerminos(usuario.getIdUsuario(), LocalDateTime.now(), "2026-10"));

        usuarioRepository.delete(usuario);
        usuarioRepository.flush();
        entityManager.clear();

        List<AceptacionTerminos> constancias = repository.findAll();
        assertEquals(1, constancias.size());
        assertEquals(usuario.getIdUsuario(), constancias.get(0).getIdUsuario());
    }
}
