package com.proyectogrado.persona_mayor_service.config;

import com.proyectogrado.persona_mayor_service.model.Gusto;
import com.proyectogrado.persona_mayor_service.repository.GustoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Opciones base del catálogo de intereses (tabla gusto), en sus cuatro
 * categorías: gustos, talentos, pasatiempos (HOBBY) y profesiones u
 * oficios (PROFESION: a qué se dedica o se dedicó la persona). Son
 * generales a propósito, para que casi cualquiera encuentre algo suyo; si
 * no, la persona agrega el suyo desde la pantalla de intereses.
 *
 * Al arrancar se agregan las que falten, así llegan a cualquier base sin
 * correr scripts. Las que ya existen (aunque cambien mayúsculas) no se
 * tocan, ni siquiera si están en otra categoría: el nombre es único. Las
 * opciones casi repetidas de FUSIONES se juntan con la del catálogo. El
 * recomendador de organizaciones (analitica-service, Oficios) usa los
 * mismos nombres de las profesiones, y el frontend les pone su icono por
 * el nombre (intereses.ts, NOMBRE_A_ICONO).
 */
@Component
public class CatalogoIntereses {

    private static final Logger log = LoggerFactory.getLogger(CatalogoIntereses.class);

    public static final List<String> GUSTOS = List.of(
            "Leer",
            "Escuchar música",
            "Música tradicional y de antaño",
            "Ver televisión o películas",
            "Ver novelas",
            "Escuchar radio",
            "Ver deportes",
            "Noticias y actualidad",
            "Cocinar",
            "Comida típica",
            "Pasear al aire libre",
            "Viajar",
            "Plantas y naturaleza",
            "Compartir con mascotas",
            "Compartir en familia",
            "Conversar con amigos",
            "Fiestas y celebraciones",
            "Actividades religiosas",
            "Historia y tradiciones",
            "Museos y lugares históricos",
            "Aprender cosas nuevas",
            "Chistes y buen humor",
            "Fotografía"
    );

    public static final List<String> TALENTOS = List.of(
            "Cantar",
            "Tocar un instrumento",
            "Actuación o teatro",
            "Pintar o dibujar",
            "Escribir",
            "Recitar poesía",
            "Contar historias",
            "Manualidades y artesanías",
            "Costura o tejido",
            "Bordado",
            "Cocina tradicional",
            "Repostería",
            "Carpintería",
            "Reparaciones de la casa",
            "Cuidar plantas o huerta",
            "Remedios caseros",
            "Enseñar o explicar",
            "Escuchar y aconsejar",
            "Organizar eventos o reuniones",
            "Hacer cuentas y negocios",
            "Primeros auxilios",
            "Hablar otro idioma",
            "Usar el celular o el computador"
    );

    public static final List<String> PASATIEMPOS = List.of(
            "Caminar",
            "Ejercicio físico",
            "Aeróbicos",
            "Baile",
            "Yoga o estiramiento",
            "Tai chi",
            "Ciclismo",
            "Natación",
            "Pesca",
            "Jardinería",
            "Juegos de mesa",
            "Parqués",
            "Dominó",
            "Cartas o naipes",
            "Ajedrez",
            "Bingo",
            "Tejo",
            "Juego de rana",
            "Rompecabezas",
            "Crucigramas",
            "Sopas de letras",
            "Colorear o pintar mandalas",
            "Coleccionar cosas",
            "Voluntariado"
    );

    public static final List<String> PROFESIONES = List.of(
            "Labores del hogar",
            "Trabajo doméstico remunerado",
            "Cuidado de personas",
            "Agricultura o trabajo del campo",
            "Ganadería",
            "Jardinería o paisajismo",
            "Construcción",
            "Electricidad",
            "Plomería",
            "Pintura de casas",
            "Comercio o ventas",
            "Negocio propio",
            "Conducción o transporte",
            "Mensajería o domicilios",
            "Modistería o sastrería",
            "Zapatería",
            "Docencia",
            "Salud (enfermería o medicina)",
            "Cocina",
            "Panadería",
            "Restaurante o atención al público",
            "Servicios generales o aseo",
            "Oficina o administración",
            "Secretariado",
            "Contaduría o finanzas",
            "Derecho",
            "Empleado público",
            "Vigilancia o seguridad",
            "Mecánica",
            "Carpintería o ebanistería",
            "Fábrica u operario",
            "Peluquería o belleza",
            "Artesanía",
            "Música o arte",
            "Fuerza pública (militar o policía)",
            "Ingeniería o trabajo técnico",
            "Sistemas o informática",
            "Trabajo social o psicología",
            "Estudiante"
    );

    /** Cada categoría con sus opciones, en el orden en que se agregan. */
    public static final Map<String, List<String>> POR_CATEGORIA = categorias();

    private static Map<String, List<String>> categorias() {
        Map<String, List<String>> mapa = new LinkedHashMap<>();
        mapa.put("GUSTO", GUSTOS);
        mapa.put("TALENTO", TALENTOS);
        mapa.put("HOBBY", PASATIEMPOS);
        mapa.put("PROFESION", PROFESIONES);
        return mapa;
    }

