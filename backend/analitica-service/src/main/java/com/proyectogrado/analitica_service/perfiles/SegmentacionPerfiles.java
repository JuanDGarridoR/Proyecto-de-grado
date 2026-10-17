package com.proyectogrado.analitica_service.perfiles;

import com.proyectogrado.analitica_service.perfiles.DatosPerfiles.RegistroPersona;
import com.proyectogrado.analitica_service.perfiles.PerfilesDtos.CalidadDatos;
import com.proyectogrado.analitica_service.perfiles.PerfilesDtos.EstadoSegmentacion;
import com.proyectogrado.analitica_service.perfiles.PerfilesDtos.EvaluacionK;
import com.proyectogrado.analitica_service.perfiles.PerfilesDtos.GrupoPerfil;
import com.proyectogrado.analitica_service.perfiles.PerfilesDtos.Modelo;
import com.proyectogrado.analitica_service.perfiles.PerfilesDtos.NivelCalidad;
import com.proyectogrado.analitica_service.perfiles.PerfilesDtos.PerfilesResponse;
import com.proyectogrado.analitica_service.perfiles.PerfilesDtos.PersonaExcluida;
import com.proyectogrado.analitica_service.perfiles.PerfilesDtos.PersonaPerfil;
import com.proyectogrado.analitica_service.perfiles.PerfilesDtos.PuntajeDimension;
import com.proyectogrado.analitica_service.perfiles.PerfilesDtos.ValorGrupo;
import com.proyectogrado.analitica_service.perfiles.PerfilesDtos.VariableModelo;
import com.proyectogrado.analitica_service.perfiles.VariablePerfil.Dimension;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Segmentación de las personas mayores de una organización en perfiles con
 * K-means. Pasos (ver docs/KMEANS_PERFILES.md):
 *
 * 1. Lee las variables de cada persona (DatosPerfiles).
 * 2. Evalúa la calidad: incluye solo a quien tiene al menos el 60 % de los
 *    datos que pueden faltar; si quedan menos de MIN_PERSONAS no entrena.
 * 3. Prepara: descarta variables con muchos faltantes o sin variación,
 *    completa los faltantes con la mediana y estandariza (z-score, recortado
 *    a ±3 para que un valor extremo no arrastre un centroide).
 * 4. Entrena K-means para k = 2..kMaximo y elige el k de mayor silueta (o el
 *    que pida la organización).
 * 5. Interpreta: centroides, rasgos, nombre del perfil y recomendaciones.
 *
 * Solo informa (RNF-13): los perfiles orientan a la organización; no son un
 * diagnóstico ni cambian nada en la plataforma.
 */
@Component
public class SegmentacionPerfiles {

    static final int MIN_PERSONAS = 12;
    /** Personas recomendadas para que los grupos sean estables. */
    static final int PERSONAS_RECOMENDADAS = 30;
    static final double UMBRAL_INCLUSION = 0.6;
    static final double MAX_FALTANTES_VARIABLE = 0.5;
    static final int K_MAXIMO = 6;
    /** Al menos 4 personas por grupo en promedio. */
    static final int PERSONAS_POR_GRUPO = 4;
    static final int REINICIOS = 20;
    static final long SEMILLA = 42;
    static final double RECORTE_Z = 3;
    /** Diferencia (en desviaciones) a partir de la cual una variable distingue al grupo. */
    static final double UMBRAL_RASGO = 0.5;
    /** Puntaje a partir del cual una dimensión da nombre al grupo. */
    static final double UMBRAL_DIMENSION = 0.4;

    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");
    private static final VariablePerfil[] VARIABLES = VariablePerfil.values();

    private final DatosPerfiles datos;

    public SegmentacionPerfiles(DatosPerfiles datos) {
        this.datos = datos;
    }

    public PerfilesResponse segmentar(int idOrganizacion, Integer kPedido) {
        return segmentar(idOrganizacion, kPedido, LocalDate.now(COLOMBIA));
    }

