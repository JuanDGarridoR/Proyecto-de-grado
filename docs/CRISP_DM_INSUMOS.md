# Insumos para completar el documento CRISP-DM de VITA+

Este archivo reúne las instrucciones y la información técnica necesarias para terminar el documento **«Metodología CRISP-DM»** del proyecto *Plataforma web para apoyar la gestión de información, mediante analítica de datos, en organizaciones que asisten a personas mayores. Caso de estudio: UPL Entrenubes (Usme, Bogotá)*.

Todo lo que aparece aquí se tomó del código fuente y de la documentación técnica del repositorio al **21 de octubre de 2026**. Las cifras y los parámetros son los que usa la plataforma implementada.

---

## Parte A. Instrucciones para quien redacte el documento

### A.1 Qué hay que hacer

1. **Corregir las fases 1 y 2**, que ya están escritas pero tienen datos desactualizados (ver Parte B).
2. **Redactar las fases 3, 4, 5 y 6**, las referencias y los anexos, siguiendo exactamente el índice que ya existe (ver Parte C).
3. **Actualizar** la lista de tablas, la lista de figuras y el historial de cambios.

### A.2 Estilo que hay que mantener

- Español formal y académico, en tercera persona impersonal («se seleccionaron», «la plataforma calcula»), igual que en las fases 1 y 2.
- Cada sección empieza con un párrafo que explica su propósito y luego desarrolla el contenido. Se usan tablas cuando hay varias variables, criterios o parámetros.
- Las tablas siguen la numeración del documento. La última es la **Tabla 6 (Verificación de calidad de los datos)**, así que las nuevas empiezan en la **Tabla 7**. Cada una lleva su leyenda debajo, en cursiva: «Tabla N Título».
- Las figuras se numeran desde la **Figura 1**, porque el documento todavía no tiene ninguna.
- Los nombres de tablas y columnas de la base de datos se escriben en negrita o como código (`persona_mayor`), como ya se hace en la sección 2.2.2.
- No se pegan fragmentos de código fuente en el cuerpo. Las fórmulas sí se incluyen (inercia, silueta, z-score, IMC, Jaccard). El código puede referenciarse en los anexos.

### A.3 Reglas de contenido

- **No inventar cifras.** Si un dato depende de una ejecución o de los datos reales (por ejemplo, la silueta obtenida o el número de personas reales), se deja el marcador **`[POR COMPLETAR: …]`** con una descripción de lo que falta y de cómo obtenerlo (ver la Parte D).
- **Datos ficticios frente a datos reales.** Todo resultado obtenido con el script de datos ficticios se presenta como un **resultado de prueba técnica**, no como una conclusión sobre la población real. Así lo establecen las secciones 2.1 y 2.4.
- **Solo apoyo a la decisión.** Los resultados analíticos no son diagnósticos médicos ni toman decisiones por la organización. Esto corresponde al requerimiento no funcional **RNF-13** y debe repetirse en las fases 4, 5 y 6.
- Se citan los requerimientos por su identificador (RF-45, RF-81, RNF-13, etc.) cuando una sección describe algo que los cumple. La lista completa está en `docs/REQUERIMIENTOS.md`.
- La metodología de gestión general sigue siendo Scrum y Kanban (SPMP). CRISP-DM solo cubre el componente analítico.

---

## Parte B. Correcciones necesarias en las fases 1 y 2

| # | Dónde | Qué dice ahora | Qué debe decir |
|---|---|---|---|
| 1 | 2.1, 2.2.1 y cierre de la Fase 2 | El conjunto ficticio tiene 5 organizaciones, 200 personas mayores, 25 acompañantes y 25 voluntarios. | El script actual (`backend/scripts/seed_datos_fictisios.sql`) genera **8 organizaciones, 300 personas mayores, 60 acompañantes y 50 voluntarios**, unas **5.000 mediciones de signos vitales** de los últimos 6 meses y **64 actividades** con inscripciones y asistencia, además de citas médicas, condiciones de salud, emergencias, retiros, donaciones e inventario (ver B.2). |
| 2 | 2.2.2 y Tabla 5 | La fecha de nacimiento, el género y la dirección están en `persona_mayor`. | Están en **`usuario`**, que es común a todos los roles. `persona_mayor` guarda **`eps`, `ips`, `direccion_ips`, `vive_solo` y `barrio`**. |
| 3 | 2.2.2 y Tabla 5 | El parentesco está en `acompanante`. | El parentesco está en **cada vínculo** (`persona_mayor_acompanante.relacion`), porque un acompañante puede ser hijo de una persona mayor y sobrino de otra. La tabla `acompanante` solo identifica al usuario. |
| 4 | 2.2.2 y Tabla 5 | `voluntario` tiene la variable `disponibilidad`. | Esa columna ya no existe: `voluntario` solo tiene `id_usuario`. Los gustos del voluntario están en `voluntario_gusto` y sus organizaciones, en `voluntario_organizacion`. |
| 5 | 2.2.2 y Tabla 5 | La actividad tiene fecha, lugar, nombre, tipo, cupos, descripción, hora y responsable. | Además tiene **`hora_fin`**, **`frecuencia_dias`** (actividades periódicas), **`estado`** (propuestas pendientes, aceptadas o rechazadas), **`latitud` y `longitud`** (ubicación obtenida automáticamente) y quién la propuso (`id_voluntario`, `id_persona_mayor_proponente`). |
| 6 | 2.2.2 | Solo se listan 14 tablas. | Faltan las tablas que el análisis sí usa: **`signo_vital`, `cita_medica`, `condicion_salud`, `persona_mayor_condicion_salud`, `emergencia` y `retiro_cuenta`**, además de `voluntario_gusto` y `voluntario_organizacion` (ver B.1). |
| 7 | 2.3.4 y cierre de la Fase 2 | El seguimiento solo se puede aproximar con actividades, participación y medicamentos. | Sigue sin existir una tabla llamada «seguimiento», pero hoy hay **historial temporal** en `signo_vital` (fecha y hora de cada medición), `cita_medica` (citas pasadas y futuras, con necesidad de acompañamiento), `emergencia` (fecha y hora) y `retiro_cuenta`. Esas son las fuentes del seguimiento. |
| 8 | 2.2.3 y 2.3 | La dimensión de salud no aparece. | Hay que agregar las dimensiones **Condiciones de salud** (enfermedades, alergias y discapacidades con su severidad), **Signos vitales** y **Necesidades de acompañamiento** (vive sola, acompañantes, citas que piden acompañamiento y emergencias). Son la base del perfilamiento con K-means. |
| 9 | 1.1.4, criterio 6 | «Tiempos de respuesta establecidos en los requerimientos funcionales». | `REQUERIMIENTOS.md` no define un tiempo de respuesta. Hay dos opciones: redactar el criterio sin cifra («las consultas analíticas se calculan en la base de datos con consultas de solo lectura y K-means tarda milisegundos con cientos de personas», según `ARQUITECTURA_DISPONIBILIDAD.md`, punto 37), o definir un umbral con el equipo. `[POR COMPLETAR: decidir si se fija un umbral, por ejemplo menos de 3 segundos por reporte]` |
| 10 | 1.3.3 | Los indicadores iniciales son cobertura de atención, frecuencia de visitas y casos activos. | La plataforma **no registra visitas**. Conviene decir que los indicadores finales se definieron en la Fase 3 y son los de la Tabla de indicadores (C.3.3): participación, asistencia, ocupación de cupos, seguimiento de signos vitales, personas que requieren atención, población, red de apoyo y retiros. |
| 11 | 1.3.1 | La técnica de perfilamiento se definirá después. | Puede quedarse así (es la Fase 1), pero agregando una frase que remita a la Fase 4, donde se eligió **K-means**. |
| 12 | Historial de cambios | Solo hay dos entradas (17 y 18 de septiembre). | Agregar las entradas de las nuevas versiones. `[POR COMPLETAR: fecha y autor]` |

