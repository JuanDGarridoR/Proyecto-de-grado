package com.proyectogrado.salud_backend.repository;

import com.proyectogrado.salud_backend.model.Medicamento;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistencia de los medicamentos en una base H2 en memoria (BD-01, BD-02
 * y BD-03): inserción, consulta, actualización, borrado, columnas
 * obligatorias y las actualizaciones condicionales con las que el scheduler
 * evita repetir un aviso.
 */
@DataJpaTest
class MedicamentoRepositoryTest {

    private static final LocalDateTime TOMA = LocalDateTime.of(2026, 10, 6, 8, 0);

    @Autowired
    private MedicamentoRepository medicamentoRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Medicamento medicamento(String nombre, Integer intervaloHoras) {
        Medicamento medicamento = new Medicamento();
        medicamento.setIdPersonaMayor(10);
        medicamento.setNombre(nombre);
        medicamento.setDosis("50mg");
        medicamento.setIntervaloHoras(intervaloHoras);
        medicamento.setHora(LocalTime.of(8, 0));
        medicamento.setFechaInicio(LocalDate.of(2026, 10, 6));
        medicamento.setProximaToma(TOMA);
        return medicamento;
    }

    @Test
    void unMedicamentoGuardadoSeRecuperaIgual() {
        Integer id = medicamentoRepository.saveAndFlush(medicamento("Losartán", 12)).getIdMedicamento();
        entityManager.clear();

        Medicamento leido = medicamentoRepository.findById(id).orElseThrow();

        assertEquals("Losartán", leido.getNombre());
        assertEquals(12, leido.getIntervaloHoras());
        assertEquals(TOMA, leido.getProximaToma());
        // Valor por defecto al insertarse.
        assertTrue(leido.getActivo());
        assertEquals(1, medicamentoRepository.findByIdPersonaMayor(10).size());
    }

    @Test
    void losCambiosDeUnMedicamentoQuedanGuardados() {
        Integer id = medicamentoRepository.saveAndFlush(medicamento("Losartán", 12)).getIdMedicamento();
        entityManager.clear();

        Medicamento editado = medicamentoRepository.findById(id).orElseThrow();
        editado.setDosis("100mg");
        editado.setIntervaloHoras(24);
        medicamentoRepository.saveAndFlush(editado);
        entityManager.clear();

        Medicamento leido = medicamentoRepository.findById(id).orElseThrow();
        assertEquals("100mg", leido.getDosis());
        assertEquals(24, leido.getIntervaloHoras());
    }

    @Test
    void unMedicamentoBorradoYaNoExiste() {
        Integer id = medicamentoRepository.saveAndFlush(medicamento("Losartán", 12)).getIdMedicamento();

        medicamentoRepository.deleteById(id);
        medicamentoRepository.flush();
        entityManager.clear();

        assertFalse(medicamentoRepository.existsById(id));
        assertTrue(medicamentoRepository.findByIdPersonaMayor(10).isEmpty());
    }

    @Test
    void sinNombreElMedicamentoNoSeGuarda() {
        assertThrows(DataIntegrityViolationException.class,
                () -> medicamentoRepository.saveAndFlush(medicamento(null, 12)));
    }

    @Test
    void sinIntervaloElMedicamentoNoSeGuarda() {
        assertThrows(DataIntegrityViolationException.class,
                () -> medicamentoRepository.saveAndFlush(medicamento("Losartán", null)));
    }

    @Test
    void unNombreVacioSiLlegaALaBase() {
        // La base solo exige que no sea null: el texto vacío pasa. Por eso la
        // validación tiene que estar en el controlador (ver hallazgo CP-GMI-002).
        Medicamento guardado = medicamentoRepository.saveAndFlush(medicamento("", 12));

        assertNotNull(guardado.getIdMedicamento());
    }

    @Test
    void elAvisoPrevioDeUnaTomaSoloSePuedeReservarUnaVez() {
        Integer id = medicamentoRepository.saveAndFlush(medicamento("Losartán", 12)).getIdMedicamento();
        LocalDateTime ventana = TOMA.minusMinutes(15);

        assertEquals(1, medicamentoRepository.reservarAvisoPrevio(id, TOMA, ventana, ventana));
        assertEquals(0, medicamentoRepository.reservarAvisoPrevio(id, TOMA, ventana, ventana.plusMinutes(1)));
    }

    @Test
    void avanzarLaTomaSoloFuncionaSiNadieLaAvanzoAntes() {
        Integer id = medicamentoRepository.saveAndFlush(medicamento("Losartán", 12)).getIdMedicamento();
        LocalDateTime siguiente = TOMA.plusHours(12);

        assertEquals(1, medicamentoRepository.avanzarToma(id, TOMA, siguiente, TOMA));
        assertEquals(0, medicamentoRepository.avanzarToma(id, TOMA, siguiente, TOMA));
        entityManager.clear();

        Medicamento leido = medicamentoRepository.findById(id).orElseThrow();
        assertEquals(siguiente, leido.getProximaToma());
        assertEquals(TOMA, leido.getUltimaToma());
    }
}
