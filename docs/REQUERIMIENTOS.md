# Requerimientos — VITA+

Requerimientos de la plataforma **VITA+** al 29 de octubre de 2026. Se organizan según los módulos principales definidos en el Plan de Administración del Proyecto (SPMP, sección 1.2.2) y sirven como base para actualizar el documento de especificación de requerimientos (SRS).

Convenciones:

* **RF**: requerimiento funcional. **RNF**: requerimiento no funcional.
* Los roles del sistema son **persona mayor**, **acompañante**, **organización** y **voluntario**.
* Un vínculo está **aceptado** cuando la otra parte aprobó la solicitud correspondiente.
* **Estado**: ✅ Implementado · ⏳ Pendiente.
* Los identificadores no se renumeran, porque las pruebas automatizadas los citan. Por eso, dentro de cada módulo pueden aparecer en desorden. Los requerimientos nuevos continúan la numeración (RF-58 en adelante, RNF-11 en adelante).

---

## 1. Gestión de usuarios y acceso

Autenticación de los usuarios, verificación de sus correos electrónicos y administración de roles.

### 1.1 Registro y autenticación

| ID | Requerimiento | Estado |
| --- | --- | --- |
| RF-01 | El sistema debe permitir que un usuario se registre con uno de los cuatro roles, ingresando sus datos personales en un único formulario que marca los campos obligatorios. | ✅ Implementado |
| RF-02 | El sistema debe exigir que el usuario acepte los términos y condiciones antes de completar el registro. | ✅ Implementado |
| RF-69 | El sistema debe guardar una constancia de la aceptación de los términos y condiciones de cada cuenta creada, con el usuario, la fecha y la hora (hora de Colombia) y la versión de los términos aceptada. La constancia se conserva aunque la cuenta se elimine. | ✅ Implementado |
| RF-03 | El sistema debe verificar, mediante un servicio externo (Hunter), que el correo electrónico ingresado existe y puede recibir mensajes; si no es válido, la cuenta no se crea. | ✅ Implementado |
| RF-04 | El sistema debe permitir iniciar sesión con correo electrónico y contraseña. | ✅ Implementado |
| RF-05 | El sistema debe permitir iniciar sesión con número de celular y un código de un solo uso (OTP) enviado por SMS, validando previamente que el número esté registrado. | ✅ Implementado |
| RF-08 | El sistema debe permitir mostrar u ocultar la contraseña en los formularios de inicio de sesión, registro y perfil. | ✅ Implementado |
| RF-09 | El sistema debe mantener la sesión iniciada aunque el usuario cierre y vuelva a abrir el navegador, hasta que cierre sesión. | ✅ Implementado |

### 1.2 Administración de la cuenta

| ID | Requerimiento | Estado |
| --- | --- | --- |
| RF-06 | El sistema debe permitir restablecer la contraseña olvidada mediante un código OTP enviado por SMS. | ✅ Implementado |
| RF-07 | El sistema debe permitir al usuario cambiar su contraseña desde su perfil. | ✅ Implementado |
| RF-10 | El sistema debe permitir a cualquier usuario eliminar su cuenta, previa confirmación. | ✅ Implementado |

### 1.3 Roles y control de acceso

| ID | Requerimiento | Estado |
| --- | --- | --- |
| RF-11 | El sistema debe redirigir a cada usuario, después de iniciar sesión, al panel que corresponde a su rol y restringir el acceso a los paneles de los demás roles. | ✅ Implementado |
| RF-76 | El sistema debe permitir que una misma cuenta sea persona mayor y acompañante, con la misma información personal, y cambiar entre los dos roles desde el óvalo del rol en la barra superior, previa confirmación; si la cuenta aún no tiene el otro rol, se activa al confirmar sin un nuevo registro. Una cuenta no puede ser acompañante de sí misma. | ✅ Implementado |

---

## 2. Gestión de información

Registro, consulta y actualización de la información de personas mayores, acompañantes, voluntarios y organizaciones, y de los datos necesarios para el seguimiento de los beneficiarios.

### 2.1 Perfil de usuario ("Mi información")

