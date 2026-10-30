# Insumos para terminar el documento CRISP-DM de VITA+

Este archivo reúne todo lo necesario para dejar terminado el documento **«Metodología CRISP-DM»** (*Plataforma web para apoyar la gestión de información, mediante analítica de datos, en organizaciones que asisten a personas mayores. Caso de estudio: UPL Entrenubes (Usme, Bogotá)*).

Parte de la versión del documento del **21/10/2026** (la que ya tiene escritas las fases 1 a 6, las referencias y los anexos) y la contrasta con el código del repositorio al **22 de octubre de 2026** (commit `4417b14`), más los cambios del **29 de octubre de 2026** (uso del celular en el reporte de población, B.8).

| Parte | Para qué sirve |
|---|---|
| [A](#parte-a-reglas-para-editar-el-documento) | Reglas de estilo para editar sin romper el documento |
| [B](#parte-b-correcciones-por-los-últimos-cambios-del-código) | Correcciones obligatorias: lo que el documento dice y ya no es cierto, con el texto nuevo listo para pegar |
| [C](#parte-c-cómo-llenar-cada-por-completar) | Cada `[POR COMPLETAR]` del documento: el dato (si ya se conoce) o cómo obtenerlo |
| [D](#parte-d-imágenes-que-faltan) | **Lista de imágenes**: qué capturar, dónde, qué debe verse y en qué figura va |
| [E](#parte-e-listas-de-tablas-y-figuras-e-historial-de-cambios) | Listas de tablas y figuras actualizadas, e historial de cambios |
| [F](#parte-f-referencia-técnica-de-lo-nuevo) | Referencia técnica de lo nuevo (donaciones e inventario, datos ficticios de Entrenubes, uso del celular) |

---

## Parte A. Reglas para editar el documento

- Español formal, tercera persona impersonal («se seleccionaron», «la plataforma calcula»), como el resto del documento.
- Las tablas llevan la leyenda debajo, en cursiva («Tabla N Título»); las figuras, igual («Figura N Título»).
- No se pegan fragmentos de código en el cuerpo; sí las fórmulas.
- **No inventar cifras.** Lo que dependa de una ejecución o de los datos reales queda como `[POR COMPLETAR: …]` hasta que se tenga el dato.
- **Datos ficticios = prueba técnica.** Todo resultado obtenido con el script de datos ficticios se presenta como prueba del procedimiento, no como conclusión sobre la población real.
- **Solo apoyo a la decisión (RNF-13).** Se repite en las fases 4, 5 y 6.
- Después de agregar o quitar tablas y figuras, **actualizar los campos** del procesador de texto (índice, lista de tablas y lista de figuras) para que los números de página cuadren.

---

## Parte B. Correcciones por los últimos cambios del código

Entre el 21 y el 29 de octubre de 2026 entraron estos cambios que afectan el documento:

| Commit | Cambio | Afecta |
|---|---|---|
| `58ea71b`, `5996d7e` | Nueva pestaña de analítica **«Donaciones e inventario»** (un solo reporte, con PDF) y endpoints `GET /api/analitica/donaciones` y `GET /api/analitica/inventario` | 2.2.2, Tabla 5, 3.1, 3.2, 3.3, Tabla 10, Tabla 13, Tabla 14, 5.3, 6.1, 6.2, 6.4, Tabla 17, figuras |
| `5996d7e` | La pestaña **«Perfiles» se llama ahora «Grupos de atención»** y es la **segunda** pestaña | 3.6, Tabla 10, 6.1, 6.3, Tabla 16, Anexo E, figuras |
| `5a85c1e` | **Nuevos nombres de los grupos** de K-means (no alarman) y **nuevos nombres de los ejes** del mapa PCA | 4.3 (paso 5 y paso 6), 4.4 |
| `9733c42` | El script de datos ficticios agrega **160 personas mayores a la Fundación Entrenubes Usme** con cuatro arquetipos bien diferenciados y **20 actividades** más | 2.1, 2.2.1, cierre de la Fase 2, 4.2, 4.4 |
| `122766c` | Spring Boot 4.1.1 → **4.1.2** en todos los servicios (las URLs no cambian) | 6.1 (opcional) |
| `4417b14` | El perfil de la organización ya no muestra «Ubicación en el mapa» | No afecta el documento |
| 29/10/2026 | Nuevo dato opcional de la persona mayor: **nivel de uso del celular** (RF-84), y gráfica nueva **«Nivel de uso del celular»** en el reporte «Perfil de la población», con PDF; el script de datos ficticios lo llena | 2.1, 2.2.2, Tabla 5, 3.1, Tabla 13, Tabla 14, 6.2, Tabla 17, Anexo F (ver B.8) |
| 29/10/2026 | El acompañante puede inscribir a sus personas mayores en actividades desde «Seguimiento» (RF-40) | No afecta el documento: la inscripción se guarda igual (`participacion`) y los indicadores de actividades no cambian |

### B.1 Pestaña «Perfiles» → «Grupos de atención»

Reemplazar en todo el documento. Ubicaciones exactas:

| Sección | Texto actual | Texto nuevo |
|---|---|---|
| 3.6, primer `[POR COMPLETAR]` | «Se obtiene en Analítica → Perfiles» | «Se obtiene en Analítica → Grupos de atención» |
| 3.6, placeholder de la Figura 1 | «…de la pestaña Perfiles» | «…de la pestaña «Grupos de atención»» |
| Tabla 10, fila Perfilamiento | «Pestaña «Perfiles» (RF-81)» | «Pestaña «Grupos de atención» (RF-81)» |
| 6.1, «Ubicación en la plataforma» | Ver B.4 (cambia la lista completa de pestañas) | |
| 6.3, título y primer párrafo | «La pestaña «Perfiles» presenta…» | «La pestaña «Grupos de atención» presenta…». El título de la sección puede quedarse «6.3 Visualización de Perfiles» (en el modelo se siguen llamando perfiles) |
| Tabla 16, encabezado | «Elemento de la pestaña «Perfiles»» | «Elemento de la pestaña «Grupos de atención»» |
| Anexos, Anexo E | «Capturas de la pestaña Perfiles y de la sección Red y mapa» | «Capturas de la pestaña «Grupos de atención» y de la sección «Red y mapa»» |

Frase sugerida para agregar al inicio de 6.3, para que no se confunda con el reporte «Perfil de la población»:

> En la plataforma, los perfiles se presentan en la pestaña «Grupos de atención», nombre que se eligió para que la organización los lea como grupos de personas con necesidades de atención parecidas y no los confunda con el reporte «Perfil de la población».

### B.2 Sección 4.3, paso 5 «Nombre del perfil» (texto completo para reemplazar)

> **Nombre del perfil:** se asigna a partir de las una o dos dimensiones con puntaje ≥ 0,4: «Cuidado de la salud» (condiciones de salud), «Control de signos vitales» (signos vitales), «Experiencia de vida» (edad) o «Acompañamiento cercano» (necesidades de acompañamiento). Cuando sobresalen dos dimensiones, sus nombres se unen con « · », por ejemplo «Cuidado de la salud · Acompañamiento cercano». La edad solo da nombre al grupo cuando es la única dimensión que sobresale; si sobresale junto con otra, el grupo toma el nombre de la otra y la edad se refleja en sus rasgos y recomendaciones. Si ninguna dimensión alcanza 0,4 y todas se encuentran en 0,1 o menos, el perfil se denomina «Activos e independientes»; en otro caso, «Bienestar en equilibrio». Los nombres describen lo que el grupo necesita de la organización y no un problema de las personas, para que no generen alarma en quien los lee.

### B.3 Sección 4.3, paso 6 «PCA» (última frase)

Cambiar «Cada eje indica el porcentaje de la variación que conserva.» por:

> Para que la gráfica sea comprensible para las organizaciones, el primer eje se denomina «Mayor diferencia entre personas» y el segundo, «Segunda mayor diferencia», y cada uno indica el porcentaje de la información que resume. La tarjeta de la gráfica y el PDF explican el significado de los ejes.

### B.4 Sección 6.1 «Ubicación en la plataforma» (texto completo para reemplazar)

> **Ubicación en la plataforma:** los resultados analíticos se integran en el panel del rol organización, sección «Analítica», que cuenta con seis pestañas, en este orden: Actividades y participación, Grupos de atención (perfiles obtenidos con K-means), Salud de la población, Perfil de la población, Donaciones e inventario y Retiros. A esto se suma la sección «Red y mapa».

Y en el punto **Arquitectura** de 6.1, agregar los dos endpoints nuevos:

> Expone los endpoints GET /api/analitica/actividades, /salud, /poblacion, /donaciones, /inventario, /retiros, /perfiles?k=, el endpoint de la red de apoyo y POST /api/analitica/reportes/pdf.

(Opcional: «Spring Boot 4.1.2, Java 17».)

### B.5 Reporte nuevo: «Donaciones e inventario»

**2.2.2 Estructura de los datos** — agregar estas viñetas (antes de «Usuario»):

> - **Donación (donacion):** registra las donaciones que los acompañantes y los voluntarios programan para una organización, con la fecha de entrega, el estado (programada, recibida, no entregada o cancelada), el donante y su rol.
> - **Artículo donado (donacion_articulo):** los artículos de cada donación, con su categoría (alimentos, aseo, ropa, medicamentos, dinero u otros), descripción, cantidad, unidad y la cantidad efectivamente recibida.
> - **Artículo de inventario (inventario_articulo):** lo que tiene hoy cada organización, con nombre, categoría, unidad, cantidad, cantidad mínima de aviso y fecha de vencimiento.
> - **Movimiento de inventario (inventario_movimiento):** las entradas y salidas de cada artículo, con su cantidad, motivo (donación, compra, aporte externo, entrega, uso, vencido, etc.) y fecha.

**Tabla 5 Variables disponibles** — agregar una fila:

| Dimensión | Tabla(s) | Variables disponibles | Posible uso analítico |
|---|---|---|---|
| Donaciones e inventario | donacion, donacion_articulo, inventario_articulo, inventario_movimiento | fecha_entrega, estado, rol_donante, id_donante; categoria, cantidad, unidad, cantidad_recibida; cantidad_minima, fecha_vencimiento; tipo (ENTRADA, SALIDA), motivo, fecha | Indicadores de gestión de recursos de la organización |

**3.1, párrafo «Variables de los reportes descriptivos»** — agregar al final de la enumeración: «…, las donaciones con sus artículos (`donacion`, `donacion_articulo`) y el inventario con sus movimientos (`inventario_articulo`, `inventario_movimiento`)».

**3.2, Tabla 8 Reglas de limpieza** — agregar dos filas:

| Regla | Detalle | Motivo |
|---|---|---|
| Donaciones sin desenlace | El porcentaje de donaciones que llegaron se calcula solo sobre las donaciones recibidas y no entregadas; las programadas y las canceladas no cuentan. Una donación programada cuya fecha de entrega ya pasó se señala como vencida. | Una donación que todavía no debía llegar, o que el donante canceló, no es un incumplimiento. |
| Vencimiento de artículos agotados | Un artículo solo se marca como vencido o por vencer (próximos 30 días) si todavía tiene existencias. | Un artículo agotado no representa un riesgo de vencimiento. Son las mismas reglas de la página de Inventario (RF-68). |

**3.3, viñeta «Agregaciones temporales»** — cambiar por:

> **Agregaciones temporales:** para los reportes, las actividades, las donaciones (por fecha de entrega) y los retiros se agregan por mes, y las mediciones de signos vitales, por semana o por mes según la longitud del período consultado.

**3.5, viñeta «Dataset para los reportes»** — sin cambios de fondo; puede agregarse «(una fila por actividad, por medición, por persona, por donación o por movimiento de inventario)».

**1.3.3 (último párrafo de indicadores) y Tabla 10** — agregar la fila:

| Objetivo analítico (Fase 1) | Técnica | Dónde se ve en la plataforma |
|---|---|---|
| Indicadores de seguimiento (1.3.3): recursos de la organización | Estadística descriptiva y reglas explícitas de aviso (agotado, por debajo de la cantidad mínima, vencido o por vencer en 30 días) | Pestaña «Donaciones e inventario» (RF-83) |

y en 1.3.3, en la lista de indicadores finales, agregar «donaciones e inventario» antes de «y retiros».

**Tabla 13 Pruebas automatizadas** — ver C.6 (tabla completa actualizada).

**Tabla 14 Definición de los indicadores** — agregar estas filas (entre Población y Retiros, en el mismo orden de las pestañas):

| Reporte | Indicador | Fórmula o regla | Fuente |
|---|---|---|---|
| Donaciones e inventario (RF-83) | Donaciones que llegaron | Donaciones recibidas / (recibidas + no entregadas) × 100, con fecha de entrega en el período | `donacion.estado` |
| Donaciones e inventario (RF-83) | Donaciones por recibir | Donaciones en estado programada con fecha de entrega en el período; se señalan las que tienen la fecha vencida | `donacion` |
| Donaciones e inventario (RF-83) | Agotados o por agotarse | Artículos con cantidad 0 o menor o igual que su cantidad mínima | `inventario_articulo` |
| Donaciones e inventario (RF-83) | Vencidos o por vencer | Artículos con existencias y fecha de vencimiento pasada o en los próximos 30 días | `inventario_articulo.fecha_vencimiento` |
| Donaciones e inventario (RF-83) | Dinero disponible | Suma de las existencias de la categoría dinero; se muestra además el dinero recibido en donaciones del período | `inventario_articulo`, `donacion_articulo.cantidad_recibida` |

> En la columna «Reporte» de estas filas va «Donaciones e inventario (RF-83)». RF-83 es el requerimiento nuevo de este reporte en `docs/REQUERIMIENTOS.md` (sección 4.1).

**5.3, párrafo final** — cambiar por:

> Los reportes de actividades, salud, donaciones e inventario y retiros se pueden filtrar por período (últimos 30 días, últimos 90 días, último año o todo el historial); en el de donaciones e inventario, el filtro aplica a las donaciones y a los movimientos, mientras que el estado del inventario corresponde siempre al día de la consulta. Los reportes de población y de grupos de atención muestran el estado actual.

**6.2, «Gráficas por reporte»** — agregar entre Población y Retiros:

> ○ **Donaciones e inventario:** donaciones por mes según su estado (recibidas, por recibir, no llegaron y canceladas), qué se recibe (artículos recibidos por categoría), quién dona (donaciones programadas y recibidas de cada donante), de dónde llega y a dónde va (entradas y salidas del inventario por motivo), existencias por categoría y la tabla «Artículos que requieren atención».

y en **«Filtros»**: «los reportes de actividades, salud, donaciones e inventario y retiros cuentan con un selector de período».

**6.4 Generación de Reportes** — la frase «cada reporte, incluido el de perfiles, se puede descargar en PDF» pasa a «cada uno de los seis reportes, incluidos el de grupos de atención y el de donaciones e inventario, se puede descargar en PDF (RF-48)».

**Tabla 17 Diccionario de datos** — agregar:

| Tabla | Columnas relevantes para la analítica |
|---|---|
| donacion | id_donacion, id_organizacion, id_donante, rol_donante (ACOMPANANTE o VOLUNTARIO), fecha_entrega, estado (PROGRAMADA, RECIBIDA, NO_ENTREGADA, CANCELADA) |
| donacion_articulo | id_donacion, categoria, descripcion, cantidad, unidad, cantidad_recibida (nula hasta que se recibe) |
| inventario_articulo | id_inventario, id_organizacion, nombre, categoria, unidad, cantidad, cantidad_minima, fecha_vencimiento |
| inventario_movimiento | id_inventario, tipo (ENTRADA o SALIDA), cantidad, motivo, fecha |

**Anexos, viñeta del Anexo F** — cambiar «RF-45 a RF-49, RF-60, RF-71 a RF-73, RF-79 a RF-81 y RNF-13» por «RF-45 a RF-49, RF-60, RF-71 a RF-73, RF-79 a RF-81, RF-83 y RNF-13».

### B.6 Datos ficticios: nuevas cifras

El script `backend/scripts/seed_datos_fictisios.sql` ahora agrega una **sección 24** con 160 personas mayores para la **Fundación Entrenubes Usme** (`org01@vitaplus.test`). Cambiar en **2.1**, **2.2.1** y en el **cierre de la Fase 2** (sección 2.4):

| Dato | Dice ahora | Debe decir |
|---|---|---|
| Personas mayores | 300 | **460**: 300 repartidas entre las ocho organizaciones y **160 adicionales en la Fundación Entrenubes Usme**, con perfiles de salud y de acompañamiento bien diferenciados para validar el perfilamiento |
| Mediciones de signos vitales | cerca de 5.000 | **cerca de 8.000** (unas 5.000 de las 300 personas y unas 3.000 de las 160 de Entrenubes, que tienen entre 8 y 34 mediciones cada una). `[Confirmar con la consulta de resumen que imprime el script al final: fila «signos_vitales».]` |
| Actividades | 64 | **84** (64 de las ocho organizaciones y 20 de la Fundación Entrenubes Usme con 45 cupos), además de las propuestas de los voluntarios. `[Confirmar con la fila «actividades» del resumen del script.]` |
| Organizaciones, acompañantes y voluntarios | 8, 60 y 50 | Sin cambios |

Texto sugerido para agregar en 2.1, después de la enumeración del conjunto ficticio:

> Adicionalmente, el script incorpora 160 personas mayores en la Fundación Entrenubes Usme, distribuidas en cuatro arquetipos de 40 personas cada uno (autónomas y estables; con varias enfermedades crónicas y medicamentos; con signos vitales fuera de rango, y de edad avanzada que viven solas y requieren acompañamiento). Todas cuentan con la información que utiliza el perfilamiento, de modo que el modelo puede validarse sobre una estructura conocida.

### B.7 Sección 4.4, párrafo de interpretación esperada

Reemplazar «Debido a que el script de datos ficticios genera perfiles de salud sintéticos (hipertensión, diabetes y EPOC), se espera que K-means recupere la estructura con la que se generaron los datos.» por:

> Debido a que el script de datos ficticios genera perfiles de salud sintéticos (hipertensión, diabetes y EPOC) y, en la Fundación Entrenubes Usme, cuatro arquetipos de 40 personas cada uno, se espera que K-means recupere la estructura con la que se generaron los datos: en esa organización, cuatro grupos que corresponden a los cuatro arquetipos.

Y en **4.2, viñeta «Datos del experimento»**, precisar: «en primer lugar, se utiliza el conjunto de datos ficticios de la Fundación Entrenubes Usme, que tiene una estructura conocida de cuatro arquetipos; posteriormente, los datos reales cuando alcancen el mínimo requerido (ver 2.1)».

### B.8 Nivel de uso del celular en el reporte «Perfil de la población»

La persona mayor (o su acompañante en su nombre) puede indicar en su perfil, de forma opcional, su nivel de uso del celular. Se guarda en `persona_mayor.uso_celular` con uno de cuatro valores, de menor a mayor uso; si no lo indica, queda nulo. El reporte de población lo muestra en una gráfica nueva. Es una variable descriptiva: **no entra al modelo K-means** (las 14 variables de la Tabla 7 no cambian).

| Valor | Opción que ve la persona | Nombre en la gráfica |
|---|---|---|
| `NO_USA` | No lo usa | No lo usa |
| `BASICO` | Bajo (solo llamadas) | Bajo |
| `INTERMEDIO` | Medio (llamadas y WhatsApp) | Medio |
| `AVANZADO` | Alto (varias aplicaciones) | Alto |
| nulo | (no lo ha indicado) | Sin dato |

**2.2.2 Estructura de los datos** — en la viñeta de **Persona mayor (persona_mayor)**, agregar al final: «…y, de forma opcional, su nivel de uso del celular (no lo usa; bajo, solo llamadas; medio, llamadas y WhatsApp, o alto, varias aplicaciones)».

**Tabla 5 Variables disponibles** — en la fila de la persona mayor, agregar la variable `uso_celular` y, en «Posible uso analítico», «caracterización del uso del celular para elegir el canal de comunicación con cada persona».

**3.1, párrafo «Variables de los reportes descriptivos»** — donde se enumeran los datos del reporte de población (edad, género, si vive sola, EPS…), agregar «el nivel de uso del celular (`persona_mayor.uso_celular`)».

**Tabla 14 Definición de los indicadores** — agregar en el bloque de Población:

| Reporte | Indicador | Fórmula o regla | Fuente |
|---|---|---|---|
| Perfil de la población (RF-84) | Nivel de uso del celular | Personas asociadas en cada nivel (no lo usa, bajo, medio, alto) y sin dato, con su porcentaje sobre el total de asociadas. En la explicación del PDF, «uso bajo» = no lo usa + bajo, como porcentaje de quienes lo indicaron | `persona_mayor.uso_celular` |

**6.2, «Gráficas por reporte»** — en la viñeta de **Población**, agregar después de «si viven solas»: «nivel de uso del celular (barras de menor a mayor nivel, en tonos de un mismo color, y una barra gris para quienes no lo han indicado)». Texto sugerido para el párrafo que explica para qué sirve:

> La gráfica de nivel de uso del celular permite a la organización elegir el canal de comunicación más adecuado para cada grupo: con las personas que no usan el celular o tienen un uso bajo (solo llamadas) conviene avisar las actividades y los recordatorios por llamada o a través de su acompañante, mientras que quienes tienen un uso medio o alto (WhatsApp o varias aplicaciones) pueden aprovechar mejor la plataforma.

**Tabla 17 Diccionario de datos** — en la fila `persona_mayor`, agregar «uso_celular (NO_USA, BASICO, INTERMEDIO o AVANZADO; nulo si no lo ha indicado)».

**Datos ficticios (2.1)** — el script ahora llena este dato en las 460 personas mayores: a más edad, menos uso, y cerca del 12 % sin indicar. Puede agregarse a la enumeración del conjunto ficticio: «…si vive sola y su nivel de uso del celular…».

**Anexos, viñeta del Anexo F** — sumar RF-84 a la lista de B.5: «RF-45 a RF-49, RF-60, RF-71 a RF-73, RF-79 a RF-81, RF-83, RF-84 y RNF-13».

**Figura 8** — no hace falta cambiarla; si se vuelve a tomar, puede incluir la gráfica de nivel de uso del celular (ver D.2).

---

## Parte C. Cómo llenar cada `[POR COMPLETAR]`

Recorrido en el orden del documento. Lo marcado **✅ Dato listo** ya se puede pegar.

### C.1 Fase 1, Tabla 1 criterio 6 y Tabla 15 criterio 6 (tiempo de respuesta)

La Tabla 1 ya quedó redactada sin cifra («…el algoritmo K-means procesa cientos de personas en milisegundos»). En la Tabla 15, el estado del criterio 6 depende de esa decisión:

- **Opción recomendada (sin umbral):** Estado = «Cumplido: los reportes se calculan en la base de datos con consultas de solo lectura y el perfilamiento se calcula en cada consulta».
- **Opción con umbral:** fijar un valor con el equipo (por ejemplo, menos de 3 segundos por reporte) y medirlo en el navegador (DevTools → pestaña Red, tiempo de `GET /api/analitica/perfiles` y de cada reporte). `[POR COMPLETAR: decisión del equipo]`

### C.2 Sección 3.6 Resultados de la Preparación

Usar la **Fundación Entrenubes Usme** (`org01@vitaplus.test`, contraseña `Vita2025*`), que es la que tiene los grupos más claros. Entrar a **Analítica → Grupos de atención** y anotar:

| Viñeta del documento | Dónde se ve | Qué esperar (no es el dato, solo para revisar que tenga sentido) |
|---|---|---|
| Personas vinculadas, incluidas y excluidas, y motivo | Indicador «Personas analizadas» y tabla «Personas sin datos suficientes» (al final de la pestaña) | Unas 190 vinculadas (las 160 de la sección 24 más las que el script reparte al azar). Las 160 de Entrenubes entran todas; las excluidas, si las hay, son de las repartidas al azar sin signos vitales en los últimos 180 días |
| Completitud por variable y % imputado | Gráfica «Calidad de los datos» (y su vista de tabla) y el aviso de valores imputados en «Ten en cuenta antes de usar estos perfiles» | Completitud alta; el % imputado lo ponen sobre todo las personas repartidas al azar |
| Variables descartadas | Aviso «No se usaron en el modelo: …» y barras en gris en «Calidad de los datos» | Lo esperable es que ninguna |
| Nivel de calidad | Indicador «Calidad de los datos» | Como máximo «Aceptable», porque hay datos de prueba |
| Datos reales de Usme | Fuera de la plataforma | `[POR COMPLETAR POR EL EQUIPO: número de personas mayores recolectadas en Usme, si ya están cargadas en la plataforma y si alguna organización alcanza el mínimo de 12 personas]` |

Redacción sugerida: «Con los datos ficticios de la Fundación Entrenubes Usme, de las N personas vinculadas se incluyeron M en el modelo y se excluyeron E, por …». Recordar que es prueba técnica.

### C.3 Sección 4.4 Resultados del Modelado

Misma organización y pestaña. Con el selector «Grupos» en el valor sugerido:

| Viñeta | Dónde se ve |
|---|---|
| Organización, personas analizadas y *k* sugerido | Indicadores «Personas analizadas» y «Perfiles encontrados»; el *k* sugerido aparece marcado en «Número de grupos» |
| Silueta para cada *k* (con la inercia) | Gráfica «Número de grupos» → botón de vista de tabla (tiene silueta e inercia por *k*). Copiarla como tabla nueva: **«Tabla 12 Silueta e inercia según el número de grupos»** (ver E.1 para la renumeración) o dejarla como texto |
| Nombre, tamaño, % y rasgos de cada perfil | Tarjetas de perfil |
| Separación (para 5.2) | Indicador «Separación de los grupos» |

Lo esperable con los datos del script (para revisar, no para copiar): *k* = 4 con una silueta razonable o buena, y grupos que correspondan a los arquetipos, con nombres como «Activos e independientes», «Cuidado de la salud», «Control de signos vitales» y «Acompañamiento cercano» (el de edad avanzada probablemente sale como «Acompañamiento cercano» o «Cuidado de la salud · Acompañamiento cercano», porque la edad solo da nombre cuando sobresale sola). Si el resultado es distinto, se reporta tal como sale.

Alternativa sin capturas: `GET /api/analitica/perfiles` (o `?k=4`) a través del gateway con el token de la organización; la respuesta JSON trae todos estos valores.

### C.4 Sección 5.1, resultado de las pruebas — ✅ Dato listo

Ejecutadas el **29 de octubre de 2026**, con los cambios del uso del celular (B.8):

- **Backend** (`./mvnw test` en `backend/analitica-service`): **72 pruebas, 72 aprobadas**, 0 fallos, 0 errores, 0 omitidas. (Las mismas del 22/10: la prueba del reporte de población ahora también verifica el uso del celular, pero no se agregaron pruebas.)
- **Frontend** (`npx ng test --watch=false` en `frontend`): **37 archivos de prueba y 172 pruebas, todas aprobadas** (incluye las 50 de los reportes de analítica y de la red; el 22/10 eran 169 y 47: 2 nuevas del uso del celular y 1 de la red, del commit `cd8f7e1`).

Texto sugerido para reemplazar el `[POR COMPLETAR]`:

> La última ejecución de las pruebas, realizada el 29 de octubre de 2026, arrojó 72 pruebas aprobadas de 72 en el servicio de analítica y 172 pruebas aprobadas de 172 en el frontend, sin fallos ni errores.

(Si se vuelve a correr antes de entregar, actualizar la fecha y las cifras.)

### C.5 Tabla 15, criterio 2 (exactitud de los indicadores) — ✅ Dato listo

Estado: **«Cumplido: todas las pruebas automatizadas aprobadas (29/10/2026)»**.

### C.6 Tabla 13 actualizada (reemplazar la tabla completa)

Los conteos salen de la ejecución del 29/10/2026:

| Componente | Pruebas | Qué verifican |
|---|---|---|
| KMeansTest | 6 | (igual que ahora) |
| PerfilesControllerTest | 6 | (igual que ahora) |
| AnaliticaControllerTest | **13** | Las consultas de los reportes de actividades, salud, población y retiros, y que solo una organización pueda consultarlos (**antes decía 9**: una de las pruebas se ejecuta para cada uno de los cuatro reportes) |
| **RecursosControllerTest** | **4** | **Nueva.** Las consultas del reporte de donaciones e inventario: donaciones con sus artículos, inventario con sus movimientos y acceso solo para organizaciones |
| RecomendacionTest y RecomendacionControllerTest | 7 + 4 | (igual) |
| DireccionBogotaTest | 8 | (igual) |
| RedControllerTest | 7 | (igual) |
| UbicadorAutomaticoTest | 11 | (igual) |
| PdfControllerTest | 4 | (igual) |
| **AuditoriaSaludInterceptorTest** | **2** | **Opcional.** Que las consultas a datos de salud queden en el registro de auditoría (RNF-25) |
| Frontend (`reporte-*.spec.ts`, `red-datos.spec.ts`) | **4 + 9 + 8 + 9 + 4 + 5 + 11** | Actividades 4, salud 9, **población 8** (2 nuevas: uso del celular y su explicación en el PDF), **donaciones e inventario 9**, retiros 4, grupos de atención 5 y **red 11** (1 nueva: el grafo de gustos destaca a las personas): el cálculo de los indicadores, las tablas y el contenido del PDF de cada reporte |

### C.7 Sección 5.2, separación de los perfiles

`[POR COMPLETAR]` → el valor del indicador «Separación de los grupos» de C.3, con su lectura en la escala de Kaufman y Rousseeuw (> 0,5 buena; 0,25–0,5 razonable; 0,1–0,25 débil; < 0,1 sin grupos claros).

### C.8 Sección 5.4, validación con usuarios

No sale del código. `[POR COMPLETAR POR EL EQUIPO]`: con qué organizaciones o personas de la UPL Entrenubes (o de PROSOFI) se revisaron los reportes y los grupos de atención, fecha, método (entrevista, encuesta o sesión de prueba), opiniones sobre utilidad y claridad, y cambios que surgieron. Dos cambios que **sí** salieron de revisar los resultados y se pueden citar como evidencia de ajuste:

- Los nombres de los grupos se cambiaron para que describan lo que el grupo necesita y no alarmen (commit `5a85c1e`, 22/10/2026).
- La pestaña se renombró «Grupos de atención» y los ejes del mapa pasaron a «Mayor diferencia entre personas» / «Segunda mayor diferencia», para que fueran comprensibles (commits `5a85c1e` y `5996d7e`).

Fuentes internas que menciona la sección:

- **Calificación de la plataforma (RF-70):** el promedio y el número de calificaciones se consultan en la tabla `calificacion_plataforma`. Ojo: el script ficticio genera calificaciones para cerca de la mitad de las cuentas, así que **solo cuentan las de cuentas reales** (correo que no termina en `@vitaplus.test`). Consulta de solo lectura sugerida:
  ```sql
  SELECT count(*), round(avg(c.estrellas), 2)
    FROM calificacion_plataforma c JOIN usuario u ON u.id_usuario = c.id_usuario
   WHERE u.correo NOT LIKE '%@vitaplus.test';
  ```
  (La columna `estrellas` va de 0 a 5; `comentario` es la observación opcional.)
- **QC del 14 de octubre de 2026** (commit `e39ed29`): `[POR COMPLETAR POR EL EQUIPO: resultados, si se documentaron]`.

### C.9 Tabla 15, criterio 3 (utilidad de los perfiles)

Depende de C.8. Si no hay validación con usuarios antes de la entrega, sugerencia: «Cumplido técnicamente (silueta de X, nombres, rasgos y recomendaciones); pendiente la validación con las organizaciones».

### C.10 Sección 6.5, despliegue público

RNF-15 sigue **⏳ Pendiente** en `docs/REQUERIMIENTOS.md`. `[POR COMPLETAR POR EL EQUIPO: si ya está desplegada, la URL y la fecha; si no, dejar «pendiente».]`

### C.11 Referencias

`[POR COMPLETAR]` → decidir la norma. El listado actual ya está en formato **APA 7** (autor, año, título en cursiva), así que lo más simple es declarar APA 7 y borrar la nota. Si el resto de la documentación usa IEEE, hay que renumerarlas en orden de aparición.

### C.12 Anexos B a F

| Anexo | Qué adjuntar | De dónde sale |
|---|---|---|
| B | Consultas SQL de lectura | `backend/analitica-service/src/main/java/com/proyectogrado/analitica_service/perfiles/DatosPerfiles.java` (perfilamiento), `.../controller/AnaliticaController.java` (reportes) y **`.../controller/RecursosController.java` (donaciones e inventario, nuevo)**. Basta con pegar los bloques SQL (texto entre `"""`) |
| C | Documento técnico del perfilamiento | `docs/KMEANS_PERFILES.md` (ya actualizado con «Grupos de atención» y los nombres nuevos). Se puede exportar a PDF y adjuntar |
| D | Ejemplo de reporte en PDF | Descargar el PDF de **Grupos de atención** de la Fundación Entrenubes Usme (botón de descarga de la pestaña). Opcional: el de Salud |
| E | Capturas | Ver Parte D, imágenes A-1 a A-4 |
| F | Requerimientos | Copiar de `docs/REQUERIMIENTOS.md` la sección «4. Analítica de datos y visualización» (RF-45 a RF-48, RF-72, RF-73, RF-81 y el nuevo RF-83), RF-49, RF-60, RF-71, RF-79, RF-80, RNF-13, RF-68 (reglas del inventario que usa RF-83) y el nuevo RF-84 (uso del celular, en la sección 2.1 del archivo) |

---

## Parte D. Imágenes que faltan

### D.1 Antes de tomar las capturas

1. Correr el script `backend/scripts/seed_datos_fictisios.sql` actualizado (con la sección 24). Si ya estaba cargado de antes del 22/10/2026, volver a correrlo: es idempotente y solo borra lo que él mismo generó.
2. Levantar backend y frontend (o usar el ambiente desplegado).
3. Iniciar sesión con **`org01@vitaplus.test`** / **`Vita2025*`** (Fundación Entrenubes Usme).
4. Ventana del navegador a **1440 px de ancho** o más y zoom al 100 %, para que las gráficas no se corten. Tema claro.
5. Período de los reportes: **«Último año»** (los datos ficticios cubren 6 meses; con 90 días se ven menos meses).
6. Herramienta: recorte de Windows (`Win + Shift + S`), guardar en PNG.
7. No hace falta tapar nombres: son ficticios. Igual, **evitar capturas donde se lea un correo o un celular**.
8. Guardar con el nombre sugerido en una carpeta, por ejemplo `docs/capturas-crisp-dm/` (no hace falta subirla al repositorio).

### D.2 Figuras del cuerpo del documento

Numeración **nueva** (se agrega la del reporte de donaciones e inventario y la de retiros pasa a ser la 10).

| Figura | Sección | Archivo sugerido | Qué capturar | Qué debe verse | Leyenda |
|---|---|---|---|---|---|
| **1** | 3.6 | `fig01-calidad-datos.png` | Analítica → **Grupos de atención** → tarjeta «Calidad de los datos» (al final de la pestaña) | Las barras de completitud de las 14 variables con sus porcentajes; si alguna está en gris (descartada), que se vea la leyenda | *Figura 1 Calidad de los datos del perfilamiento* |
| **2** | 4.4 | `fig02-flujo-perfilamiento.png` | **No es captura.** Diagrama de flujo a partir del bloque Mermaid de `docs/KMEANS_PERFILES.md` (sección «Flujo completo») | Los pasos: lectura de las 14 variables → calidad por persona → mínimo de 12 → descarte de variables → imputación y z-score → K-means con *k* de 2 a kMáx → silueta → interpretación → respuesta | *Figura 2 Diagrama de flujo del proceso de perfilamiento* |
| **3** | 4.4 | `fig03-mapa-perfiles.png` | Grupos de atención → tarjeta «Mapa de los perfiles» | Los puntos de colores por grupo, la leyenda en una línea y los dos ejes con sus nombres («Mayor diferencia entre personas» y «Segunda mayor diferencia») y porcentajes | *Figura 3 Mapa de los perfiles (PCA)* |
| **4** | 4.4 | `fig04-que-distingue.png` | Grupos de atención → tarjeta «Qué distingue a cada grupo» | Las barras por variable y grupo, con el nombre de cada grupo en la leyenda | *Figura 4 Variables que distinguen a cada grupo* |
| **5** | 4.4 | `fig05-tarjetas-perfil.png` | Grupos de atención → las tarjetas de los grupos | Nombre, tamaño (personas y %), rasgos, atención y actividades sugeridas, y asistencia. Si no caben todas, capturar dos (5a y 5b) o la del Grupo 1 y la del último | *Figura 5 Tarjetas de perfil* |
| **6** | 6.2 | `fig06-reporte-actividades.png` | Analítica → **Actividades y participación** (Último año) | Los indicadores de arriba y la gráfica de inscritos y asistentes por mes | *Figura 6 Reporte de actividades y participación* |
| **7** | 6.2 | `fig07-reporte-salud.png` | Analítica → **Salud de la población** (Último año) | Los indicadores, el estado actual por indicador y el inicio de la tabla «Personas que requieren atención» | *Figura 7 Reporte de salud de la población* |
| **8** | 6.2 | `fig08-reporte-poblacion.png` | Analítica → **Perfil de la población** | Los indicadores y las gráficas de rangos de edad y género (o intereses). Si cabe, también «Nivel de uso del celular» (B.8) | *Figura 8 Reporte de perfil de la población* |
| **9 (nueva)** | 6.2 | `fig09-reporte-donaciones-inventario.png` | Analítica → **Donaciones e inventario** (Último año) | Los 5 indicadores (donaciones que llegaron, por recibir, agotados o por agotarse, vencidos o por vencer, dinero disponible) y la gráfica «Donaciones por mes» | *Figura 9 Reporte de donaciones e inventario* |
| **10 (antes 9)** | 6.2 | `fig10-reporte-retiros.png` | Analítica → **Retiros** (Último año o Todo) | Los indicadores y la gráfica de razones | *Figura 10 Reporte de retiros* |

**Dónde va la Figura 9 nueva:** en 6.2, después del placeholder de la Figura 8, con su propio placeholder:

> [Insertar la captura del reporte de donaciones e inventario.]
> *Figura 9 Reporte de donaciones e inventario*

Y la actual «Figura 9 Reporte de retiros» pasa a ser **Figura 10**.

**Cómo hacer la Figura 2 (diagrama):** copiar el bloque `mermaid` de `docs/KMEANS_PERFILES.md` en <https://mermaid.live>, cambiar el texto «Organización abre la pestaña «Grupos de atención»» si se quiere más corto, y exportar como PNG o SVG. Si el diagrama queda muy alto, partirlo en dos o girarlo a horizontal cambiando `flowchart TD` por `flowchart LR`.

### D.3 Imágenes de los anexos (opcionales pero recomendadas)

| Imagen | Anexo | Archivo sugerido | Qué capturar |
|---|---|---|---|
| A-1 | E | `anexoE-1-grupos-atencion-completa.png` | La pestaña Grupos de atención completa: indicadores y el recuadro «Ten en cuenta antes de usar estos perfiles» (las advertencias, incluida la de datos ficticios) |
| A-2 | E | `anexoE-2-numero-grupos.png` | Tarjeta «Número de grupos» con la vista de **tabla** abierta (silueta e inercia por *k*) |
| A-3 | E | `anexoE-3-red.png` | Sección **Red y mapa** → grafo de la red, con las personas sin acompañante resaltadas |
| A-4 | E | `anexoE-4-mapa.png` | Sección **Red y mapa** → mapa con la sede, los barrios y los lugares de las actividades |
| A-5 | D | `anexoD-reporte-grupos.pdf` | No es captura: el PDF descargado de Grupos de atención |

### D.4 Lista de verificación

- [ ] Fig. 1 Calidad de los datos
- [ ] Fig. 2 Diagrama de flujo (Mermaid)
- [ ] Fig. 3 Mapa de los perfiles (PCA)
- [ ] Fig. 4 Qué distingue a cada grupo
- [ ] Fig. 5 Tarjetas de perfil
- [ ] Fig. 6 Reporte de actividades
- [ ] Fig. 7 Reporte de salud
- [ ] Fig. 8 Reporte de perfil de la población
- [ ] Fig. 9 Reporte de donaciones e inventario (**nueva**)
- [ ] Fig. 10 Reporte de retiros (**renumerada**)
- [ ] Anexo D: PDF de Grupos de atención
- [ ] Anexo E: A-1 a A-4

---

## Parte E. Listas de tablas y figuras e historial de cambios

### E.1 Tablas

No hay tablas nuevas obligatorias: las filas nuevas van dentro de las tablas 5, 8, 10, 13, 14 y 17. **Si** se agrega la tabla de silueta e inercia de C.3, conviene ponerla al final de 4.4 para no renumerar las anteriores; quedaría como **Tabla 13** y todas las siguientes suben un número (Pruebas → 14, Indicadores → 15, Cumplimiento → 16, Visualización de perfiles → 17, Diccionario → 18), y hay que revisar las referencias cruzadas en el texto («Tabla 13», «Tabla 14», «Tabla 15» aparecen en 1.3.3, 4.1, 5.1, 5.3, 5.5 y Anexo A). Si no se quiere renumerar, poner los valores de silueta como texto en 4.4.

### E.2 Lista de figuras (nueva)

1. Calidad de los datos del perfilamiento
2. Diagrama de flujo del proceso de perfilamiento
3. Mapa de los perfiles (PCA)
4. Variables que distinguen a cada grupo
5. Tarjetas de perfil
6. Reporte de actividades y participación
7. Reporte de salud de la población
8. Reporte de perfil de la población
9. **Reporte de donaciones e inventario**
10. Reporte de retiros

### E.3 Historial de cambios (fila nueva)

| Fecha de cambio | Autor | Descripción |
|---|---|---|
| 22/10/2026 | `[POR COMPLETAR: autor]` | Reporte de donaciones e inventario, pestaña «Grupos de atención», nuevos nombres de los grupos y de los ejes del mapa, datos ficticios de la Fundación Entrenubes Usme, resultado de las pruebas y actualización de las listas de tablas y figuras. |
| 29/10/2026 | `[POR COMPLETAR: autor]` | Nivel de uso del celular de la persona mayor (RF-84) como variable del reporte de perfil de la población: estructura de los datos, indicador, gráfica, diccionario de datos y datos ficticios. |

(Si las capturas y los resultados de 3.6 y 4.4 se pegan otro día, agregar otra fila con esa fecha.)

---

## Parte F. Referencia técnica de lo nuevo

### F.1 Reporte «Donaciones e inventario»

- **Backend:** `RecursosController` en `analitica-service` (`GET /api/analitica/donaciones` y `GET /api/analitica/inventario`). Solo lee las tablas de `donacion-service`, con consultas SQL de solo lectura. Como el reporte de retiros, devuelve todo el historial sin agregar y el frontend filtra por período, así que cambiar el período no vuelve a consultar. Una cuenta que no es organización recibe 403.
- **Frontend:** `reporte-recursos.ts` (pestaña 5 de la Analítica). Los estados de donación se muestran con su nombre y su color (recibidas, por recibir, no llegaron, canceladas), nunca solo con el color.
- **Indicadores:** los 5 de la Tabla 14 (B.5). Umbral «bueno» del cumplimiento: 80 % o más.
- **Gráficas:** Donaciones por mes (barras apiladas por estado), Qué se recibe (artículos recibidos por categoría), Quién dona (programadas frente a recibidas por donante), De dónde llega y a dónde va (entradas y salidas por motivo), Existencias por categoría (con existencias, por agotarse, agotados) y la tabla «Artículos que requieren atención» (primero agotados, luego vencidos, por agotarse y por vencer).
- **Reglas:** las mismas de la página de Inventario (RF-68): por agotarse = cantidad ≤ cantidad mínima; aviso de vencimiento a 30 días; el vencimiento solo se señala si hay existencias.
- **PDF:** igual que los demás reportes (RF-48), con la explicación de cada gráfica.
- **Requerimiento:** RF-83 (agregado a `docs/REQUERIMIENTOS.md` el 22/10/2026).

### F.2 Arquetipos de la Fundación Entrenubes Usme (sección 24 del script)

160 personas (`pmen001` a `pmen160@vitaplus.test`), 40 por arquetipo, todas con vínculo aceptado, signos vitales de los últimos 180 días (la más reciente con peso y estatura) y citas, de modo que ninguna queda excluida ni con valores imputados:

| # | Arquetipo | Edad | Rasgos que el modelo ve | Grupo esperado |
|---|---|---|---|---|
| 1 | Autónomas y estables | 61–69 | Casi sin enfermedades ni medicamentos (0–1), signos normales, no viven solas, 2 acompañantes, sin emergencias | «Activos e independientes» |
| 2 | Salud compleja | 70–79 | 4–6 enfermedades (algunas severas), 6–8 medicamentos, presión casi controlada, 1 acompañante | «Cuidado de la salud» |
| 3 | Signos vitales por vigilar | 66–75 | Presión alta, pulso rápido, saturación baja (EPOC), obesidad; casi todas las mediciones fuera de rango | «Control de signos vitales» |
| 4 | Edad avanzada que necesita acompañamiento | 86–95 | Viven solas, sin acompañantes, discapacidades, todas las citas requieren acompañamiento, 3–5 emergencias en 90 días | «Acompañamiento cercano» (posiblemente con «Cuidado de la salud») |

A la organización también le quedan las personas que las secciones anteriores del script le asignan al azar (unas 30 a 40), que se reparten entre los grupos y bajan un poco la silueta. Además, la fundación tiene 20 actividades pasadas con 45 cupos, en las que la asistencia depende del arquetipo (≈ 92 %, 75 %, 65 % y 35 %), lo que se ve en el «% de asistencia» de cada tarjeta de perfil.

### F.3 Nombres de los grupos (resumen)

| Dimensión que sobresale (puntaje ≥ 0,4) | Nombre |
|---|---|
| Condiciones de salud | Cuidado de la salud |
| Signos vitales | Control de signos vitales |
| Edad (solo si es la única) | Experiencia de vida |
| Necesidades de acompañamiento | Acompañamiento cercano |
| Dos dimensiones | Se unen con « · » |
| Ninguna ≥ 0,4 y todas ≤ 0,1 | Activos e independientes |
| Otro caso | Bienestar en equilibrio |

### F.4 Nivel de uso del celular (RF-84)

- **Dato:** `persona_mayor.uso_celular`, opcional. Lo guarda auth-service desde «Mi información» de la persona mayor y desde el perfil que gestiona su acompañante; acepta solo `NO_USA`, `BASICO`, `INTERMEDIO` o `AVANZADO` (cualquier otro valor se rechaza) y vacío queda nulo. auth-service crea la columna al arrancar.
- **Backend de analítica:** `GET /api/analitica/poblacion` devuelve `usoCelular` en cada persona, con la misma consulta de solo lectura del resto del reporte.
- **Frontend:** `reporte-poblacion.ts`, gráfica «Nivel de uso del celular» después de «¿Viven solas?», con vista de tabla y sección en el PDF. Las opciones y sus textos están en `core/persona-mayor/uso-celular.ts`.
- **Datos ficticios:** puntaje = número al azar + (edad − 60) / 50; menos de 0,25 → alto, menos de 0,65 → medio, menos de 1,05 → bajo, el resto → no lo usa. Cerca del 12 % queda sin indicar.
- **Fuera del modelo:** no es una de las 14 variables de K-means. Si en el futuro se quisiera usar en los grupos de atención, habría que agregarla a `VariablePerfil` y actualizar la Tabla 7 y `docs/KMEANS_PERFILES.md`.

### F.5 Lo que no cambió

Las 14 variables del modelo (Tabla 7), las reglas de limpieza y suficiencia (Tablas 8 y 9), las transformaciones, el diseño del experimento (2 ≤ *k* ≤ mín(6, n/4), silueta, 20 reinicios, semilla 42), el algoritmo (K-means++, Lloyd, tolerancia 10⁻⁶, 300 iteraciones), las recomendaciones por dimensión (Tabla 11), el recomendador de organizaciones (Tabla 12), la red de apoyo, las limitaciones (4.5) y las demás filas de la Tabla 14 siguen siendo correctas tal como están en el documento.
