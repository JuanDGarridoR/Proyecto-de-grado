# Resultados de las pruebas de rendimiento – VITA+

## 1. Resumen ejecutivo

Se midió el rendimiento de VITA+ en producción (vita-plus.app) con dos pruebas: una **prueba de carga** con 100 usuarios concurrentes durante 5 minutos y una **prueba de tiempos de respuesta sin carga** con un solo usuario.

Con carga se procesaron 11.754 solicitudes. El 99,97 % fue exitoso y el **98,80 % respondió bien en menos de 3 s**, con P95 de 1.318 ms. Sin carga, las consultas respondieron con un P95 inferior a 0,6 s y ningún reporte superó los 6,6 s.

En el frontend, las páginas cargan su contenido principal en menos de 2,2 s en escritorio, pero tardan entre 6,3 y 7,6 s en un móvil simulado con 4G lenta.

- **RD-01:** se cumple.
- **RD-04:** se cumple en ambas pruebas.
- **RD-06:** se cumple sin carga; con carga se cumple en el 99,5 % de las solicitudes de reportes, pero 8 superaron los 10 s durante dos episodios breves de degradación.
- **RD-05:** queda por verificar.

## 2. Contexto

| | |
|---|---|
| Fecha | 24/11/2026 |
| Herramientas | Apache JMeter 5.6.3 (carga); script en Python 3.13 (tiempos del backend sin carga); Lighthouse 12.8.2 con Puppeteer y Google Chrome (frontend) |
| Entorno | AWS EC2 de 8 GB, Docker Compose (Caddy, API Gateway y 10 microservicios Spring Boot con `-Xmx256m` y SerialGC), PostgreSQL en Supabase |
| Origen de las peticiones | Un equipo personal externo al servidor (una sola IP y una sola red) |
| Cuentas | Ficticias del script `seed_datos_fictisios.sql` (`@vitaplus.test`) |

> **Nota:** estas pruebas se ejecutaron después de la entrega de los documentos del proyecto. Por eso el documento de control de calidad (QC) todavía muestra como **pendientes** los casos CP-REN-001 a CP-REN-004.

---

# PARTE A – Prueba de carga

## 3. Diseño

| Parámetro | Valor |
|---|---|
| Usuarios concurrentes | 100 (80 con rol persona mayor y 20 con rol organización) |
| Rampa | 30 s |
| Duración | 5 minutos |
| Pausa entre solicitudes | Aleatoria entre 1 y 3 s, para simular el tiempo de lectura |
| Autenticación | Un solo inicio de sesión por cuenta antes de la carga (14 cuentas, cada 7 s), porque el gateway limita `POST /api/auth/login` a 10 por minuto por IP. Los 100 hilos se reparten los JWT obtenidos. |
| Cuentas | `org01`–`org02` y `pm001`–`pm012` `@vitaplus.test` |

Endpoints, todos GET y autenticados:

| Rol | Endpoint | Servicio |
|---|---|---|
| Persona mayor | `/api/auth/informacion` | auth-service (2 réplicas) |
| Persona mayor | `/api/persona-mayor/signos-vitales` | salud-service (2 réplicas) |
| Persona mayor | `/api/actividades/disponibles` | actividad-service |
| Organización | `/api/analitica/poblacion` | analitica-service |
| Organización | `/api/analitica/actividades` | analitica-service |

Se excluyeron:
- las rutas que envían SMS u OTP;
- el botón de emergencia;
- toda escritura;
- `/api/analitica/salud` y `/api/analitica/perfiles`, porque cada consulta inserta un registro en la tabla `auditoria_salud`.

El límite por IP del gateway no afecta a estos GET.

## 4. Resultados globales

| Métrica | Valor |
|---|---|
| Total de solicitudes | 11.754 (sin contar los 14 inicios de sesión) |
| Duración medida | 312 s |
| Rendimiento | 37,6 solicitudes/s |
| Éxito (HTTP 200) | 99,97 % |
| **Éxito y < 3 s** | **98,80 %** |
| Promedio | 419 ms |
| Mediana | 202 ms |
| P95 | 1.318 ms |
| P99 | 3.371 ms |
| Máximo | 46.237 ms |
| Errores por código | 0 respuestas 4xx/5xx (sin 429 ni 503); 3 `ConnectTimeoutException` (0,03 %) |