| ID | Requerimiento | Estado |
| --- | --- | --- |
| RF-12 | El sistema debe permitir a cada usuario consultar y editar sus datos personales desde la página "Mi información". | ✅ Implementado |
| RF-13 | El sistema debe verificar con Hunter el nuevo correo electrónico solo cuando el usuario lo cambie. | ✅ Implementado |
| RF-14 | El sistema debe permitir a la persona mayor registrar su EPS, su IPS y la dirección de la IPS. | ✅ Implementado |
| RF-15 | El sistema debe permitir a la organización registrar y actualizar su información institucional. | ✅ Implementado |
| RF-16 | El sistema debe permitir a la persona mayor y al voluntario registrar sus gustos e intereses a partir de un catálogo, y agregar al catálogo uno que no esté en la lista. | ✅ Implementado |
| RF-71 | El sistema debe permitir a la persona mayor y al voluntario registrar, junto a sus intereses, su profesión u oficio (a qué se dedica o se dedicó), a partir de un catálogo; tenerla en cuenta, con menor peso que los gustos, en la recomendación de organizaciones, y mostrar a la organización las profesiones de sus personas mayores en el reporte de población. | ✅ Implementado |
| RF-84 | El sistema debe permitir a la persona mayor, o a su acompañante en su nombre, indicar de forma opcional su nivel de uso del celular (no lo usa; bajo, solo llamadas; medio, llamadas y WhatsApp, o alto, varias aplicaciones), y mostrar a la organización, en el reporte de población, cuántas personas hay en cada nivel y cuántas no lo han indicado. | ✅ Implementado |
| RF-61 | El sistema debe permitir a cada usuario subir, cambiar o quitar una foto de perfil (JPG, PNG o WebP), que se muestra a sus vínculos en sus listas y junto a su nombre en la barra superior del panel; si no tiene foto, se muestra su inicial o un ícono. | ✅ Implementado |
| RF-70 | El sistema debe permitir a cada usuario calificar la plataforma desde su perfil, de 0 a 5 estrellas, con una observación u oportunidad de mejora opcional; cada usuario tiene una sola calificación, que puede actualizar. | ✅ Implementado |

### 2.2 Información de salud de la persona mayor

| ID | Requerimiento | Estado |
| --- | --- | --- |
| RF-24 | El sistema debe permitir registrar, consultar, editar y eliminar los medicamentos de la persona mayor, con su dosis, frecuencia, hora de la primera toma, intervalo entre tomas y fechas de inicio y fin del tratamiento. | ✅ Implementado |
| RF-26 | El sistema debe permitir registrar, consultar, editar y eliminar citas médicas de la persona mayor, incluyendo el consultorio. | ✅ Implementado |
| RF-75 | El sistema debe permitir indicar, al registrar o editar una cita médica, si necesita acompañamiento (porque la cita lo requiere o porque la persona mayor quiere ir acompañada); mostrar esas citas en el seguimiento de sus acompañantes para que uno de ellos acepte acompañarla indicando el punto de encuentro, y avisar por SMS a la persona mayor quién la acompañará y dónde se encontrarán. | ✅ Implementado |
| RF-28 | El sistema debe permitir registrar las condiciones de salud de la persona mayor (enfermedades, alergias y discapacidades) a partir de un catálogo. | ✅ Implementado |
| RF-29 | El sistema debe permitir registrar signos vitales (presión arterial, pulso, temperatura corporal, saturación de oxígeno, frecuencia respiratoria, peso y estatura) tanto a la persona mayor como a la organización. | ✅ Implementado |
| RF-30 | El sistema debe rechazar valores de signos vitales que estén fuera de los límites físicamente posibles. | ✅ Implementado |
| RF-31 | El sistema debe calcular el índice de masa corporal (IMC) a partir del peso y la estatura. | ✅ Implementado |
| RF-32 | El sistema debe clasificar cada signo vital según rangos de referencia y mostrar el historial de mediciones de la persona mayor. | ✅ Implementado |
| RF-33 | El sistema debe permitir al acompañante gestionar el perfil, las condiciones de salud, los medicamentos y las citas médicas de las personas mayores a su cargo. | ✅ Implementado |

### 2.3 Información consultada por la organización

| ID | Requerimiento | Estado |
| --- | --- | --- |
| RF-58 | El sistema debe permitir a la organización consultar, en una sola sección, los acompañantes de todas las personas mayores vinculadas a ella. | ⏳ Pendiente |
| RF-59 | El sistema debe permitir a la organización consultar los medicamentos vigentes de las personas mayores vinculadas a ella. | ⏳ Pendiente |
| RF-74 | El sistema debe permitir a la organización tomar notas sobre cada persona mayor vinculada (con su fecha, hora y autor), y editarlas o eliminarlas cuando quiera; las notas solo las ve la organización que las escribió. | ✅ Implementado |

