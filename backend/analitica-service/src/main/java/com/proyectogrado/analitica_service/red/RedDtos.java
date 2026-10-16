package com.proyectogrado.analitica_service.red;

import java.time.LocalDate;
import java.util.List;

/**
 * Red de apoyo de una organización, para el grafo y el mapa de su panel.
 * El frontend arma las dos vistas a partir de estas listas (quién está
 * vinculado con quién, qué gustos tiene cada uno y a qué actividades fue).
 *
 * De cada persona mayor solo sale el barrio, nunca su dirección: en el
 * mapa se ven agrupadas por barrio.
 */
public final class RedDtos {

    private RedDtos() {
    }

    /** La organización y su sede (latitud y longitud null si aún no se ubica). */
    public record Sede(Integer id, String nombre, Double latitud, Double longitud) {
    }

    /** Persona mayor vinculada: su barrio (o null), sus gustos y las actividades en que se inscribió. */
    public record PersonaRed(Integer id, String nombre, String barrio,
                             List<Integer> gustos, List<Integer> actividades) {
    }

    /** Acompañante de alguna persona mayor de la organización. */
    public record AcompananteRed(Integer id, String nombre) {
    }

    /** Vínculo aceptado entre una persona mayor y su acompañante ("Hija", "Vecino"...). */
    public record VinculoRed(Integer idPersonaMayor, Integer idAcompanante, String relacion) {
    }

    /** Voluntario vinculado a la organización. */
    public record VoluntarioRed(Integer id, String nombre, List<Integer> gustos) {
    }

    public record GustoRed(Integer id, String nombre, String categoria) {
    }

    /** Centro aproximado de un barrio donde vive al menos una persona mayor. */
    public record BarrioRed(String nombre, double latitud, double longitud) {
    }

    /** Actividad reciente o próxima, con su punto de encuentro si ya se ubicó. */
    public record ActividadRed(Integer id, String nombre, LocalDate fecha, String lugar,
                               Double latitud, Double longitud) {
    }

    public record RedResponse(
            Sede organizacion,
            List<PersonaRed> personasMayores,
            List<AcompananteRed> acompanantes,
            List<VinculoRed> vinculos,
            List<VoluntarioRed> voluntarios,
            List<GustoRed> gustos,
            List<BarrioRed> barrios,
            List<ActividadRed> actividades
    ) {
    }
}