## 5. Resultados por endpoint

| Endpoint | Solicitudes | Error | Promedio (ms) | P95 (ms) | P99 (ms) | Máximo (ms) | Éxito y < 3 s |
|---|---|---|---|---|---|---|---|
| `/api/auth/informacion` | 3.210 | 0 % | 350 | 855 | 2.201 | 19.851 | 99,69 % |
| `/api/persona-mayor/signos-vitales` | 3.183 | 0,03 % | 399 | 1.330 | 3.068 | 46.237 | 98,99 % |
| `/api/actividades/disponibles` | 3.157 | 0,06 % | 368 | 1.098 | 2.690 | 24.249 | 99,24 % |
| `/api/analitica/poblacion` | 1.109 | 0 % | 907 | 3.310 | 7.603 | 43.987 | 94,32 % |
| `/api/analitica/actividades` | 1.095 | 0 % | 330 | 1.071 | 4.702 | 20.884 | 98,90 % |

## 6. Observaciones

**Dos episodios de degradación general.** Entre los segundos **80 y 140** y entre los **260 y 290** de la prueba, todos los endpoints se volvieron lentos al mismo tiempo, incluido `/api/auth/informacion`, que es una consulta liviana:

| Intervalo | Solicitudes | > 3 s | Máximo |
|---|---|---|---|
| 0–80 s | 2.899 | 4 | 8.261 ms |
| 80–140 s | 2.017 | 97 (4,8 %) | 46.237 ms |
| 140–260 s | 5.455 | 1 | 3.917 ms |
| 260–300 s | 1.383 | 39 (2,8 %) | 43.987 ms |

Fuera de esos intervalos solo 5 solicitudes superaron los 3 s; entre los segundos 140 y 240, el máximo de cada intervalo de 20 s se mantuvo entre 464 y 868 ms.

**Tres timeouts de conexión.** Entre los segundos 265 y 272, JMeter no pudo abrir la conexión TCP hacia vita-plus.app en 10 s:
- 2 en `/api/actividades/disponibles`;
- 1 en `/api/persona-mayor/signos-vitales`.

**Hipótesis.** Como la lentitud afectó a todos los servicios a la vez y hubo timeouts de conexión, apunta a un cuello de botella compartido y no a un servicio en particular. Las posibles causas son:
- pausas del recolector de basura (SerialGC detiene la aplicación) en el API Gateway, que es único y tiene 256 MB de heap, por donde pasa todo el tráfico;
- saturación de CPU del EC2, que comparten todos los contenedores;
- agotamiento del pool de conexiones hacia Supabase;
- la red de origen (conexión doméstica).

**Ninguna de estas hipótesis se confirmó**, porque durante la prueba no se monitorearon los contenedores (`docker stats`) ni los registros del recolector de basura.

**Rate limiting y circuit breaker.** No hubo ninguna respuesta 429 ni 503, por lo que no se activaron. Los reintentos automáticos de GET del gateway (hasta 2) pudieron ocultar fallos pasajeros; los tiempos medidos incluyen esos reintentos.

**Prueba corta previa.** Con 5 usuarios se ejecutó dos veces. En la primera, las primeras solicitudes tardaron hasta 26 s (`/api/analitica/poblacion`), por arranque en frío. En la segunda, ya en caliente, el 100 % respondió en menos de 3 s, con P95 de 494 ms.

---

# PARTE B – Tiempos de respuesta sin carga

## 7. Diseño

