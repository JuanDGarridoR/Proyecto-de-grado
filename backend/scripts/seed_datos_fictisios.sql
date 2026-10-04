-- VITA+ — Script de datos ficticios para pruebas y analítica
--
-- Genera:
--   - 5 organizaciones (con su cuenta de usuario)
--   - 200 personas mayores con fecha de nacimiento, género, dirección,
--     EPS e IPS, y un perfil de salud (hipertensión, diabetes, EPOC)
--   - 25 acompañantes y 25 voluntarios
--   - catálogo de gustos, talentos y pasatiempos + 3 a 6 por persona
--   - vínculos persona mayor <-> acompañante y persona mayor <->
--     organización (con estados PENDIENTE / ACEPTADA / RECHAZADA); las
--     solicitudes de acompañamiento pendientes van en los dos sentidos
--     (unas las envió la persona mayor y otras el acompañante)
--   - vínculos voluntario <-> organización (aceptados, y algunas
--     solicitudes pendientes)
--   - medicamentos coherentes con el perfil de salud de cada persona
--   - historial de signos vitales de los últimos 6 meses (~5.000
--     mediciones), con algunos valores fuera de rango y unos pocos
--     errores de digitación para probar los reportes
--   - citas médicas: cada persona tiene una próxima cita en las
--     siguientes 2 semanas (la mitad, otra más adelante) y un historial
--     de citas pasadas, con lugar y consultorio
--   - 48 actividades con inscripciones y asistencia
--   - actividades propuestas por voluntarios a sus organizaciones
--     (pendientes, aceptadas y rechazadas)
--   - notificaciones del panel (recordatorios de medicamentos, citas y
--     actividades, y alertas de emergencia)
--
-- Cómo correrlo:
--   - Todos los servicios apuntan a la misma base de datos Postgres en
--     Supabase, con los datos de conexión que están en el
--     application.properties de cualquiera de ellos
--     (spring.datasource.url/username/password).
--   - Las tablas las crea Hibernate al arrancar (ddl-auto=update): arranca
--     al menos una vez todos los servicios antes de correr este script.
--   - SQL Editor de Supabase, pgAdmin 4 o cualquier cliente: pega este
--     archivo completo y ejecútalo.
--   - psql: psql "<cadena de conexión>" -f seed_datos_fictisios.sql
--
-- Es seguro volver a correrlo: al inicio borra únicamente lo que este
-- mismo script generó antes (todo correo termina en @vitaplus.test, y
-- las organizaciones se reconocen por su cuenta org%@vitaplus.test), así
-- que nunca toca tus usuarios reales.
--
-- Contraseña de todas las cuentas ficticias: Vita2025*
--
-- SMS: los celulares ficticios usan el prefijo +570000, que no existe en
-- Colombia, para que ningún SMS llegue a una persona real. Aun así:
--   - las inscripciones a actividades y las citas médicas quedan con el
--     recordatorio marcado como enviado, así esos schedulers no mandan
--     nada;
--   - los medicamentos quedan con la próxima toma a 3-6 días en el
--     futuro. Cuando llegue esa fecha, si el backend está prendido, el
--     scheduler de medicamentos intentará enviar el SMS a esos números
--     inexistentes y TextBee fallará. Vuelve a correr el script para
--     correr las fechas otra vez hacia el futuro.
--   - El scheduler de cumpleaños (8 a. m.) felicita a todo el que cumpla
--     años ese día, y las cuentas ficticias tienen fechas de nacimiento
--     en todo el año: casi todos los días intentará enviar algún SMS a
--     estos números y TextBee fallará. No afecta nada más.
--   - Los celulares ficticios tampoco reciben el código OTP, así que
--     para entrar con estas cuentas usa correo y contraseña.
--
-- Los voluntarios no tienen una tabla que los conecte con personas
-- mayores (no existe en el modelo actual): quedan conectados de forma
-- indirecta a través de la organización a la que están vinculados
-- (tabla voluntario_organizacion).
--
-- Todas las fechas y horas se calculan en hora de Colombia
-- (America/Bogota), que es la que usan los servicios; la base de datos
-- de Supabase está en UTC.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Tablas auxiliares del script. Van en un esquema propio y no como
-- tablas temporales porque el SQL Editor de Supabase no mantiene la
-- transacción: una TEMP ... ON COMMIT DROP desaparecería al crearse. Se
-- borra al final (y aquí, por si una corrida anterior falló a medias).
DROP SCHEMA IF EXISTS seed_tmp CASCADE;
CREATE SCHEMA seed_tmp;

-- Reloj único para todo el script, en hora de Colombia.
CREATE TABLE seed_tmp.seed_reloj AS
SELECT date_trunc('minute', now() AT TIME ZONE 'America/Bogota') AS ahora,
       (now() AT TIME ZONE 'America/Bogota')::date                AS hoy;

-- 0. Limpieza idempotente (solo borra lo generado por este script)
-- voluntario_organizacion la crea voluntario-service al arrancar; se
-- crea aquí igual por si esta base de datos nunca lo ha arrancado.
CREATE TABLE IF NOT EXISTS voluntario_organizacion (
    id_voluntario   integer      NOT NULL,
    id_organizacion integer      NOT NULL,
    estado          varchar(255) NOT NULL,
    PRIMARY KEY (id_organizacion, id_voluntario)
);

-- Columnas de las propuestas de actividades de voluntarios; las agrega
-- actividad-service al arrancar, y aquí igual por la misma razón.
ALTER TABLE actividad ADD COLUMN IF NOT EXISTS id_voluntario integer;

-- Estatura de los signos vitales; la agrega salud-service al arrancar.
ALTER TABLE signo_vital ADD COLUMN IF NOT EXISTS estatura double precision;
ALTER TABLE actividad ADD COLUMN IF NOT EXISTS estado varchar(255);

-- Quién envió la solicitud de acompañamiento; la agregan
-- persona-mayor-service y acompanante-service al arrancar.
ALTER TABLE persona_mayor_acompanante ADD COLUMN IF NOT EXISTS solicitada_por varchar(255);

-- Recordatorios de citas médicas ya enviados; los agrega salud-service al
-- arrancar.
ALTER TABLE cita_medica ADD COLUMN IF NOT EXISTS recordatorio_dia_enviado_para timestamp;
ALTER TABLE cita_medica ADD COLUMN IF NOT EXISTS recordatorio_hora_enviado_para timestamp;
ALTER TABLE cita_medica ADD COLUMN IF NOT EXISTS consultorio varchar(255);

CREATE TABLE seed_tmp.seed_usuarios_viejos AS
SELECT id_usuario, celular FROM usuario WHERE correo LIKE '%@vitaplus.test';

CREATE TABLE seed_tmp.seed_orgs_viejas AS
SELECT DISTINCT id_organizacion FROM usuario
 WHERE correo LIKE 'org%@vitaplus.test' AND id_organizacion IS NOT NULL;

DELETE FROM notificacion
 WHERE celular IN (SELECT celular FROM seed_tmp.seed_usuarios_viejos WHERE celular IS NOT NULL);

DELETE FROM participacion
 WHERE id_persona_mayor IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos)
    OR id_actividad IN (SELECT id_actividad FROM actividad
                         WHERE id_organizacion IN (SELECT id_organizacion FROM seed_tmp.seed_orgs_viejas));

DELETE FROM actividad
 WHERE id_organizacion IN (SELECT id_organizacion FROM seed_tmp.seed_orgs_viejas);

DELETE FROM cita_medica
 WHERE id_persona_mayor IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);

DELETE FROM signo_vital
 WHERE id_persona_mayor IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);

DELETE FROM medicamento
 WHERE id_persona_mayor IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);

DELETE FROM persona_mayor_gusto
 WHERE id_persona_mayor IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);

DELETE FROM persona_mayor_organizacion
 WHERE id_persona_mayor IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos)
    OR id_organizacion IN (SELECT id_organizacion FROM seed_tmp.seed_orgs_viejas);

DELETE FROM persona_mayor_acompanante
 WHERE id_persona_mayor IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos)
    OR id_acompanante IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);

DELETE FROM voluntario_organizacion
 WHERE id_voluntario IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos)
    OR id_organizacion IN (SELECT id_organizacion FROM seed_tmp.seed_orgs_viejas);

DELETE FROM usuario_rol    WHERE id_usuario IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);
DELETE FROM voluntario     WHERE id_usuario IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);
DELETE FROM acompanante    WHERE id_usuario IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);
DELETE FROM persona_mayor  WHERE id_usuario IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);
DELETE FROM usuario        WHERE id_usuario IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);
DELETE FROM organizacion   WHERE id_organizacion IN (SELECT id_organizacion FROM seed_tmp.seed_orgs_viejas);

