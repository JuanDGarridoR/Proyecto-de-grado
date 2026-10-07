package com.proyectogrado.salud_backend.config;

import com.proyectogrado.salud_backend.model.CondicionSalud;
import com.proyectogrado.salud_backend.model.TipoCondicionSalud;
import com.proyectogrado.salud_backend.repository.CondicionSaludRepository;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.proyectogrado.salud_backend.model.TipoCondicionSalud.ALERGIA;
import static com.proyectogrado.salud_backend.model.TipoCondicionSalud.DISCAPACIDAD;
import static com.proyectogrado.salud_backend.model.TipoCondicionSalud.ENFERMEDAD;

/**
 * Llena el catálogo de enfermedades, alergias y discapacidades al arrancar
 * salud-service. Solo agrega las que faltan, así que se puede ampliar la
 * lista sin tocar la base de datos a mano y sin duplicar nada.
 *
 * Las categorías de discapacidad siguen las de la Resolución 113 de 2020
 * del Ministerio de Salud (certificado de discapacidad en Colombia).
 */
@Component
public class CatalogoCondicionSaludInicializador implements ApplicationRunner {

    /** Nombre de la opción "Otra" de cada tipo. */
    public static final String OTRA = "Otra";

    private record Entrada(TipoCondicionSalud tipo, String categoria, String nombre) {
    }

    private static final List<Entrada> CATALOGO = List.of(
            // Enfermedades crónicas frecuentes en personas mayores
            new Entrada(ENFERMEDAD, "Cardiovascular", "Hipertensión arterial"),
            new Entrada(ENFERMEDAD, "Cardiovascular", "Insuficiencia cardiaca"),
            new Entrada(ENFERMEDAD, "Cardiovascular", "Arritmia cardiaca"),
            new Entrada(ENFERMEDAD, "Cardiovascular", "Enfermedad coronaria"),
            new Entrada(ENFERMEDAD, "Metabólica", "Diabetes"),
            new Entrada(ENFERMEDAD, "Metabólica", "Colesterol o triglicéridos altos"),
            new Entrada(ENFERMEDAD, "Metabólica", "Hipotiroidismo"),
            new Entrada(ENFERMEDAD, "Metabólica", "Obesidad"),
            new Entrada(ENFERMEDAD, "Respiratoria", "EPOC"),
            new Entrada(ENFERMEDAD, "Respiratoria", "Asma"),
            new Entrada(ENFERMEDAD, "Neurológica", "Alzheimer u otra demencia"),
            new Entrada(ENFERMEDAD, "Neurológica", "Parkinson"),
            new Entrada(ENFERMEDAD, "Neurológica", "Secuelas de un accidente cerebrovascular"),
            new Entrada(ENFERMEDAD, "Osteomuscular", "Artrosis"),
            new Entrada(ENFERMEDAD, "Osteomuscular", "Artritis"),
            new Entrada(ENFERMEDAD, "Osteomuscular", "Osteoporosis"),
            new Entrada(ENFERMEDAD, "Renal", "Enfermedad renal crónica"),
            new Entrada(ENFERMEDAD, "Salud mental", "Depresión"),
            new Entrada(ENFERMEDAD, "Salud mental", "Ansiedad"),
            new Entrada(ENFERMEDAD, "Oncológica", "Cáncer"),
            new Entrada(ENFERMEDAD, "Visual", "Cataratas"),
            new Entrada(ENFERMEDAD, "Visual", "Glaucoma"),
            new Entrada(ENFERMEDAD, "Digestiva", "Gastritis o reflujo"),

            // Alergias
            new Entrada(ALERGIA, "Medicamentos", "Penicilina"),
            new Entrada(ALERGIA, "Medicamentos", "Sulfas"),
            new Entrada(ALERGIA, "Medicamentos", "Aspirina"),
            new Entrada(ALERGIA, "Medicamentos", "Antiinflamatorios (ibuprofeno, naproxeno)"),
            new Entrada(ALERGIA, "Medicamentos", "Dipirona"),
            new Entrada(ALERGIA, "Medicamentos", "Medio de contraste"),
            new Entrada(ALERGIA, "Alimentos", "Mariscos"),
            new Entrada(ALERGIA, "Alimentos", "Pescado"),
            new Entrada(ALERGIA, "Alimentos", "Maní o frutos secos"),
            new Entrada(ALERGIA, "Alimentos", "Huevo"),
            new Entrada(ALERGIA, "Alimentos", "Leche"),
            new Entrada(ALERGIA, "Alimentos", "Trigo o gluten"),
            new Entrada(ALERGIA, "Ambientales", "Polen"),
            new Entrada(ALERGIA, "Ambientales", "Ácaros del polvo"),
            new Entrada(ALERGIA, "Ambientales", "Pelo de animales"),
            new Entrada(ALERGIA, "Ambientales", "Humedad u hongos"),
            new Entrada(ALERGIA, "Ambientales", "Picaduras de insectos"),
            new Entrada(ALERGIA, "Contacto", "Látex"),
            new Entrada(ALERGIA, "Contacto", "Níquel u otros metales"),

            // Discapacidades
            new Entrada(DISCAPACIDAD, "Física", "Dificultad para caminar"),
            new Entrada(DISCAPACIDAD, "Física", "Usa silla de ruedas"),
            new Entrada(DISCAPACIDAD, "Física", "Amputación"),
            new Entrada(DISCAPACIDAD, "Física", "Parálisis parcial o total"),
            new Entrada(DISCAPACIDAD, "Visual", "Baja visión"),
            new Entrada(DISCAPACIDAD, "Visual", "Ceguera"),
            new Entrada(DISCAPACIDAD, "Auditiva", "Pérdida auditiva parcial"),
            new Entrada(DISCAPACIDAD, "Auditiva", "Sordera"),
            new Entrada(DISCAPACIDAD, "Sordoceguera", "Sordoceguera"),
            new Entrada(DISCAPACIDAD, "Intelectual", "Discapacidad intelectual"),
            new Entrada(DISCAPACIDAD, "Psicosocial", "Discapacidad psicosocial"),
            new Entrada(DISCAPACIDAD, "Múltiple", "Discapacidad múltiple")
    );

    private final CondicionSaludRepository condicionSaludRepository;

    public CatalogoCondicionSaludInicializador(CondicionSaludRepository condicionSaludRepository) {
        this.condicionSaludRepository = condicionSaludRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Entrada entrada : CATALOGO) {
            agregarSiFalta(entrada.tipo(), entrada.nombre(), entrada.categoria(), false);
        }

        // Una opción "Otra" por tipo, para lo que no está en la lista.
        for (TipoCondicionSalud tipo : TipoCondicionSalud.values()) {
            agregarSiFalta(tipo, OTRA, OTRA, true);
        }
    }

    private void agregarSiFalta(TipoCondicionSalud tipo, String nombre, String categoria, boolean esOtra) {
        if (!condicionSaludRepository.existsByTipoAndNombre(tipo, nombre)) {
            condicionSaludRepository.save(new CondicionSalud(tipo, nombre, categoria, esOtra));
        }
    }
}