### B.1 Tablas que usa el componente analítico

Todas están en una sola base **PostgreSQL** alojada en **Supabase**, compartida por los microservicios. El servicio de analítica (`analitica-service`) **solo las lee**, con consultas SQL de solo lectura y sin modificar el esquema.

| Tabla | Columnas relevantes para la analítica |
|---|---|
| `usuario` | `id_usuario`, `nombre_usuario`, `fecha_nacimiento`, `genero`, `direccion`, `correo`, `celular`, `activo`, `fecha_creacion` |
| `persona_mayor` | `id_usuario`, `eps`, `ips`, `direccion_ips`, `vive_solo` (sí, no o nulo si no se ha indicado), `barrio` (calculado a partir de la dirección) |
| `organizacion` | `id_organizacion`, `nombre`, `direccion` (y la ubicación de su sede) |
| `persona_mayor_organizacion` | `id_persona_mayor`, `id_organizacion`, `estado` (`PENDIENTE`, `ACEPTADA`, `RECHAZADA`, `INACTIVA`, `CUENTA_INACTIVA`…) |
| `persona_mayor_acompanante` | `id_persona_mayor`, `id_acompanante`, `estado`, `relacion` (parentesco), `solicitada_por` |
| `gusto`, `persona_mayor_gusto`, `voluntario_gusto` | Catálogo con las categorías `GUSTO`, `TALENTO`, `HOBBY` y `PROFESION` (profesión u oficio) |
| `actividad` | `fecha`, `hora`, `hora_fin`, `tipo`, `cupos`, `lugar`, `latitud`, `longitud`, `frecuencia_dias`, `estado`, `id_organizacion` |
| `participacion` | `id_actividad`, `id_persona_mayor`, `asistio` (verdadero, falso o nulo si no se ha tomado asistencia) |
| `signo_vital` | `fecha_hora`, `presion_sistolica`, `presion_diastolica`, `frecuencia_cardiaca`, `temperatura`, `saturacion_oxigeno`, `frecuencia_respiratoria`, `peso`, `estatura` |
| `condicion_salud` y `persona_mayor_condicion_salud` | `tipo` (`ENFERMEDAD`, `ALERGIA`, `DISCAPACIDAD`), `severidad` (`LEVE`, `MODERADA`, `SEVERA`), `fecha_registro` |
| `medicamento` | `activo`, `fecha_inicio`, `fecha_fin`, `frecuencia`, `intervalo_horas`, `proxima_toma`, `ultima_toma` |
| `cita_medica` | `fecha`, `hora`, `acompanamiento` (`REQUERIDO`, `PREFERIDO` o ninguno), `id_acompanante_cita` |
| `emergencia` | `id_persona_mayor`, `fecha_hora` |
| `retiro_cuenta` | `id_organizacion`, `nombre`, `razon`, `comentario`, `fecha` (personas que eliminaron su cuenta) |
| `voluntario_organizacion` | Vínculo de cada voluntario con una organización |
| `auditoria_salud` | Quién consultó o cambió datos de salud, cuándo y con qué resultado (RNF-25). No es una fuente analítica, pero demuestra el control de acceso a datos sensibles. |

### B.2 Contenido del conjunto de datos ficticios

El archivo `backend/scripts/seed_datos_fictisios.sql` genera:

- 8 organizaciones, 300 personas mayores, 60 acompañantes y 50 voluntarios.
- Para cada persona mayor: fecha de nacimiento, género, dirección, EPS, IPS, si vive sola y un **perfil de salud sintético** (hipertensión, diabetes, EPOC). Ese perfil determina sus enfermedades, alergias, discapacidades y medicamentos.
- Un historial de signos vitales de 6 meses (unas 5.000 mediciones), con algunos valores fuera de rango y **unos pocos errores de digitación a propósito**, para probar la detección de valores imposibles.
- Citas médicas pasadas y futuras; cerca del 25 % de las próximas necesitan acompañamiento.
- 64 actividades con inscripciones y asistencia (algunas recientes sin asistencia tomada), y propuestas de actividades de los voluntarios.
- Emergencias de los últimos 21 días, retiros con su razón, vínculos en todos los estados posibles, intereses (de 3 a 6 por persona) y profesión u oficio.
- Todas las cuentas ficticias se identifican porque su correo termina en **`@vitaplus.test`** o su celular empieza por **`+570000`**. La analítica usa esa marca para **avisar cuando un resultado incluye datos de prueba** (criterio «Origen del dato» de la Tabla 6).

---

## Parte C. Contenido de las fases 3 a 6

### Fase 3: Preparación de los Datos

Párrafo introductorio sugerido: la preparación convierte los registros de varias tablas en un conjunto analítico por persona mayor, aplicando reglas de selección, limpieza y transformación que se ejecutan **automáticamente en cada consulta**. No se construye un archivo aparte que pueda quedar desactualizado: la preparación se hace cada vez que la organización abre un reporte.

#### 3.1 Selección de Variables

**Población analizada.** Solo entran las personas mayores con vínculo **`ACEPTADA`** con la organización que consulta y con la cuenta activa. Cada organización ve únicamente a sus personas, nunca las de otra.

**Variables del perfilamiento (K-means).** Se eligieron 14 variables, organizadas en cuatro dimensiones (Tabla 7):