-- 1. Roles base (por si esta base de datos nunca ha arrancado el backend)
INSERT INTO rol (nombre)
SELECT r FROM (VALUES ('ORGANIZACION'), ('VOLUNTARIO'), ('ACOMPANANTE'), ('PERSONA_MAYOR')) AS v(r)
ON CONFLICT (nombre) DO NOTHING;

-- 2. Organizaciones ficticias (+ su cuenta de usuario, como lo hace el
--    registro real: la organización inicia sesión con su correo)
WITH datos_org AS (
    SELECT * FROM (VALUES
      ('Fundación Entrenubes Usme',                     'Calle 91 Sur # 3-15, Usme Centro',      'org01@vitaplus.test'),
      ('Hogar de Paso San Rafael',                       'Carrera 4 Este # 76-20 Sur, Yomasa',    'org02@vitaplus.test'),
      ('Corporación Vida Plena Usme',                    'Calle 73 Sur # 14-30, Santa Librada',   'org03@vitaplus.test'),
      ('Centro Día Renacer',                             'Diagonal 68 Sur # 1-45 Este, La Flora', 'org04@vitaplus.test'),
      ('Parroquia Nuestra Señora de la Esperanza - Usme','Calle 89 Sur # 5-10, Comuneros',        'org05@vitaplus.test')
    ) AS v(nombre, direccion, correo)
),
ins_org AS (
    INSERT INTO organizacion (nombre, direccion)
    SELECT nombre, direccion FROM datos_org
    RETURNING id_organizacion, nombre
),
ins_usuario AS (
    INSERT INTO usuario (nombre_usuario, contrasena_hash, correo, direccion, id_organizacion, activo, fecha_creacion)
    SELECT d.nombre, crypt('Vita2025*', gen_salt('bf', 10)), d.correo, d.direccion, o.id_organizacion, true,
           r.ahora - make_interval(days => 300 + floor(random() * 300)::int)
    FROM ins_org o
    JOIN datos_org d ON d.nombre = o.nombre
    CROSS JOIN seed_tmp.seed_reloj r
    RETURNING id_usuario
)
INSERT INTO usuario_rol (id_usuario, id_rol)
SELECT iu.id_usuario, r.id_rol
FROM ins_usuario iu, rol r
WHERE r.nombre = 'ORGANIZACION';

-- 3. Catálogo de gustos, talentos y pasatiempos (las 3 categorías que
--    muestra la pantalla "Intereses"). Si un nombre ya existe con una
--    categoría vieja que el frontend ya no muestra (MUSICA, ARTE...), se
--    le corrige; si ya tiene una categoría vigente, no se toca.
INSERT INTO gusto (nombre, categoria)
VALUES
 ('Boleros', 'GUSTO'),
 ('Música ranchera', 'GUSTO'),
 ('Música carranguera', 'GUSTO'),
 ('Novelas de televisión', 'GUSTO'),
 ('Películas clásicas', 'GUSTO'),
 ('Fútbol por televisión', 'GUSTO'),
 ('Tomar café con amigos', 'GUSTO'),
 ('Misa dominical', 'GUSTO'),
 ('Tejido', 'TALENTO'),
 ('Bordado', 'TALENTO'),
 ('Costura', 'TALENTO'),
 ('Cocina tradicional', 'TALENTO'),
 ('Repostería', 'TALENTO'),
 ('Carpintería', 'TALENTO'),
 ('Canto', 'TALENTO'),
 ('Contar historias', 'TALENTO'),
 ('Jardinería', 'HOBBY'),
 ('Caminatas', 'HOBBY'),
 ('Juegos de mesa', 'HOBBY'),
 ('Bingo', 'HOBBY'),
 ('Parqués', 'HOBBY'),
 ('Crucigramas', 'HOBBY'),
 ('Lectura', 'HOBBY'),
 ('Baile', 'HOBBY'),
 ('Pintura', 'HOBBY'),
 ('Tejo', 'HOBBY')
ON CONFLICT (nombre) DO UPDATE
   SET categoria = EXCLUDED.categoria
 WHERE gusto.categoria IS NULL OR gusto.categoria NOT IN ('GUSTO', 'TALENTO', 'HOBBY');

-- 4. 200 personas mayores (usuario + persona_mayor con EPS e IPS)
WITH datos AS (
    SELECT
        i,
        random() AS r_genero,
        random() AS r_eps,
        (ARRAY['María','Rosa','Carmen','Luz','Ana','Blanca','Gloria','Esperanza','Cecilia','Teresa',
               'Marleny','Consuelo','Amparo','Stella','Nubia','Yolanda','Beatriz','Marina','Isabel','Elvia'])
            [1 + floor(random() * 20)::int] AS nombre_f,
        (ARRAY['José','Luis','Carlos','Jorge','Pedro','Manuel','Roberto','Alberto','Guillermo','Rafael',
               'Hernando','Gustavo','Eduardo','Fernando','Ricardo','Julio','Antonio','Miguel','Alfonso','Arturo'])
            [1 + floor(random() * 20)::int] AS nombre_m,
        (ARRAY['González','Rodríguez','Martínez','López','García','Pérez','Sánchez','Ramírez','Torres',
               'Rivera','Gómez','Díaz','Reyes','Cruz','Morales','Ortiz','Gutiérrez','Chaparro','Vargas',
               'Suárez','Castañeda','Bautista','Cárdenas','Rincón'])
            [1 + floor(random() * 24)::int] AS apellido1,
        (ARRAY['González','Rodríguez','Martínez','López','García','Pérez','Sánchez','Ramírez','Torres',
               'Rivera','Gómez','Díaz','Reyes','Cruz','Morales','Ortiz','Gutiérrez','Chaparro','Vargas',
               'Suárez','Castañeda','Bautista','Cárdenas','Rincón'])
            [1 + floor(random() * 24)::int] AS apellido2,
        (ARRAY['Usme Centro','La Flora','Yomasa','Danubio Azul','Santa Librada','Los Soches','El Uval',
               'Comuneros','Alfonso López','La Aurora','Arrayanes','El Destino','Tocaimita','La Requilina'])
            [1 + floor(random() * 14)::int] AS barrio,
        (ARRAY['Calle','Carrera','Diagonal','Transversal'])[1 + floor(random() * 4)::int]
            || ' ' || (60 + floor(random() * 80)::int) || ' Sur # '
            || (1 + floor(random() * 20)::int) || '-' || (2 + floor(random() * 80)::int) AS via,
        -- Edades de 60 a 95 años, con más personas en los 60 y 70
        (60 + floor(power(random(), 1.6) * 36))::int AS edad,
        floor(random() * 365)::int AS dias_extra
    FROM generate_series(1, 200) AS s(i)
),
filas AS (
    SELECT
        d.i,
        CASE WHEN d.r_genero < 0.57 THEN 'Femenino'
             WHEN d.r_genero < 0.98 THEN 'Masculino'
             ELSE 'Otro' END AS genero,
        (CASE WHEN d.r_genero < 0.57 OR (d.r_genero >= 0.98 AND d.i % 2 = 0) THEN d.nombre_f ELSE d.nombre_m END
            || ' ' || d.apellido1 || ' ' || d.apellido2) AS nombre_usuario,
        (r.hoy - make_interval(years => d.edad, days => d.dias_extra))::date AS fecha_nacimiento,
        d.via || ', ' || d.barrio AS direccion,
        -- Distribución aproximada de afiliación en Usme (régimen subsidiado
        -- sobre todo en Capital Salud); ~4% sin EPS registrada.
        CASE WHEN d.r_eps < 0.30 THEN 'Capital Salud'
             WHEN d.r_eps < 0.48 THEN 'Nueva EPS'
             WHEN d.r_eps < 0.63 THEN 'Compensar'
             WHEN d.r_eps < 0.75 THEN 'Famisanar'
             WHEN d.r_eps < 0.85 THEN 'Sanitas'
             WHEN d.r_eps < 0.92 THEN 'Salud Total'
             WHEN d.r_eps < 0.96 THEN 'Sura'
             ELSE NULL END AS eps,
        'pm' || lpad(d.i::text, 3, '0') || '@vitaplus.test' AS correo,
        '+5700001' || lpad(d.i::text, 5, '0') AS celular
    FROM datos d CROSS JOIN seed_tmp.seed_reloj r
),
con_ips AS (
    SELECT f.*,
           CASE f.eps
             WHEN 'Capital Salud' THEN (ARRAY['USS Usme - Subred Sur','USS Santa Librada - Subred Sur',
                                              'USS Betania - Subred Sur','USS Marichuela - Subred Sur',
                                              'Hospital de Meissen'])[1 + floor(random() * 5)::int]
             WHEN 'Nueva EPS'     THEN (ARRAY['Virrey Solís IPS Santa Librada','Virrey Solís IPS Kennedy',
                                              'Hospital El Tunal'])[1 + floor(random() * 3)::int]
             WHEN 'Compensar'     THEN (ARRAY['Compensar Sede Usme','Compensar Sede Kennedy'])[1 + floor(random() * 2)::int]
             WHEN 'Famisanar'     THEN (ARRAY['Cafam Santa Librada','Colsubsidio IPS Restrepo'])[1 + floor(random() * 2)::int]
             WHEN 'Sanitas'       THEN (ARRAY['Centro Médico Colsanitas Sur','Clínica Sanitas Tintal'])[1 + floor(random() * 2)::int]
             WHEN 'Salud Total'   THEN 'Virrey Solís IPS Santa Librada'
             WHEN 'Sura'          THEN 'IPS Sura Calle 100'
             ELSE NULL END AS ips
    FROM filas f
),
ins_usuario AS (
    INSERT INTO usuario (nombre_usuario, contrasena_hash, correo, celular, fecha_nacimiento, genero,
                         direccion, activo, fecha_creacion)
    SELECT c.nombre_usuario, crypt('Vita2025*', gen_salt('bf', 10)), c.correo, c.celular,
           c.fecha_nacimiento, c.genero, c.direccion,
           random() >= 0.03,  -- ~3% de cuentas desactivadas
           r.ahora - make_interval(days => floor(random() * 400)::int)
    FROM con_ips c CROSS JOIN seed_tmp.seed_reloj r
    RETURNING id_usuario, correo
)
INSERT INTO persona_mayor (id_usuario, eps, ips)
SELECT u.id_usuario, c.eps, c.ips
FROM ins_usuario u JOIN con_ips c ON c.correo = u.correo;