    /**
     * Opciones casi repetidas (de versiones anteriores del catálogo o del
     * script de datos ficticios) y la opción del catálogo que queda en su
     * lugar. Quien tenía marcada la vieja queda con la nueva.
     */
    public static final Map<String, String> FUSIONES = Map.ofEntries(
            Map.entry("Novelas de televisión", "Ver novelas"),
            Map.entry("Películas clásicas", "Ver televisión o películas"),
            Map.entry("Ir a cine", "Ver televisión o películas"),
            Map.entry("Fútbol por televisión", "Ver deportes"),
            Map.entry("Tomar café con amigos", "Conversar con amigos"),
            Map.entry("Misa dominical", "Actividades religiosas"),
            Map.entry("Boleros", "Música tradicional y de antaño"),
            Map.entry("Música ranchera", "Música tradicional y de antaño"),
            Map.entry("Música carranguera", "Música tradicional y de antaño"),
            Map.entry("Paseos y excursiones", "Pasear al aire libre"),
            Map.entry("Lectura", "Leer"),
            Map.entry("Club de lectura", "Leer"),
            Map.entry("Canto", "Cantar"),
            Map.entry("Tejido", "Costura o tejido"),
            Map.entry("Costura", "Costura o tejido"),
            Map.entry("Pintura", "Pintar o dibujar"),
            Map.entry("Caminatas", "Caminar"),
            Map.entry("Caminatas ecológicas", "Caminar"),
            Map.entry("Bailoterapia", "Baile"),
            Map.entry("Huerta casera", "Jardinería")
    );

    /** Tablas con los intereses marcados de cada persona, y la columna de la persona. */
    private static final Map<String, String> TABLAS_DE_SELECCIONES = Map.of(
            "persona_mayor_gusto", "id_persona_mayor",
            "voluntario_gusto", "id_voluntario"
    );

    private final GustoRepository gustoRepository;
    private final JdbcTemplate jdbc;

    public CatalogoIntereses(GustoRepository gustoRepository, JdbcTemplate jdbc) {
        this.gustoRepository = gustoRepository;
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        agregarFaltantes();
        fusionarRepetidas();
    }

    /**
     * Pasa a quien tenía marcada una opción repetida a la que queda y borra
     * la repetida. Si no existe la repetida, no hace nada, así que se puede
     * correr en cada arranque. voluntario_gusto es de voluntario-service y
     * puede no existir todavía.
     */
    public void fusionarRepetidas() {
        int fusionadas = 0;
        for (Map.Entry<String, String> fusion : FUSIONES.entrySet()) {
            Integer vieja = idDe(fusion.getKey());
            Integer nueva = idDe(fusion.getValue());
            if (vieja == null || nueva == null || vieja.equals(nueva)) {
                continue;
            }

            for (Map.Entry<String, String> tabla : TABLAS_DE_SELECCIONES.entrySet()) {
                if (existeTabla(tabla.getKey())) {
                    moverSelecciones(tabla.getKey(), tabla.getValue(), vieja, nueva);
                }
            }
            jdbc.update("DELETE FROM gusto WHERE id_gusto = ?", vieja);
            fusionadas++;
        }

        if (fusionadas > 0) {
            log.info("Se fusionaron {} opciones repetidas del catálogo de intereses", fusionadas);
        }
    }

    /** Quien tenía la vieja y no la nueva, queda con la nueva; luego se borra la vieja. */
    private void moverSelecciones(String tabla, String columnaPersona, int vieja, int nueva) {
        jdbc.update("INSERT INTO " + tabla + " (" + columnaPersona + ", id_gusto) "
                + "SELECT " + columnaPersona + ", ? FROM " + tabla + " WHERE id_gusto = ? "
                + "AND " + columnaPersona + " NOT IN (SELECT " + columnaPersona + " FROM " + tabla
                + " WHERE id_gusto = ?)", nueva, vieja, nueva);
        jdbc.update("DELETE FROM " + tabla + " WHERE id_gusto = ?", vieja);
    }

    private Integer idDe(String nombre) {
        List<Integer> ids = jdbc.queryForList(
                "SELECT id_gusto FROM gusto WHERE LOWER(nombre) = LOWER(?)", Integer.class, nombre);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private boolean existeTabla(String tabla) {
        Integer cuantas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = ?",
                Integer.class, tabla);
        return cuantas != null && cuantas > 0;
    }

    public void agregarFaltantes() {
        int agregadas = 0;
        for (Map.Entry<String, List<String>> categoria : POR_CATEGORIA.entrySet()) {
            for (String nombre : categoria.getValue()) {
                if (!gustoRepository.existsByNombreIgnoreCase(nombre)) {
                    gustoRepository.save(new Gusto(nombre, categoria.getKey()));
                    agregadas++;
                }
            }
        }

        if (agregadas > 0) {
            log.info("Se agregaron {} opciones al catálogo de intereses", agregadas);
        }
    }
}