| Dimensión | Variable | Fuente | Ventana de tiempo | Peso en la dimensión | ¿Puede faltar? |
|---|---|---|---|---|---|
| Edad | Edad (años) | `usuario.fecha_nacimiento` | Actual | +1 | Sí |
| Condiciones de salud | Enfermedades crónicas | `persona_mayor_condicion_salud` (tipo ENFERMEDAD) | Todo | +1 | No |
| | Discapacidades | ídem (tipo DISCAPACIDAD) | Todo | +1 | No |
| | Condiciones severas | ídem (severidad SEVERA) | Todo | +1 | No |
| | Medicamentos activos | `medicamento` (activo y sin fecha de fin vencida) | Vigentes | +1 | No |
| Signos vitales | Presión sistólica promedio | `signo_vital` | Últimos 180 días | 0 | Sí |
| | Saturación de oxígeno promedio | `signo_vital` | Últimos 180 días | −1 | Sí |
| | Pulso promedio | `signo_vital` | Últimos 180 días | 0 | Sí |
| | Índice de masa corporal | Última medición con peso y estatura | Últimos 180 días | 0 | Sí |
| | % de mediciones fuera de rango | `signo_vital` frente a los rangos de referencia | Últimos 180 días | +1 | Sí |
| Necesidades de acompañamiento | Vive sola (0 o 100) | `persona_mayor.vive_solo` | Actual | +1 | Sí |
| | Acompañantes vinculados | `persona_mayor_acompanante` (aceptados y activos) | Actual | −1 | No |
| | % de citas que piden acompañamiento | `cita_medica.acompanamiento` (REQUERIDO o PREFERIDO) | Último año y próximos 90 días | +1 | Sí |
| | Emergencias | `emergencia` | Últimos 90 días | +1 | No |

*Tabla 7 Variables seleccionadas para el perfilamiento*

El peso indica cómo cuenta la variable al calcular la necesidad de su dimensión: +1 significa que un valor más alto implica más necesidad; −1, que un valor más bajo implica más necesidad (por ejemplo, menos acompañantes o menos saturación de oxígeno); 0, que la variable entra al modelo pero no al puntaje de necesidad.

**Variables excluidas del modelo y por qué:**

- **Alergias:** no describen necesidad de cuidado ni de acompañamiento, y agregarían ruido.
- **Género, EPS y barrio:** son atributos demográficos o administrativos, no necesidades. Agrupar por ellos podría producir recomendaciones sesgadas.
- **Intereses y asistencia a actividades:** se usan para **describir** cada grupo y sugerir actividades, pero no para **formarlo**. Así, los perfiles responden solo a salud y acompañamiento.
- **Tablas de autenticación, roles, sesiones y auditoría:** son componentes funcionales y no aportan al objetivo analítico, como ya anticipaba la sección 2.2.2.

**Variables de los reportes descriptivos.** Además del perfilamiento, la analítica usa otras variables para los indicadores: actividades y participación (`actividad`, `participacion`), todas las mediciones de signos vitales, datos demográficos (edad, género, EPS, vive sola), intereses y profesiones, retiros (`retiro_cuenta`) y la red de apoyo (vínculos, gustos, barrios y actividades de los últimos 90 días y los próximos 60).

#### 3.2 Limpieza de los Datos

| Regla | Detalle | Motivo |
|---|---|---|
| Valores físicamente imposibles | Una medición se ignora si sale de estos límites: presión sistólica 60–260, diastólica 30–160 mmHg; pulso 30–220 lpm; temperatura 32–43 °C; saturación 50–100 %; peso 20–250 kg; estatura 100–220 cm. Son los mismos límites con que la plataforma valida al registrar (`SignoVitalValidador` en el backend y `rangos.ts` en el frontend). | Son errores de digitación. Los reportes los muestran aparte como «posible error de registro», sin mezclarlos con valores de salud. |
| Edades fuera de rango | Una edad menor de 40 o mayor de 120 años se toma como error de registro y se deja vacía. | Evita que una fecha de nacimiento mal digitada distorsione los grupos. |
| Vínculos no vigentes | Se excluyen los vínculos pendientes, rechazados, inactivos o de cuentas inactivas. | Solo se analiza a la población que la organización atiende de verdad. |
| Actividades no confirmadas | En el reporte de actividades se excluyen las propuestas pendientes o rechazadas (solo cuentan las actividades con estado nulo o `ACEPTADA`). | Una propuesta no aceptada no es una actividad realizada. |
| Asistencia sin registrar | `asistio` nulo significa que aún no se tomó la asistencia. No se cuenta como inasistencia: el porcentaje de asistencia se calcula solo sobre las inscripciones con asistencia registrada. | Evita subestimar la asistencia. |
| Conteos vacíos | En enfermedades, medicamentos, acompañantes y emergencias, la ausencia de registros se toma como 0. | Es un conteo; se documenta como limitación (ver 4.5). |
| Duplicados | Las tablas de vínculos tienen clave compuesta (persona y organización, o persona y acompañante), lo que impide asociaciones duplicadas. | Unicidad (Tabla 6). |

*Tabla 8 Reglas de limpieza de los datos*

**Verificación de suficiencia antes del modelado** (aplica los criterios de la Tabla 6):

1. **Inclusión por persona:** de las 8 variables que pueden faltar, cada persona debe tener al menos el **60 % (5 de 8)**. En la práctica, quien no tiene signos vitales en los últimos 180 días queda por fuera, y la plataforma le dice a la organización qué le falta registrar.
2. **Mínimo de personas:** se necesitan al menos **12 personas incluidas**. Con menos, no se entrena el modelo y el estado es «Insuficiente».
3. **Descarte de variables:** una variable no entra al modelo si falta en más del **50 %** de las personas incluidas, o si todas tienen el mismo valor (varianza cero).
4. **Nivel de calidad** (Tabla 9):

| Nivel | Condición |
|---|---|
| Buena | 50 personas o más, 10 % de valores imputados o menos y ningún dato de prueba |
| Aceptable | 25 personas o más y 25 % de valores imputados o menos |
| Limitada | 12 personas o más, sin cumplir lo anterior |
| Insuficiente | Menos de 12 personas, o menos de 2 variables utilizables |

*Tabla 9 Niveles de calidad de los datos para el perfilamiento*

#### 3.3 Transformación de los Datos

- **Variables derivadas:** la edad (a partir de la fecha de nacimiento); los promedios de presión sistólica, saturación y pulso en 180 días; el IMC de la última medición con peso y estatura; el porcentaje de mediciones fuera de rango; el porcentaje de citas con acompañamiento; los conteos.
- **IMC** = peso (kg) / estatura (m)². Se clasifica con la escala de la OMS para adultos: bajo peso (< 18,5), normal (18,5–24,9), sobrepeso (25–29,9) y obesidad (≥ 30).
- **Medición fuera de rango:** una medición está fuera de rango si alguno de sus indicadores sale de los rangos de referencia para adultos: presión 90–139 / 60–89 mmHg, pulso 60–100 lpm, temperatura 36–37,5 °C o saturación menor que 95 %.
- **Vive sola** se codifica como 0 o 100, para que el promedio del grupo se lea directamente como «% que vive sola».
- **Imputación con la mediana** de las personas incluidas, para los valores que faltan. Se prefiere la mediana a la media porque es robusta a los valores extremos.
- **Estandarización z-score:** z = (x − media) / desviación estándar. Es necesaria porque K-means usa distancias euclidianas: sin estandarizar, la presión (≈ 120) pesaría mucho más que el número de enfermedades (≈ 2) solo por su escala.
- **Recorte a ±3 desviaciones**, para que un valor extremo (por ejemplo, 10 emergencias) no arrastre solo un centroide.
- **Ubicación:** el barrio de cada persona mayor se obtiene de su dirección. Primero se busca en el texto; si no aparece, se usa el barrio que Google asigna a la dirección o el barrio conocido más cercano (a menos de 2 km). Se guarda **solo el nombre del barrio**, nunca las coordenadas de la casa (RNF-17). Las sedes y los lugares de las actividades se ubican con Google Places.
- **Agregaciones temporales** para los reportes: por mes (actividades y retiros) y por semana o por mes (mediciones de signos vitales, según la longitud del período).