-- 5. 25 acompañantes (usuario + acompanante)
WITH datos AS (
    SELECT
        i,
        floor(random() * 20)::int AS idx_nombre,
        (ARRAY['González','Rodríguez','Martínez','López','García','Pérez','Sánchez','Ramírez','Torres',
               'Rivera','Gómez','Díaz','Reyes','Cruz','Morales','Ortiz','Gutiérrez','Chaparro','Vargas',
               'Suárez','Castañeda','Bautista','Cárdenas','Rincón'])
            [1 + floor(random() * 24)::int] AS apellido1,
        (ARRAY['González','Rodríguez','Martínez','López','García','Pérez','Sánchez','Ramírez','Torres',
               'Rivera','Gómez','Díaz','Reyes','Cruz','Morales','Ortiz','Gutiérrez','Chaparro','Vargas',
               'Suárez','Castañeda','Bautista','Cárdenas','Rincón'])
            [1 + floor(random() * 24)::int] AS apellido2,
        (ARRAY['Usme Centro','La Flora','Yomasa','Danubio Azul','Santa Librada','El Uval','Comuneros',
               'Alfonso López','Tunjuelito','Kennedy'])[1 + floor(random() * 10)::int] AS barrio,
        (ARRAY['Hijo/a', 'Hijo/a', 'Hijo/a', 'Nieto/a', 'Nieto/a', 'Sobrino/a', 'Cuidador/a contratado',
               'Hermano/a', 'Vecino/a de confianza'])[1 + floor(random() * 9)::int] AS relacion,
        (25 + floor(random() * 41))::int AS edad
    FROM generate_series(1, 25) AS s(i)
),
filas AS (
    SELECT d.i, d.relacion,
           -- Los primeros 10 nombres son femeninos y los últimos 10 masculinos
           CASE WHEN d.idx_nombre < 10 THEN 'Femenino' ELSE 'Masculino' END AS genero,
           (ARRAY['María','Rosa','Sandra','Luz','Ana','Diana','Patricia','Andrea','Camila','Daniela',
                  'José','Luis','Carlos','Jorge','Pedro','Julián','David','Andrés','Felipe','Óscar'])
               [1 + d.idx_nombre] || ' ' || d.apellido1 || ' ' || d.apellido2 AS nombre_usuario,
           (r.hoy - make_interval(years => d.edad, days => floor(random() * 365)::int))::date AS fecha_nacimiento,
           'Calle ' || (40 + floor(random() * 90)::int) || ' Sur # ' || (1 + floor(random() * 30)::int)
               || '-' || (2 + floor(random() * 80)::int) || ', ' || d.barrio AS direccion,
           'acomp' || lpad(d.i::text, 3, '0') || '@vitaplus.test' AS correo,
           '+5700002' || lpad(d.i::text, 5, '0') AS celular
    FROM datos d CROSS JOIN seed_tmp.seed_reloj r
),
ins_usuario AS (
    INSERT INTO usuario (nombre_usuario, contrasena_hash, correo, celular, fecha_nacimiento, genero,
                         direccion, activo, fecha_creacion)
    SELECT f.nombre_usuario, crypt('Vita2025*', gen_salt('bf', 10)), f.correo, f.celular,
           f.fecha_nacimiento, f.genero, f.direccion, true,
           r.ahora - make_interval(days => floor(random() * 400)::int)
    FROM filas f CROSS JOIN seed_tmp.seed_reloj r
    RETURNING id_usuario, correo
)
INSERT INTO acompanante (id_usuario, relacion)
SELECT u.id_usuario, f.relacion
FROM ins_usuario u JOIN filas f ON f.correo = u.correo;

-- 6. 25 voluntarios (usuario + voluntario), cada uno vinculado a una de
--    las 5 organizaciones ficticias (voluntario_organizacion, ACEPTADA).
--    El usuario no lleva id_organizacion: esa columna es solo para las
--    cuentas de las organizaciones.
WITH orgs AS (
    SELECT id_organizacion, row_number() OVER (ORDER BY id_organizacion) AS rn
    FROM usuario WHERE correo LIKE 'org%@vitaplus.test'
),
n_org AS (SELECT count(*) AS n FROM orgs),
datos AS (
    SELECT
        s.i,
        floor(random() * 20)::int AS idx_nombre,
        (ARRAY['González','Rodríguez','Martínez','López','García','Pérez','Sánchez','Ramírez','Torres',
               'Rivera','Gómez','Díaz','Reyes','Cruz','Morales','Ortiz','Gutiérrez','Chaparro','Vargas',
               'Suárez','Castañeda','Bautista','Cárdenas','Rincón'])
            [1 + floor(random() * 24)::int] AS apellido1,
        (ARRAY['González','Rodríguez','Martínez','López','García','Pérez','Sánchez','Ramírez','Torres',
               'Rivera','Gómez','Díaz','Reyes','Cruz','Morales','Ortiz','Gutiérrez','Chaparro','Vargas',
               'Suárez','Castañeda','Bautista','Cárdenas','Rincón'])
            [1 + floor(random() * 24)::int] AS apellido2,
        (18 + floor(random() * 50))::int AS edad,
        o.id_organizacion
    FROM generate_series(1, 25) AS s(i)
    CROSS JOIN n_org
    JOIN orgs o ON o.rn = 1 + ((s.i - 1) % n_org.n)
),
filas AS (
    SELECT d.i, d.id_organizacion,
           CASE WHEN d.idx_nombre < 10 THEN 'Femenino' ELSE 'Masculino' END AS genero,
           (ARRAY['Laura','Valentina','Sandra','Natalia','Ana','Diana','Paola','Andrea','Camila','Daniela',
                  'Sebastián','Luis','Carlos','Mateo','Santiago','Julián','David','Andrés','Felipe','Nicolás'])
               [1 + d.idx_nombre] || ' ' || d.apellido1 || ' ' || d.apellido2 AS nombre_usuario,
           (r.hoy - make_interval(years => d.edad, days => floor(random() * 365)::int))::date AS fecha_nacimiento,
           'vol' || lpad(d.i::text, 3, '0') || '@vitaplus.test' AS correo,
           '+5700003' || lpad(d.i::text, 5, '0') AS celular
    FROM datos d CROSS JOIN seed_tmp.seed_reloj r
),
ins_usuario AS (
    INSERT INTO usuario (nombre_usuario, contrasena_hash, correo, celular, fecha_nacimiento, genero,
                         activo, fecha_creacion)
    SELECT f.nombre_usuario, crypt('Vita2025*', gen_salt('bf', 10)), f.correo, f.celular,
           f.fecha_nacimiento, f.genero, true,
           r.ahora - make_interval(days => floor(random() * 400)::int)
    FROM filas f CROSS JOIN seed_tmp.seed_reloj r
    RETURNING id_usuario, correo
),
ins_voluntario AS (
    INSERT INTO voluntario (id_usuario)
    SELECT u.id_usuario
    FROM ins_usuario u
)
INSERT INTO voluntario_organizacion (id_voluntario, id_organizacion, estado)
SELECT u.id_usuario, f.id_organizacion, 'ACEPTADA'
FROM ins_usuario u JOIN filas f ON f.correo = u.correo;

