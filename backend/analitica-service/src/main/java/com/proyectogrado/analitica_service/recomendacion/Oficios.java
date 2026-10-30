package com.proyectogrado.analitica_service.recomendacion;

import java.util.List;
import java.util.Map;

/**
 * Qué actividades le corresponden a cada profesión u oficio del catálogo
 * (categoría PROFESION; ver CatalogoProfesiones en persona-mayor-service):
 * quien fue docente puede disfrutar o apoyar un "Grupo de lectura"; quien
 * trabajó en salud, una "Jornada de vacunación".
 *
 * Las palabras se comparan por su raíz con el nombre, el tipo y la
 * descripción de la actividad (ver CoincidenciaGustos). Una profesión que
 * no esté aquí se compara solo con sus propias palabras.
 */
public final class Oficios {

    public static final String CATEGORIA = "PROFESION";

    private static final Map<String, List<String>> PALABRAS = Map.ofEntries(
            Map.entry("Labores del hogar", List.of("cocina", "manualidades", "tejer", "huerta")),
            Map.entry("Agricultura o trabajo del campo", List.of("huerta", "siembra", "jardin", "plantas", "campo")),
            Map.entry("Construcción", List.of("manualidades", "madera", "reparaciones")),
            Map.entry("Comercio o ventas", List.of("mercado", "bazar", "emprendimiento", "ventas")),
            Map.entry("Conducción o transporte", List.of("paseo", "salida", "excursion", "recorrido")),
            Map.entry("Modistería o sastrería", List.of("costura", "coser", "tejer", "tejido", "bordar", "manualidades")),
            Map.entry("Docencia", List.of("lectura", "taller", "clase", "curso", "memoria", "alfabetizacion")),
            Map.entry("Salud (enfermería o medicina)", List.of("salud", "vacunacion", "tamizaje", "presion", "medica", "bienestar")),
            Map.entry("Cocina", List.of("cocina", "culinaria", "reposteria", "almuerzo", "comida")),
            Map.entry("Servicios generales o aseo", List.of("limpieza", "aseo")),
            Map.entry("Oficina o administración", List.of("celular", "computador", "tecnologia", "finanzas")),
            Map.entry("Vigilancia o seguridad", List.of("seguridad", "prevencion")),
            Map.entry("Mecánica", List.of("reparaciones", "bicicleta", "mecanica")),
            Map.entry("Carpintería o ebanistería", List.of("madera", "carpinteria", "manualidades")),
            Map.entry("Peluquería o belleza", List.of("belleza", "peluqueria", "autocuidado")),
            Map.entry("Artesanía", List.of("manualidades", "artesanias", "dibujo", "pintura", "arte")),
            Map.entry("Fuerza pública (militar o policía)", List.of("seguridad", "prevencion", "civica")),
            Map.entry("Ingeniería o trabajo técnico", List.of("celular", "computador", "tecnologia", "reparaciones")),
            Map.entry("Trabajo social o psicología", List.of("bienestar", "memoria", "recuerdos", "acompañamiento", "conversatorio")),
            Map.entry("Estudiante", List.of("lectura", "taller", "celular", "tecnologia", "juegos")),
            Map.entry("Trabajo doméstico remunerado", List.of("cocina", "manualidades", "limpieza", "huerta")),
            Map.entry("Cuidado de personas", List.of("bienestar", "salud", "acompañamiento", "cuidado", "autocuidado")),
            Map.entry("Ganadería", List.of("campo", "huerta", "animales", "granja", "siembra")),
            Map.entry("Jardinería o paisajismo", List.of("jardin", "huerta", "plantas", "siembra", "flores")),
            Map.entry("Electricidad", List.of("reparaciones", "tecnologia", "manualidades")),
            Map.entry("Plomería", List.of("reparaciones", "manualidades")),
            Map.entry("Pintura de casas", List.of("pintura", "manualidades", "arte", "reparaciones")),
            Map.entry("Negocio propio", List.of("mercado", "bazar", "emprendimiento", "ventas", "finanzas")),
            Map.entry("Mensajería o domicilios", List.of("paseo", "salida", "recorrido", "caminata")),
            Map.entry("Zapatería", List.of("manualidades", "artesanias", "reparaciones")),
            Map.entry("Panadería", List.of("cocina", "reposteria", "culinaria", "comida")),
            Map.entry("Restaurante o atención al público", List.of("cocina", "comida", "almuerzo", "integracion")),
            Map.entry("Secretariado", List.of("celular", "computador", "tecnologia", "lectura")),
            Map.entry("Contaduría o finanzas", List.of("finanzas", "emprendimiento", "celular", "computador")),
            Map.entry("Derecho", List.of("derechos", "conversatorio", "charla", "orientacion")),
            Map.entry("Empleado público", List.of("civica", "charla", "orientacion", "tramites")),
            Map.entry("Fábrica u operario", List.of("manualidades", "reparaciones", "taller")),
            Map.entry("Música o arte", List.of("musica", "canto", "coro", "baile", "pintura", "arte", "teatro")),
            Map.entry("Sistemas o informática", List.of("celular", "computador", "tecnologia", "internet"))
    );

    private Oficios() {
    }

    /** true si la actividad tiene que ver con la profesión. */
    public static boolean relacionada(String profesion, String textoActividad) {
        if (CoincidenciaGustos.coincide(profesion, textoActividad)) {
            return true;
        }

        for (String palabra : PALABRAS.getOrDefault(profesion, List.of())) {
            if (CoincidenciaGustos.coincide(palabra, textoActividad)) {
                return true;
            }
        }
        return false;
    }
}
