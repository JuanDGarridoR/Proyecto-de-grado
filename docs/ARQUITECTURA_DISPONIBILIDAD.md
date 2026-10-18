# Arquitectura de Vita+ y requisitos no funcionales

Este documento reúne las decisiones de arquitectura con las que Vita+ cumple sus requisitos no funcionales (RNF): disponibilidad, seguridad y privacidad, rendimiento, escalabilidad, mantenibilidad y portabilidad. Para cada decisión explica qué problema resuelve, dónde está en el código y cómo se comprobó. Los RNF están en `REQUERIMIENTOS.md`.

## Contexto: cómo está desplegada la plataforma

Vita+ corre en **un solo servidor** (8 GB de RAM) con Docker Compose. La base de datos es PostgreSQL en **Supabase** (servicio administrado externo), y los SMS salen por **TextBee**.

```mermaid
flowchart TD
    U[Navegador] -->|HTTPS| C[Caddy<br/>HTTPS · compresión · caché de estáticos]
    C -->|/api/*| G[API Gateway<br/>JWT · balanceo · circuit breaker · reintentos]
    G --> A1[auth-service] & A2[auth-service-2]
    G --> P1[persona-mayor-service] & P2[persona-mayor-service-2]
    G --> S1[salud-service] & S2[salud-service-2]
    G --> O[mensajeria · acompañante · organización<br/>voluntario · actividad · analítica · donación]
    A1 & P1 & S1 & O -.SMS.-> M[mensajeria-service] -.-> T[TextBee]
    A1 & A2 & P1 & P2 & S1 & S2 & O --> DB[(Supabase<br/>PostgreSQL + PgBouncer)]
    H[autoheal] -.reinicia los contenedores que no responden.-> G
```

## Requisitos no funcionales y dónde se cumplen

| RNF | Qué pide | Dónde se explica |
|---|---|---|
| RNF-01 | Servicios independientes detrás de un único gateway | Puntos 1 y 38 |
| RNF-02 | Acceso protegido con JWT validado en el gateway | Puntos 23, 24 y 25 |
| RNF-03 | OTP como HMAC, con vencimiento a los 10 min y anulación tras 5 intentos | Punto 27 |
| RNF-04 | Actualización en tiempo real | Puntos 10 y 36 |
| RNF-05 | Recordatorios por SMS sin duplicados | Puntos 8 y 17 |
| RNF-06 | Hora de Colombia | Punto 43 |
| RNF-08 | PostgreSQL en Supabase | Contexto y punto 5 |
| RNF-09 | SMS con TextBee y validación de correos con Hunter | Puntos 7 y 27 |
| RNF-10 | Pruebas automatizadas sin base de datos real ni SMS | Punto 41 |
| RNF-11 | Java 17 con Spring Boot y Angular 17 o superior | Punto 42 |
| RNF-14 | Contenedores Docker con Docker Compose | Punto 42 |
| RNF-15 | Desplegada en un hosting accesible por internet | Pendiente (ver *Cómo se comprobó*) |
| RNF-16 | Tolerancia a fallos | Parte 1, puntos 11 a 21 |
| RNF-17 | Mapas de la organización sin la dirección exacta | Punto 30 |
| RNF-18 | Bloqueo tras 5 contraseñas incorrectas y límite de intentos por IP | Punto 22 |
| Seguridad y privacidad en general | Datos protegidos, acceso solo con vínculo, cifrado | Parte 2, puntos 22 a 32 |
| Rendimiento | Páginas que cargan rápido con mala señal | Parte 3, puntos 33 a 37 |
| Escalabilidad | Crecer agregando instancias | Parte 4, punto 38 |

RNF-07 (usabilidad), RNF-12 (compatibilidad) y RNF-13 (apoyo a la decisión) se cumplen en el diseño de la interfaz y de las funcionalidades, no con decisiones de arquitectura, así que no se detallan aquí.

---

## 1. Disponibilidad y tolerancia a fallos

Estas decisiones evitan que la plataforma se caiga o, si una parte falla, permiten que el resto siga funcionando. Se distingue lo que **ya existía** antes del 15 de octubre de 2026 de lo que se **implementó ese día**.

### Resumen

| # | Mecanismo | Estado | Protege contra |
|---|---|---|---|
| 1 | Punto de entrada único (API Gateway) | Ya existía | Exponer servicios internos, lógica de seguridad repetida |
| 2 | Proxy inverso con HTTPS (Caddy) | Ya existía | Tráfico sin cifrar, puertos internos expuestos |
| 3 | Reinicio automático de contenedores (`restart: unless-stopped`) | Ya existía | Un servicio que se cae (el proceso termina) |
| 4 | Límites de memoria por contenedor y JVM acotada | Ya existía | Un servicio que consume toda la RAM y tumba a los demás |
| 5 | Pool de conexiones pequeño + pooler de Supabase | Ya existía | Agotar las conexiones de la base de datos |
| 6 | Autenticación sin estado (JWT) | Ya existía | Depender de una sesión guardada en un servidor (permite réplicas) |
| 7 | Degradación elegante en el envío de SMS | Ya existía | Que una falla de SMS rompa la operación principal |
| 8 | Avisos idempotentes en los recordatorios (medicamentos y citas) | Ya existía | SMS duplicados si el scheduler corre dos veces |
| 9 | Caché y tiempos máximos de espera para Google Places | Ya existía | Costos de API y un proveedor externo lento |
| 10 | Notificaciones en tiempo real (SSE) con reconexión | Ya existía | Pantallas desactualizadas |
| 11 | **Health checks** (Spring Boot Actuator + Docker) | **Nuevo** | No saber si un servicio está vivo |
| 12 | **Autoreparación** (autoheal) | **Nuevo** | Un servicio encendido pero colgado |
| 13 | **Balanceo de carga y réplicas** (Spring Cloud LoadBalancer) | **Nuevo** | La caída de un servicio crítico deja sin servicio a todos |
| 14 | **Circuit breaker por ruta** (Resilience4j) | **Nuevo** | Fallas en cascada y peticiones colgadas acumulándose |
| 15 | **Reintentos seguros en el gateway** | **Nuevo** | Fallas pasajeras y réplicas caídas |
| 16 | **Tiempos máximos de espera** (gateway y llamadas entre servicios) | **Nuevo** | Un servicio lento que bloquea a quien lo llama |
| 17 | **Bloqueo distribuido de tareas programadas** (ShedLock) | **Nuevo** | Tareas duplicadas al tener réplicas |
| 18 | **Caché de catálogos** (Caffeine) | **Nuevo** | Consultas repetidas a la base de datos |
| 19 | **Caché del navegador para el frontend** (Caddy) | **Nuevo** | Descargar la aplicación completa en cada visita |
| 20 | **Apagado ordenado** (graceful shutdown) | **Nuevo** | Cortar peticiones a medias al reiniciar o actualizar |
| 21 | **Reintentos en el frontend** | **Nuevo** | Mostrar errores por un corte de red de un segundo |