#### 3.4 Integración de Fuentes

- Todos los microservicios (autenticación, persona mayor, acompañante, actividad, salud, organización, voluntario, donación y analítica) **comparten una sola base de datos PostgreSQL**. El servicio de analítica integra las fuentes con **consultas SQL que unen las tablas por los identificadores de usuario**, en lugar de pedir los datos a cada servicio.
- El identificador común es `id_usuario`: es el mismo en `usuario`, `persona_mayor`, los vínculos, `signo_vital`, `cita_medica`, `medicamento`, `emergencia` y `participacion`.
- La organización que consulta se obtiene del token de sesión: el API Gateway agrega el encabezado `X-User-Id` y el servicio resuelve a qué organización pertenece ese usuario. Si no es una organización, responde 403.
- Las fuentes externas (Google Places para ubicaciones) solo enriquecen la dirección con barrio y coordenadas. No aportan datos personales nuevos.

#### 3.5 Construcción del Dataset Analítico

- **Unidad de análisis:** una fila por persona mayor vinculada a la organización.
- **Para el perfilamiento:** una matriz de *n* personas × 14 variables (Tabla 7), más los datos descriptivos de cada persona (intereses, actividades con asistencia registrada y asistencias), que no entran al modelo.
- **Para los reportes:** el backend entrega datos «crudos» (una fila por actividad, por medición o por persona) y el frontend calcula los indicadores. Esto permite filtrar por período sin volver a consultar.
- El dataset se construye **en cada petición**, así que siempre refleja los datos vigentes y no hay un modelo desactualizado. La actualización en tiempo real (RNF-04) avisa al panel cuando cambian los datos para que vuelva a consultar.

#### 3.6 Resultados de la Preparación

Describir aquí el estado del dataset preparado con los datos disponibles:

- `[POR COMPLETAR: con los datos ficticios de una organización, cuántas personas vinculadas, cuántas incluidas y cuántas excluidas, y por qué. Se obtiene en Analítica → Grupos de atención: indicador «Personas analizadas» y tabla «Personas sin datos suficientes».]`
- `[POR COMPLETAR: completitud por variable (gráfica «Calidad de los datos») y porcentaje de valores imputados.]`
- `[POR COMPLETAR: variables descartadas, si hubo alguna.]`
- `[POR COMPLETAR: nivel de calidad obtenido. Con datos ficticios será como máximo «Aceptable», porque el nivel «Buena» exige que no haya datos de prueba.]`
- `[POR COMPLETAR: estado de los datos reales recolectados en Usme: cuántos registros hay y si ya alcanzan el mínimo de 12 personas por organización.]`

Figura sugerida: captura de la gráfica «Calidad de los datos» de la pestaña «Grupos de atención».

---

### Fase 4: Modelado

Párrafo introductorio sugerido: el componente analítico combina **técnicas descriptivas** (indicadores, distribuciones y tendencias), **reglas de priorización** (personas que requieren atención), una **técnica no supervisada** (K-means para el perfilamiento), un **sistema de recomendación por puntaje** y **análisis de redes** (grafo de la red de apoyo).

#### 4.1 Selección de Técnicas de Modelado

| Objetivo analítico (Fase 1) | Técnica | Dónde se ve en la plataforma |
|---|---|---|
| Caracterización de la población (1.3) | Estadística descriptiva: conteos, proporciones, promedios y distribuciones | Reportes «Perfil de la población» y «Salud de la población» |
| Perfilamiento (1.3.1) | **Clustering K-means** con inicialización K-means++, elección de *k* por coeficiente de silueta y PCA para visualizar | Pestaña «Grupos de atención» (RF-81) |
| Casos prioritarios (1.3.2) | Reglas explícitas sobre los rangos de referencia y la antigüedad de la última medición; detección de personas sin acompañante; orden de los perfiles por necesidad | Tabla «Personas que requieren atención» (RF-46), grafo de la red (RF-79) y Grupo 1 de los perfiles |
| Indicadores de seguimiento (1.3.3) | Indicadores con fórmula definida (ver 5.3) | Tarjetas de indicadores de cada reporte |
| Tendencias (1.3.4) | Series de tiempo agregadas por mes o por semana | Inscritos y asistentes por mes, mediciones por semana o mes, evolución de cada persona, retiros por mes |
| Apoyo a la vinculación | Recomendación por puntaje ponderado (similitud de Jaccard, coincidencia de gustos y distancia) | «Organizaciones recomendadas» de la persona mayor y del voluntario (RF-49, RF-71) |
| Redes de apoyo | Grafo bipartito y mapa por barrios | Sección «Red y mapa» de la organización (RF-79, RF-80) |

*Tabla 10 Técnicas seleccionadas por objetivo analítico*

**Por qué K-means:**

- Es un método no supervisado: no hacen falta etiquetas previas de «tipo de persona», que no existen.
- Es interpretable: cada grupo se describe por su centroide, es decir, el promedio de sus integrantes en unidades reales.
- Es eficiente con volúmenes pequeños: decenas o cientos de personas por organización, que se procesan en milisegundos en cada petición.
- Se implementó **en Java, sin librerías externas** (unas 200 líneas), para que se pueda leer y explicar sin cajas negras y sin agregar un servicio en Python a un servidor de 8 GB de RAM que ya ejecuta 15 contenedores.

**Alternativas consideradas para trabajo futuro:** K-prototypes (para variables mixtas), clustering jerárquico y DBSCAN (para grupos no esféricos).

#### 4.2 Diseño del Experimento

- **Valores de *k* evaluados:** de 2 a *kMáx*, con *kMáx* = mín(6, n/4). Así hay en promedio al menos 4 personas por grupo y no más de 6 perfiles, que es lo que una organización puede atender de forma diferenciada.
- **Criterio de selección:** se sugiere el *k* con el **coeficiente de silueta** promedio más alto. La **inercia** se muestra como referencia (método del codo), pero no sirve sola para elegir porque siempre baja al agregar grupos. La organización puede cambiar *k* manualmente.
- **Interpretación de la silueta** (Kaufman y Rousseeuw): mayor que 0,5, grupos bien separados; entre 0,25 y 0,5, separación razonable; entre 0,1 y 0,25, débil; menor que 0,1, no hay grupos claros.
- **Reproducibilidad:** 20 reinicios con distintas inicializaciones y una semilla aleatoria fija (42), de modo que con los mismos datos siempre se obtienen los mismos grupos.
- **Validación técnica con datos sintéticos controlados:** las pruebas automáticas construyen poblaciones con una estructura conocida (8 personas «autónomas» y 8 «frágiles») y verifican que el modelo recupere 2 grupos con silueta mayor que 0,5 (ver 5.1).
- **Datos del experimento:** primero el conjunto ficticio y luego los datos reales cuando alcancen el mínimo (ver 2.1).