---

## 3. Redes de apoyo y seguimiento

Relaciones de apoyo entre usuarios, contactos de emergencia, actividades de acompañamiento de las organizaciones y avisos para el seguimiento de los beneficiarios.

### 3.1 Vinculaciones (redes de apoyo)

| ID | Requerimiento | Estado |
| --- | --- | --- |
| RF-17 | El sistema debe permitir que una persona mayor y un acompañante se vinculen mediante una solicitud enviada por cualquiera de los dos, que la otra parte puede aceptar o rechazar. | ✅ Implementado |
| RF-18 | El sistema debe permitir que una persona mayor y una organización se vinculen mediante una solicitud enviada por cualquiera de las dos, que la otra parte puede aceptar o rechazar. | ✅ Implementado |
| RF-19 | El sistema debe permitir que un voluntario solicite vincularse a una organización, y que la organización acepte o rechace la solicitud. | ✅ Implementado |
| RF-20 | El sistema debe permitir a cualquiera de las partes cancelar un vínculo existente, previa confirmación. | ✅ Implementado |
| RF-21 | El sistema debe mostrar las solicitudes pendientes recibidas y las enviadas por el usuario. | ✅ Implementado |
| RF-22 | El sistema debe permitir a la organización consultar los acompañantes de cada persona mayor vinculada, y al acompañante consultar los demás acompañantes de sus personas mayores. | ✅ Implementado |
| RF-23 | El sistema debe permitir buscar por nombre dentro de las listas de personas vinculadas. | ✅ Implementado |
| RF-77 | El sistema debe guardar el parentesco del acompañante en cada vínculo, porque puede ser distinto con cada persona mayor, y permitir editarlo desde las tarjetas: la persona mayor, en sus acompañantes, y el acompañante, en sus personas mayores. Los vínculos creados antes conservan el parentesco que tenían. | ✅ Implementado |

### 3.2 Contactos y emergencias

| ID | Requerimiento | Estado |
| --- | --- | --- |
| RF-35 | El sistema debe ofrecer a la persona mayor un botón de emergencia que, previa confirmación, envíe un SMS a todos sus acompañantes y organizaciones con vínculo aceptado. | ✅ Implementado |
| RF-36 | El sistema debe registrar cada emergencia y mostrarla en las alertas del inicio del acompañante. | ✅ Implementado |
| RF-37 | El sistema debe mostrar a la persona mayor la lista de sus contactos (acompañantes y organizaciones vinculadas). | ✅ Implementado |

### 3.3 Seguimiento por parte del acompañante

| ID | Requerimiento | Estado |
| --- | --- | --- |
| RF-34 | El sistema debe permitir al acompañante hacer seguimiento de los signos vitales, medicamentos, citas médicas y contactos de cada persona mayor a su cargo. | ✅ Implementado |

### 3.4 Actividades de acompañamiento

| ID | Requerimiento | Estado |
| --- | --- | --- |
| RF-38 | El sistema debe permitir a la organización crear, editar y eliminar actividades, indicando nombre, tipo, fecha, hora, lugar, responsable y cupos. | ✅ Implementado |
| RF-39 | El sistema debe permitir a la organización registrar rápidamente una actividad desde su página de inicio, exigiendo nombre, fecha, hora y lugar. | ✅ Implementado |
| RF-40 | El sistema debe permitir que la persona mayor, o su acompañante en su nombre, se inscriba o cancele la inscripción en las actividades de las organizaciones a las que está vinculada. | ✅ Implementado |
| RF-41 | El sistema debe permitir a la organización consultar los participantes de cada actividad y registrar su asistencia. | ✅ Implementado |
| RF-42 | El sistema debe permitir que voluntarios y personas mayores propongan actividades a las organizaciones a las que están vinculados, y que la organización acepte o rechace cada propuesta. | ✅ Implementado |
| RF-44 | El sistema debe eliminar las inscripciones de una actividad cuando esta se elimine. | ✅ Implementado |
| RF-78 | El sistema debe mostrar a la persona mayor, en un mapa, sus próximas actividades y las organizaciones a las que pertenece o puede unirse, con la opción "Cómo llegar" (Google Maps) y la distancia desde su ubicación actual si la comparte. La ubicación de cada sede y de cada lugar de actividad se obtiene automáticamente a partir de su dirección (Google Places), sin que nadie escriba coordenadas; si no se encuentra el lugar, la actividad se muestra en la sede de su organización. | ✅ Implementado |