**Backend.** Un solo usuario, sin otros hilos de prueba.
- Se hicieron **30 peticiones secuenciales por endpoint**, con 1 s de pausa entre una y otra.
- La primera petición de cada serie se reporta aparte ("en frío"); las estadísticas se calculan con las 29 restantes.
- Hubo 2 inicios de sesión (`pm001` y `org01`).
- Además de los 5 endpoints de la prueba de carga, se midieron:
  - `GET /api/analitica/perfiles` (segmentación K-means);
  - `POST /api/analitica/reportes/pdf` (exportación a PDF). Es un POST, pero no guarda datos ni publica eventos. Se le envió un reporte ficticio con 4 indicadores y 3 secciones, cada una con una gráfica PNG de 1200×600 y una tabla de 40 filas.

> "En frío" se refiere solo a la primera petición de cada serie. Los contenedores ya estaban en ejecución y calientes por las pruebas anteriores; no se reiniciaron.

**Frontend.** Lighthouse 12.8.2 con Chrome sin interfaz.
- **Páginas:** `/iniciar-sesion`, `/panel/persona-mayor`, `/panel/persona-mayor/signos-vitales` y `/panel/organizacion`.
- **Modos:**
  - escritorio;
  - móvil simulado (perfil por defecto de Lighthouse: 4G lenta con 150 ms de latencia y 1,6 Mbps, y CPU 4 veces más lenta).
- **Corridas:** 3 por página y modo, con la caché HTTP vacía en cada una; se reporta la mediana.
- **Sesión:** en los paneles se inyectó la sesión del inicio de sesión por API en `localStorage`, como la guarda el frontend.

## 8. Resultados del backend (ms)

| Endpoint | Tipo | En frío | Mínimo | Mediana | Promedio | P95 | Máximo | Respuestas |
|---|---|---|---|---|---|---|---|---|
| `GET /api/auth/informacion` | Consulta | 499 | 407 | 459 | 487 | 582 | 1.019 | 30 × 200 |
| `GET /api/persona-mayor/signos-vitales` | Consulta | 514 | 324 | 380 | 390 | 479 | 489 | 30 × 200 |
| `GET /api/actividades/disponibles` | Consulta | 433 | 358 | 392 | 399 | 456 | 461 | 30 × 200 |
| `GET /api/analitica/poblacion` | Reporte | 747 | 620 | 671 | 675 | 743 | 745 | 30 × 200 |
| `GET /api/analitica/actividades` | Reporte | 368 | 330 | 368 | 375 | 423 | 425 | 30 × 200 |
| `GET /api/analitica/perfiles` (K-means) | Reporte | 780 | 629 | 1.340 | 1.828 | 4.860 | 6.220 | 30 × 200 |
| `POST /api/analitica/reportes/pdf` | Reporte | 3.327 | 2.445 | 3.046 | 3.209 | 4.170 | 6.620 | 30 × 200 |

**PDF.** El cuerpo enviado pesó **1,7 MB**, porque las gráficas de prueba con ruido se comprimen mal. Una gráfica real de ECharts suele pesar mucho menos. Parte del tiempo medido corresponde a subir ese cuerpo desde la red de origen, así que el tiempo de un reporte real probablemente sea menor.

## 9. Resultados del frontend (mediana de 3 corridas, ms)

| Página | Dispositivo | Primer contenido (FCP) | Largest Contentful Paint (LCP) | Tiempo hasta interactivo (TTI) | Total Blocking Time | Puntaje Lighthouse |
|---|---|---|---|---|---|---|
| Iniciar sesión | Escritorio | 651 | 1.316 | 1.440 | 92 | 95 |
| Iniciar sesión | Móvil 4G lenta | 2.501 | 6.341 | 6.797 | 527 | 60 |
| Panel persona mayor (inicio) | Escritorio | 764 | 1.597 | 1.697 | 163 | 85 |
| Panel persona mayor (inicio) | Móvil 4G lenta | 2.742\* | 7.442\* | 7.761\* | 1.121\* | 27\* |
| Panel persona mayor (signos vitales) | Escritorio | 1.194 | 1.599 | 2.145 | 141 | 74 |
| Panel persona mayor (signos vitales) | Móvil 4G lenta | 2.884 | 7.138 | 8.179 | 1.484 | 19 |
| Panel organización (inicio) | Escritorio | 779 | 1.785 | 1.905 | 258 | 80 |
| Panel organización (inicio) | Móvil 4G lenta | 2.797 | 7.649 | 8.498 | 1.609 | 43 |