---

### Lo que ya existía

#### 1. Punto de entrada único: API Gateway

Todo el tráfico del frontend entra por `api-gateway` (Spring Cloud Gateway, puerto 8080), que valida el JWT (`JwtAuthGatewayFilterFactory`) y enruta cada petición al servicio correspondiente. Los servicios no se exponen a internet.

- **Dónde:** `backend/api-gateway/src/main/resources/application.yml` (rutas), `backend/api-gateway/.../filter/JwtAuthGatewayFilterFactory.java`.
- **Por qué ayuda:** centraliza la seguridad y es el lugar natural para el balanceo, el circuit breaker y los reintentos (ver la sección de lo nuevo).

#### 2. Proxy inverso con HTTPS: Caddy

El contenedor `web` sirve el frontend de Angular y reenvía `/api/*` al gateway. Obtiene y renueva solo el certificado HTTPS (Let's Encrypt) y comprime las respuestas (`encode zstd gzip`). Es el único contenedor con puertos abiertos (80 y 443).

- **Dónde:** `frontend/Caddyfile`, `frontend/Dockerfile`, servicio `web` en `docker-compose.yml`.

#### 3. Reinicio automático de contenedores

Todos los contenedores tienen `restart: unless-stopped`: si el proceso de un servicio termina (por ejemplo, por un error de memoria), Docker lo vuelve a levantar.

- **Dónde:** `docker-compose.yml` (ancla `x-servicio`).
- **Límite:** no cubre un servicio que sigue encendido pero dejó de responder; eso lo resuelve ahora *autoheal* (punto 12).

#### 4. Límites de memoria

Cada contenedor tiene `mem_limit: 600m`, y la JVM arranca con `-Xmx256m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC`. Así, un servicio con una fuga de memoria no puede consumir toda la RAM del servidor y tumbar a los demás.

- **Dónde:** `docker-compose.yml`, `backend/Dockerfile` (`JAVA_OPTS`).

#### 5. Pool de conexiones acotado y pooler de Supabase

Todos los servicios comparten una sola base de datos. Cada uno abre como máximo 3 conexiones (`spring.datasource.hikari.maximum-pool-size=3`) y se conecta por el *pooler* de Supabase (PgBouncer, puerto 6543), que multiplexa las conexiones. Esto evita agotar el límite de conexiones de PostgreSQL, que con muchos servicios es una causa común de caídas.

- **Dónde:** `application.properties` de cada servicio.

#### 6. Autenticación sin estado (JWT)

La sesión viaja en el token JWT; ningún servicio guarda sesiones en memoria. Gracias a esto, cualquier réplica puede atender cualquier petición, que es la condición para poder balancear la carga (punto 13).

#### 7. Degradación elegante en los SMS

Los clientes de `mensajeria-service` (`MensajeriaClient` en salud, persona mayor, actividad y auth) capturan los errores: si un SMS no sale, la operación principal (registrar una cita, activar una emergencia) no falla.

#### 8. Recordatorios idempotentes

Los recordatorios de medicamentos y de citas médicas **reservan cada aviso en la base de datos** antes de enviarlo (consultas `reservarAviso...` con `@Modifying`). Si el scheduler corre dos veces, o en dos instancias, solo una consigue la reserva y envía el SMS.

- **Dónde:** `salud-service/.../scheduler/MedicamentoRecordatorioScheduler.java`, `CitaMedicaRecordatorioScheduler.java`, `MedicamentoRepository`, `CitaMedicaRepository`.
- **Consecuencia:** `salud-service` se pudo replicar sin cambios en sus schedulers.

#### 9. Google Places: caché y tiempos máximos de espera

La búsqueda de lugares guarda en memoria las últimas 500 búsquedas (LRU), para no pagar dos veces la misma consulta, y corta la llamada si Google no conecta en 5 s o no responde en 10 s.

- **Dónde:** `analitica-service/.../lugares/BuscadorLugares.java`.

#### 10. Tiempo real con reconexión

El gateway publica avisos de cambio por SSE (`/api/eventos`) y el frontend vuelve a consultar los datos al recibirlos o al reconectarse. Caddy no guarda la respuesta en búfer (`flush_interval -1`).

- **Dónde:** `api-gateway/.../eventos/`, `frontend/src/app/core/tiempo-real/`.

---

### Lo que se implementó

#### 11. Health checks

Los 11 servicios y el gateway incluyen **Spring Boot Actuator**, que expone `/actuator/health/liveness` (el proceso está vivo) y `/actuator/health/readiness` (está listo para recibir tráfico). Solo se expone `health`, ningún otro endpoint de Actuator.

En el servidor, el health check va en el **puerto 9000** (`MANAGEMENT_SERVER_PORT`), separado del de la API, y no se publica: el gateway solo enruta `/api/**`. En `auth-service`, que tiene Spring Security, se permitió el acceso a `/actuator/health/**`.

Docker revisa cada servicio cada 15 s con `curl` (que se agregó a la imagen). Después de 4 fallos seguidos, lo marca como `unhealthy`. El `start_period` de 120 s da tiempo a que arranque Spring.

> **Decisión:** se usa *liveness* y no el health completo, porque este último incluye la base de datos. Si Supabase falla, reiniciar los servicios no lo arregla y solo provocaría reinicios en cadena.

- **Dónde:** `pom.xml` y `application.properties` de cada servicio, `backend/Dockerfile`, `healthcheck` en `docker-compose.yml`, `auth-service/.../security/SecurityConfig.java`.

#### 12. Autoreparación: autoheal

`restart: unless-stopped` solo actúa si el proceso termina. Un servicio colgado (sin memoria libre, bloqueado, sin responder) sigue "encendido" para Docker. El contenedor `autoheal` revisa cada 10 s y **reinicia los contenedores marcados como `unhealthy`**.

- **Dónde:** servicio `autoheal` en `docker-compose.yml`. Actúa sobre los contenedores con la etiqueta `autoheal=true`, que llevan todos los servicios.
- **Consideración de seguridad:** autoheal necesita el socket de Docker (`/var/run/docker.sock`). Es una imagen pública muy usada (`willfarrell/autoheal`), con la versión fijada en `1.2.0`.

#### 13. Balanceo de carga y réplicas

Tres servicios corren en **dos réplicas** cada uno, elegidos por ser los más críticos para una persona mayor:

| Servicio | Por qué se replicó |
|---|---|
| `auth-service` | Sin él nadie puede iniciar sesión |
| `persona-mayor-service` | Tiene el **botón de emergencia** |
| `salud-service` | Medicamentos, citas médicas y sus recordatorios |

El gateway usa **Spring Cloud LoadBalancer**: las rutas apuntan a `lb://<servicio>` y el balanceador reparte las peticiones entre las instancias con *round robin*. En desarrollo, cada servicio tiene una sola instancia en `localhost`. En el servidor, el perfil `docker` (`application-docker.yml`) agrega la segunda réplica. Cada segunda réplica arranca cuando la primera ya está sana (`depends_on: condition: service_healthy`), para que no ejecuten al mismo tiempo las tareas de arranque (crear tablas, catálogos o el bucket de fotos).

**Servicios que no se replicaron y por qué:**

- **`mensajeria-service`**: guarda los códigos OTP en memoria (`TextBeeOtpService`). Con dos réplicas, el código se podría generar en una y verificar en la otra. Replicarlo requiere guardar los OTP en la base de datos o en Redis.
- **`api-gateway`**: los avisos en tiempo real (SSE) viven en memoria del gateway (`CambiosPublisher`). Con dos gateways, un cambio hecho a través de uno no avisaría a quienes están conectados al otro. Replicarlo requiere un canal compartido (Redis Pub/Sub).
- **Los demás**: por memoria. Cada réplica consume entre 300 y 450 MB. Con las tres réplicas hay 15 JVM, alrededor de 5,5 a 6 GB en uso real, dentro de los 8 GB del servidor.

- **Dónde:** `api-gateway/pom.xml` (`spring-cloud-starter-loadbalancer`), `application.yml` (`spring.cloud.discovery.client.simple.instances` y rutas `lb://`), `application-docker.yml`, servicios `*-2` en `docker-compose.yml`.

> **Decisión técnica:** se probó el *health check activo* del balanceador (`spring.cloud.loadbalancer.configurations: health-check`). Con la lista fija de instancias, en esta versión (Spring Cloud 2025.1.2, LoadBalancer 5.0.2), no recibe ninguna instancia y deja **todas** las rutas sin servicio. Lo detectó la prueba `ReplicaCaidaTest`. Por eso, a las réplicas caídas las saltan los reintentos (punto 15) y las reinicia autoheal (punto 12).

#### 14. Circuit breaker por ruta

Cada ruta del gateway tiene su propio **circuit breaker** (Resilience4j, filtro `CircuitBreaker` en `default-filters`; el nombre se toma del id de la ruta). Si en las últimas 20 peticiones de una ruta (mínimo 10) falla la mitad o más, el circuito se **abre** durante 20 s. En ese tiempo, el gateway responde de inmediato con un **503 y un mensaje para la persona**, en vez de acumular peticiones a un servicio que no responde. Después deja pasar 3 peticiones de prueba y, si salen bien, lo cierra.

El mensaje es: *"Esta parte de Vita+ no está disponible en este momento. Intenta de nuevo en unos minutos."* El frontend ya muestra el texto de los errores tal cual (`mensajeDeError`).

Como cada ruta tiene su circuito, la caída de analítica no afecta el inicio de sesión, por ejemplo.

| Parámetro | Valor |
|---|---|
| Ventana | 20 peticiones (mínimo 10) |
| Umbral de fallas | 50 % |
| Tiempo abierto | 20 s |
| Peticiones de prueba | 3 |
| Tiempo máximo por petición | 20 s (60 s para analítica, por el PDF) |
| Estados que cuentan como falla | 502, 503, 504, errores de conexión y tiempo agotado |

- **Dónde:** `api-gateway/pom.xml` (`spring-cloud-starter-circuitbreaker-reactor-resilience4j`), `application.yml` (`default-filters` y `resilience4j`), `api-gateway/.../fallback/FallbackController.java`.

#### 15. Reintentos seguros en el gateway

Hay dos filtros `Retry`, pensados para **nunca duplicar una operación**:

1. **GET**: se repiten hasta 2 veces, con espera de 200 ms y luego 400 ms, si no hubo respuesta a tiempo o si llegó 502, 503 o 504. Repetirlos es seguro porque un GET no cambia datos.
2. **Cualquier método** (POST, PUT, DELETE...): se repite **solo si no se pudo conectar** (conexión rechazada o host inexistente, por ejemplo una réplica apagada o reiniciándose). En ese caso la petición nunca llegó al servicio, así que repetir un POST no crea dos registros ni envía dos alertas.

Con `spring.cloud.loadbalancer.retry.enabled: true`, el reintento **va a una instancia distinta** de la que falló. Así, con una réplica caída, el login o el botón de emergencia llegan a la otra.

El circuit breaker envuelve a los reintentos, así que solo cuenta como falla lo que siguió fallando después de reintentar. Una réplica caída no abre el circuito mientras la otra responda.

- **Dónde:** `application.yml` del gateway (`default-filters`, `spring.cloud.loadbalancer.retry`).

#### 16. Tiempos máximos de espera

Sin tiempos máximos, un servicio que se cuelga deja colgado a quien lo llama, y así sucesivamente.

| Llamada | Conexión | Respuesta |
|---|---|---|
| Gateway → servicios | 3 s | 60 s (más el límite del circuit breaker: 20 s, o 60 s para analítica) |
| Servicios → `mensajeria-service` (salud, persona mayor, actividad, auth) | 3 s | 15 s |
| `auth-service` → Supabase Storage (fotos de perfil) | 5 s | 30 s |
| `analitica-service` → Google Places | 5 s | 10 s (ya existía) |

- **Dónde:** `application.yml` del gateway (`httpclient`), `MensajeriaClientConfig.java` de cada servicio, `auth-service/.../service/FotoPerfilStorage.java`.

#### 17. Bloqueo distribuido de tareas programadas: ShedLock

Con dos réplicas de `persona-mayor-service`, las dos correrían la tarea de cumpleaños de las 8 a. m. y cada persona recibiría **dos** felicitaciones. Con **ShedLock**, la réplica que empieza la tarea deja una marca en la tabla `shedlock` de la base de datos y la otra se salta esa ejecución. Se usa la hora de la base de datos, para que no importe si los relojes de las réplicas están desfasados. La marca dura al menos 5 minutos y como máximo 30.

`salud-service` no lo necesita: sus recordatorios ya eran idempotentes (punto 8).

- **Dónde:** `persona-mayor-service/pom.xml` (`shedlock-spring` y `shedlock-provider-jdbc-template` 7.10.1), `config/BloqueoTareasConfig.java` (crea la tabla si no existe), `@SchedulerLock` en `scheduler/CumpleanosScheduler.java`.
- **Si se replica otro servicio con tareas programadas** (actividad o analítica), hay que agregarle ShedLock de la misma forma.

#### 18. Caché de catálogos: Caffeine

Los catálogos casi no cambian y los piden todos los formularios. Se guardan en memoria con **Caffeine**:

| Catálogo | Servicio | Duración | Se borra cuando |
|---|---|---|---|
| Gustos, talentos y pasatiempos (`GET /api/gustos`) | persona mayor | 10 min | Se agrega un gusto (`@CacheEvict`) |
| Enfermedades, alergias y discapacidades (`GET .../condiciones-salud/catalogo`) | salud | 1 h | Solo cambia al arrancar el servicio |

> **Decisión:** caché local por instancia en vez de una caché distribuida (Redis). Para catálogos casi estáticos no compensa agregar y mantener otro servidor. La consecuencia es que, si se agrega un gusto, la otra réplica lo muestra a más tardar en 10 minutos. No se cacheó información personal ni los reportes de analítica: deben verse al instante cuando cambian (el tiempo real avisa al frontend y este vuelve a consultar).

- **Dónde:** `pom.xml` (`spring-boot-starter-cache`, `caffeine`), `config/CacheConfig.java` y `application.properties` (`spring.cache.*`) de persona mayor y salud, `@Cacheable` en `GustoController` y `CondicionSaludController`.

#### 19. Caché del navegador para el frontend

Los archivos `.js` y `.css` que genera Angular llevan un *hash* en el nombre (`main-ABC123.js`) que cambia en cada versión. Caddy los marca con `Cache-Control: public, max-age=31536000, immutable`, así que el navegador los guarda un año y no los vuelve a descargar. `index.html` va con `no-cache`, para que siempre apunte a los archivos de la versión vigente. Esto reduce la carga del servidor y acelera las visitas, que es importante con conexiones lentas.

- **Dónde:** `frontend/Caddyfile`.

#### 20. Apagado ordenado

Con `server.shutdown=graceful`, al detener o actualizar un servicio, Spring deja de aceptar peticiones nuevas y **termina las que están en curso** (hasta 20 s) antes de cerrar. Con réplicas, se puede reiniciar una mientras la otra atiende.

- **Dónde:** `application.properties` de cada servicio y `application.yml` del gateway.

#### 21. Reintentos en el frontend

Un interceptor HTTP de Angular repite **solo las consultas (GET)** que fallan por un error pasajero: sin conexión (0), 502, 503 o 504. Hace hasta 2 reintentos, después de 1 s y de 2 s. Si un GET falla por algo que no es pasajero (por ejemplo, un 404), el error se muestra de inmediato. Los POST, PUT y DELETE nunca se repiten desde el navegador.

- **Dónde:** `frontend/src/app/core/reintentos/reintentos.interceptor.ts`, registrado en `app.config.ts`.

---

## 2. Seguridad y privacidad

Vita+ guarda datos sensibles de personas mayores: salud, medicamentos, citas médicas, signos vitales, dirección y contactos. Las decisiones de esta parte buscan que **solo quien tiene un vínculo aceptado con una persona vea sus datos**, que esos datos viajen cifrados y que la plataforma exponga lo mínimo a internet.

### 22. Contraseñas con BCrypt e inicio de sesión protegido contra ataques de fuerza bruta (RNF-18)

Las contraseñas se guardan solo como hash **BCrypt** (`BCryptPasswordEncoder`), nunca en claro. Cambiar la contraseña exige la actual, y recuperarla exige el código OTP enviado al celular de la cuenta. El login responde lo mismo si el correo no existe o si la contraseña no coincide (*"Correo o contraseña incorrectos"*), para no revelar qué correos tienen cuenta. También se puede entrar sin contraseña, con el celular y un código OTP (punto 27).

Para que no se puedan probar contraseñas sin fin, hay dos barreras que se complementan:

| Barrera | Qué frena | Cómo funciona |
|---|---|---|
| **Bloqueo de la cuenta** (`auth-service`) | A quien ataca una cuenta | Tras **5 contraseñas incorrectas seguidas**, la cuenta no acepta contraseñas durante **15 minutos**: ni siquiera se revisa la contraseña, y se responde 429 con un mensaje que dice cuánto falta. Un inicio de sesión exitoso borra los intentos. Restablecer la contraseña con el código por SMS desbloquea de una vez, y entrar con el celular y el código sigue funcionando, así que la persona dueña de la cuenta nunca queda por fuera |
| **Límite por IP** (gateway) | A quien prueba muchas cuentas desde la misma IP | Máximo **10 intentos por minuto** por IP en `/api/auth/login`, `/api/auth/login-otp` y `/api/auth/restablecer-contrasena`. Pasado el límite, el gateway responde 429 (con `Retry-After`) sin llegar a `auth-service` |

> **Decisiones:**
> - El contador del bloqueo vive **en la base de datos**, no en memoria, porque `auth-service` corre en dos réplicas y cada intento puede caer en una distinta. Se suma con una sola sentencia `UPDATE`, para que dos intentos simultáneos no se pisen.
> - El límite por IP vive en la memoria del gateway, lo que basta porque hay un solo gateway. El filtro `RequestRateLimiter` de Spring Cloud Gateway necesita Redis, y Caddy necesita un plugin aparte para limitar peticiones. La IP se toma de `X-Forwarded-For`, que en el servidor pone Caddy con la IP real del navegador.
> - Un correo que no existe nunca se bloquea, así que alguien podría notar la diferencia tras cinco intentos. Se aceptó porque, con el límite por IP, averiguar cuentas así es muy lento.

- **Dónde:** `auth-service/.../security/SecurityConfig.java`, `service/AuthService.java` (`login`, `restablecerContrasena`), `service/CuentaBloqueadaException.java`, `repository/UsuarioRepository.java` (`registrarIntentoFallido`, `reiniciarIntentosFallidos`), columnas `intentos_fallidos` y `bloqueado_hasta` de la tabla `usuario`, `controller/ContrasenaController.java`, `api-gateway/.../seguridad/LimiteIntentosGlobalFilter.java`.

### 23. Tokens JWT firmados y validados en el gateway

Al iniciar sesión, `auth-service` emite un **JWT firmado con HMAC-SHA** que vence a las **24 horas** y lleva el id del usuario y su rol. El gateway revisa la **firma y la expiración** en cada ruta protegida (filtro `JwtAuth`). Sin un token válido responde 401, y la petición no llega a ningún servicio.

Las rutas de `/api/auth/**` no llevan el filtro porque el login y el registro son públicos. Las demás rutas de auth las protege `auth-service` con su propio filtro de Spring Security. El stream de tiempo real (`/api/eventos`) también valida el token, y se cierra cuando el token vence.

La clave de firma es la misma en `auth-service` y en el gateway. Sale de una variable de entorno (`JWT_SECRET`, mínimo 32 caracteres) y nunca va al navegador.

- **Dónde:** `auth-service/.../security/JwtService.java` y `JwtAuthenticationFilter.java`, `api-gateway/.../filter/JwtAuthGatewayFilterFactory.java`, `api-gateway/.../eventos/EventosController.java`, `jwt.expiration` en el `application.properties` de `auth-service`.

### 24. La identidad la pone el gateway, no el navegador

Cuando el token es válido, el gateway agrega a la petición los encabezados `X-User-Id` y `X-User-Rol` con los datos **del token**. Si el navegador manda esos encabezados por su cuenta, **se sobrescriben**.

Los servicios toman el id del usuario de ese encabezado, nunca del cuerpo de la petición ni de la URL. Así, una persona solo puede leer y cambiar lo suyo: sus medicamentos, sus gustos, sus vínculos o su perfil. En `/api/auth/**`, donde el gateway no agrega el encabezado, `auth-service` saca el id del token.

- **Dónde:** `JwtAuthGatewayFilterFactory.java`, `@RequestHeader("X-User-Id")` en los controladores de cada servicio.

### 25. Autorización por vínculo

Para ver los datos de **otra** persona hace falta un vínculo aceptado por las dos partes:

| Quién | Qué puede ver o hacer | Condición |
|---|---|---|
| Acompañante | Perfil, salud, medicamentos, citas y signos vitales de una persona mayor | Que la acompañe (vínculo aceptado) |
| Organización | Sus personas mayores, los acompañantes de ellas, las notas, los signos vitales y la analítica | Que la persona mayor esté vinculada a la organización |
| Voluntario | Las actividades de una organización | Que esté vinculado a ella |

Un vínculo nace de una **solicitud que la otra parte acepta o rechaza**: nadie se vincula solo. Si no hay vínculo, el servicio responde 403 (por ejemplo, *"Solo puedes tomar notas de las personas mayores vinculadas a tu organización"*). La analítica solo cuenta a las personas mayores de la organización del usuario.

El frontend también oculta lo que no le corresponde a cada rol (`authGuard`), pero eso es solo comodidad: **el control real está en el backend**.

- **Dónde:** controladores de `acompanante-service`, `salud-service` (rutas `/api/acompanante/personas-mayores/...`), `organizacion-service` (`NotaPersonaMayorController`, `OrganizacionRelacionController`), `persona-mayor-service` (`PersonaMayorOrganizacionController`), `auth-service` (`PersonaMayorAcompananteInformacionController`) y `analitica-service` (`OrganizacionActual`).

### 26. Lo mínimo expuesto a internet

- Solo el contenedor `web` (Caddy) publica puertos (80 y 443). Los servicios y el gateway se comunican por la red interna de Docker y no se pueden llamar desde afuera.
- El gateway solo enruta las rutas de su lista. Lo que no está en la lista no existe para internet:
  - `/api/otp/verify` no se expone. Si estuviera abierto, se podrían probar códigos de otros celulares sin pasar por el login.
  - Tampoco se exponen el PUT ni el DELETE de los signos vitales de la persona mayor, porque esos registros no se editan ni se borran.
  - El catálogo de intereses (`/api/gustos`) se puede consultar y ampliar, pero no editar ni borrar: un interés lo pueden tener marcado otras personas, y nadie debe poder cambiárselo. Los intereses de cada persona mayor solo los ve ella misma.
- De Actuator solo se expone `health`, en el puerto 9000, que no se publica (punto 11).
- **CORS** solo acepta peticiones del navegador que vengan de la dirección pública de la plataforma (`CORS_ORIGINS`).

- **Dónde:** `docker-compose.yml`, `api-gateway/src/main/resources/application.yml` (rutas y `globalcors`), `persona-mayor-service/.../controller/GustoController.java` y `PersonaMayorGustoController.java`.

### 27. Códigos OTP (RNF-03)

Los códigos para entrar con el celular o recuperar la contraseña tienen seis dígitos y se generan con `SecureRandom`. Se protegen así:

| Medida | Valor |
|---|---|
| Cómo se guardan | Solo su **HMAC-SHA256** ligado al celular, con una clave aleatoria que vive en memoria. Nunca en claro |
| Vencimiento | 10 minutos |
| Intentos fallidos | Al quinto, el código se anula y hay que pedir otro |
| Después de usarlo | Se borra: sirve una sola vez |
| Espera entre envíos | 30 s por celular (responde 429) |
| Registros (logs) | No se imprime la respuesta de TextBee, que puede incluir el texto del SMS con el código |

- **Dónde:** `mensajeria-service/.../security/TextBeeOtpService.java`, `controller/OtpController.java`.

### 28. Cifrado en tránsito y en reposo

- **Navegador ↔ servidor:** HTTPS con un certificado de Let's Encrypt que Caddy obtiene y renueva solo. Caddy redirige a HTTPS las peticiones que llegan por HTTP.
- **Servicios ↔ base de datos:** la conexión a Supabase exige TLS (`sslmode=require` en la URL de conexión de cada servicio).
- **Servicios ↔ proveedores externos** (TextBee, Hunter, Google Places y Supabase Storage): siempre por HTTPS.
- **En reposo:** Supabase cifra en disco la base de datos y los archivos de Storage (AES-256). Esto lo da el proveedor, no el código de Vita+.

### 29. Claves fuera del código

Ninguna clave va en el código. La contraseña de la base, el secreto del JWT y las claves de TextBee, Hunter, Supabase y Google se leen de variables:

- **En desarrollo:** del archivo `backend/secretos.properties`, que está en `.gitignore` y en `backend/.dockerignore`, así que tampoco entra a las imágenes de Docker.
- **En el servidor:** del archivo `.env`, que tampoco se sube a git. La plantilla `.env.example` lista las claves sin valores y explica cómo generar un secreto de JWT nuevo (`openssl rand -base64 48`).
- **En el navegador:** no queda ninguna clave. Las llamadas a TextBee, Hunter, Google Places y Supabase Storage las hace el backend.

- **Dónde:** `spring.config.import=optional:file:../secretos.properties` y las variables `${...}` de los `application.properties`, `.gitignore`, `backend/.dockerignore`, `.env.example`.

### 30. Datos personales y privacidad

- **Mapas sin direcciones exactas (RNF-17):** los mapas y la red de la organización ubican a cada persona mayor en el **centro de su barrio**, nunca en su dirección. El barrio se calcula en el backend, y en esas vistas la dirección no viaja al navegador.
- **Constancia de los términos y condiciones:** al registrarse queda guardado quién los aceptó, cuándo y qué versión del texto. El backend rechaza el registro si no se aceptaron.
- **Eliminar la cuenta** borra, en **una sola transacción**, todo lo que referencia al usuario en las tablas de todos los servicios: o se borra todo, o no se borra nada. Solo se conservan la constancia de los términos y el registro del retiro: la razón, y, si era persona mayor, su nombre, para que sus organizaciones sepan quién se fue.
- **Inactivar la cuenta** la oculta para todos sus vínculos sin borrar nada, y se puede reactivar.
- **Los avisos de tiempo real no llevan datos**, solo el nombre del recurso que cambió (por ejemplo, `gustos`). Cada pantalla vuelve a consultar sus datos con su propio token, así que un aviso no le muestra a nadie información ajena.
- **La caché no guarda información personal**, solo catálogos (punto 18).

- **Dónde:** `analitica-service/.../red/RedApoyo.java` y `recomendacion/Barrios.java`, `auth-service/.../service/TerminosService.java` y `model/AceptacionTerminos.java`, `service/CuentaService.java`, `api-gateway/.../eventos/`.

### 31. Fotos de perfil

- Cada foto se guarda en Supabase Storage como `{idUsuario}/{uuid}.{extensión}`. La URL no se puede adivinar, y el bucket no se puede listar sin la clave secreta, que solo tiene `auth-service`.
- El backend revisa los **primeros bytes del archivo** para confirmar que es una imagen JPEG, PNG o WebP: no se fía del nombre ni del tipo que declara el navegador. Rechaza las fotos de más de 2 MB.
- El navegador reduce la foto antes de subirla (punto 35), así que nunca viaja la imagen original de la cámara, con todo su peso.

- **Dónde:** `auth-service/.../service/FotoPerfilStorage.java`, `controller/FotoPerfilController.java`, `frontend/src/app/core/foto-perfil/reducir-foto.ts`.

### 32. Consultas parametrizadas y validación en el backend

- Todas las consultas a la base usan **parámetros**, con JPA o con `NamedParameterJdbcTemplate`: lo que escribe el usuario nunca se pega dentro del SQL. Así se evita la inyección SQL.
- Las reglas importantes se validan **también en el backend**, aunque el formulario ya las revise: el formato del celular (`+57` y 10 dígitos), el correo, el largo mínimo de la contraseña, la aceptación de los términos y los rangos de los signos vitales.
- **Inyección de HTML o de scripts (XSS):** Angular escapa todo lo que muestra con `{{ }}`. El único `innerHTML` de la aplicación es el de los iconos, y su contenido es texto fijo del código, no datos del usuario.

- **Dónde:** repositorios y consultas de cada servicio, `auth-service/.../service/AuthService.java` (`validarRegistro`), `salud-service/.../SignoVitalValidador.java`, `frontend/src/app/shared/icono/icono.ts`.

---

## 3. Rendimiento

La mayoría de los usuarios entra desde el celular, muchas veces con mala señal. Por eso se buscó que la aplicación descargue poco y que el servidor no repita trabajo.

### 33. Cada página se descarga solo cuando se visita

Las 42 rutas del frontend usan **carga diferida** (`loadComponent`). Al entrar se descarga solo el código común; el de cada página (analítica, mapas, reportes...) llega la primera vez que se abre. Angular tiene además un **presupuesto de tamaño** para la carga inicial: avisa si pasa de 500 kB y no compila si pasa de 1 MB.

- **Dónde:** `frontend/src/app/app.routes.ts`, `budgets` en `frontend/angular.json`.

### 34. Compresión y caché

| Qué | Efecto | Detalle |
|---|---|---|
| Compresión zstd o gzip en Caddy | Las respuestas viajan comprimidas | Punto 2 |
| Caché del navegador para los `.js` y `.css` | La aplicación se descarga una sola vez por versión | Punto 19 |
| Caché de catálogos (Caffeine) | Los formularios no consultan la base cada vez | Punto 18 |
| Caché de Google Places | Una búsqueda repetida no vuelve a llamar a Google | Punto 9 |

### 35. Fotos reducidas en el navegador

Antes de subir una foto de perfil, el navegador la reduce a **512 px** por su lado mayor y la convierte a JPEG. Las fotos de celular, que pesan varios MB, quedan en decenas de KB: suben rápido aunque la señal sea mala, y cargan rápido en las listas.

- **Dónde:** `frontend/src/app/core/foto-perfil/reducir-foto.ts`.

### 36. Tiempo real en vez de consultas periódicas

En lugar de que cada pantalla le pregunte al servidor cada pocos segundos si algo cambió, el gateway avisa por una sola conexión SSE, **y solo cuando algo cambia** (punto 10). Así se evitan miles de consultas inútiles por cada usuario conectado.

### 37. La analítica se calcula en la base de datos

Los reportes de la organización se arman con consultas SQL de solo lectura que **agregan los datos en la base** y traen solo las columnas necesarias, en vez de cargar todas las entidades en memoria. La segmentación con K-means trabaja con decenas o cientos de personas y tarda milisegundos (ver `KMEANS_PERFILES.md`).

- **Dónde:** `analitica-service/.../controller/AnaliticaController.java`, `perfiles/DatosPerfiles.java`.

---

## 4. Escalabilidad

### 38. Servicios independientes y sin estado

El backend está dividido en **11 servicios**, uno por dominio (autenticación, salud, actividades, analítica...), detrás de un único gateway (RNF-01). Cada uno se puede escalar por separado según su carga, como ya se hizo con `auth-service`, `persona-mayor-service` y `salud-service` (punto 13). Esto es posible por cuatro decisiones:

- **Sin sesión en el servidor:** el JWT lleva la sesión, así que cualquier réplica atiende cualquier petición (punto 6).
- **Balanceo en el gateway:** agregar una réplica es agregar su dirección en `application-docker.yml` y un servicio en `docker-compose.yml` (punto 13).
- **Pocas conexiones a la base por instancia** (3), más el *pooler* de Supabase, para que agregar instancias no agote las conexiones (punto 5).
- **Tareas programadas seguras con varias réplicas:** reservas idempotentes y ShedLock (puntos 8 y 17).

Lo que todavía limita el crecimiento está en *Limitaciones conocidas*: los OTP y los avisos de tiempo real viven en memoria, y todo corre en un solo servidor.

---

## 5. Mantenibilidad y portabilidad

### 39. La misma estructura en todos los servicios

Los 11 servicios siguen la misma organización (`controller`, `dto`, `model`, `repository`, `service`, `config`, `scheduler`), con nombres en español que corresponden a los términos del negocio. El código se comenta según `GUIA_COMENTARIOS.md`, y las decisiones grandes están en este documento y en `KMEANS_PERFILES.md`.

Cada servicio es dueño de sus tablas. Cuando necesita leer datos de otro servicio, usa entidades de solo lectura (`...Lookup`) en vez de duplicar la lógica.

### 40. Configuración fuera del código

Las direcciones de los servicios, las claves y el origen permitido para CORS se leen de variables de entorno, con valores por defecto para desarrollo (por ejemplo, `${AUTH_SERVICE_URL:http://localhost:8081}`). El mismo `.jar` sirve para desarrollo y para el servidor; en Docker, el perfil `docker` solo agrega las réplicas.

### 41. Pruebas automatizadas (RNF-10)

El backend tiene **68 clases de prueba** (JUnit con MockMvc, Mockito y H2), y el frontend, **34 archivos de prueba** (Vitest). Ninguna prueba usa la base de datos real ni envía SMS, así que se pueden correr en cualquier computador y después de cada cambio.

- **Dónde:** `src/test` de cada servicio, archivos `*.spec.ts` en `frontend/src`.

### 42. Contenedores reproducibles (RNF-14)

Un solo `backend/Dockerfile` construye cualquier servicio con el argumento `SERVICIO`, en dos etapas: compila con Maven y deja una imagen final que solo tiene Java 17 y el `.jar`. El frontend se compila con Node y se sirve con Caddy. Con `docker compose up -d --build` se levanta toda la plataforma en cualquier servidor que tenga Docker.

- **Tecnologías (RNF-11):** Java 17 (LTS) con Spring Boot 4.1, y Angular 22.
- **Dónde:** `backend/Dockerfile`, `frontend/Dockerfile`, `docker-compose.yml`.

---

## 6. Hora de Colombia (RNF-06)

### 43. Un solo reloj para toda la plataforma

Las personas escriben las horas de sus medicamentos, citas y actividades en hora colombiana. Si se usara la zona del servidor (en la nube suele ser UTC), los recordatorios llegarían con cinco horas de diferencia. Por eso, en los servicios con recordatorios, todo cálculo de "ahora" pasa por `ZonaHoraria`, que usa `America/Bogota`, y los schedulers declaran esa misma zona.

- **Dónde:** `config/ZonaHoraria.java` en salud, persona mayor y actividad, y `zone = "America/Bogota"` en sus `@Scheduled`.

---

## Cómo se comprobó

| Prueba | Qué demuestra |
|---|---|
| `api-gateway/.../fallback/ServicioCaidoTest` | Con un servicio caído, el gateway responde 503 con el mensaje amable, sin quedarse colgado |
| `api-gateway/.../fallback/ReplicaCaidaTest` | Con una de dos réplicas apagada, **todos** los GET y POST llegan a la réplica viva; un POST que el servicio sí recibió (respondió 500) **no se repite** |
| `api-gateway/.../RutasGatewayTest` | Cada ruta apunta al servicio correcto (`lb://<servicio>`) |
| `persona-mayor-service/.../config/BloqueoTareasConfigTest` | Si una réplica tomó la tarea de cumpleaños, la segunda no la puede tomar |
| `persona-mayor-service/.../controller/GustoCacheTest` | El catálogo se consulta una sola vez a la base y la caché se borra al agregar un gusto |
| `frontend/.../reintentos/reintentos.interceptor.spec.ts` | Los GET se reintentan ante errores pasajeros; los 404 y los POST nunca |
| `api-gateway/.../filter/JwtAuthGatewayFilterFactoryTest` | Sin token, con un token vencido o firmado con otra clave, la petición no llega al servicio; un `X-User-Id` enviado por el cliente se reemplaza por el del token |
| `api-gateway/.../RutasGatewayTest` | Las rutas protegidas exigen token y las que no se deben exponer (como `/api/otp/verify`) no tienen ruta |
| `mensajeria-service/.../security/TextBeeOtpServiceTest` | El OTP se guarda como HMAC, vence a los 10 minutos, sirve una sola vez, se anula tras 5 fallos y no sirve para otro celular |
| `organizacion-service/.../NotaPersonaMayorControllerTest`, `auth-service/.../PersonaMayorAcompananteInformacionControllerTest` | Una organización o un acompañante sin vínculo no ve ni edita los datos de la persona mayor |
| `auth-service/.../FotoPerfilControllerTest` | Se rechaza un archivo que dice ser imagen pero no lo es |
| `auth-service/.../AuthControllerTest`, `UsuarioRepositoryTest` | La quinta contraseña incorrecta seguida bloquea la cuenta 15 minutos; bloqueada, ni la contraseña correcta sirve; al vencer el bloqueo o restablecer la contraseña se puede volver a entrar |
| `api-gateway/.../seguridad/LimiteIntentosGlobalFilterTest`, `LimiteIntentosGatewayTest` | Pasados 10 intentos por minuto, la IP recibe 429 sin llegar a `auth-service`, con las cabeceras CORS para que el frontend muestre el mensaje; otras IP y otras rutas no se afectan |
| `persona-mayor-service/.../GustoControllerTest` | El catálogo de intereses no se puede editar ni borrar, y los intereses de una persona mayor solo los ve ella |

Las pruebas existentes de todos los servicios siguen pasando.

**Pendiente de verificar en el servidor:** levantar todo con `docker compose up -d --build` y comprobar con `docker compose ps` que todos los contenedores quedan `healthy`. Luego detener una réplica (`docker compose stop salud-service-2`) y confirmar que la plataforma sigue respondiendo. Estas pruebas no se hicieron todavía en un entorno real con Docker.

---

## Limitaciones conocidas y siguientes pasos

| Limitación | Impacto | Cómo resolverla |
|---|---|---|
| Un solo servidor | Si se cae la máquina, se cae todo | Segundo servidor con un balanceador delante (por ejemplo, el de AWS) |
| Una sola instancia del gateway | Si el gateway falla, la API no responde hasta que autoheal lo reinicia (alrededor de 1 minuto) | Avisos de tiempo real por Redis Pub/Sub y dos gateways detrás de Caddy |
| `mensajeria-service` sin réplica | Sin SMS ni códigos OTP mientras se reinicia | Guardar los OTP en la base de datos o en Redis y replicarlo |
| Base de datos única (Supabase) | Si Supabase falla, fallan todos los servicios | Depende del plan de Supabase (réplicas de lectura, *point-in-time recovery*) |
| Límite de peticiones solo en el inicio de sesión | Pedir muchos códigos OTP (cada uno es un SMS) o consultar en masa las rutas públicas del registro podría saturar un servicio o gastar SMS | Agregar esas rutas a `LimiteIntentosGlobalFilter` (punto 22), o usar `RequestRateLimiter` con Redis si hay más de un gateway |
| Sin monitoreo centralizado | Las caídas se ven en los logs, no en un tablero | Exponer `/actuator/prometheus` y usar Prometheus con Grafana |
| `ddl-auto=update` en producción | Un cambio de modelo podría alterar tablas al arrancar | Migraciones versionadas (Flyway) |