-- Los 5 primeros voluntarios también tienen una solicitud PENDIENTE con
-- la organización siguiente, para ver solicitudes en el panel.
WITH orgs AS (
    SELECT id_organizacion, row_number() OVER (ORDER BY id_organizacion) AS rn
    FROM usuario WHERE correo LIKE 'org%@vitaplus.test'
),
n_org AS (SELECT count(*) AS n FROM orgs),
vols AS (
    SELECT id_usuario, row_number() OVER (ORDER BY correo) AS i
    FROM usuario WHERE correo LIKE 'vol%@vitaplus.test'
)
INSERT INTO voluntario_organizacion (id_voluntario, id_organizacion, estado)
SELECT v.id_usuario, o.id_organizacion, 'PENDIENTE'
FROM vols v
CROSS JOIN n_org
JOIN orgs o ON o.rn = 1 + (v.i % n_org.n)
WHERE v.i <= 5
ON CONFLICT DO NOTHING;

-- 7. Roles de las 250 cuentas de personas
INSERT INTO usuario_rol (id_usuario, id_rol)
SELECT u.id_usuario, r.id_rol
FROM usuario u JOIN rol r ON r.nombre = 'PERSONA_MAYOR'
WHERE u.correo LIKE 'pm%@vitaplus.test'
ON CONFLICT DO NOTHING;

INSERT INTO usuario_rol (id_usuario, id_rol)
SELECT u.id_usuario, r.id_rol
FROM usuario u JOIN rol r ON r.nombre = 'ACOMPANANTE'
WHERE u.correo LIKE 'acomp%@vitaplus.test'
ON CONFLICT DO NOTHING;

INSERT INTO usuario_rol (id_usuario, id_rol)
SELECT u.id_usuario, r.id_rol
FROM usuario u JOIN rol r ON r.nombre = 'VOLUNTARIO'
WHERE u.correo LIKE 'vol%@vitaplus.test'
ON CONFLICT DO NOTHING;

-- 8. Vínculo persona mayor - acompañante. Cada persona mayor tiene un
--    acompañante principal (85% ACEPTADA) y ~30% tiene además un
--    segundo acompañante (70% ACEPTADA), para probar personas con
--    varios contactos.
--    solicitada_por: las aceptadas las envió la persona mayor; de las
--    pendientes, ~40% las envió el acompañante (la persona mayor las ve
--    para aceptar o rechazar) y el resto la persona mayor.
WITH pm AS (
    SELECT id_usuario, row_number() OVER (ORDER BY id_usuario) AS rn
    FROM usuario WHERE correo LIKE 'pm%@vitaplus.test'
),
ac AS (
    SELECT id_usuario, row_number() OVER (ORDER BY id_usuario) AS rn
    FROM usuario WHERE correo LIKE 'acomp%@vitaplus.test'
),
n_ac AS (SELECT count(*) AS n FROM ac),
vinculos AS (
    -- principal
    SELECT pm.id_usuario AS id_persona_mayor, ac.id_usuario AS id_acompanante, 0.85 AS p_aceptada
    FROM pm CROSS JOIN n_ac
    JOIN ac ON ac.rn = 1 + ((pm.rn - 1) % n_ac.n)
    UNION ALL
    -- segundo acompañante, desplazado 7 posiciones para que no coincida
    SELECT pm.id_usuario, ac.id_usuario, 0.70
    FROM pm CROSS JOIN n_ac
    JOIN ac ON ac.rn = 1 + ((pm.rn + 6) % n_ac.n)
    WHERE random() < 0.30
),
con_estado AS (
    SELECT id_persona_mayor, id_acompanante,
           CASE WHEN random() < p_aceptada THEN 'ACEPTADA' ELSE 'PENDIENTE' END AS estado
    FROM vinculos
)
INSERT INTO persona_mayor_acompanante (id_persona_mayor, id_acompanante, estado, solicitada_por)
SELECT id_persona_mayor, id_acompanante, estado,
       CASE WHEN estado = 'PENDIENTE' AND random() < 0.40 THEN 'ACOMPANANTE'
            ELSE 'PERSONA_MAYOR' END
FROM con_estado
ON CONFLICT DO NOTHING;

-- 9. Vínculo persona mayor - organización. Cada persona mayor queda con
--    una organización principal (75% ACEPTADA, 15% PENDIENTE, 10%
--    RECHAZADA; el botón de emergencia solo notifica organizaciones
--    ACEPTADAS) y ~12% además con una segunda organización.
WITH pm AS (
    SELECT id_usuario, row_number() OVER (ORDER BY id_usuario) AS rn
    FROM usuario WHERE correo LIKE 'pm%@vitaplus.test'
),
orgs AS (
    SELECT id_organizacion, row_number() OVER (ORDER BY id_organizacion) AS rn
    FROM usuario WHERE correo LIKE 'org%@vitaplus.test'
),
n_org AS (SELECT count(*) AS n FROM orgs),
-- r se calcula una sola vez por fila (no dentro del CASE de abajo):
-- cada llamado a random() da un valor distinto y los porcentajes no
-- serían los que dicen los comentarios.
asignaciones AS (
    SELECT pm.id_usuario AS id_persona_mayor, o.id_organizacion, random() AS r
    FROM pm CROSS JOIN n_org
    JOIN orgs o ON o.rn = 1 + ((pm.rn - 1) % n_org.n)
    UNION ALL
    SELECT pm.id_usuario, o.id_organizacion, random() * 0.9  -- la segunda nunca queda RECHAZADA
    FROM pm CROSS JOIN n_org
    JOIN orgs o ON o.rn = 1 + ((pm.rn + 1) % n_org.n)
    WHERE random() < 0.12
)
INSERT INTO persona_mayor_organizacion (id_persona_mayor, id_organizacion, estado)
SELECT id_persona_mayor, id_organizacion,
       CASE WHEN r < 0.75 THEN 'ACEPTADA'
            WHEN r < 0.90 THEN 'PENDIENTE'
            ELSE 'RECHAZADA' END
FROM asignaciones
ON CONFLICT DO NOTHING;

-- 10. Entre 3 y 6 gustos/talentos/pasatiempos por persona mayor (solo
--     de las categorías que muestra el frontend)
WITH gusto_ids AS (
    SELECT array_agg(id_gusto) AS ids FROM gusto
    WHERE categoria IN ('GUSTO', 'TALENTO', 'HOBBY')
),
pm AS (
    -- n_gustos va como columna (y no dentro de generate_series) para que
    -- varíe fila a fila.
    SELECT id_usuario, 3 + floor(random() * 4)::int AS n_gustos
    FROM usuario WHERE correo LIKE 'pm%@vitaplus.test'
),
asignaciones AS (
    SELECT pm.id_usuario AS id_persona_mayor,
           g.ids[1 + floor(random() * array_length(g.ids, 1))::int] AS id_gusto
    FROM pm
    CROSS JOIN gusto_ids g
    CROSS JOIN LATERAL generate_series(1, pm.n_gustos) AS k
)
INSERT INTO persona_mayor_gusto (id_persona_mayor, id_gusto)
SELECT DISTINCT id_persona_mayor, id_gusto FROM asignaciones
ON CONFLICT DO NOTHING;

-- 11. Perfil de salud de cada persona mayor (tabla temporal, no se
--     guarda). Sirve para que medicamentos, signos vitales y citas sean
--     coherentes entre sí: quien es hipertenso tiene la presión más alta,
--     toma antihipertensivos y va a control de hipertensión.
--     Prevalencias aproximadas en mayores de 60 años en Colombia.
CREATE TABLE seed_tmp.seed_perfil AS
WITH base AS (
    SELECT u.id_usuario, u.nombre_usuario, u.genero, pm.ips,
           extract(year FROM age(r.hoy, u.fecha_nacimiento))::int AS edad,
           random() < 0.45 AS hta,
           random() < 0.22 AS dm,
           random() < 0.08 AS epoc,
           random() AS r_signos
    FROM usuario u
    JOIN persona_mayor pm ON pm.id_usuario = u.id_usuario
    CROSS JOIN seed_tmp.seed_reloj r
    WHERE u.correo LIKE 'pm%@vitaplus.test'
)
SELECT b.*,
       -- Valores "habituales" de cada persona, alrededor de los cuales
       -- varían sus mediciones
       CASE WHEN b.hta THEN 136 + random() * 16 ELSE 110 + random() * 18 END AS base_sis,
       CASE WHEN b.hta THEN 82 + random() * 8   ELSE 68 + random() * 12 END AS base_dia,
       66 + random() * 18                                                      AS base_fc,
       CASE WHEN b.epoc THEN 91 + random() * 3  ELSE 95 + random() * 3 END   AS base_spo2,
       CASE WHEN b.genero = 'Masculino' THEN 60 + random() * 25 ELSE 50 + random() * 25 END AS base_peso,
       CASE WHEN b.genero = 'Masculino' THEN 158 + random() * 20 ELSE 147 + random() * 18 END AS base_estatura,
       -- ~85% registra signos vitales; quienes tienen alguna condición
       -- se miden más seguido
       b.r_signos < 0.85 AS con_signos,
       CASE WHEN b.hta OR b.dm OR b.epoc THEN 25 + floor(random() * 30)::int
            ELSE 8 + floor(random() * 20)::int END AS n_signos,
       CASE WHEN random() < 0.4 THEN 2 ELSE 1 END AS n_hta_meds,
       CASE WHEN random() < 0.3 THEN 2 ELSE 1 END AS n_dm_meds,
       floor(random() * 3)::int                    AS n_otros_meds,
       3 + floor(random() * 5)::int                AS n_citas