#### 4.3 Construcción del Modelo

1. **Inicialización K-means++:** el primer centroide es una persona al azar. Cada centroide siguiente se elige con una probabilidad proporcional a la distancia al cuadrado al centroide más cercano. Así los centros iniciales quedan repartidos.
2. **Algoritmo de Lloyd:** se asigna cada persona al centroide más cercano y luego se mueve cada centroide al promedio de sus personas. Se repite hasta que los centroides casi no se muevan (tolerancia 10⁻⁶) o hasta 300 iteraciones. Si un grupo queda vacío, recibe al punto más lejano de su centroide.
3. **Función objetivo (inercia):** inercia = Σᵢ ‖zᵢ − μ_grupo(i)‖².
4. **Coeficiente de silueta** de cada persona: s = (b − a) / máx(a, b), donde *a* es su distancia media a los de su grupo y *b* su distancia media al grupo vecino más cercano. Va de −1 a 1.
5. **Interpretación automática de los grupos:**
   - Centroide de cada grupo en unidades reales (por ejemplo, «84 años») y en desviaciones (z) respecto al promedio de la organización.
   - **Puntaje por dimensión** = promedio de (peso × z) de las variables de esa dimensión. Si es positivo, el grupo tiene más necesidad que el promedio.
   - **Orden:** el Grupo 1 es el de mayor necesidad (suma de los puntajes positivos).
   - **Nombre del perfil**, a partir de las 1 o 2 dimensiones con puntaje ≥ 0,4: «Cuidado de la salud», «Control de signos vitales», «Experiencia de vida» o «Acompañamiento cercano» (dos se unen con « · »; la edad solo da nombre cuando es la única que sobresale). Si ninguna llega a 0,4 y todas están en 0,1 o menos, el perfil es «Activos e independientes»; en otro caso, «Bienestar en equilibrio».
   - **Rasgos:** las variables en que el grupo se aleja al menos 0,5 desviaciones del promedio (máximo 5), con el valor real. Por ejemplo: «Medicamentos activos más alto que el promedio: 5 frente a 2,1».
   - **Recomendaciones de atención y de actividades** por dimensión (Tabla 11), más los 3 intereses más comunes del grupo y su porcentaje de asistencia a actividades.
6. **PCA a 2 dimensiones**, solo para dibujar el mapa de los grupos. K-means agrupa con todas las variables, así que dos grupos pueden verse superpuestos en el plano y estar bien separados en el espacio completo. Los ejes se llaman «Mayor diferencia entre personas» y «Segunda mayor diferencia», con el porcentaje de la información que resume cada uno.

| Dimensión destacada | Recomendación de atención | Actividades sugeridas |
|---|---|---|
| Condiciones de salud | Revisar con frecuencia los medicamentos y que sigan los tratamientos (recordatorios en VITA+). Coordinar con su EPS o IPS los controles de sus enfermedades crónicas. | Actividades de bajo impacto y adaptadas: estiramientos, ejercicios en silla y talleres de memoria. |
| Signos vitales | Promover la toma regular de signos vitales y revisar las mediciones fuera de rango con un profesional de salud. | Jornadas de toma de signos vitales y charlas de autocuidado (alimentación, hidratación, control de la presión). |
| Edad | Verificar la movilidad y el riesgo de caídas, y que tengan una red de apoyo cercana. | Actividades sentadas o de ritmo suave y espacios de memoria y conversación (historias de vida, música). |
| Acompañamiento | Asignar acompañantes o voluntarios para sus citas médicas y hacer llamadas de seguimiento. Revisar las emergencias recientes y que sus contactos estén al día. | Actividades grupales de socialización que fortalezcan su red de apoyo (encuentros, celebraciones, salidas acompañadas). |
| Ninguna (autónomos) | Seguimiento preventivo habitual: controles periódicos y signos vitales al día. | Actividades de mayor exigencia (caminatas, baile, deporte adaptado) e invitarlos a apoyar a otros grupos, como voluntariado entre pares. |

*Tabla 11 Recomendaciones por dimensión del perfil*

**Reglas de casos prioritarios** (reporte de salud, «Personas que requieren atención»). Una persona aparece en la lista si cumple alguna de estas condiciones, y la lista muestra el motivo:

- su última medición tiene la presión, el pulso o el oxígeno fuera del rango de referencia;
- no tiene mediciones hace más de **30 días**;
- nunca se le han medido signos vitales;
- tiene mediciones con valores imposibles (posible error de registro).

El orden es: primero las que tienen más indicadores fuera de rango y luego las que tienen errores de registro. Además, el grafo de la red resalta a las personas **sin acompañante** como posible aislamiento.

`[NOTA: la sección de «Alertas» de la organización, que unificaría inactividad en actividades, falta de acompañantes y signos fuera de rango (RF-60), sigue pendiente. Hay que mencionarla como trabajo pendiente, no como implementada.]`

**Modelo de recomendación de organizaciones** (RF-49 y RF-71). Para cada organización a la que la persona mayor o el voluntario todavía no pertenece, se calcula un puntaje de 0 a 1 como promedio ponderado de cuatro criterios:

| Criterio | Peso | Cálculo |
|---|---|---|
| Actividades | 0,40 | Cuántos de sus gustos tienen actividades en la organización (últimos 6 meses y próximas) |
| Comunidad | 0,25 | Similitud de Jaccard entre sus gustos y los de los miembros de la organización: J(A, B) = \|A ∩ B\| / \|A ∪ B\| |
| Cercanía | 0,35 | Distancia entre las direcciones: 1 a 1 km o menos, 0 a 8 km o más (si una dirección no se puede interpretar, se usa la de su barrio) |
| Oficio | 0,10 | Si la organización tiene actividades relacionadas con su profesión u oficio y si alguna de sus personas mayores se dedicó a lo mismo |

*Tabla 12 Criterios del recomendador de organizaciones*

Si un criterio no se puede calcular (sin gustos, sin profesión o sin barrio), su peso se reparte entre los demás. Con los cuatro criterios, los pesos efectivos son 36 %, 23 %, 32 % y 9 %. Se muestran como máximo 5 recomendaciones, cada una con sus razones.

**Análisis de la red de apoyo** (RF-79 y RF-80):

- Grafo de la red: la organización unida a sus personas mayores y voluntarios, y cada persona mayor a sus acompañantes (con el parentesco). Se resaltan las personas sin acompañante.
- Grafo por gustos: cada gusto unido a quienes lo tienen.
- Mapa: la sede, las personas mayores agrupadas por barrio (nunca su dirección exacta), los lugares de las actividades de los últimos 90 días y los próximos 60, y líneas de cada barrio a los lugares a los que va su gente. Se puede filtrar por gusto.
- Indicadores: personas mayores, personas sin acompañante, acompañantes y voluntarios.