### 3.5 Recordatorios y notificaciones

| ID | Requerimiento | Estado |
| --- | --- | --- |
| RF-25 | El sistema debe enviar por SMS a la persona mayor y a sus acompañantes aceptados un recordatorio 15 minutos antes de cada toma de medicamento y otro a la hora exacta, sin repetir avisos. | ✅ Implementado |
| RF-27 | El sistema debe enviar por SMS a la persona mayor y a sus acompañantes aceptados un recordatorio un día antes y otro una hora antes de cada cita médica, y volver a avisar si la fecha u hora de la cita cambia. | ✅ Implementado |
| RF-43 | El sistema debe enviar por SMS un recordatorio una hora antes de cada actividad, solo a las personas mayores inscritas y sin repetirlo. | ✅ Implementado |
| RF-50 | El sistema debe mostrar en el panel de cada usuario una campana con las notificaciones recibidas y permitir marcarlas como leídas. | ✅ Implementado |
| RF-51 | El sistema debe permitir a cada usuario activar o desactivar cada tipo de notificación (emergencias, cumpleaños, cumpleaños de personas mayores vinculadas, actividades, citas médicas y medicamentos). | ✅ Implementado |
| RF-52 | El sistema debe felicitar por SMS a las personas mayores, acompañantes y voluntarios el día de su cumpleaños, y avisar a los acompañantes y organizaciones vinculados cuando una persona mayor cumpla años. | ✅ Implementado |

### 3.6 Donaciones e inventario

Las donaciones registran lo que un acompañante o un voluntario se compromete a llevar a una organización (alimentos, ropa, medicamentos, artículos de aseo, dinero u otros). La plataforma no procesa pagos: el dinero se registra como un artículo más, con su monto en pesos.

| ID | Requerimiento | Estado |
| --- | --- | --- |
| RF-62 | El sistema debe permitir al acompañante y al voluntario programar una donación para una organización con la que estén vinculados, indicando la fecha de entrega, una hora aproximada opcional, observaciones y uno o más artículos, cada uno con categoría, descripción, cantidad, unidad y fecha de vencimiento opcional. El voluntario puede donar a las organizaciones en las que está aceptado; el acompañante, a las organizaciones de las personas mayores que acompaña. | ✅ Implementado |
| RF-63 | El sistema debe permitir al donante consultar sus donaciones con su estado (programada, recibida, no entregada o cancelada) y cancelar las que aún estén programadas. | ✅ Implementado |
| RF-64 | El sistema debe permitir a la organización consultar las donaciones programadas para ella y marcar cada una como recibida, indicando la cantidad que llegó de cada artículo, o como no entregada, con una nota opcional para el donante. Una donación solo se procesa una vez. | ✅ Implementado |
| RF-65 | El sistema debe sumar automáticamente al inventario de la organización lo recibido de cada donación, agregándolo al artículo existente con el mismo nombre, categoría y unidad, o creando uno nuevo. | ✅ Implementado |
| RF-66 | El sistema debe permitir a la organización crear, editar y eliminar artículos de su inventario, con nombre, categoría, unidad, cantidad mínima de aviso y fecha de vencimiento opcional. | ✅ Implementado |
| RF-67 | El sistema debe permitir a la organización registrar entradas y salidas de cada artículo con su motivo (compra, entrega a personas mayores, uso en actividades, vencimiento, daño o ajuste), sin permitir salidas mayores que la existencia, y consultar el historial de movimientos de cada artículo. | ✅ Implementado |
| RF-68 | El sistema debe señalar en el inventario los artículos agotados o por debajo de su cantidad mínima y los vencidos o que vencen en los próximos 30 días, y mostrar el dinero disponible. | ✅ Implementado |

---

## 4. Analítica de datos y visualización

Indicadores, reportes y visualizaciones para identificar patrones, apoyar el perfilamiento de las personas mayores y orientar la toma de decisiones de las organizaciones.

### 4.1 Reportes e indicadores