    PerfilesResponse segmentar(int idOrganizacion, Integer kPedido, LocalDate hoy) {
        List<RegistroPersona> registros = datos.leer(idOrganizacion, hoy);
        List<String> advertencias = new ArrayList<>();

        // ---------- 2. Calidad y suficiencia ----------

        List<RegistroPersona> incluidas = new ArrayList<>();
        List<PersonaExcluida> excluidas = new ArrayList<>();
        long medibles = Arrays.stream(VARIABLES).filter(VariablePerfil::puedeFaltar).count();
        for (RegistroPersona r : registros) {
            long presentes = Arrays.stream(VARIABLES)
                    .filter(v -> v.puedeFaltar() && !Double.isNaN(r.valores()[v.ordinal()])).count();
            if ((double) presentes / medibles >= UMBRAL_INCLUSION) {
                incluidas.add(r);
            } else {
                excluidas.add(new PersonaExcluida(r.id(), r.nombre(), faltantes(r)));
            }
        }

        int n = incluidas.size();
        int dePrueba = (int) incluidas.stream().filter(RegistroPersona::dePrueba).count();
        double[] completitud = new double[VARIABLES.length];
        for (VariablePerfil v : VARIABLES) {
            long conDato = registros.stream().filter(r -> !Double.isNaN(r.valores()[v.ordinal()])).count();
            completitud[v.ordinal()] = registros.isEmpty() ? 0 : redondear(100.0 * conDato / registros.size(), 1);
        }

        if (!excluidas.isEmpty()) {
            advertencias.add(excluidas.size() + (excluidas.size() == 1 ? " persona no entró" : " personas no entraron")
                    + " al análisis porque les faltan datos: se necesita al menos el "
                    + (int) (UMBRAL_INCLUSION * 100) + " % de la fecha de nacimiento, si vive sola, los signos "
                    + "vitales, el peso y la estatura y las citas médicas. En la lista de personas sin datos "
                    + "suficientes está lo que le falta a cada una.");
        }

        if (n < MIN_PERSONAS) {
            advertencias.add(0, "No hay datos suficientes para encontrar perfiles: hay " + n
                    + (n == 1 ? " persona" : " personas") + " con datos completos y se necesitan al menos "
                    + MIN_PERSONAS + ". Registra los signos vitales, la fecha de nacimiento y los demás "
                    + "datos de tus personas mayores para poder hacer el análisis.");
            return insuficiente(registros.size(), n, excluidas, dePrueba, completitud, advertencias, hoy);
        }

        // ---------- 3. Preparación: selección de variables, imputación y normalización ----------

        double[][] crudos = new double[n][];
        for (int i = 0; i < n; i++) {
            crudos[i] = incluidas.get(i).valores().clone();
        }

        List<VariableModelo> variables = new ArrayList<>();
        List<VariablePerfil> usadas = new ArrayList<>();
        double[] medias = new double[VARIABLES.length];
        double[] desviaciones = new double[VARIABLES.length];

        for (VariablePerfil v : VARIABLES) {
            int j = v.ordinal();
            double[] presentes = Arrays.stream(crudos).mapToDouble(f -> f[j]).filter(x -> !Double.isNaN(x)).toArray();
            double faltantes = 1 - (double) presentes.length / n;
            String motivo = null;

            if (faltantes > MAX_FALTANTES_VARIABLE) {
                motivo = "Falta en el " + Math.round(faltantes * 100) + " % de las personas incluidas";
            } else {
                double mediana = mediana(presentes);
                for (double[] fila : crudos) {
                    if (Double.isNaN(fila[j])) {
                        fila[j] = mediana;
                    }
                }
                medias[j] = Arrays.stream(crudos).mapToDouble(f -> f[j]).average().orElse(0);
                desviaciones[j] = desviacion(crudos, j, medias[j]);
                if (desviaciones[j] < 1e-9) {
                    motivo = "Todas las personas tienen el mismo valor";
                }
            }

            boolean usada = motivo == null;
            if (usada) {
                usadas.add(v);
            }
            variables.add(new VariableModelo(v.clave(), v.nombre(), v.dimension().nombre(), v.unidad(),
                    completitud[j], presentes.length > 0 ? redondear(medias[j], 2) : null,
                    presentes.length > 0 ? redondear(desviaciones[j], 2) : null, usada, motivo));
        }

        List<String> descartadas = variables.stream().filter(v -> !v.usada())
                .map(v -> v.nombre() + " (" + v.motivoDescarte().toLowerCase(ES_CO) + ")").toList();
        if (!descartadas.isEmpty()) {
            advertencias.add("No se usaron en el modelo: " + String.join("; ", descartadas) + ".");
        }

        if (usadas.size() < 2) {
            advertencias.add(0, "No hay datos suficientes para encontrar perfiles: menos de dos variables tienen "
                    + "datos y variación entre las personas.");
            return insuficiente(registros.size(), n, excluidas, dePrueba, completitud, advertencias, hoy, variables);
        }

        int d = usadas.size();
        double[][] z = new double[n][d];
        for (int i = 0; i < n; i++) {
            for (int c = 0; c < d; c++) {
                int j = usadas.get(c).ordinal();
                double valor = (crudos[i][j] - medias[j]) / desviaciones[j];
                z[i][c] = Math.max(-RECORTE_Z, Math.min(RECORTE_Z, valor));
            }
        }

        // Solo se cuentan los valores que el modelo usa
        long celdasUsadas = (long) n * d;
        long imputadosUsados = 0;
        for (RegistroPersona r : incluidas) {
            for (VariablePerfil v : usadas) {
                if (Double.isNaN(r.valores()[v.ordinal()])) {
                    imputadosUsados++;
                }
            }
        }
        double porcentajeImputado = redondear(100.0 * imputadosUsados / celdasUsadas, 1);

        // ---------- 4. K-means: elección de k y entrenamiento ----------

        int kMaximo = Math.max(2, Math.min(K_MAXIMO, n / PERSONAS_POR_GRUPO));
        List<EvaluacionK> evaluaciones = new ArrayList<>();
        Map<Integer, KMeans.Resultado> resultados = new HashMap<>();
        int kSugerido = 2;
        double mejorSilueta = Double.NEGATIVE_INFINITY;
        for (int k = 2; k <= kMaximo; k++) {
            KMeans.Resultado resultado = KMeans.agrupar(z, k, REINICIOS, SEMILLA);
            double silueta = KMeans.silueta(z, resultado.etiquetas(), k);
            resultados.put(k, resultado);
            evaluaciones.add(new EvaluacionK(k, redondear(resultado.inercia(), 2), redondear(silueta, 3)));
            if (silueta > mejorSilueta + 1e-9) {
                mejorSilueta = silueta;
                kSugerido = k;
            }
        }

        boolean elegidoPorUsuario = kPedido != null && kPedido >= 2 && kPedido <= kMaximo;
        int k = elegidoPorUsuario ? kPedido : kSugerido;
        KMeans.Resultado resultado = resultados.get(k);
        double silueta = KMeans.silueta(z, resultado.etiquetas(), k);
        Pca.Proyeccion mapa = Pca.proyectar(z);

        // ---------- 5. Interpretación ----------

        List<Grupo> grupos = new ArrayList<>();
        for (int c = 0; c < k; c++) {
            grupos.add(new Grupo(c));
        }
        for (int i = 0; i < n; i++) {
            grupos.get(resultado.etiquetas()[i]).miembros.add(i);
        }
        for (Grupo g : grupos) {
            g.calcular(crudos, z, usadas);
        }
        // Grupo 1 = el de más necesidad de atención
        grupos.sort(Comparator.comparingDouble(Grupo::prioridad).reversed()
                .thenComparing(Comparator.comparingInt((Grupo g) -> g.miembros.size()).reversed()));

        List<GrupoPerfil> perfiles = new ArrayList<>();
        int[] numeroDe = new int[k];
        for (int posicion = 0; posicion < grupos.size(); posicion++) {
            Grupo g = grupos.get(posicion);
            numeroDe[g.etiqueta] = posicion + 1;
            perfiles.add(interpretar(posicion + 1, g, incluidas, usadas, medias, n));
        }

        List<PersonaPerfil> personas = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            RegistroPersona r = incluidas.get(i);
            personas.add(new PersonaPerfil(r.id(), r.nombre(), numeroDe[resultado.etiquetas()[i]],
                    redondear(mapa.coordenadas()[i][0], 3), redondear(mapa.coordenadas()[i][1], 3)));
        }