#### 4.4 Resultados del Modelado

Presentar aquí los resultados de una ejecución con datos ficticios, aclarando que son de prueba técnica:

- `[POR COMPLETAR: organización usada, número de personas analizadas y k sugerido.]`
- `[POR COMPLETAR: silueta obtenida para cada k (gráfica «Número de grupos»; la tabla incluye la inercia).]`
- `[POR COMPLETAR: nombre, tamaño y porcentaje de cada perfil, con sus rasgos principales (tarjetas de perfil).]`
- `[POR COMPLETAR: captura del mapa de los perfiles (PCA) y de la gráfica «Qué distingue a cada grupo».]`
- Interpretación esperada: como el script genera perfiles de salud sintéticos (hipertensión, diabetes, EPOC), es esperable que K-means **recupere la estructura con que se generaron los datos**. Esto valida el procedimiento, pero no dice nada sobre la población real.

Figuras sugeridas: diagrama de flujo del proceso de perfilamiento (puede adaptarse del diagrama Mermaid de `docs/KMEANS_PERFILES.md`), mapa de los perfiles y tarjetas de perfil.

#### 4.5 Limitaciones del Modelado

1. **Datos reales escasos:** con datos ficticios, el modelo recupera la estructura con que se generaron, no la de una población real. El reporte avisa cuántos registros son de prueba.
2. **Muestras pequeñas:** cada organización se analiza por separado; con pocas decenas de personas, agregar o corregir unos registros puede cambiar los grupos. Se recomiendan al menos 30 personas con datos completos, y la plataforma lo advierte.
3. **Un 0 no siempre es un 0:** «0 enfermedades» puede significar que no se han registrado.
4. **La imputación acerca al promedio:** completar con la mediana evita perder personas, pero las hace parecer más «promedio» de lo que son.
5. **Supuestos de K-means:** supone grupos más o menos esféricos y de tamaño parecido, es sensible a los valores extremos (se mitiga con el recorte a ±3) y siempre devuelve *k* grupos aunque los datos no los tengan. Por eso se reporta la silueta.
6. **Signos vitales autorreportados:** muchas mediciones las registra la propia persona o su acompañante, con equipos domésticos y frecuencias distintas.
7. **Recomendaciones genéricas:** son reglas por dimensión, no planes de cuidado individuales. La organización decide qué hacer (RNF-13).
8. **Pesos iguales:** todas las variables pesan igual en la distancia, así que las dimensiones con más variables (salud y signos vitales) influyen más en la agrupación.
9. **Recomendador:** los pesos (0,40 / 0,25 / 0,35 / 0,10) se fijaron por criterio del equipo y no se han calibrado con la respuesta de los usuarios.

---

### Fase 5: Evaluación

#### 5.1 Evaluación de los Resultados

La evaluación técnica se apoya en **pruebas automatizadas** que se ejecutan sin la base de datos real: usan una base H2 en memoria con datos controlados (RNF-10).

| Componente | Pruebas | Qué verifican |
|---|---|---|
| `KMeansTest` | 6 | Que separe nubes bien diferenciadas; que con la misma semilla dé siempre los mismos grupos; que la silueta prefiera el número real de grupos; que la inercia baje al aumentar *k*; que soporte puntos repetidos sin grupos vacíos ni valores NaN; que el primer eje del PCA siga la dirección de mayor varianza |
| `PerfilesControllerTest` | 6 | Con 8 personas «autónomas» y 8 «frágiles», más casos de borde (una persona sin datos, una de otra organización, una con vínculo pendiente): la calidad, las excluidas y las advertencias; el descarte de variables; que encuentre 2 grupos con silueta > 0,5 y que el de más necesidad salga primero; que se pueda elegir *k*; que con menos de 12 personas no entrene |
| `AnaliticaControllerTest` | 9 | Las consultas de los reportes de actividades, salud, población y retiros |
| `RecomendacionTest` y `RecomendacionControllerTest` | 7 + 4 | Los criterios y el puntaje del recomendador |
| `DireccionBogotaTest` | 8 | La interpretación de las direcciones de Bogotá para calcular distancias |
| `RedControllerTest` | 7 | La red de apoyo |
| `UbicadorAutomaticoTest` | 11 | La obtención del barrio y de la ubicación a partir de las direcciones |
| `PdfControllerTest` | 4 | La generación de los reportes en PDF |
| Frontend (`reporte-*.spec.ts`, `red-datos.spec.ts`) | 4 + 9 + 6 + 4 + 5 + 10 | El cálculo de los indicadores, las tablas y el contenido del PDF de cada reporte |

*Tabla 13 Pruebas automatizadas del componente analítico*

`[POR COMPLETAR: resultado de la última ejecución de las pruebas (todas aprobadas o no), con la fecha.]`

#### 5.2 Evaluación de los Perfiles

- **Separación:** el coeficiente de silueta del *k* elegido, interpretado con la escala de Kaufman y Rousseeuw (4.2). `[POR COMPLETAR: valor obtenido]`
- **Tamaño de los grupos:** la plataforma advierte cuando un grupo tiene menos de 3 personas.
- **Interpretabilidad:** cada perfil tiene nombre, rasgos en unidades reales y recomendaciones (criterio 3 de la Tabla 1).
- **Advertencias automáticas** que acompañan siempre a los perfiles: presencia de datos ficticios, menos de 30 personas (grupos poco estables), más del 15 % de valores imputados, silueta menor que 0,25, grupos con menos de 3 personas y variables descartadas.
- **Estabilidad:** no se mide todavía (se propone *bootstrap* como trabajo futuro).

#### 5.3 Evaluación de Indicadores

Cada indicador tiene una definición, una fórmula y una fuente identificable (criterios 2 y 7 de la Tabla 1). La exactitud se comprueba con las pruebas de la Tabla 13, comparando los resultados con datos de entrada conocidos.