\* Una de las 3 corridas falló (`NO_FCP`: Chrome sin interfaz no pintó la página), así que el valor es la mediana de las 2 corridas válidas, es decir, su promedio. En `lighthouse.json`, la mediana de esa fila incluye la corrida fallida y no debe usarse.

**Observaciones del frontend:**
- **Escritorio.** Todas las páginas muestran su contenido principal (LCP) en menos de 2,2 s y quedan interactivas en menos de 2,2 s. Están dentro del umbral "bueno" de Google para LCP (≤ 2,5 s).
- **Móvil con 4G lenta.** El primer contenido aparece en unos 2,5–2,9 s, pero el contenido principal tarda entre 6,3 y 7,6 s y la página queda interactiva entre 6,8 y 8,5 s. Eso supera el umbral "bueno" de LCP (2,5 s). Los Total Blocking Time altos (hasta 1,6 s) indican que el costo principal es descargar y ejecutar JavaScript en un dispositivo lento, no la respuesta del backend, que tarda menos de 1 s.
- **Requisitos.** El SRS no define un requisito específico de tiempos del frontend, por lo que estos datos se reportan como referencia.
- **Efecto en los datos.** Cada carga del panel de organización consulta `/api/analitica/salud`, así que estas corridas dejaron registros de consulta en `auditoria_salud` a nombre de la cuenta ficticia `org01`.

## 10. Comparación sin carga vs. con carga

| Endpoint | Promedio sin carga | Promedio con carga | P95 sin carga | P95 con carga | P95 con carga / sin carga |
|---|---|---|---|---|---|
| `/api/auth/informacion` | 487 | 350 | 582 | 855 | 1,5× |
| `/api/persona-mayor/signos-vitales` | 390 | 399 | 479 | 1.330 | 2,8× |
| `/api/actividades/disponibles` | 399 | 368 | 456 | 1.098 | 2,4× |
| `/api/analitica/poblacion` | 675 | 907 | 743 | 3.310 | 4,5× |
| `/api/analitica/actividades` | 375 | 330 | 423 | 1.071 | 2,5× |

**Interpretación:**
- **El promedio casi no cambia** con 100 usuarios: la mayoría de las solicitudes responden igual de rápido.
- **La degradación está en la cola.** El P95 crece entre 1,5 y 4,5 veces, sobre todo por los dos episodios de degradación descritos en la sección 6.
- **El más afectado es `/api/analitica/poblacion`**, que es la consulta más pesada (agrega los datos de las ~460 personas mayores de org01).

**Por qué en algunos endpoints el promedio sin carga es *mayor* que con carga.** Las dos pruebas no se conectan igual:
- JMeter reutiliza las conexiones HTTPS (keep-alive).
- El script sin carga abre una conexión TCP y TLS nueva en cada petición, lo que suma la negociación TLS en cada medición.

Por eso la comparación sirve para ver la tendencia de la cola (P95), no como diferencia exacta de promedios.

---

# PARTE C – Conclusiones

## 11. Cumplimiento de requerimientos

| Requerimiento | Criterio | Con carga (100 usuarios) | Sin carga (1 usuario) | ¿Cumple? |
|---|---|---|---|---|
| **RD-01** | 100 usuarios concurrentes con al menos 95 % de solicitudes exitosas en menos de 3 s | 98,80 % exitosas en < 3 s | No aplica | **Sí** |
| **RD-04** | Consultas en menos de 3 s para el 95 % | 99,31 % de las consultas en < 3 s (9.550 solicitudes de información, signos vitales y actividades disponibles) | P95 entre 456 y 582 ms; máximo 1.019 ms | **Sí** |
| **RD-06** | Reportes en menos de 10 s | Población: 99,46 % en < 10 s (máx. 43.987 ms). Actividades: 99,82 % en < 10 s (máx. 20.884 ms). Perfiles y PDF no se probaron con carga. | Todos los reportes en < 10 s: máximo 745 ms (población), 425 ms (actividades), 6.220 ms (perfiles) y 6.620 ms (PDF) | **Sí sin carga. Con carga, parcialmente:** 8 de 2.204 solicitudes de reportes superaron los 10 s, todas durante los episodios de degradación |
| **RD-05** | No se tiene el texto del requerimiento | — | — | **Por verificar** |