| ID | Requerimiento | Estado |
| --- | --- | --- |
| RF-45 | El sistema debe mostrar a la organización un reporte de actividades con indicadores y gráficas de participación por tipo de actividad, inscritos y asistentes por mes y ocupación de cupos. | ✅ Implementado |
| RF-46 | El sistema debe mostrar a la organización un reporte de salud con el estado actual de cada indicador y la lista de personas mayores que requieren atención. | ✅ Implementado |
| RF-47 | El sistema debe mostrar a la organización un reporte de población con la distribución por rangos de edad, género, EPS e intereses más comunes, como apoyo al perfilamiento de las personas mayores. | ✅ Implementado |
| RF-48 | El sistema debe permitir descargar cada reporte en PDF, incluyendo una explicación de cada gráfica. | ✅ Implementado |
| RF-72 | El sistema debe mostrar a la organización, en el reporte de población, cuántas personas mayores viven solas y, en el reporte de salud, cuántas están en cada categoría de índice de masa corporal (IMC) según su medición más reciente. | ✅ Implementado |
| RF-73 | El sistema debe mostrar a la organización un reporte de retiros con las personas mayores que eliminaron su cuenta en el período: cuántas fueron, la tasa de retiro, las razones (separando las relacionadas con la plataforma), los retiros por mes y los comentarios que dejaron. | ✅ Implementado |
| RF-83 | El sistema debe mostrar a la organización un reporte de donaciones e inventario, filtrado por el período elegido, con el porcentaje de donaciones que llegaron (sobre las recibidas y no entregadas), las donaciones por recibir y las que tienen la fecha de entrega vencida, las donaciones por mes según su estado, qué se recibe por categoría y quién dona; y, con el estado de hoy del inventario, los artículos agotados o por agotarse, los vencidos o por vencer, el dinero disponible, las entradas y salidas por motivo y las existencias por categoría, con las mismas reglas de RF-68. | ✅ Implementado |
| RF-81 | El sistema debe agrupar a las personas mayores de la organización en perfiles con características parecidas (edad, condiciones de salud, signos vitales y necesidades de acompañamiento) mediante K-means, eligiendo el número de grupos con el coeficiente de silueta; evaluar antes si la calidad y la cantidad de los datos alcanzan para entrenar el modelo, explicando a quién se excluyó y por qué; y mostrar en la pestaña "Grupos de atención" de la analítica cada perfil con sus rasgos, recomendaciones de atención y de actividades, un mapa de los grupos en dos dimensiones (PCA) y las advertencias sobre los datos, con exportación a PDF. Detalle en `KMEANS_PERFILES.md`. | ✅ Implementado |

### 4.2 Alertas básicas de seguimiento

| ID | Requerimiento | Estado |
| --- | --- | --- |
| RF-60 | El sistema debe mostrar a la organización, en su sección de alertas, las personas mayores vinculadas que podrían estar en situación de aislamiento o vulnerabilidad: las que no participan en actividades desde hace un tiempo definido, las que no tienen acompañantes y las que tienen signos vitales recientes fuera de rango, indicando el motivo de cada alerta. | ⏳ Pendiente |

### 4.3 Recomendaciones

| ID | Requerimiento | Estado |
| --- | --- | --- |
| RF-49 | El sistema debe recomendar a la persona mayor y al voluntario organizaciones a las que aún no pertenecen, según la coincidencia de sus gustos con las actividades de la organización (40 %), la similitud con los gustos de sus miembros (25 %) y la cercanía entre direcciones (35 %), explicando las razones de cada recomendación. | ✅ Implementado |

### 4.4 Red y mapa de la organización

| ID | Requerimiento | Estado |
| --- | --- | --- |
| RF-79 | El sistema debe mostrar a la organización, en la sección "Red y mapa", un grafo de su red de apoyo (la organización unida a sus personas mayores y voluntarios, y cada persona mayor a sus acompañantes), resaltando a las personas mayores sin acompañante como posible aislamiento, y un grafo por gustos que una cada gusto con quienes lo tienen. | ✅ Implementado |
| RF-80 | El sistema debe mostrar a la organización un mapa de su red con la sede, las personas mayores agrupadas por barrio (nunca su dirección exacta), los lugares de sus actividades de los últimos 3 meses y los próximos 2, y líneas de cada barrio a los lugares a los que va su gente, con filtro por gusto. Cada gráfica incluye indicadores y una vista de tabla. | ✅ Implementado |

---

## 5. Interfaz web y paneles por rol