| Reporte | Indicador | Fórmula o regla | Fuente |
|---|---|---|---|
| Actividades (RF-45) | Actividades del período | Número de actividades confirmadas con fecha en el período | `actividad` |
| | Inscripciones | Suma de inscritos; promedio por actividad = inscritos / actividades | `participacion` |
| | Asistencia | Asistentes / inscripciones con asistencia registrada × 100 | `participacion.asistio` |
| | Ocupación de cupos | Σ mín(inscritos, cupos) / Σ cupos × 100, solo en las actividades con cupos definidos | `actividad.cupos`, `participacion` |
| Salud (RF-46, RF-72) | Con seguimiento | Personas con al menos una medición en el período / personas vinculadas | `signo_vital` |
| | Mediciones registradas | Número de mediciones en el período | `signo_vital` |
| | Valores fuera de rango | Personas cuya última medición tiene algún indicador fuera de rango | `signo_vital` y rangos de referencia |
| | Sin medición reciente | Personas sin mediciones en los últimos 30 días o sin ninguna | `signo_vital.fecha_hora` |
| | Posibles errores de registro | Mediciones del período con algún valor imposible | `signo_vital` y límites físicos |
| | Distribución por IMC | Personas por categoría de la OMS según su IMC más reciente | `signo_vital.peso`, `estatura` |
| Población (RF-47, RF-71, RF-72) | Personas asociadas | Personas con vínculo aceptado | `persona_mayor_organizacion` |
| | Edad promedio y rangos de edad | Promedio de las edades; rangos de menos de 60, 60–69, 70–79, 80–89 y 90 o más | `usuario.fecha_nacimiento` |
| | Con EPS registrada | Personas con EPS / total × 100 | `persona_mayor.eps` |
| | Con intereses registrados | Personas con al menos un interés / total × 100 | `persona_mayor_gusto` |
| | Género, viven solas, EPS, intereses y profesiones | Conteos y porcentajes por categoría | `usuario`, `persona_mayor`, `gusto` |
| Retiros (RF-73) | Retiros | Personas que eliminaron su cuenta en el período | `retiro_cuenta` |
| | Tasa de retiro | Retiros / (personas actuales + retiros) × 100 | `retiro_cuenta`, vínculos |
| | Por la plataforma | Retiros por razones relacionadas con la plataforma (difícil de usar, ya no la necesita, privacidad) | `retiro_cuenta.razon` |
| | Razón más común | Moda de las razones | `retiro_cuenta.razon` |
| Red (RF-79) | Sin acompañante | Personas mayores sin vínculos aceptados con acompañantes | `persona_mayor_acompanante` |

*Tabla 14 Definición de los indicadores*

Los reportes de actividades, salud y retiros se pueden filtrar por período (últimos 30 días, últimos 90 días, último año o todo). Los de población y perfiles muestran el estado actual.

#### 5.4 Validación con Usuarios

`[POR COMPLETAR POR EL EQUIPO: esta sección no se puede escribir a partir del código. Debe incluir con qué organizaciones o personas de la UPL Entrenubes (o de PROSOFI) se mostraron los reportes y los perfiles, cuándo, qué método se usó (entrevista, encuesta, sesión de prueba), qué opinaron sobre la utilidad y la claridad, y qué cambios surgieron.]`

Fuentes internas que pueden apoyar esta sección:

- La **calificación de la plataforma** (RF-70): de 0 a 5 estrellas, con una observación opcional, guardada en la tabla `calificacion_plataforma`. `[POR COMPLETAR: promedio y número de calificaciones reales, si las hay.]`
- Las **razones de retiro** relacionadas con la plataforma (reporte de retiros).
- Las pruebas de usuario del QC del 14 de octubre de 2026 (commit `e39ed29`, «pruebas por usuario para el QC del 14 de octubre»). `[POR COMPLETAR: resultados de ese QC, si se documentaron.]`

#### 5.5 Conclusiones de la Evaluación

Contrastar cada criterio de éxito de la Tabla 1 con la evidencia:

| Criterio (Tabla 1) | Evidencia | Estado |
|---|---|---|
| 1. Calidad de los datos | Verificación automática de completitud, validez y suficiencia antes de entrenar; niveles de calidad; advertencias | Cumplido técnicamente; depende de los datos reales |
| 2. Exactitud de los indicadores | Pruebas automatizadas con datos conocidos (Tabla 13) | `[POR COMPLETAR según el resultado de las pruebas]` |
| 3. Utilidad de los perfiles | Silueta, nombres, rasgos y recomendaciones | `[POR COMPLETAR: validar con usuarios]` |
| 4. Casos prioritarios | Reglas explícitas con el motivo de cada caso | Cumplido (las alertas unificadas de RF-60 están pendientes) |
| 5. Interpretabilidad | Gráficas con vista de tabla y explicación escrita en el PDF | Cumplido |
| 6. Tiempo de respuesta | Cálculo en la base de datos; K-means en milisegundos | `[POR COMPLETAR según la decisión de B.9]` |
| 7. Trazabilidad | Cada indicador con fuente y fórmula (Tabla 14); cada caso prioritario con su motivo; cada persona con su perfil | Cumplido |

*Tabla 15 Cumplimiento de los criterios de éxito*

Cierre sugerido: los procedimientos funcionan y están validados técnicamente con datos ficticios. Las conclusiones sobre la población real quedan sujetas a la consolidación de los datos recolectados en Usme.

---

### Fase 6: Despliegue

#### 6.1 Integración de los Resultados

- Los resultados analíticos se integran en la plataforma web VITA+, en el panel del rol **organización**, sección **«Analítica»**, con seis pestañas: Actividades y participación, Grupos de atención (perfiles con K-means), Salud de la población, Perfil de la población, Donaciones e inventario y Retiros. A esto se suma la sección **«Red y mapa»**.
- Las recomendaciones de organizaciones aparecen en los paneles de la **persona mayor** y del **voluntario**.
- Todo se calcula bajo demanda: no hay procesos por lotes ni modelos guardados que puedan quedar desactualizados.
- Arquitectura: microservicio **`analitica-service`** (Spring Boot, Java 17), detrás del **API Gateway**, con acceso de solo lectura a la base compartida. Endpoints: `GET /api/analitica/actividades`, `/salud`, `/poblacion`, `/retiros`, `/perfiles?k=`, el de la red y `POST /api/analitica/reportes/pdf`.
- Seguridad y privacidad: solo las cuentas de organización acceden a la analítica (las demás reciben 403); cada organización ve solo a sus personas con vínculo aceptado; las consultas a datos de salud quedan en el registro de auditoría (RNF-25); los mapas muestran el barrio, no la dirección (RNF-17).

#### 6.2 Visualización de Indicadores

- Librería **Apache ECharts**, con un tema propio y consistente en toda la plataforma.
- Cada reporte tiene **tarjetas de indicadores** (KPI) y gráficas; **cada gráfica tiene una vista de tabla** por accesibilidad.
- Gráficas por reporte:
  - **Actividades:** inscritos y asistentes por mes, participación por tipo de actividad y ocupación de cupos (las 10 actividades más ocupadas).
  - **Salud:** estado actual por indicador (normal, revisar o posible error de registro; al hacer clic se filtra la tabla de atención), mediciones por semana o por mes, IMC por categoría, evolución de una persona con la franja del rango normal y la tabla «Personas que requieren atención».
  - **Población:** rangos de edad, género, si viven solas, EPS, intereses más comunes y profesiones u oficios.
  - **Retiros:** razones (separando las personales de las relacionadas con la plataforma), retiros por mes y comentarios.
- Selector de período para actividades, salud y retiros.
- Los datos se actualizan en tiempo real (RNF-04).

Figuras sugeridas: una captura de cada reporte.

#### 6.3 Visualización de Perfiles