FROM base b;

-- 12. Medicamentos según el perfil de salud.
--     proxima_toma queda en el futuro (3 a 6 días) a propósito: ver la
--     nota de SMS al inicio del archivo.
WITH catalogo AS (
    SELECT * FROM (VALUES
        ('hta',     'Losartán',                '50 mg',              24, '08:00'::time),
        ('hta',     'Enalapril',               '10 mg',              12, '08:00'::time),
        ('hta',     'Amlodipino',              '5 mg',               24, '08:00'::time),
        ('hta',     'Hidroclorotiazida',       '25 mg',              24, '07:00'::time),
        ('dm',      'Metformina',              '850 mg',             12, '07:00'::time),
        ('dm',      'Insulina glargina',       '20 UI',              24, '21:00'::time),
        ('dm',      'Glibenclamida',           '5 mg',               24, '07:30'::time),
        ('epoc',    'Salbutamol inhalador',    '2 inhalaciones',      8, '06:00'::time),
        ('epoc',    'Bromuro de ipratropio',   '2 inhalaciones',     12, '08:00'::time),
        ('general', 'Atorvastatina',           '20 mg',              24, '21:00'::time),
        ('general', 'Omeprazol',               '20 mg',              24, '06:30'::time),
        ('general', 'Ácido acetilsalicílico',  '100 mg',             24, '08:00'::time),
        ('general', 'Levotiroxina',            '50 mcg',             24, '06:00'::time),
        ('general', 'Calcio + Vitamina D',     '1 tableta',          24, '13:00'::time),
        ('general', 'Acetaminofén',            '500 mg',              8, '06:00'::time),
        ('general', 'Sertralina',              '50 mg',              24, '08:00'::time),
        ('general', 'Tamsulosina',             '0.4 mg',             24, '20:00'::time)
    ) AS v(condicion, nombre, dosis, intervalo_horas, hora)
),
candidatos AS (
    SELECT p.id_usuario, p.n_hta_meds, p.n_dm_meds, p.n_otros_meds,
           c.condicion, c.nombre, c.dosis, c.intervalo_horas, c.hora,
           row_number() OVER (PARTITION BY p.id_usuario, c.condicion ORDER BY random()) AS rn
    FROM seed_tmp.seed_perfil p
    JOIN catalogo c
      ON (c.condicion = 'hta'  AND p.hta)
      OR (c.condicion = 'dm'   AND p.dm)
      OR (c.condicion = 'epoc' AND p.epoc)
      OR  c.condicion = 'general'
),
elegidos AS (
    SELECT *, random() AS r_fin, random() AS r_activo,
           3 + floor(random() * 4)::int AS dias_proxima
    FROM candidatos
    WHERE (condicion = 'hta'     AND rn <= n_hta_meds)
       OR (condicion = 'dm'      AND rn <= n_dm_meds)
       OR (condicion = 'epoc'    AND rn <= 1)
       OR (condicion = 'general' AND rn <= n_otros_meds)
)
INSERT INTO medicamento (id_persona_mayor, nombre, dosis, frecuencia, intervalo_horas, hora,
                         fecha_inicio, fecha_fin, proxima_toma, ultima_toma, activo)
SELECT e.id_usuario, e.nombre, e.dosis,
       'Cada ' || e.intervalo_horas || ' horas',
       e.intervalo_horas, e.hora,
       r.hoy - (30 + floor(random() * 330)::int),
       -- Los crónicos (hta, dm, epoc) no tienen fecha de fin; ~25% de los
       -- "generales" son tratamientos con fin, unos ya terminados.
       CASE WHEN e.condicion = 'general' AND e.r_fin < 0.25
            THEN r.hoy + (floor(random() * 60)::int - 20)
            ELSE NULL END,
       (r.hoy + e.dias_proxima) + e.hora,
       (r.hoy - 1) + e.hora,
       e.r_activo >= 0.10   -- ~10% suspendidos
FROM elegidos e CROSS JOIN seed_tmp.seed_reloj r;

-- 13. Historial de signos vitales (últimos 6 meses).
--     - Cada medición varía alrededor de los valores habituales de la
--       persona (hipertensos con la presión alta, EPOC con la saturación
--       baja), así que hay valores normales y otros "para revisar".
--     - La primera medición de cada persona es de los últimos 3 días,
--       para que los paneles muestren una "última medición" reciente.
--     - No todas las mediciones traen todos los campos (como pasa en la
--       vida real): presión y pulso casi siempre, peso pocas veces. La
--       estatura se toma junto con el peso, para poder calcular el IMC.
--     - ~1.5% trae un error de digitación imposible (sistólica de 1300,
--       saturación de 9, temperatura de 3.6) para probar cómo los
--       reportes marcan "posible error de registro".
WITH mediciones AS (
    SELECT p.*, k,
           CASE WHEN k = 1
                THEN r.ahora - make_interval(hours => 2 + floor(random() * 70)::int,
                                             mins  => floor(random() * 60)::int)
                ELSE (r.hoy - (1 + floor(random() * 180)::int)) + time '06:30'
                     + make_interval(mins => floor(random() * 780)::int)
           END AS fecha_hora,
           random() AS r_error,
           random() AS r_presion, random() AS r_temp, random() AS r_spo2,
           random() AS r_fr, random() AS r_peso, random() AS r_obs
    FROM seed_tmp.seed_perfil p
    CROSS JOIN seed_tmp.seed_reloj r
    CROSS JOIN LATERAL generate_series(1, p.n_signos) AS k
    WHERE p.con_signos
),
valores AS (
    SELECT m.id_usuario, m.fecha_hora, m.r_error, m.r_obs,
           CASE WHEN m.r_presion < 0.92 THEN round(m.base_sis + (random() - 0.5) * 26)::int END AS sis,
           CASE WHEN m.r_presion < 0.92 THEN round(m.base_dia + (random() - 0.5) * 14)::int END AS dia,
           round(m.base_fc + (random() - 0.5) * 18 + CASE WHEN random() < 0.04 THEN 25 ELSE 0 END)::int AS fc,
           CASE WHEN m.r_temp < 0.60
                THEN round((36.1 + random() * 0.9 + CASE WHEN random() < 0.04 THEN 1.4 ELSE 0 END)::numeric, 1)
           END AS temp,
           CASE WHEN m.r_spo2 < 0.75
                THEN least(100, round(m.base_spo2 + (random() - 0.5) * 4))::int
           END AS spo2,
           CASE WHEN m.r_fr < 0.30
                THEN (14 + floor(random() * 7) + CASE WHEN m.epoc THEN 4 ELSE 0 END)::int
           END AS fr,
           CASE WHEN m.r_peso < 0.25
                THEN round((m.base_peso + (random() - 0.5) * 3)::numeric, 1)
           END AS peso,
           CASE WHEN m.r_peso < 0.25
                THEN round(m.base_estatura::numeric, 1)
           END AS estatura
    FROM mediciones m
)
INSERT INTO signo_vital (id_persona_mayor, fecha_hora, presion_sistolica, presion_diastolica,
                         frecuencia_cardiaca, temperatura, saturacion_oxigeno,
                         frecuencia_respiratoria, peso, estatura, observaciones)