        // ---------- Advertencias sobre los resultados ----------

        if (dePrueba > 0) {
            advertencias.add(0, dePrueba + " de las " + n + " personas analizadas son datos ficticios del script de "
                    + "pruebas: los grupos muestran cómo se generaron esos datos, no la situación real de la población.");
        }
        if (n < PERSONAS_RECOMENDADAS) {
            advertencias.add("Con " + n + " personas los grupos son poco estables: pueden cambiar al agregar o "
                    + "corregir registros. Se recomiendan al menos " + PERSONAS_RECOMENDADAS
                    + " personas con datos completos.");
        }
        if (porcentajeImputado > 15) {
            advertencias.add("El " + formato(porcentajeImputado) + " % de los valores se completó con la mediana "
                    + "de la organización porque la persona no lo tenía registrado; eso acerca a esas personas al "
                    + "promedio y puede ocultar diferencias.");
        }
        String estructura = estructura(silueta);
        if (silueta < 0.25) {
            advertencias.add("La silueta es baja (" + formato(redondear(silueta, 2)) + "): los grupos se "
                    + "solapan y deben leerse como tendencias, no como perfiles bien definidos.");
        }
        for (GrupoPerfil p : perfiles) {
            if (p.personas() < 3) {
                advertencias.add("El grupo " + p.numero() + " tiene solo " + p.personas()
                        + (p.personas() == 1 ? " persona" : " personas") + ": puede tratarse de casos atípicos "
                        + "más que de un perfil.");
            }
        }

