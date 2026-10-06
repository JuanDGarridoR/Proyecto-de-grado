package com.proyectogrado.messaging_backend.repository;

import com.proyectogrado.messaging_backend.model.Notificacion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Consultas del historial de notificaciones sobre una base H2 en memoria
 * (RF-26 y RF-27): orden y límite de la campanita, conteo de no leídas y
 * marcado como leídas.
 */
@DataJpaTest
class NotificacionRepositoryTest {

    private static final String CELULAR = "+573001112233";
    private static final String OTRO_CELULAR = "+573009998877";

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private void guardar(String celular, String mensaje, Instant fechaEnvio) {
        Notificacion notificacion = new Notificacion(celular, mensaje);
        ReflectionTestUtils.setField(notificacion, "fechaEnvio", fechaEnvio);
        notificacionRepository.save(notificacion);
    }

    @Test
    void laCampanitaMuestraLasVeinteMasRecientesDeLaMasNuevaALaMasVieja() {
        Instant inicio = Instant.parse("2026-10-01T12:00:00Z");
        for (int i = 0; i < 25; i++) {
            guardar(CELULAR, "Aviso " + i, inicio.plusSeconds(60L * i));
        }
        guardar(OTRO_CELULAR, "Aviso de otra persona", inicio.plusSeconds(100_000));
        entityManager.flush();
        entityManager.clear();

        List<Notificacion> recientes = notificacionRepository.findTop20ByCelularOrderByFechaEnvioDesc(CELULAR);

        assertEquals(20, recientes.size());
        assertEquals("Aviso 24", recientes.get(0).getMensaje());
        assertEquals("Aviso 5", recientes.get(19).getMensaje());
    }

    @Test
    void marcarLeidasSoloAfectaLasDeEseCelular() {
        Instant ahora = Instant.now();
        guardar(CELULAR, "Aviso 1", ahora);
        guardar(CELULAR, "Aviso 2", ahora.plusSeconds(1));
        guardar(OTRO_CELULAR, "Aviso de otra persona", ahora);
        entityManager.flush();

        assertEquals(2, notificacionRepository.countByCelularAndLeidaFalse(CELULAR));

        int marcadas = notificacionRepository.marcarLeidas(CELULAR);
        entityManager.clear();

        assertEquals(2, marcadas);
        assertEquals(0, notificacionRepository.countByCelularAndLeidaFalse(CELULAR));
        assertEquals(1, notificacionRepository.countByCelularAndLeidaFalse(OTRO_CELULAR));
    }
}