| Elemento de la pestaña «Grupos de atención» | Qué muestra |
|---|---|
| Indicadores | Personas analizadas, perfiles encontrados, separación de los grupos (silueta) y calidad de los datos |
| «Ten en cuenta antes de usar estos perfiles» | Las advertencias de calidad y suficiencia |
| Mapa de los perfiles | Dispersión en 2D (PCA), con un color por grupo |
| Tarjetas de perfil | Nombre, tamaño, rasgos, atención y actividades sugeridas, y asistencia |
| Qué distingue a cada grupo | Barras con el z del centroide por variable; la tabla da los valores en unidades reales |
| Número de grupos | Silueta según *k*, con el sugerido marcado; la tabla incluye la inercia |
| Calidad de los datos | Completitud por variable; en gris, las que no entraron al modelo |
| Personas de cada grupo y personas sin datos suficientes | Para planear la atención y para completar los registros |

*Tabla 16 Elementos de la visualización de perfiles*

El selector «Grupos» permite cambiar *k*. Si los datos no alcanzan, la pestaña explica por qué y qué falta registrar.

#### 6.4 Generación de Reportes

- Cada reporte (incluido el de perfiles) se descarga en **PDF** (RF-48).
- El PDF lo genera el backend con **OpenPDF**. Lleva un encabezado con la organización y el período, los indicadores, cada gráfica como imagen con su **explicación escrita** y su tabla, y una nota con las limitaciones. En el reporte de salud, la nota recuerda que los rangos son orientativos y no reemplazan el criterio médico.
- Las explicaciones se **redactan automáticamente con los datos de cada organización**. Por ejemplo: «En el período hubo N inscripciones y M asistencias, y el mes con el mayor número de inscritos fue…».

#### 6.5 Integración con la Plataforma

- **Despliegue:** contenedores Docker orquestados con Docker Compose (RNF-14), con Caddy como proxy HTTPS, el API Gateway, health checks, reinicio automático y circuit breaker (RNF-16). Detalle en `docs/ARQUITECTURA_DISPONIBILIDAD.md`.
- **Estado del despliegue público:** RNF-15 (plataforma accesible por internet durante la validación) sigue pendiente. `[POR COMPLETAR: confirmar si ya está desplegada y dónde.]`
- **Mantenimiento:** como el modelo se recalcula en cada consulta, no hay que reentrenarlo. Cuando lleguen los datos reales, basta con registrarlos en la plataforma.
- **Respaldo:** copia de seguridad diaria de la base y procedimiento de prueba de restauración (RNF-27).
- **Trabajo futuro:**
  - Validar los perfiles con las organizaciones cuando haya datos reales y ajustar los umbrales (0,4 y 0,5) y las recomendaciones.
  - Medir la estabilidad de los grupos (*bootstrap*).
  - Comparar K-means con K-prototypes, clustering jerárquico y DBSCAN.
  - Guardar un histórico de perfiles para ver cómo cambia cada persona en el tiempo.
  - Implementar las alertas unificadas de la organización (RF-60) y las secciones pendientes de acompañantes (RF-58) y medicamentos (RF-59).

---

### Referencias

Referencias técnicas que el contenido anterior cita. `[POR COMPLETAR: dar formato con la norma que use el resto de la documentación del proyecto, por ejemplo APA 7 o IEEE.]`

- Chapman, P., Clinton, J., Kerber, R., Khabaza, T., Reinartz, T., Shearer, C. y Wirth, R. (2000). *CRISP-DM 1.0: Step-by-step data mining guide*. SPSS.
- MacQueen, J. (1967). Some methods for classification and analysis of multivariate observations. *Proceedings of the Fifth Berkeley Symposium on Mathematical Statistics and Probability*, 1, 281–297.
- Lloyd, S. (1982). Least squares quantization in PCM. *IEEE Transactions on Information Theory*, 28(2), 129–137.
- Arthur, D. y Vassilvitskii, S. (2007). k-means++: The advantages of careful seeding. *Proceedings of the 18th Annual ACM-SIAM Symposium on Discrete Algorithms*, 1027–1035.
- Rousseeuw, P. J. (1987). Silhouettes: A graphical aid to the interpretation and validation of cluster analysis. *Journal of Computational and Applied Mathematics*, 20, 53–65.
- Kaufman, L. y Rousseeuw, P. J. (1990). *Finding Groups in Data: An Introduction to Cluster Analysis*. Wiley.
- Jolliffe, I. T. (2002). *Principal Component Analysis* (2.ª ed.). Springer.
- Jaccard, P. (1912). The distribution of the flora in the alpine zone. *New Phytologist*, 11(2), 37–50.
- Organización Mundial de la Salud. Clasificación del índice de masa corporal para adultos.
- Congreso de Colombia (2012). Ley Estatutaria 1581 de 2012, por la cual se dictan disposiciones generales para la protección de datos personales.
- Apache Software Foundation. Apache ECharts (documentación oficial).

### Anexos

Anexos sugeridos:

- **Anexo A.** Diccionario de datos de las tablas usadas (a partir de B.1).
- **Anexo B.** Consultas SQL de lectura del perfilamiento (`backend/analitica-service/.../perfiles/DatosPerfiles.java`) y de los reportes (`.../controller/AnaliticaController.java`).
- **Anexo C.** Documento técnico del perfilamiento: `docs/KMEANS_PERFILES.md`.
- **Anexo D.** Ejemplo de un reporte en PDF descargado de la plataforma, con datos ficticios.
- **Anexo E.** Capturas de la pestaña «Grupos de atención» y de la sección Red y mapa.
- **Anexo F.** Requerimientos relacionados con la analítica: RF-45 a RF-49, RF-60, RF-71 a RF-73, RF-79 a RF-81 y RNF-13 (de `docs/REQUERIMIENTOS.md`).

---

## Parte D. Qué debe aportar el equipo y cómo obtenerlo

| Dato faltante | Cómo obtenerlo |
|---|---|
| Resultados de la preparación y del modelado (3.6 y 4.4) | Cargar los datos ficticios, entrar con una cuenta de organización (`org…@vitaplus.test`, contraseña `Vita2025*`), ir a **Analítica → Grupos de atención**, tomar capturas y anotar las personas analizadas, el *k* sugerido, la silueta y los perfiles. También sirve llamar a `GET /api/analitica/perfiles?k=3` a través del gateway con el token de la organización. |
| Resultado de las pruebas (5.1) | `./mvnw test` en `backend/analitica-service` y `npx ng test --watch=false` en `frontend`. |
| Estado de los datos reales (2.1 y 3.6) | Número de personas mayores recolectadas en Usme y si ya están cargadas en la plataforma. |
| Validación con usuarios (5.4) | Sesiones con las organizaciones de la UPL Entrenubes o con PROSOFI. |
| Criterio de tiempo de respuesta (1.1.4) | Decisión del equipo (ver B.9). |
| Despliegue público (6.5) | Confirmar si ya está desplegada y la URL. |
| Historial de cambios | Fechas y autores de las nuevas versiones. |