SELECT v.id_usuario, v.fecha_hora,
       CASE WHEN v.r_error < 0.005 AND v.sis IS NOT NULL THEN v.sis * 10 ELSE v.sis END,
       v.dia,
       v.fc,
       CASE WHEN v.r_error >= 0.010 AND v.r_error < 0.015 AND v.temp IS NOT NULL
            THEN round(v.temp / 10, 1) ELSE v.temp END,
       CASE WHEN v.r_error >= 0.005 AND v.r_error < 0.010 AND v.spo2 IS NOT NULL
            THEN v.spo2 / 10 ELSE v.spo2 END,
       v.fr,
       v.peso,
       v.estatura,
       CASE WHEN v.sis >= 150 AND v.r_obs < 0.6 THEN 'Refiere dolor de cabeza leve'
            WHEN v.temp >= 37.5 AND v.r_obs < 0.7 THEN 'Se siente con escalofríos'
            WHEN v.spo2 < 93 AND v.r_obs < 0.6 THEN 'Se fatiga al caminar'
            WHEN v.r_obs < 0.05 THEN 'Medición después de caminar'
            WHEN v.r_obs < 0.10 THEN 'Tomada en reposo, sentado'
            WHEN v.r_obs < 0.13 THEN 'No durmió bien anoche'
            WHEN v.r_obs < 0.15 THEN 'Olvidó la toma de la mañana'
            ELSE NULL END
FROM valores v;

-- 14. Citas médicas. Cada persona tiene entre 3 y 7 citas:
--     - la primera es su próxima cita, en los próximos 1 a 14 días (así
--       la página de citas y el seguimiento del acompañante siempre
--       muestran algo en "Próximas");
--     - la mitad tiene una segunda cita próxima, entre 15 y 75 días;
--     - el resto (al menos una) son citas pasadas de los últimos 150
--       días (historial).
--     Los títulos dependen del perfil de salud; los controles y la
--     medicina general son en la IPS de la persona y las especialidades
--     en un hospital. ~70% tiene consultorio. Horas entre 7:00 y 16:00
--     cada media hora. Los recordatorios quedan marcados como enviados.
WITH catalogo AS (
    SELECT row_number() OVER () AS id, * FROM (VALUES
        ('general', 'Medicina general',                         false, NULL),
        ('general', 'Odontología',                              false, 'Llevar cepillo de dientes'),
        ('general', 'Optometría',                               false, 'Llevar las gafas que usa actualmente'),
        ('general', 'Toma de muestras de laboratorio',          false, 'Ir en ayunas de 8 horas'),
        ('general', 'Vacunación contra la influenza',           false, 'Llevar el carné de vacunación'),
        ('general', 'Valoración por geriatría',                 true,  'Asistir con acompañante'),
        ('general', 'Fisioterapia',                             false, 'Ir con ropa cómoda'),
        ('general', 'Nutrición',                                false, NULL),
        ('hta',     'Control de hipertensión',                  false, 'Llevar el registro de presión arterial'),
        ('hta',     'Cardiología',                              true,  'Llevar electrocardiograma anterior'),
        ('hta',     'Electrocardiograma',                       true,  NULL),
        ('dm',      'Control de diabetes',                      false, 'Llevar el glucómetro'),
        ('dm',      'Laboratorio: hemoglobina glicosilada',     false, 'Ir en ayunas de 8 horas'),
        ('dm',      'Oftalmología (fondo de ojo)',              true,  'Ir con acompañante: le dilatarán las pupilas'),
        ('epoc',    'Neumología',                               true,  'Llevar los inhaladores'),
        ('epoc',    'Espirometría',                             true,  'No usar el inhalador 4 horas antes')
    ) AS v(condicion, titulo, especialista, recomendacion)
),
opciones AS (
    SELECT p.id_usuario, p.ips, p.n_citas, array_agg(c.id) AS ids,
           random() < 0.5 AS segunda_proxima
    FROM seed_tmp.seed_perfil p
    JOIN catalogo c
      ON c.condicion = 'general'
      OR (c.condicion = 'hta'  AND p.hta)
      OR (c.condicion = 'dm'   AND p.dm)
      OR (c.condicion = 'epoc' AND p.epoc)
    GROUP BY p.id_usuario, p.ips, p.n_citas
),
citas AS (
    SELECT o.id_usuario, o.ips,
           o.ids[1 + floor(random() * array_length(o.ids, 1))::int] AS id_catalogo,
           CASE WHEN k = 1 THEN r.hoy + 1 + floor(random() * 14)::int
                WHEN k = 2 AND o.segunda_proxima THEN r.hoy + 15 + floor(random() * 61)::int
                ELSE r.hoy - 1 - floor(random() * 150)::int END AS fecha,
           time '07:00' + make_interval(mins => 30 * floor(random() * 19)::int) AS hora,
           random() AS r_obs
    FROM opciones o
    CROSS JOIN seed_tmp.seed_reloj r
    CROSS JOIN LATERAL generate_series(1, o.n_citas) AS k
)
INSERT INTO cita_medica (id_persona_mayor, titulo, lugar, consultorio, fecha, hora, observaciones,
                         recordatorio_dia_enviado_para, recordatorio_hora_enviado_para)
SELECT ci.id_usuario, c.titulo,
       CASE WHEN c.especialista OR ci.ips IS NULL
            THEN (ARRAY['Hospital El Tunal', 'Hospital de Meissen', 'Hospital de Kennedy',
                        'Hospital San José'])[1 + floor(random() * 4)::int]
            ELSE ci.ips END,
       CASE WHEN ci.r_obs < 0.7
            THEN 'Consultorio ' || (100 + floor(random() * 400)::int)
            ELSE NULL END,
       ci.fecha, ci.hora,
       CASE WHEN c.recomendacion IS NOT NULL AND ci.r_obs < 0.8 THEN c.recomendacion
            WHEN ci.r_obs < 0.88 THEN NULL
            WHEN ci.r_obs < 0.92 THEN 'Llevar documento de identidad y carné de la EPS'
            WHEN ci.r_obs < 0.96 THEN 'Pedir la autorización en la EPS antes de la cita'
            ELSE 'Llegar 20 minutos antes' END,
       ci.fecha + ci.hora, ci.fecha + ci.hora
FROM citas ci
JOIN catalogo c ON c.id = ci.id_catalogo;

-- 15. Actividades de las 5 organizaciones (16 plantillas x 3 rondas =
--     48 actividades). La ronda 1 cae en los próximos 10 días (para ver
--     actividades "de hoy" y "de esta semana"), la 2 en los últimos 4
--     meses y la 3 entre 2 meses atrás y 2 meses adelante.
WITH orgs AS (
    SELECT id_organizacion, row_number() OVER (ORDER BY id_organizacion) AS rn
    FROM usuario WHERE correo LIKE 'org%@vitaplus.test'
),
n_org AS (SELECT count(*) AS n FROM orgs),
plantillas AS (
    SELECT row_number() OVER () AS rn, nombre, tipo, descripcion FROM (VALUES
      ('Taller de manualidades',             'Manualidades',
       'Espacio para tejer, bordar y crear piezas decorativas guiado por un facilitador.'),
      ('Caminata ecológica',                 'Deportiva',
       'Recorrido corto y de bajo impacto por senderos cercanos, con pausas de hidratación.'),
      ('Charla de salud y bienestar',        'Salud y bienestar',
       'Charla informativa sobre hábitos saludables, prevención y cuidado personal.'),
      ('Encuentro espiritual',               'Espiritual',
       'Espacio de oración y reflexión comunitaria.'),
      ('Bingo comunitario',                  'Social',
       'Juego de bingo con premios sencillos para fomentar la integración.'),
      ('Clase de yoga suave',                'Deportiva',
       'Rutina de estiramientos y respiración adaptada a personas mayores.'),
      ('Jornada de vacunación',              'Salud y bienestar',
       'Aplicación de vacunas con apoyo de personal de salud.'),
      ('Celebración de cumpleaños del mes',  'Social',
       'Celebración conjunta de los cumpleaños del mes con torta y música.'),
      ('Taller de memoria',                  'Educativa',
       'Ejercicios y juegos para estimular la memoria y la concentración.'),
      ('Paseo recreativo',                   'Recreativa',
       'Salida grupal a un parque o punto de interés cercano.'),
      ('Grupo de lectura',                   'Educativa',
       'Lectura y comentario de cuentos, poemas o noticias en grupo.'),
      ('Noche de música y baile',            'Recreativa',
       'Encuentro con música en vivo o grabada para bailar y socializar.'),
      ('Tamizaje de presión arterial',       'Salud y bienestar',
       'Toma de presión y glucometría con orientación sobre los resultados.'),
      ('Taller de uso del celular',          'Educativa',
       'Aprender a usar WhatsApp, videollamadas y la aplicación VITA+.'),
      ('Huerta comunitaria',                 'Recreativa',
       'Siembra y cuidado de plantas aromáticas y hortalizas en la huerta de la organización.'),
      ('Tarde de juegos de mesa',            'Social',
       'Parqués, dominó y cartas en grupo.')
    ) AS v(nombre, tipo, descripcion)
)
INSERT INTO actividad (id_organizacion, nombre, descripcion, fecha, hora, lugar, tipo, cupos, responsable)
SELECT o.id_organizacion, p.nombre, p.descripcion,
       CASE ronda
            WHEN 1 THEN r.hoy + floor(random() * 11)::int
            WHEN 2 THEN r.hoy - (1 + floor(random() * 120)::int)
            ELSE        r.hoy + (floor(random() * 121)::int - 60)
       END,
       (ARRAY['08:00', '09:00', '10:00', '14:00', '15:00', '16:00'])[1 + floor(random() * 6)::int],
       (ARRAY['Salón comunal Usme Centro', 'Parque La Flora', 'Sede Yomasa', 'Polideportivo Santa Librada',
              'Salón parroquial Comuneros'])[1 + floor(random() * 5)::int],
       p.tipo,
       10 + floor(random() * 26)::int,
       (ARRAY['Sandra Melo', 'Diana Rojas', 'Patricia Uribe', 'Andrea Salazar', 'Camila Peña',
              'Julián Bermúdez', 'David Cortés', 'Andrés Franco', 'Felipe Aguilar', 'Daniela Reyes',
              'Marcela Duarte', 'Liliana Rico'])[1 + floor(random() * 12)::int]