| ID | Requerimiento | Estado |
| --- | --- | --- |
| RF-53 | El sistema debe mostrar a la persona mayor, en su inicio, la agenda del día con sus tomas de medicamentos, citas y actividades. | ✅ Implementado |
| RF-54 | El sistema debe mostrar al acompañante, en su inicio, el resumen de sus personas mayores y las alertas de emergencia. | ✅ Implementado |
| RF-55 | El sistema debe mostrar a la organización, en su inicio, el resumen de sus personas mayores, voluntarios y actividades, con acciones rápidas para registrar signos vitales y actividades. | ✅ Implementado |
| RF-56 | El sistema debe mostrar al voluntario, en su inicio, sus organizaciones y actividades. | ✅ Implementado |
| RF-57 | El sistema debe ofrecer una página pública de inicio (landing) que presente la plataforma y sus módulos. | ✅ Implementado |
| RF-82 | El sistema debe ofrecer en todas las páginas un botón flotante para activar o desactivar un lector de voz que diga en voz alta para qué sirve lo que el usuario toca: botones, campos de texto y texto común. | ✅ Implementado |

---

## 6. Requerimientos no funcionales

| ID | Requerimiento | Estado |
| --- | --- | --- |
| RNF-01 | **Arquitectura.** El backend debe estar dividido en servicios independientes (Spring Boot) y todas las peticiones del frontend deben pasar por un único API Gateway. | ✅ Implementado |
| RNF-02 | **Seguridad.** El acceso a los servicios debe estar protegido con tokens JWT validados en el API Gateway. | ✅ Implementado |
| RNF-03 | **Seguridad.** Los códigos OTP no deben guardarse en texto plano (se almacenan como HMAC), deben vencer a los 10 minutos, anularse tras 5 intentos fallidos y borrarse en cuanto se usan. | ✅ Implementado |
| RNF-04 | **Actualización en tiempo real.** Los cambios en datos compartidos entre usuarios deben reflejarse en los paneles abiertos sin recargar la página (eventos enviados por el servidor). | ✅ Implementado |
| RNF-05 | **Confiabilidad.** Los recordatorios por SMS no deben enviarse dos veces, aunque el servicio se reinicie o haya más de una instancia en ejecución. | ✅ Implementado |
| RNF-06 | **Zona horaria.** Las fechas, horas y recordatorios deben manejarse en la hora de Colombia (America/Bogota). | ✅ Implementado |
| RNF-07 | **Usabilidad.** La interfaz debe estar en español, usar un diseño consistente entre roles, pedir confirmación antes de las acciones destructivas y mostrar mensajes de carga mientras se obtienen los datos. | ✅ Implementado |
| RNF-08 | **Persistencia.** Los datos deben almacenarse en una base de datos PostgreSQL alojada en Supabase. | ✅ Implementado |
| RNF-09 | **Integraciones.** El envío de SMS debe hacerse con TextBee y la validación de correos con Hunter. | ✅ Implementado |
| RNF-10 | **Mantenibilidad.** Cada servicio del backend y el frontend deben contar con pruebas automatizadas que no dependan de la base de datos real ni envíen SMS. | ✅ Implementado |
| RNF-11 | **Tecnologías.** El backend debe desarrollarse en Java LTS (Java 17) con Spring Boot, y el frontend en Angular 17 o superior. | ✅ Implementado |
| RNF-12 | **Compatibilidad.** La plataforma debe funcionar en las versiones recientes de los navegadores web modernos (Chrome, Edge y Firefox) y adaptarse a pantallas de computador, tableta y celular. | ✅ Implementado |
| RNF-13 | **Apoyo a la decisión.** La analítica, las recomendaciones y las alertas deben limitarse a informar a los usuarios; el sistema no debe tomar decisiones ni ejecutar acciones de forma autónoma a partir de ellas. | ✅ Implementado |
| RNF-14 | **Portabilidad.** Los componentes de la aplicación que se ejecutan localmente (frontend y servicios del backend) deben poder ejecutarse en contenedores Docker, orquestados con Docker Compose. | ✅ Implementado |
| RNF-15 | **Disponibilidad.** La plataforma debe estar desplegada en un servicio de hosting accesible por internet durante la etapa de validación y demostración. | ⏳ Pendiente |
| RNF-16 | **Tolerancia a fallos.** La plataforma debe seguir respondiendo cuando falle una instancia o un servicio: verificaciones de salud con reinicio automático de los contenedores que dejan de responder, dos réplicas de los servicios de autenticación, persona mayor y salud balanceadas por el gateway, circuit breaker por ruta con una respuesta amable, reintentos seguros, tiempos máximos de espera entre servicios y bloqueo de tareas programadas para que no se dupliquen con varias réplicas. Detalle en `ARQUITECTURA_DISPONIBILIDAD.md`. | ✅ Implementado |
| RNF-17 | **Privacidad.** Las vistas de mapa de la organización no deben mostrar la dirección exacta de las personas mayores, solo su barrio. | ✅ Implementado |
| RNF-18 | **Seguridad.** El inicio de sesión con contraseña debe bloquear la cuenta durante 15 minutos tras 5 contraseñas incorrectas seguidas, y el gateway debe limitar por IP las rutas públicas que se pueden abusar: inicio de sesión y recuperación de contraseña (10 por minuto), envío de códigos (5 cada 10 minutos y 20 al día), consulta de si un celular tiene cuenta (10 por minuto y 100 al día) y registro (30 por hora). Detalle en `ARQUITECTURA_DISPONIBILIDAD.md`. | ✅ Implementado |
| RNF-19 | **Seguridad.** Los códigos por SMS solo deben enviarse a celulares con una cuenta, respondiendo igual si el celular no la tiene, y como máximo 8 por celular al día. | ✅ Implementado |
| RNF-20 | **Seguridad.** El token de acceso debe durar 15 minutos y renovarse con un token de renovación que se guarde solo como hash y se pueda revocar: al cerrar sesión, al cambiar o restablecer la contraseña y al eliminar la cuenta. Las contraseñas nuevas deben tener mínimo 8 caracteres. Detalle en `ARQUITECTURA_DISPONIBILIDAD.md`. | ✅ Implementado |
| RNF-21 | **Seguridad.** El servidor web debe enviar cabeceras de seguridad: Content-Security-Policy (sin scripts en línea ni de otros dominios), HSTS, protección contra iframes, `nosniff`, `Referrer-Policy` y `Permissions-Policy`. | ✅ Implementado |
| RNF-22 | **Privacidad.** Los registros (logs) no deben llevar datos personales: ni celulares, ni el texto de los SMS, ni las consultas SQL en el servidor, ni partes de las claves. | ✅ Implementado |
| RNF-23 | **Seguridad.** Los contenedores deben correr con un usuario sin privilegios (salvo autoheal, que necesita el socket de Docker), y cada uno debe recibir solo las claves que usa. | ✅ Implementado |
| RNF-24 | **Privacidad.** Las fotos de perfil deben estar en almacenamiento privado y mostrarse solo a personas con sesión iniciada, de modo que un enlace compartido no las abra fuera de la plataforma. | ✅ Implementado |
| RNF-25 | **Privacidad.** Debe quedar un registro de quién consultó, creó, editó o borró los datos de salud de cada persona mayor, cuándo y si se le permitió (Ley 1581 de 2012). Detalle en `ARQUITECTURA_DISPONIBILIDAD.md`. | ✅ Implementado |
| RNF-26 | **Mantenibilidad.** Las dependencias deben revisarse automáticamente en busca de vulnerabilidades (Dependabot, revisión en cada pull request y `npm audit`), y las imágenes de Docker deben usar versiones exactas. | ✅ Implementado |
| RNF-27 | **Disponibilidad.** Debe haber una copia de seguridad diaria de la base de datos, propia e independiente del proveedor, y un procedimiento para probar que se puede restaurar. | ✅ Implementado |
| RNF-28 | **Seguridad.** Todas las tablas de la base deben tener Row Level Security activado, incluidas las que se creen después, para que la API REST automática de Supabase no deje leer ni cambiar datos sin pasar por el backend. Detalle en `ARQUITECTURA_DISPONIBILIDAD.md`. | ✅ Implementado |
| RNF-29 | **Seguridad.** Ninguna clave (contraseña de la base de datos, secreto del JWT ni claves de los servicios externos) debe estar en el código ni en el historial de git; se leen de archivos que no se suben. Toda clave que haya quedado en un commit debe cambiarse y revocarse en su proveedor. Detalle en `ARQUITECTURA_DISPONIBILIDAD.md`. | ✅ Implementado |