Los endpoints de analítica (población, actividades, perfiles y PDF) se clasifican como **reportes** y se evalúan contra RD-06 (10 s), no contra el límite de 3 s de las consultas.

## 12. Limitaciones

- **Solo lecturas.** No se midieron escrituras (registros, inscripciones, signos vitales nuevos).
- **Una sola IP y una sola red de origen.** Toda la carga salió de un equipo con conexión doméstica. Los tiempos incluyen esa red y no representan usuarios distribuidos.
- **Una sola corrida de cada prueba** de carga y de tiempos del backend; Lighthouse usó 3 corridas por página. Los resultados pueden variar entre ejecuciones.
- **Sin monitoreo de contenedores.** No se registraron CPU, memoria ni recolector de basura durante las pruebas, por lo que las causas de los episodios de degradación son hipótesis.
- **Perfiles (K-means) y PDF solo se midieron sin carga.**
- **Las cuentas comparten tokens:** 100 hilos usaron 14 sesiones. Como el JWT no guarda estado en el servidor, el efecto esperado es menor.

## 13. Trabajo futuro

- Repetir la prueba de carga registrando `docker stats` y los registros del recolector de basura (por ejemplo `-Xlog:gc`) del gateway y de analítica, para confirmar la causa de los episodios de degradación.
- Probar escrituras con cuentas y datos de prueba (en un entorno de *staging* o con limpieza posterior).
- Agregar caché o precálculo al reporte de población, que es el endpoint más pesado bajo carga.
- Ejecutar la carga desde varias IP o regiones (por ejemplo, JMeter distribuido o un servicio en la nube) para descartar la red de origen.
- Medir perfiles y PDF bajo carga.
- Reducir el JavaScript inicial del frontend (tamaño de los paquetes, carga diferida de gráficas y mapas) para mejorar LCP y TTI en móviles con redes lentas.

## 14. Frases para la sustentación

1. "Probamos la plataforma en producción con 100 usuarios simultáneos durante 5 minutos: de casi 12 mil solicitudes, el 98,8 % respondió bien en menos de 3 segundos, así que cumplimos RD-01."
2. "Con un solo usuario, las consultas responden en menos de medio segundo y los reportes, incluido el PDF y los perfiles con K-means, en menos de 7 segundos, dentro de los 10 que pide RD-06."
3. "Con carga, el promedio casi no cambia, pero vimos dos picos cortos en los que todos los servicios se pusieron lentos a la vez. Todavía no sabemos la causa porque no monitoreamos el servidor durante la prueba."
4. "En escritorio las páginas cargan en unos 2 segundos, pero en un celular con 4G lenta tardan unos 7, así que optimizar el frontend para móvil es trabajo pendiente. Además, solo probamos lecturas, desde un solo computador y una sola red, con una sola corrida: es una primera medición, no una certificación."

## 15. Referencias

| Archivo | Contenido |
|---|---|
| `pruebas-carga/resultados/reporte/index.html` | Reporte HTML de JMeter de la prueba de carga |
| `pruebas-carga/resultados/carga.jtl` | Resultados crudos de la prueba de carga |
| `pruebas-carga/resultados/humo.jtl`, `humo2.jtl` | Pruebas cortas previas (5 usuarios) |
| `pruebas-carga/resultados/tiempos_backend.json` | Tiempos del backend sin carga (las 30 mediciones de cada endpoint) |
| `pruebas-carga/resultados/lighthouse.json` | Resultados de Lighthouse por página, modo y corrida |
| `pruebas-carga/vita-carga.jmx`, `cuentas.csv`, `analizar.py`, `tiempos_backend.py` | Plan de JMeter, cuentas ficticias y scripts de medición y análisis |