FROM plantillas p
CROSS JOIN generate_series(1, 3) AS ronda
CROSS JOIN seed_tmp.seed_reloj r
JOIN n_org ON true
JOIN orgs o ON o.rn = 1 + ((p.rn + ronda - 2) % n_org.n);

-- 15b. Actividades propuestas por voluntarios. ~60% de los voluntarios le
--      propone una actividad a la organización con la que tiene vínculo
--      ACEPTADO; el voluntario queda como responsable. Estados: 45%
--      PENDIENTE (para verlas en el panel de la organización), 35%
--      ACEPTADA (las ven las personas mayores) y 20% RECHAZADA.
WITH vols AS (
    SELECT u.id_usuario, u.nombre_usuario, vo.id_organizacion, random() AS r
    FROM usuario u
    JOIN voluntario_organizacion vo ON vo.id_voluntario = u.id_usuario AND vo.estado = 'ACEPTADA'
    WHERE u.correo LIKE 'vol%@vitaplus.test'
),
plantillas AS (
    SELECT row_number() OVER () AS rn, nombre, tipo, descripcion FROM (VALUES
      ('Clases de dibujo',               'Educativa',
       'Dibujo con lápiz y acuarela para principiantes, con materiales incluidos.'),
      ('Tarde de cuentos y recuerdos',   'Social',
       'Cada participante comparte una historia de su juventud con el grupo.'),
      ('Rumba terapia',                  'Deportiva',
       'Ejercicio suave con música tropical, adaptado a personas mayores.'),
      ('Acompañamiento a citas médicas', 'Salud y bienestar',
       'Voluntarios acompañan a quienes no tienen quien los lleve a sus citas.'),
      ('Taller de cocina tradicional',   'Manualidades',
       'Preparación de recetas típicas como envueltos, ajiaco y arepas.'),
      ('Cine foro',                      'Recreativa',
       'Proyección de una película clásica colombiana y conversación al final.')
    ) AS v(nombre, tipo, descripcion)
),
propuestas AS (
    SELECT v.*,
           CASE WHEN v.r < 0.45 THEN 'PENDIENTE'
                WHEN v.r < 0.80 THEN 'ACEPTADA'
                ELSE 'RECHAZADA' END AS estado,
           1 + floor(random() * 6)::int AS idx_plantilla
    FROM vols v
    WHERE random() < 0.60
)
INSERT INTO actividad (id_organizacion, id_voluntario, estado, nombre, descripcion, fecha, hora,
                       lugar, tipo, cupos, responsable)
SELECT p.id_organizacion, p.id_usuario, p.estado, pl.nombre, pl.descripcion,
       -- Las aceptadas pueden haber pasado ya; las demás aún no ocurren
       CASE WHEN p.estado = 'ACEPTADA' THEN r.hoy + (floor(random() * 91)::int - 60)
            ELSE r.hoy + (5 + floor(random() * 26)::int) END,
       (ARRAY['09:00', '10:00', '14:00', '15:00'])[1 + floor(random() * 4)::int],
       (ARRAY['Salón comunal Usme Centro', 'Parque La Flora', 'Sede Yomasa', 'Polideportivo Santa Librada',
              'Salón parroquial Comuneros'])[1 + floor(random() * 5)::int],
       pl.tipo,
       8 + floor(random() * 13)::int,
       p.nombre_usuario
FROM propuestas p
JOIN plantillas pl ON pl.rn = p.idx_plantilla
CROSS JOIN seed_tmp.seed_reloj r;

-- 16. Participación en actividades (inscripciones + asistencia).
--     - Solo se inscriben personas mayores cuya relación con la
--       organización de la actividad esté ACEPTADA, y solo en
--       actividades visibles (las de la organización y las propuestas
--       ACEPTADAS).
--     - Cada actividad llena entre 70% y 90% de sus cupos.
--     - asistio queda en NULL para las actividades que aún no pasan y en
--       true/false (85%/15%) para las que ya pasaron.
--     - recordatorio_enviado_para se llena con el inicio de la actividad:
--       así el scheduler cree que ya avisó y no manda SMS.
WITH actividades_ficticias AS (
    SELECT a.id_actividad, a.id_organizacion, a.fecha,
           a.fecha + a.hora::time AS inicio,
           greatest(1, floor(a.cupos * (0.7 + random() * 0.2))::int) AS n_inscritos
    FROM actividad a
    WHERE a.id_organizacion IN (SELECT id_organizacion FROM usuario WHERE correo LIKE 'org%@vitaplus.test')
      AND (a.estado IS NULL OR a.estado = 'ACEPTADA')
),
candidatos AS (
    SELECT af.id_actividad, af.fecha, af.inicio, af.n_inscritos,
           pmo.id_persona_mayor,
           row_number() OVER (PARTITION BY af.id_actividad ORDER BY random()) AS rn
    FROM actividades_ficticias af
    JOIN persona_mayor_organizacion pmo
      ON pmo.id_organizacion = af.id_organizacion AND pmo.estado = 'ACEPTADA'
    JOIN usuario u ON u.id_usuario = pmo.id_persona_mayor AND u.correo LIKE 'pm%@vitaplus.test'
)
INSERT INTO participacion (id_persona_mayor, id_actividad, asistio, recordatorio_enviado_para)
SELECT c.id_persona_mayor, c.id_actividad,
       CASE WHEN c.inicio >= r.ahora THEN NULL
            WHEN random() < 0.85 THEN true
            ELSE false END,
       c.inicio
FROM candidatos c CROSS JOIN seed_tmp.seed_reloj r
WHERE c.rn <= c.n_inscritos
ON CONFLICT DO NOTHING;

-- 17. Notificaciones del panel (historial de los últimos 21 días), con
--     los mismos textos que mandan los servicios:
--     - recordatorios de medicamentos a la persona mayor y a sus
--       acompañantes ACEPTADOS;
--     - recordatorios de citas médicas (un día antes y una hora antes)
--       a la persona mayor y a sus acompañantes ACEPTADOS;
--     - recordatorios de actividades a la que asistió la persona;
--     - alertas de emergencia de ~5% de las personas mayores a sus
--       acompañantes.
--     Las de hace más de 2 días quedan casi todas leídas.
CREATE TABLE seed_tmp.seed_notif (celular text, mensaje text, fecha timestamp);

-- Hora con el formato de los SMS: "8:00 a. m."
CREATE OR REPLACE FUNCTION seed_tmp.hora_sms(t time) RETURNS text LANGUAGE sql IMMUTABLE AS $$
    SELECT to_char(t, 'FMHH12:MI') || CASE WHEN t < time '12:00' THEN ' a. m.' ELSE ' p. m.' END
$$;

-- Medicamentos -> persona mayor
INSERT INTO seed_tmp.seed_notif
SELECT u.celular,
       'Es hora de tomar ' || m.nombre || coalesce(' (' || m.dosis || ')', '') || '.',
       (r.hoy - d) + m.hora
FROM medicamento m
JOIN usuario u ON u.id_usuario = m.id_persona_mayor AND u.correo LIKE 'pm%@vitaplus.test'
CROSS JOIN seed_tmp.seed_reloj r
CROSS JOIN LATERAL (SELECT generate_series(1, 21) AS d) dias
WHERE m.activo AND random() < 0.25;

-- Medicamentos -> acompañantes aceptados
INSERT INTO seed_tmp.seed_notif
SELECT ua.celular,
       'Es hora de que ' || u.nombre_usuario || ' tome ' || m.nombre
           || coalesce(' (' || m.dosis || ')', '') || '.',
       (r.hoy - d) + m.hora