        CalidadDatos calidad = new CalidadDatos(registros.size(), n, excluidas.size(), MIN_PERSONAS,
                porcentajeImputado, dePrueba, nivelCalidad(n, porcentajeImputado, dePrueba));
        Modelo modelo = new Modelo(k, kSugerido, elegidoPorUsuario, kMaximo, redondear(silueta, 3), estructura,
                redondear(resultado.inercia(), 2), resultado.iteraciones(), REINICIOS, SEMILLA,
                new double[]{redondear(mapa.varianzaExplicada()[0], 3), redondear(mapa.varianzaExplicada()[1], 3)},
                evaluaciones);

        return new PerfilesResponse(EstadoSegmentacion.LISTO, hoy.toString(), calidad, variables, modelo,
                perfiles, personas, excluidas, advertencias);
    }

    // ---------- Grupos ----------

    /** Un grupo mientras se interpreta: sus miembros, centroide y puntajes por dimensión. */
    private static final class Grupo {
        final int etiqueta;
        final List<Integer> miembros = new ArrayList<>();
        final Map<VariablePerfil, double[]> centroide = new EnumMap<>(VariablePerfil.class); // {valor, z}
        final Map<Dimension, Double> puntajes = new EnumMap<>(Dimension.class);

        Grupo(int etiqueta) {
            this.etiqueta = etiqueta;
        }

        void calcular(double[][] crudos, double[][] z, List<VariablePerfil> usadas) {
            Map<Dimension, double[]> suma = new EnumMap<>(Dimension.class);
            for (int c = 0; c < usadas.size(); c++) {
                VariablePerfil v = usadas.get(c);
                int columna = c;
                double valor = miembros.stream().mapToDouble(i -> crudos[i][v.ordinal()]).average().orElse(0);
                double zGrupo = miembros.stream().mapToDouble(i -> z[i][columna]).average().orElse(0);
                centroide.put(v, new double[]{valor, zGrupo});
                if (v.peso() != 0) {
                    double[] s = suma.computeIfAbsent(v.dimension(), x -> new double[2]);
                    s[0] += v.peso() * zGrupo;
                    s[1]++;
                }
            }
            suma.forEach((dimension, s) -> puntajes.put(dimension, s[0] / s[1]));
        }

        /** Suma de las necesidades por encima del promedio; ordena los grupos. */
        double prioridad() {
            return puntajes.values().stream().mapToDouble(p -> Math.max(0, p)).sum();
        }
    }

    private GrupoPerfil interpretar(int numero, Grupo g, List<RegistroPersona> incluidas,
                                    List<VariablePerfil> usadas, double[] medias, int n) {
        // Dimensiones que dan nombre al grupo: puntaje alto, de mayor a menor
        List<Dimension> destacadas = g.puntajes.entrySet().stream()
                .filter(e -> e.getValue() >= UMBRAL_DIMENSION)
                .sorted(Map.Entry.<Dimension, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(2)
                .toList();
        boolean autonomo = destacadas.isEmpty() && g.puntajes.values().stream().allMatch(p -> p <= 0.1);

        String nombre;
        String descripcion;
        if (!destacadas.isEmpty()) {
            nombre = NOMBRE.get(destacadas.get(0))
                    + (destacadas.size() > 1 ? " y " + NOMBRE.get(destacadas.get(1)).toLowerCase(ES_CO) : "");
            descripcion = String.join(" ", destacadas.stream().map(DESCRIPCION::get).toList());
        } else if (autonomo) {
            nombre = "Autónomos y estables";
            descripcion = "Igual o menos necesidad que el promedio de la organización en salud, signos vitales, "
                    + "edad y acompañamiento.";
        } else {
            nombre = "Perfil intermedio";
            descripcion = "Cerca del promedio de la organización, sin una necesidad que sobresalga.";
        }

        // Rasgos: variables en que el grupo se aleja del promedio, de mayor a menor diferencia
        List<String> rasgos = usadas.stream()
                .filter(v -> Math.abs(g.centroide.get(v)[1]) >= UMBRAL_RASGO)
                .sorted(Comparator.comparingDouble((VariablePerfil v) -> -Math.abs(g.centroide.get(v)[1])))
                .limit(5)
                .map(v -> {
                    double[] c = g.centroide.get(v);
                    return v.nombre() + (c[1] > 0 ? " más alto" : " más bajo") + " que el promedio: "
                            + conUnidad(c[0], v) + " frente a " + conUnidad(medias[v.ordinal()], v);
                })
                .toList();

        List<String> atencion = new ArrayList<>();
        List<String> actividades = new ArrayList<>();
        if (destacadas.isEmpty()) {
            atencion.add(autonomo ? "Seguimiento preventivo habitual: controles periódicos y signos vitales al día."
                    : "Seguimiento preventivo y revisar periódicamente si cambia su situación de salud o de apoyo.");
            actividades.add(autonomo
                    ? "Actividades de mayor exigencia (caminatas, baile, deporte adaptado) e invitarlos a liderar o "
                      + "apoyar a otros grupos, como voluntariado entre pares."
                    : "Actividades variadas según sus intereses, con opción de ritmo suave.");
        }
        for (Dimension dimension : destacadas) {
            atencion.addAll(ATENCION.get(dimension));
            actividades.add(ACTIVIDADES.get(dimension));
        }

        // Intereses y asistencia: describen al grupo, no entran al modelo
        Map<String, Integer> conteo = new HashMap<>();
        int registradas = 0;
        int asistencias = 0;
        for (int i : g.miembros) {
            RegistroPersona r = incluidas.get(i);
            r.intereses().forEach(interes -> conteo.merge(interes, 1, Integer::sum));
            registradas += r.actividadesConRegistro();
            asistencias += r.asistencias();
        }
        List<String> intereses = conteo.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();
        if (!intereses.isEmpty()) {
            actividades.add("Según los intereses más comunes del grupo: " + unir(intereses) + ".");
        }

        List<ValorGrupo> centroide = usadas.stream()
                .map(v -> new ValorGrupo(v.clave(), redondear(g.centroide.get(v)[0], 2), redondear(g.centroide.get(v)[1], 3)))
                .toList();
        List<PuntajeDimension> dimensiones = g.puntajes.entrySet().stream()
                .map(e -> new PuntajeDimension(e.getKey().nombre(), redondear(e.getValue(), 3)))
                .toList();

        return new GrupoPerfil(numero, nombre, descripcion, g.miembros.size(),
                redondear(100.0 * g.miembros.size() / n, 1), rasgos, centroide, dimensiones, atencion, actividades,
                intereses, registradas > 0 ? redondear(100.0 * asistencias / registradas, 1) : null);
    }

    private static final Map<Dimension, String> NOMBRE = Map.of(
            Dimension.SALUD, "Salud compleja",
            Dimension.SIGNOS, "Signos vitales por vigilar",
            Dimension.EDAD, "Edad avanzada",
            Dimension.ACOMPANAMIENTO, "Necesita acompañamiento");

    private static final Map<Dimension, String> DESCRIPCION = Map.of(
            Dimension.SALUD, "Más enfermedades, discapacidades o medicamentos que el promedio de la organización.",
            Dimension.SIGNOS, "Más mediciones de signos vitales fuera del rango de referencia.",
            Dimension.EDAD, "Mayor edad que el promedio de la organización.",
            Dimension.ACOMPANAMIENTO, "Viven solas con más frecuencia, tienen menos acompañantes o piden más "
                    + "acompañamiento a sus citas y han tenido emergencias.");

    private static final Map<Dimension, List<String>> ATENCION = Map.of(
            Dimension.SALUD, List.of(
                    "Revisar con frecuencia sus medicamentos y que sigan los tratamientos (recordatorios en VITA+).",
                    "Coordinar con su EPS o IPS los controles de sus enfermedades crónicas."),
            Dimension.SIGNOS, List.of(
                    "Promover la toma regular de signos vitales y revisar las mediciones fuera de rango con un "
                    + "profesional de salud."),
            Dimension.EDAD, List.of(
                    "Verificar la movilidad y el riesgo de caídas, y que tengan una red de apoyo cercana."),
            Dimension.ACOMPANAMIENTO, List.of(
                    "Asignar acompañantes o voluntarios para sus citas médicas y hacer llamadas de seguimiento.",
                    "Revisar las emergencias recientes y que sus contactos de emergencia estén al día."));

    private static final Map<Dimension, String> ACTIVIDADES = Map.of(
            Dimension.SALUD, "Actividades de bajo impacto y adaptadas: estiramientos, ejercicios en silla y "
                    + "talleres de memoria.",
            Dimension.SIGNOS, "Jornadas de toma de signos vitales y charlas de autocuidado (alimentación, "
                    + "hidratación, control de la presión).",
            Dimension.EDAD, "Actividades sentadas o de ritmo suave y espacios de memoria y conversación "
                    + "(historias de vida, música).",
            Dimension.ACOMPANAMIENTO, "Actividades grupales de socialización que fortalezcan su red de apoyo "
                    + "(encuentros, celebraciones, salidas acompañadas).");

    // ---------- Datos insuficientes ----------

    private PerfilesResponse insuficiente(int asociadas, int incluidas, List<PersonaExcluida> excluidas, int dePrueba,
                                          double[] completitud, List<String> advertencias, LocalDate hoy) {
        List<VariableModelo> variables = Arrays.stream(VARIABLES)
                .map(v -> new VariableModelo(v.clave(), v.nombre(), v.dimension().nombre(), v.unidad(),
                        completitud[v.ordinal()], null, null, false, null))
                .toList();
        return insuficiente(asociadas, incluidas, excluidas, dePrueba, completitud, advertencias, hoy, variables);
    }

    private PerfilesResponse insuficiente(int asociadas, int incluidas, List<PersonaExcluida> excluidas, int dePrueba,
                                          double[] completitud, List<String> advertencias, LocalDate hoy,
                                          List<VariableModelo> variables) {
        CalidadDatos calidad = new CalidadDatos(asociadas, incluidas, excluidas.size(), MIN_PERSONAS, 0, dePrueba,
                NivelCalidad.INSUFICIENTE);
        return new PerfilesResponse(EstadoSegmentacion.INSUFICIENTE, hoy.toString(), calidad, variables, null,
                List.of(), List.of(), excluidas, advertencias);
    }

    // ---------- Apoyo ----------

    /** Qué le falta registrar a una persona para entrar al análisis. */
    private static List<String> faltantes(RegistroPersona r) {
        Set<String> faltan = new LinkedHashSet<>();
        for (VariablePerfil v : VARIABLES) {
            if (v.puedeFaltar() && Double.isNaN(r.valores()[v.ordinal()])) {
                faltan.add(switch (v) {
                    case EDAD -> "Fecha de nacimiento";
                    case IMC -> "Peso y estatura";
                    case VIVE_SOLO -> "Si vive sola";
                    case CITAS_CON_ACOMPANAMIENTO -> "Citas médicas del último año";
                    default -> "Signos vitales de los últimos " + DatosPerfiles.DIAS_SIGNOS + " días";
                });
            }
        }
        return List.copyOf(faltan);
    }

    /**
     * BUENA: 50 o más personas, poca imputación y sin datos de prueba.
     * ACEPTABLE: 25 o más y hasta 25 % imputado. Si no, LIMITADA.
     */
    static NivelCalidad nivelCalidad(int personas, double porcentajeImputado, int dePrueba) {
        if (personas < MIN_PERSONAS) {
            return NivelCalidad.INSUFICIENTE;
        }
        if (personas >= 50 && porcentajeImputado <= 10 && dePrueba == 0) {
            return NivelCalidad.BUENA;
        }
        if (personas >= 25 && porcentajeImputado <= 25) {
            return NivelCalidad.ACEPTABLE;
        }
        return NivelCalidad.LIMITADA;
    }

    /** Lectura habitual de la silueta (Kaufman y Rousseeuw). */
    static String estructura(double silueta) {
        if (silueta > 0.5) return "FUERTE";
        if (silueta > 0.25) return "RAZONABLE";
        if (silueta > 0.1) return "DEBIL";
        return "SIN_ESTRUCTURA";
    }

    private static double mediana(double[] valores) {
        if (valores.length == 0) {
            return 0;
        }
        double[] ordenados = valores.clone();
        Arrays.sort(ordenados);
        int mitad = ordenados.length / 2;
        return ordenados.length % 2 == 1 ? ordenados[mitad] : (ordenados[mitad - 1] + ordenados[mitad]) / 2;
    }

    /** Desviación estándar muestral de la columna j. */
    private static double desviacion(double[][] filas, int j, double media) {
        if (filas.length < 2) {
            return 0;
        }
        double suma = 0;
        for (double[] fila : filas) {
            suma += (fila[j] - media) * (fila[j] - media);
        }
        return Math.sqrt(suma / (filas.length - 1));
    }

    private static double redondear(double valor, int decimales) {
        double factor = Math.pow(10, decimales);
        return Math.round(valor * factor) / factor;
    }

    private static String formato(double valor) {
        return String.format(ES_CO, valor == Math.rint(valor) ? "%.0f" : "%.1f", valor);
    }

    private static String conUnidad(double valor, VariablePerfil v) {
        String numero = formato(redondear(valor, 1));
        if (v.unidad().isEmpty()) return numero;
        return v.unidad().equals("%") ? numero + " %" : numero + " " + v.unidad();
    }

    /** "a, b y c". */
    private static String unir(List<String> elementos) {
        if (elementos.size() == 1) return elementos.get(0);
        return String.join(", ", elementos.subList(0, elementos.size() - 1)) + " y " + elementos.get(elementos.size() - 1);
    }
}