FROM medicamento m
JOIN usuario u ON u.id_usuario = m.id_persona_mayor AND u.correo LIKE 'pm%@vitaplus.test'
JOIN persona_mayor_acompanante pma ON pma.id_persona_mayor = m.id_persona_mayor AND pma.estado = 'ACEPTADA'
JOIN usuario ua ON ua.id_usuario = pma.id_acompanante
CROSS JOIN seed_tmp.seed_reloj r
CROSS JOIN LATERAL (SELECT generate_series(1, 21) AS d) dias
WHERE m.activo AND random() < 0.10;

-- Citas médicas pasadas de los últimos 21 días: aviso del día antes y
-- de una hora antes, a la persona mayor y a sus acompañantes aceptados.
-- Mismos textos que CitaMedicaReminderScheduler.
WITH avisos AS (
    SELECT c.id_persona_mayor, u.nombre_usuario, u.celular,
           seed_tmp.hora_sms(c.hora) AS hora,
           'cita médica: ' || c.titulo || ' en ' || c.lugar
               || coalesce(', ' || c.consultorio, '') || '.' AS detalle,
           coalesce(' Recuerda: ' || c.observaciones, '') AS indicaciones,
           c.fecha + c.hora AS inicio
    FROM cita_medica c
    JOIN usuario u ON u.id_usuario = c.id_persona_mayor AND u.correo LIKE 'pm%@vitaplus.test'
    CROSS JOIN seed_tmp.seed_reloj r
    WHERE c.fecha BETWEEN r.hoy - 21 AND r.hoy - 1
),
destinatarios AS (
    SELECT a.*, a.celular AS para, 'tienes ' AS verbo FROM avisos a
    UNION ALL
    SELECT a.*, ua.celular, a.nombre_usuario || ' tiene '
    FROM avisos a
    JOIN persona_mayor_acompanante pma
      ON pma.id_persona_mayor = a.id_persona_mayor AND pma.estado = 'ACEPTADA'
    JOIN usuario ua ON ua.id_usuario = pma.id_acompanante
)
INSERT INTO seed_tmp.seed_notif
SELECT d.para, 'Mañana, a las ' || d.hora || ', ' || d.verbo || d.detalle || d.indicaciones,
       d.inicio - interval '1 day'
FROM destinatarios d
UNION ALL
SELECT d.para, 'En 1 hora, a las ' || d.hora || ', ' || d.verbo || d.detalle,
       d.inicio - interval '1 hour'
FROM destinatarios d;

-- Actividades -> persona mayor (1 hora antes)
INSERT INTO seed_tmp.seed_notif
SELECT u.celular,
       'En 1 hora, a las ' || seed_tmp.hora_sms(a.hora::time) || ', empieza tu actividad "'
           || a.nombre || '" en ' || a.lugar || '.',
       a.fecha + a.hora::time - interval '1 hour'
FROM participacion pa
JOIN actividad a ON a.id_actividad = pa.id_actividad
JOIN usuario u ON u.id_usuario = pa.id_persona_mayor AND u.correo LIKE 'pm%@vitaplus.test'
CROSS JOIN seed_tmp.seed_reloj r
WHERE pa.asistio IS NOT NULL
  AND a.fecha >= r.hoy - 21;

-- Alertas de emergencia -> acompañantes aceptados
WITH alertas AS (
    SELECT p.id_usuario, p.nombre_usuario,
           r.ahora - make_interval(days => floor(random() * 21)::int,
                                   hours => floor(random() * 12)::int) AS fecha
    FROM seed_tmp.seed_perfil p CROSS JOIN seed_tmp.seed_reloj r
    WHERE random() < 0.05
)
INSERT INTO seed_tmp.seed_notif
SELECT ua.celular,
       'ALERTA DE EMERGENCIA: ' || al.nombre_usuario
           || ' ha activado una alerta desde VITA+. Por favor, verifica que se encuentre bien.',
       al.fecha
FROM alertas al
JOIN persona_mayor_acompanante pma ON pma.id_persona_mayor = al.id_usuario AND pma.estado = 'ACEPTADA'
JOIN usuario ua ON ua.id_usuario = pma.id_acompanante;

-- fecha_envio es un Instant (UTC): se convierte desde la hora de Colombia
INSERT INTO notificacion (celular, mensaje, fecha_envio, leida)
SELECT n.celular, n.mensaje,
       n.fecha AT TIME ZONE 'America/Bogota',
       CASE WHEN n.fecha < r.ahora - interval '2 days' THEN random() < 0.9
            ELSE random() < 0.3 END
FROM seed_tmp.seed_notif n CROSS JOIN seed_tmp.seed_reloj r
WHERE n.celular IS NOT NULL
  AND n.fecha <= r.ahora;

DROP SCHEMA seed_tmp CASCADE;

COMMIT;

-- Resumen: qué quedó cargado
SELECT 'organizaciones' AS dato, count(*) AS cantidad FROM usuario WHERE correo LIKE 'org%@vitaplus.test'
UNION ALL SELECT 'personas_mayores', count(*) FROM usuario WHERE correo LIKE 'pm%@vitaplus.test'
UNION ALL SELECT 'acompanantes', count(*) FROM usuario WHERE correo LIKE 'acomp%@vitaplus.test'
UNION ALL SELECT 'voluntarios', count(*) FROM usuario WHERE correo LIKE 'vol%@vitaplus.test'
UNION ALL SELECT 'vinculos_voluntario_organizacion', count(*) FROM voluntario_organizacion
    WHERE id_voluntario IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'vol%@vitaplus.test')
UNION ALL SELECT 'gustos_en_catalogo', count(*) FROM gusto WHERE categoria IN ('GUSTO', 'TALENTO', 'HOBBY')
UNION ALL SELECT 'vinculos_persona_mayor_gusto', count(*) FROM persona_mayor_gusto
    WHERE id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
UNION ALL SELECT 'vinculos_persona_mayor_acompanante', count(*) FROM persona_mayor_acompanante
    WHERE id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
UNION ALL SELECT 'solicitudes_enviadas_por_acompanantes', count(*) FROM persona_mayor_acompanante
    WHERE estado = 'PENDIENTE' AND solicitada_por = 'ACOMPANANTE'
      AND id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
UNION ALL SELECT 'vinculos_persona_mayor_organizacion', count(*) FROM persona_mayor_organizacion
    WHERE id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
UNION ALL SELECT 'vinculos_persona_mayor_organizacion_aceptados', count(*) FROM persona_mayor_organizacion
    WHERE estado = 'ACEPTADA'
      AND id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
UNION ALL SELECT 'medicamentos', count(*) FROM medicamento
    WHERE id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
UNION ALL SELECT 'signos_vitales', count(*) FROM signo_vital
    WHERE id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
UNION ALL SELECT 'citas_medicas', count(*) FROM cita_medica
    WHERE id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
UNION ALL SELECT 'personas_mayores_con_cita_proxima', count(DISTINCT id_persona_mayor) FROM cita_medica
    WHERE fecha > (now() AT TIME ZONE 'America/Bogota')::date
      AND id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
UNION ALL SELECT 'actividades', count(*) FROM actividad
    WHERE id_organizacion IN (SELECT id_organizacion FROM usuario WHERE correo LIKE 'org%@vitaplus.test')
UNION ALL SELECT 'propuestas_de_voluntarios', count(*) FROM actividad
    WHERE id_voluntario IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'vol%@vitaplus.test')
UNION ALL SELECT 'participaciones_en_actividades', count(*) FROM participacion
    WHERE id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
UNION ALL SELECT 'notificaciones', count(*) FROM notificacion WHERE celular LIKE '+570000%';

-- Cuántas personas mayores (con vínculo ACEPTADO) tiene cada
-- organización y cada acompañante: mínimo, promedio y máximo.
SELECT 'personas_por_organizacion' AS vinculo,
       min(n) AS minimo, round(avg(n), 1) AS promedio, max(n) AS maximo
FROM (
    SELECT u.id_organizacion, count(pmo.id_persona_mayor) AS n
    FROM usuario u
    LEFT JOIN persona_mayor_organizacion pmo
      ON pmo.id_organizacion = u.id_organizacion AND pmo.estado = 'ACEPTADA'
    WHERE u.correo LIKE 'org%@vitaplus.test'
    GROUP BY u.id_organizacion
) t
UNION ALL
SELECT 'personas_por_acompanante', min(n), round(avg(n), 1), max(n)
FROM (
    SELECT u.id_usuario, count(pma.id_persona_mayor) AS n
    FROM usuario u
    LEFT JOIN persona_mayor_acompanante pma
      ON pma.id_acompanante = u.id_usuario AND pma.estado = 'ACEPTADA'
    WHERE u.correo LIKE 'acomp%@vitaplus.test'
    GROUP BY u.id_usuario
) t;
