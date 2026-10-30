-- VITA+ — Script de datos ficticios para pruebas y analítica
--
-- Genera:
--   - 8 organizaciones (con su cuenta de usuario)
--   - 300 personas mayores con fecha de nacimiento, género, dirección,
--     EPS e IPS, y un perfil de salud (hipertensión, diabetes, EPOC)
--   - 160 personas mayores más para la Fundación Entrenubes Usme, con
--     cuatro perfiles bien diferenciados para el perfilamiento con K-means
--     (sección 24), y 20 actividades de la fundación en las que participan
--   - datos de salud de cada persona mayor (enfermedades, alergias y
--     discapacidades), coherentes con su perfil de salud
--   - 60 acompañantes y 50 voluntarios (los voluntarios con dirección)
--   - catálogo de gustos, talentos y pasatiempos + 3 a 6 por persona
--     mayor y por voluntario (con ellos se les recomiendan organizaciones)
--   - profesión u oficio de la mayoría de personas mayores y voluntarios
--     (categoría PROFESION del mismo catálogo)
--   - vínculos persona mayor <-> acompañante y persona mayor <->
--     organización (con estados PENDIENTE / ACEPTADA / RECHAZADA). Las
--     solicitudes pendientes van en los dos sentidos: unas las envió la
--     persona mayor y otras el acompañante o la organización. El
--     parentesco del acompañante va en cada vínculo (puede ser hijo de una
--     persona mayor y sobrino de otra); unos pocos vínculos no lo tienen
--   - vínculos voluntario <-> organización (aceptados, y algunas
--     solicitudes pendientes)
--   - medicamentos coherentes con el perfil de salud de cada persona
--   - historial de signos vitales de los últimos 6 meses (~5.000
--     mediciones), con algunos valores fuera de rango y unos pocos
--     errores de digitación para probar los reportes
--   - citas médicas: cada persona tiene una próxima cita en las
--     siguientes 2 semanas (la mitad, otra más adelante) y un historial
--     de citas pasadas, con lugar y consultorio. ~25% de las próximas
--     necesitan acompañamiento y en la mitad de ellas un acompañante ya
--     confirmó, con el punto de encuentro
--   - 64 actividades con inscripciones y asistencia (algunas recientes
--     quedan sin asistencia registrada, para la alerta del inicio de la
--     organización)
--   - actividades propuestas por voluntarios a sus organizaciones
--     (pendientes, aceptadas y rechazadas)
--   - emergencias (botón de emergencia) de los últimos 21 días; unas
--     pocas de las últimas horas, para verlas en las alertas del inicio
--     del acompañante
--   - notificaciones del panel (recordatorios de medicamentos, citas y
--     actividades, y alertas de emergencia)
--   - EPS, IPS con su dirección, si vive sola y su nivel de uso del celular
--     (menos uso a más edad; algunas no lo han indicado), para cada
--     persona mayor
--   - cuentas inactivas coherentes con la aplicación: sus vínculos quedan
--     en CUENTA_INACTIVA / PENDIENTE_CUENTA_INACTIVA, como los deja
--     auth-service; y algunos vínculos pausados a mano (INACTIVA)
--   - retiros: personas mayores que eliminaron su cuenta y por qué (la
--     organización los ve en "Personas mayores")
--   - preferencias de notificación: algunos usuarios apagaron algún tipo
--   - notas de las organizaciones sobre algunas de sus personas mayores
--   - constancia de aceptación de los términos y condiciones de cada
--     cuenta, con la fecha y hora de su registro
--   - donaciones de acompañantes y voluntarios a sus organizaciones
--     (programadas, algunas con la fecha ya pasada, recibidas, no
--     entregadas y canceladas), con alimentos, aseo, ropa, medicamentos,
--     dinero y otros
--   - inventario de cada organización: lo recibido en donaciones, compras
--     y aportes externos, y las salidas (entregas, uso, vencidos...), con
--     artículos agotados, por agotarse, vencidos y por vencer
--   - calificaciones de la plataforma (0 a 5 estrellas, algunas con una
--     observación) de cerca de la mitad de las cuentas
--   - registro de auditoría de los datos de salud de los últimos 30 días:
--     acompañantes que consultan o actualizan a sus personas mayores,
--     organizaciones que consultan signos vitales y reportes de salud, y
--     unos pocos intentos sin vínculo (403)
--
-- Las fotos de perfil no se generan: necesitan archivos reales en
-- Supabase Storage. Las cuentas ficticias muestran su inicial.
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
-- Colombia, para que ningún SMS llegue a una persona real. mensajeria-service
-- no le envía nada a un celular que después del +57 empieza por 000
-- (TextBeeOtpService.esCelularFicticio): los recordatorios de medicamentos,
-- citas y actividades, los cumpleaños y las alertas de estas cuentas se
-- guardan en la campanita del panel, pero no gastan el cupo de SMS de
-- TextBee. Por eso tampoco reciben el código OTP: para entrar con estas
-- cuentas usa correo y contraseña.
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
-- Hora en que termina cada actividad; la agrega actividad-service al arrancar.
ALTER TABLE actividad ADD COLUMN IF NOT EXISTS hora_fin varchar(255);

-- Ubicación en el mapa de las actividades y las sedes. Las agrega
-- analitica-service, que también las llena solo: le pregunta a Google
-- dónde quedan el lugar de cada actividad y la dirección de cada
-- organización (UbicadorAutomatico), así que este script no las llena.
ALTER TABLE actividad ADD COLUMN IF NOT EXISTS latitud double precision;
ALTER TABLE actividad ADD COLUMN IF NOT EXISTS longitud double precision;
ALTER TABLE actividad ADD COLUMN IF NOT EXISTS ubicacion_buscada varchar(255);
ALTER TABLE organizacion ADD COLUMN IF NOT EXISTS latitud double precision;
ALTER TABLE organizacion ADD COLUMN IF NOT EXISTS longitud double precision;
ALTER TABLE organizacion ADD COLUMN IF NOT EXISTS ubicacion_buscada varchar(255);
-- Barrio de cada persona mayor (nunca sus coordenadas): lo saca
-- analitica-service de la dirección; aquí ya viene escrito en ella.
ALTER TABLE persona_mayor ADD COLUMN IF NOT EXISTS barrio varchar(100);
ALTER TABLE persona_mayor ADD COLUMN IF NOT EXISTS barrio_buscado varchar(255);

-- Quién envió la solicitud de acompañamiento; la agregan
-- persona-mayor-service y acompanante-service al arrancar.
ALTER TABLE persona_mayor_acompanante ADD COLUMN IF NOT EXISTS solicitada_por varchar(255);

-- Parentesco del acompañante en cada vínculo (puede ser distinto con cada
-- persona mayor); la agregan persona-mayor-service y acompanante-service
-- al arrancar. Antes iba en acompanante.relacion; auth-service pasa ese
-- valor a los vínculos y borra la columna al arrancar.
ALTER TABLE persona_mayor_acompanante ADD COLUMN IF NOT EXISTS relacion varchar(255);

-- Quién envió la solicitud persona mayor <-> organización; la agregan
-- persona-mayor-service y organizacion-service al arrancar.
ALTER TABLE persona_mayor_organizacion ADD COLUMN IF NOT EXISTS solicitada_por varchar(255);

-- Gustos de los voluntarios; la crea voluntario-service al arrancar.
CREATE TABLE IF NOT EXISTS voluntario_gusto (
    id_voluntario integer NOT NULL,
    id_gusto      integer NOT NULL,
    PRIMARY KEY (id_gusto, id_voluntario)
);

-- Emergencias (botón de emergencia); la crea persona-mayor-service al arrancar.
CREATE TABLE IF NOT EXISTS emergencia (
    id_emergencia    integer GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_persona_mayor integer   NOT NULL,
    fecha_hora       timestamp NOT NULL
);

-- Recordatorios de citas médicas ya enviados; los agrega salud-service al
-- arrancar.
ALTER TABLE cita_medica ADD COLUMN IF NOT EXISTS recordatorio_dia_enviado_para timestamp;
ALTER TABLE cita_medica ADD COLUMN IF NOT EXISTS recordatorio_hora_enviado_para timestamp;
ALTER TABLE cita_medica ADD COLUMN IF NOT EXISTS consultorio varchar(255);

-- Acompañamiento a las citas; lo agregan salud-service y acompanante-service al arrancar.
ALTER TABLE cita_medica ADD COLUMN IF NOT EXISTS acompanamiento varchar(20);
ALTER TABLE cita_medica ADD COLUMN IF NOT EXISTS id_acompanante_cita integer;
ALTER TABLE cita_medica ADD COLUMN IF NOT EXISTS punto_encuentro varchar(300);
ALTER TABLE cita_medica ADD COLUMN IF NOT EXISTS fecha_acompanamiento_aceptado timestamp;

-- Dirección de la IPS, si vive sola y nivel de uso del celular; las agrega auth-service al arrancar.
ALTER TABLE persona_mayor ADD COLUMN IF NOT EXISTS direccion_ips varchar(255);
ALTER TABLE persona_mayor ADD COLUMN IF NOT EXISTS vive_solo boolean;
ALTER TABLE persona_mayor ADD COLUMN IF NOT EXISTS uso_celular varchar(20);

-- Por qué alguien eliminó su cuenta; la crea auth-service al arrancar.
CREATE TABLE IF NOT EXISTS retiro_cuenta (
    id_retiro       integer GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_organizacion integer,
    nombre          varchar(255),
    rol             varchar(255),
    razon           varchar(255) NOT NULL,
    comentario      varchar(500),
    fecha           timestamp    NOT NULL
);

-- Tipos de notificación que cada usuario apagó; la crea
-- mensajeria-service al arrancar.
CREATE TABLE IF NOT EXISTS preferencia_notificacion (
    id_preferencia bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_usuario     integer     NOT NULL,
    tipo           varchar(40) NOT NULL,
    activa         boolean     NOT NULL,
    UNIQUE (id_usuario, tipo)
);

-- Constancias de aceptación de los términos; la crea auth-service al arrancar.
CREATE TABLE IF NOT EXISTS aceptacion_terminos (
    id_aceptacion    integer GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_usuario       integer     NOT NULL,
    acepto           boolean     NOT NULL,
    fecha_aceptacion timestamp   NOT NULL,
    version_terminos varchar(20),
    origen           varchar(20) NOT NULL
);

-- Notas de las organizaciones sobre sus personas mayores; la crea
-- organizacion-service al arrancar.
CREATE TABLE IF NOT EXISTS nota_persona_mayor (
    id_nota             integer GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_organizacion     integer       NOT NULL,
    id_persona_mayor    integer       NOT NULL,
    texto               varchar(2000) NOT NULL,
    id_autor            integer       NOT NULL,
    fecha_creacion      timestamp     NOT NULL,
    fecha_actualizacion timestamp     NOT NULL
);

-- Donaciones e inventario; las crea donacion-service al arrancar.
CREATE TABLE IF NOT EXISTS donacion (
    id_donacion       integer GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_donante        integer      NOT NULL,
    rol_donante       varchar(255) NOT NULL,
    id_organizacion   integer      NOT NULL,
    fecha_entrega     date         NOT NULL,
    hora_entrega      time,
    observaciones     varchar(500),
    estado            varchar(255) NOT NULL,
    nota_organizacion varchar(300),
    fecha_registro    timestamp    NOT NULL,
    fecha_respuesta   timestamp
);

CREATE TABLE IF NOT EXISTS donacion_articulo (
    id_articulo       integer GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_donacion       integer        NOT NULL REFERENCES donacion (id_donacion),
    categoria         varchar(255)   NOT NULL,
    descripcion       varchar(120)   NOT NULL,
    cantidad          numeric(12, 2) NOT NULL,
    unidad            varchar(30)    NOT NULL,
    fecha_vencimiento date,
    cantidad_recibida numeric(12, 2)
);

CREATE TABLE IF NOT EXISTS inventario_articulo (
    id_inventario       integer GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_organizacion     integer        NOT NULL,
    nombre              varchar(120)   NOT NULL,
    categoria           varchar(255)   NOT NULL,
    unidad              varchar(30)    NOT NULL,
    cantidad            numeric(12, 2) NOT NULL,
    cantidad_minima     numeric(12, 2),
    fecha_vencimiento   date,
    fecha_actualizacion timestamp      NOT NULL
);

CREATE TABLE IF NOT EXISTS inventario_movimiento (
    id_movimiento integer GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_inventario integer        NOT NULL,
    tipo          varchar(255)   NOT NULL,
    cantidad      numeric(12, 2) NOT NULL,
    motivo        varchar(255)   NOT NULL,
    detalle       varchar(300),
    id_donacion   integer,
    id_usuario    integer        NOT NULL,
    fecha         timestamp      NOT NULL
);

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

DELETE FROM persona_mayor_condicion_salud
 WHERE id_persona_mayor IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);

DELETE FROM emergencia
 WHERE id_persona_mayor IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);

DELETE FROM voluntario_gusto
 WHERE id_voluntario IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);

DELETE FROM persona_mayor_organizacion
 WHERE id_persona_mayor IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos)
    OR id_organizacion IN (SELECT id_organizacion FROM seed_tmp.seed_orgs_viejas);

DELETE FROM persona_mayor_acompanante
 WHERE id_persona_mayor IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos)
    OR id_acompanante IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);

DELETE FROM voluntario_organizacion
 WHERE id_voluntario IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos)
    OR id_organizacion IN (SELECT id_organizacion FROM seed_tmp.seed_orgs_viejas);

DELETE FROM inventario_movimiento
 WHERE id_inventario IN (SELECT id_inventario FROM inventario_articulo
                          WHERE id_organizacion IN (SELECT id_organizacion FROM seed_tmp.seed_orgs_viejas));

DELETE FROM inventario_articulo
 WHERE id_organizacion IN (SELECT id_organizacion FROM seed_tmp.seed_orgs_viejas);

DELETE FROM donacion_articulo
 WHERE id_donacion IN (SELECT id_donacion FROM donacion
                        WHERE id_organizacion IN (SELECT id_organizacion FROM seed_tmp.seed_orgs_viejas)
                           OR id_donante IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos));

DELETE FROM donacion
 WHERE id_organizacion IN (SELECT id_organizacion FROM seed_tmp.seed_orgs_viejas)
    OR id_donante IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);

DELETE FROM retiro_cuenta
 WHERE id_organizacion IN (SELECT id_organizacion FROM seed_tmp.seed_orgs_viejas);

DELETE FROM nota_persona_mayor
 WHERE id_organizacion IN (SELECT id_organizacion FROM seed_tmp.seed_orgs_viejas)
    OR id_persona_mayor IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);

DELETE FROM preferencia_notificacion
 WHERE id_usuario IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);

-- En la aplicación la constancia se conserva al borrar una cuenta, pero
-- las de las cuentas ficticias se borran para no acumularlas.
DELETE FROM aceptacion_terminos
 WHERE id_usuario IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);

-- calificacion_plataforma la crea auth-service al arrancar; se crea aquí
-- igual por si esta base de datos nunca lo ha arrancado.
CREATE TABLE IF NOT EXISTS calificacion_plataforma (
    id_calificacion     integer GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_usuario          integer       NOT NULL UNIQUE,
    estrellas           integer       NOT NULL,
    comentario          varchar(1000),
    fecha_creacion      timestamp     NOT NULL,
    fecha_actualizacion timestamp     NOT NULL
);
DELETE FROM calificacion_plataforma
 WHERE id_usuario IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);

-- auditoria_salud la crean salud, acompañante y analítica al arrancar
-- (AuditoriaSaludInterceptor), con esta misma definición.
CREATE TABLE IF NOT EXISTS auditoria_salud (
    id_auditoria     bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    fecha            timestamp    NOT NULL,
    id_usuario       integer      NOT NULL,
    rol              varchar(30),
    id_persona_mayor integer,
    recurso          varchar(40)  NOT NULL,
    accion           varchar(20)  NOT NULL,
    metodo           varchar(10)  NOT NULL,
    ruta             varchar(255) NOT NULL,
    estado           integer      NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_auditoria_salud_persona ON auditoria_salud (id_persona_mayor);
DELETE FROM auditoria_salud
 WHERE id_usuario IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos)
    OR id_persona_mayor IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);

-- Sesiones abiertas de las cuentas ficticias (las crea auth-service al
-- iniciar sesión). Si la tabla aún no existe, no hay nada que borrar.
DO $$
BEGIN
    IF to_regclass('public.sesion') IS NOT NULL THEN
        DELETE FROM sesion WHERE id_usuario IN (SELECT id_usuario FROM seed_tmp.seed_usuarios_viejos);
    END IF;
END $$;

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
      ('Parroquia Nuestra Señora de la Esperanza - Usme','Calle 89 Sur # 5-10, Comuneros',        'org05@vitaplus.test'),
      ('Fundación Abuelos Felices Danubio',              'Calle 85 Sur # 2-40 Este, Danubio Azul', 'org06@vitaplus.test'),
      ('Asociación Adulto Mayor El Uval',                'Carrera 2 Este # 102-15 Sur, El Uval',  'org07@vitaplus.test'),
      ('Centro Comunitario Alfonso López',               'Calle 97 Sur # 8-30, Alfonso López',    'org08@vitaplus.test')
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

-- 3. Catálogo de gustos, talentos y pasatiempos (3 de las 4 categorías de
--    la pantalla "Intereses"; las profesiones van en la sección 10c). Si un nombre ya existe con una
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
 WHERE gusto.categoria IS NULL OR gusto.categoria NOT IN ('GUSTO', 'TALENTO', 'HOBBY', 'PROFESION');

-- 4. 300 personas mayores (usuario + persona_mayor con EPS e IPS)
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
    FROM generate_series(1, 300) AS s(i)
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
INSERT INTO persona_mayor (id_usuario, eps, ips, direccion_ips, vive_solo, uso_celular)
SELECT u.id_usuario, c.eps, c.ips,
       CASE c.ips
         WHEN 'USS Usme - Subred Sur'          THEN 'Carrera 3 # 90A-30 Sur, Usme Centro'
         WHEN 'USS Santa Librada - Subred Sur'  THEN 'Calle 76 Sur # 14-30, Santa Librada'
         WHEN 'USS Betania - Subred Sur'        THEN 'Calle 81A Sur # 11-50 Este, Betania'
         WHEN 'USS Marichuela - Subred Sur'     THEN 'Calle 76 Sur # 14-80, Marichuela'
         WHEN 'Hospital de Meissen'             THEN 'Carrera 18B # 60G-36 Sur, Meissen'
         WHEN 'Virrey Solís IPS Santa Librada'  THEN 'Calle 75 Sur # 14-15, Santa Librada'
         WHEN 'Virrey Solís IPS Kennedy'        THEN 'Calle 38 Sur # 78-40, Kennedy'
         WHEN 'Hospital El Tunal'               THEN 'Carrera 20 # 47B-35 Sur, El Tunal'
         WHEN 'Compensar Sede Usme'             THEN 'Calle 90 Sur # 4-20, Usme Centro'
         WHEN 'Compensar Sede Kennedy'          THEN 'Avenida Primero de Mayo # 72-38 Sur, Kennedy'
         WHEN 'Cafam Santa Librada'             THEN 'Calle 74 Sur # 14-60, Santa Librada'
         WHEN 'Colsubsidio IPS Restrepo'        THEN 'Calle 17 Sur # 18-35, Restrepo'
         WHEN 'Centro Médico Colsanitas Sur'    THEN 'Avenida Boyacá # 64-40 Sur, Kennedy'
         WHEN 'Clínica Sanitas Tintal'          THEN 'Avenida Ciudad de Cali # 6C-09, Tintal'
         WHEN 'IPS Sura Calle 100'              THEN 'Calle 100 # 19-61, Chicó'
       END,
       -- ~25% vive sola, ~60% con otras personas y ~15% no lo ha indicado
       CASE WHEN c.r_vive < 0.25 THEN true
            WHEN c.r_vive < 0.85 THEN false
       END,
       -- Nivel de uso del celular: a más edad, menos uso (a los 60 años
       -- casi nadie queda en "No lo usa"; pasados los 85, casi nadie en
       -- "Alto"). ~12% no lo ha indicado.
       CASE WHEN c.r_dato_cel < 0.12 THEN NULL
            WHEN c.r_cel + (date_part('year', age(c.fecha_nacimiento)) - 60) / 50.0 < 0.25 THEN 'AVANZADO'
            WHEN c.r_cel + (date_part('year', age(c.fecha_nacimiento)) - 60) / 50.0 < 0.65 THEN 'INTERMEDIO'
            WHEN c.r_cel + (date_part('year', age(c.fecha_nacimiento)) - 60) / 50.0 < 1.05 THEN 'BASICO'
            ELSE 'NO_USA'
       END
FROM ins_usuario u
JOIN (SELECT con_ips.*, random() AS r_vive, random() AS r_cel, random() AS r_dato_cel FROM con_ips) c
  ON c.correo = u.correo;

-- 5. 60 acompañantes (usuario + acompanante)
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
        (25 + floor(random() * 41))::int AS edad
    FROM generate_series(1, 60) AS s(i)
),
filas AS (
    SELECT d.i,
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
INSERT INTO acompanante (id_usuario)
SELECT id_usuario FROM ins_usuario;

-- 6. 50 voluntarios (usuario + voluntario), cada uno vinculado a una de
--    las organizaciones ficticias (voluntario_organizacion, ACEPTADA).
--    El usuario no lleva id_organizacion: esa columna es solo para las
--    cuentas de las organizaciones. Llevan dirección (el recomendador de
--    organizaciones la usa para la cercanía).
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
        (ARRAY['Usme Centro','La Flora','Yomasa','Danubio Azul','Santa Librada','El Uval','Comuneros',
               'Alfonso López','Marichuela','Betania','Tunjuelito','Kennedy'])[1 + floor(random() * 12)::int] AS barrio,
        o.id_organizacion
    FROM generate_series(1, 50) AS s(i)
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
           (ARRAY['Calle','Carrera','Diagonal'])[1 + floor(random() * 3)::int] || ' '
               || (45 + floor(random() * 75)::int) || ' Sur # ' || (1 + floor(random() * 25)::int)
               || '-' || (2 + floor(random() * 80)::int) || ', ' || d.barrio AS direccion,
           'vol' || lpad(d.i::text, 3, '0') || '@vitaplus.test' AS correo,
           '+5700003' || lpad(d.i::text, 5, '0') AS celular
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
),
ins_voluntario AS (
    INSERT INTO voluntario (id_usuario)
    SELECT u.id_usuario
    FROM ins_usuario u
)
INSERT INTO voluntario_organizacion (id_voluntario, id_organizacion, estado)
SELECT u.id_usuario, f.id_organizacion, 'ACEPTADA'
FROM ins_usuario u JOIN filas f ON f.correo = u.correo;

-- Los 10 primeros voluntarios también tienen una solicitud PENDIENTE con
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
WHERE v.i <= 10
ON CONFLICT DO NOTHING;

-- 7. Roles de las 410 cuentas de personas
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
--    relacion: el parentesco va en cada vínculo, porque un acompañante
--    puede ser hijo de una persona mayor y sobrino de otra. ~15% queda en
--    null (la persona mayor no lo indicó).
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
INSERT INTO persona_mayor_acompanante (id_persona_mayor, id_acompanante, estado, solicitada_por, relacion)
SELECT id_persona_mayor, id_acompanante, estado,
       CASE WHEN estado = 'PENDIENTE' AND random() < 0.40 THEN 'ACOMPANANTE'
            ELSE 'PERSONA_MAYOR' END,
       CASE WHEN random() < 0.15 THEN NULL
            ELSE (ARRAY['Hijo/a', 'Hijo/a', 'Hijo/a', 'Nieto/a', 'Nieto/a', 'Sobrino/a',
                        'Cuidador/a contratado', 'Hermano/a', 'Vecino/a de confianza'])
                     [1 + floor(random() * 9)::int] END
FROM con_estado
ON CONFLICT DO NOTHING;

-- 8b. Cuentas con los dos roles (RF-76): pm001 a pm005 también son
--     acompañantes, con la misma cuenta y la misma información. Cada una
--     acompaña a la persona mayor siguiente (pm001 -> pm002...), como un
--     vecino o una pareja. Desde el óvalo de la barra superior cambian de rol.
INSERT INTO usuario_rol (id_usuario, id_rol)
SELECT u.id_usuario, r.id_rol
FROM usuario u JOIN rol r ON r.nombre = 'ACOMPANANTE'
WHERE u.correo IN ('pm001@vitaplus.test', 'pm002@vitaplus.test', 'pm003@vitaplus.test',
                   'pm004@vitaplus.test', 'pm005@vitaplus.test')
ON CONFLICT DO NOTHING;

INSERT INTO acompanante (id_usuario)
SELECT u.id_usuario
FROM usuario u
WHERE u.correo IN ('pm001@vitaplus.test', 'pm002@vitaplus.test', 'pm003@vitaplus.test',
                   'pm004@vitaplus.test', 'pm005@vitaplus.test')
ON CONFLICT DO NOTHING;

INSERT INTO persona_mayor_acompanante (id_persona_mayor, id_acompanante, estado, solicitada_por, relacion)
SELECT siguiente.id_usuario, u.id_usuario, 'ACEPTADA', 'PERSONA_MAYOR', 'Vecino(a)'
FROM usuario u
JOIN usuario siguiente
  ON siguiente.correo = 'pm' || lpad((substring(u.correo FROM 3 FOR 3)::int + 1)::text, 3, '0') || '@vitaplus.test'
WHERE u.correo IN ('pm001@vitaplus.test', 'pm002@vitaplus.test', 'pm003@vitaplus.test',
                   'pm004@vitaplus.test', 'pm005@vitaplus.test')
ON CONFLICT DO NOTHING;

-- 9. Vínculo persona mayor - organización. Cada persona mayor queda con
--    una organización principal (75% ACEPTADA, 15% PENDIENTE, 10%
--    RECHAZADA; el botón de emergencia solo notifica organizaciones
--    ACEPTADAS) y ~12% además con una segunda organización.
--    solicitada_por: de las pendientes, ~40% las pidió la persona mayor
--    (la organización las ve en "Solicitudes para unirse") y el resto son
--    invitaciones de la organización; las demás las envió la organización.
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
    SELECT pm.id_usuario AS id_persona_mayor, o.id_organizacion, random() AS r, random() AS r_quien
    FROM pm CROSS JOIN n_org
    JOIN orgs o ON o.rn = 1 + ((pm.rn - 1) % n_org.n)
    UNION ALL
    SELECT pm.id_usuario, o.id_organizacion, random() * 0.9, random()  -- la segunda nunca queda RECHAZADA
    FROM pm CROSS JOIN n_org
    JOIN orgs o ON o.rn = 1 + ((pm.rn + 1) % n_org.n)
    WHERE random() < 0.12
)
INSERT INTO persona_mayor_organizacion (id_persona_mayor, id_organizacion, estado, solicitada_por)
SELECT id_persona_mayor, id_organizacion,
       CASE WHEN r < 0.75 THEN 'ACEPTADA'
            WHEN r < 0.90 THEN 'PENDIENTE'
            ELSE 'RECHAZADA' END,
       CASE WHEN r >= 0.75 AND r < 0.90 AND r_quien < 0.40 THEN 'PERSONA_MAYOR'
            ELSE 'ORGANIZACION' END
FROM asignaciones
ON CONFLICT DO NOTHING;

-- 9b. Vínculos pausados y cuentas inactivas.
--     Algunas personas mayores pausaron el vínculo con una organización
--     (INACTIVA) y algunos voluntarios el suyo; la otra parte deja de verlos.
UPDATE persona_mayor_organizacion
   SET estado = 'INACTIVA'
 WHERE estado = 'ACEPTADA'
   AND id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
   AND random() < 0.04;

UPDATE voluntario_organizacion
   SET estado = 'INACTIVA'
 WHERE estado = 'ACEPTADA'
   AND id_voluntario IN (SELECT id_usuario FROM usuario
                          WHERE correo IN ('vol046@vitaplus.test', 'vol047@vitaplus.test', 'vol048@vitaplus.test'));

--     Las cuentas inactivas (~3% de las personas mayores) quedan como las
--     deja auth-service al inactivarlas: sus vínculos pasan a un estado que
--     nadie lista (ver CuentaService). Los pausados a mano no se tocan.
UPDATE persona_mayor_organizacion
   SET estado = CASE estado WHEN 'ACEPTADA' THEN 'CUENTA_INACTIVA' ELSE 'PENDIENTE_CUENTA_INACTIVA' END
 WHERE estado IN ('ACEPTADA', 'PENDIENTE')
   AND id_persona_mayor IN (SELECT id_usuario FROM usuario
                             WHERE correo LIKE 'pm%@vitaplus.test' AND activo = false);

UPDATE persona_mayor_acompanante
   SET estado = CASE estado WHEN 'ACEPTADA' THEN 'CUENTA_INACTIVA' ELSE 'PENDIENTE_CUENTA_INACTIVA' END
 WHERE estado IN ('ACEPTADA', 'PENDIENTE')
   AND id_persona_mayor IN (SELECT id_usuario FROM usuario
                             WHERE correo LIKE 'pm%@vitaplus.test' AND activo = false);

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

-- 10b. Entre 3 y 6 gustos por voluntario (mismo catálogo); ~10% no
--      registra ninguno, para ver el aviso "Registra tus intereses" en
--      sus recomendaciones.
WITH gusto_ids AS (
    SELECT array_agg(id_gusto) AS ids FROM gusto
    WHERE categoria IN ('GUSTO', 'TALENTO', 'HOBBY')
),
vol AS (
    SELECT id_usuario, 3 + floor(random() * 4)::int AS n_gustos
    FROM usuario WHERE correo LIKE 'vol%@vitaplus.test' AND random() >= 0.10
),
asignaciones AS (
    SELECT vol.id_usuario AS id_voluntario,
           g.ids[1 + floor(random() * array_length(g.ids, 1))::int] AS id_gusto
    FROM vol
    CROSS JOIN gusto_ids g
    CROSS JOIN LATERAL generate_series(1, vol.n_gustos) AS k
)
INSERT INTO voluntario_gusto (id_voluntario, id_gusto)
SELECT DISTINCT id_voluntario, id_gusto FROM asignaciones
ON CONFLICT DO NOTHING;

-- 10c. Profesión u oficio (categoría PROFESION del mismo catálogo; los
--      mismos nombres que agrega persona-mayor-service al arrancar, ver
--      CatalogoProfesiones). ~85% de las personas mayores registra una
--      (~15% de ellas, dos) y ~90% de los voluntarios, una.
INSERT INTO gusto (nombre, categoria)
SELECT nombre, 'PROFESION' FROM (VALUES
    ('Labores del hogar'), ('Agricultura o trabajo del campo'), ('Construcción'), ('Comercio o ventas'),
    ('Conducción o transporte'), ('Modistería o sastrería'), ('Docencia'), ('Salud (enfermería o medicina)'),
    ('Cocina'), ('Servicios generales o aseo'), ('Oficina o administración'), ('Vigilancia o seguridad'),
    ('Mecánica'), ('Carpintería o ebanistería'), ('Peluquería o belleza'), ('Artesanía'),
    ('Fuerza pública (militar o policía)'), ('Ingeniería o trabajo técnico'), ('Trabajo social o psicología'),
    ('Estudiante')
) AS v(nombre)
ON CONFLICT (nombre) DO NOTHING;

--      Oficios más comunes en personas mayores de Usme; los repetidos
--      salen más seguido.
WITH oficios AS (
    SELECT ARRAY['Labores del hogar', 'Labores del hogar', 'Labores del hogar', 'Modistería o sastrería',
                 'Cocina', 'Comercio o ventas', 'Servicios generales o aseo', 'Docencia',
                 'Salud (enfermería o medicina)', 'Peluquería o belleza', 'Agricultura o trabajo del campo',
                 'Artesanía'] AS mujeres,
           ARRAY['Construcción', 'Construcción', 'Agricultura o trabajo del campo', 'Conducción o transporte',
                 'Conducción o transporte', 'Comercio o ventas', 'Vigilancia o seguridad', 'Mecánica',
                 'Carpintería o ebanistería', 'Fuerza pública (militar o policía)', 'Docencia',
                 'Oficina o administración'] AS hombres
),
pm AS (
    SELECT u.id_usuario, u.genero, random() AS r
    FROM usuario u WHERE u.correo LIKE 'pm%@vitaplus.test'
),
elegidos AS (
    SELECT pm.id_usuario,
           (CASE WHEN pm.genero = 'Femenino' THEN o.mujeres ELSE o.hombres END)
               [1 + floor(random() * 12)::int + pm.id_usuario * 0] AS profesion
    FROM pm CROSS JOIN oficios o
    WHERE pm.r < 0.85
    UNION ALL
    SELECT pm.id_usuario,
           (CASE WHEN pm.genero = 'Femenino' THEN o.mujeres ELSE o.hombres END)
               [1 + floor(random() * 12)::int + pm.id_usuario * 0]
    FROM pm CROSS JOIN oficios o
    WHERE pm.r < 0.85 * 0.15
)
INSERT INTO persona_mayor_gusto (id_persona_mayor, id_gusto)
SELECT DISTINCT e.id_usuario, g.id_gusto
FROM elegidos e JOIN gusto g ON g.nombre = e.profesion
ON CONFLICT DO NOTHING;

INSERT INTO voluntario_gusto (id_voluntario, id_gusto)
SELECT v.id_usuario, g.id_gusto
FROM (
    SELECT u.id_usuario,
           (ARRAY['Estudiante', 'Estudiante', 'Estudiante', 'Docencia', 'Salud (enfermería o medicina)',
                  'Trabajo social o psicología', 'Oficina o administración', 'Ingeniería o trabajo técnico',
                  'Comercio o ventas', 'Cocina'])[1 + floor(random() * 10)::int + u.id_usuario * 0] AS profesion
    FROM usuario u
    WHERE u.correo LIKE 'vol%@vitaplus.test' AND random() < 0.90
) v
JOIN gusto g ON g.nombre = v.profesion
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

-- 11b. Datos de salud (enfermedades, alergias y discapacidades) del
--      catálogo condicion_salud. Lo llena salud-service al arrancar
--      (CatalogoCondicionSaludInicializador); por si esta base de datos
--      nunca lo ha arrancado, aquí se agregan las que usa este script,
--      con los mismos nombres y categorías, solo si faltan. Las
--      enfermedades siguen el perfil de salud (hipertensión, diabetes,
--      EPOC) y se agregan otras frecuentes en la vejez; ~25% tiene una
--      alergia y ~15% una discapacidad, más probable a mayor edad.
INSERT INTO condicion_salud (tipo, categoria, nombre, es_otra)
SELECT v.tipo, v.categoria, v.nombre, false
FROM (VALUES
    ('ENFERMEDAD', 'Cardiovascular', 'Hipertensión arterial'),
    ('ENFERMEDAD', 'Metabólica', 'Diabetes'),
    ('ENFERMEDAD', 'Metabólica', 'Colesterol o triglicéridos altos'),
    ('ENFERMEDAD', 'Metabólica', 'Hipotiroidismo'),
    ('ENFERMEDAD', 'Respiratoria', 'EPOC'),
    ('ENFERMEDAD', 'Osteomuscular', 'Artrosis'),
    ('ENFERMEDAD', 'Salud mental', 'Depresión'),
    ('ENFERMEDAD', 'Visual', 'Cataratas'),
    ('ENFERMEDAD', 'Digestiva', 'Gastritis o reflujo'),
    ('ALERGIA', 'Medicamentos', 'Penicilina'),
    ('ALERGIA', 'Medicamentos', 'Sulfas'),
    ('ALERGIA', 'Medicamentos', 'Aspirina'),
    ('ALERGIA', 'Medicamentos', 'Antiinflamatorios (ibuprofeno, naproxeno)'),
    ('ALERGIA', 'Medicamentos', 'Dipirona'),
    ('ALERGIA', 'Alimentos', 'Mariscos'),
    ('ALERGIA', 'Ambientales', 'Polen'),
    ('ALERGIA', 'Ambientales', 'Ácaros del polvo'),
    ('DISCAPACIDAD', 'Física', 'Dificultad para caminar'),
    ('DISCAPACIDAD', 'Física', 'Usa silla de ruedas'),
    ('DISCAPACIDAD', 'Visual', 'Baja visión'),
    ('DISCAPACIDAD', 'Auditiva', 'Pérdida auditiva parcial')
) AS v(tipo, categoria, nombre)
WHERE NOT EXISTS (SELECT 1 FROM condicion_salud c WHERE c.tipo = v.tipo AND c.nombre = v.nombre);

WITH elegidas AS (
    SELECT p.id_usuario, 'ENFERMEDAD' AS tipo, 'Hipertensión arterial' AS nombre FROM seed_tmp.seed_perfil p WHERE p.hta
    UNION ALL SELECT p.id_usuario, 'ENFERMEDAD', 'Diabetes' FROM seed_tmp.seed_perfil p WHERE p.dm
    UNION ALL SELECT p.id_usuario, 'ENFERMEDAD', 'EPOC' FROM seed_tmp.seed_perfil p WHERE p.epoc
    UNION ALL SELECT p.id_usuario, 'ENFERMEDAD', 'Colesterol o triglicéridos altos' FROM seed_tmp.seed_perfil p WHERE random() < 0.18
    UNION ALL SELECT p.id_usuario, 'ENFERMEDAD', 'Artrosis' FROM seed_tmp.seed_perfil p WHERE random() < 0.08 + p.edad * 0.003
    UNION ALL SELECT p.id_usuario, 'ENFERMEDAD', 'Hipotiroidismo' FROM seed_tmp.seed_perfil p WHERE random() < 0.08
    UNION ALL SELECT p.id_usuario, 'ENFERMEDAD', 'Cataratas' FROM seed_tmp.seed_perfil p WHERE random() < 0.04 + p.edad * 0.002
    UNION ALL SELECT p.id_usuario, 'ENFERMEDAD', 'Gastritis o reflujo' FROM seed_tmp.seed_perfil p WHERE random() < 0.12
    UNION ALL SELECT p.id_usuario, 'ENFERMEDAD', 'Depresión' FROM seed_tmp.seed_perfil p WHERE random() < 0.07
    UNION ALL
    SELECT p.id_usuario, 'ALERGIA',
           (ARRAY['Penicilina', 'Aspirina', 'Antiinflamatorios (ibuprofeno, naproxeno)', 'Sulfas',
                  'Mariscos', 'Polen', 'Ácaros del polvo', 'Dipirona'])[1 + floor(random() * 8)::int]
    FROM seed_tmp.seed_perfil p WHERE random() < 0.25
    UNION ALL
    SELECT p.id_usuario, 'DISCAPACIDAD',
           (ARRAY['Dificultad para caminar', 'Pérdida auditiva parcial', 'Baja visión',
                  'Dificultad para caminar', 'Usa silla de ruedas'])[1 + floor(random() * 5)::int]
    FROM seed_tmp.seed_perfil p WHERE random() < 0.02 + greatest(0, p.edad - 65) * 0.006
)
INSERT INTO persona_mayor_condicion_salud (id_persona_mayor, id_condicion_salud, severidad, detalle, fecha_registro)
SELECT DISTINCT ON (e.id_usuario, c.id_condicion_salud)
       e.id_usuario, c.id_condicion_salud,
       CASE WHEN e.tipo = 'DISCAPACIDAD' OR random() < 0.3 THEN NULL
            ELSE (ARRAY['LEVE', 'MODERADA', 'SEVERA'])[1 + floor(power(random(), 1.5) * 3)::int] END,
       CASE WHEN e.nombre = 'Penicilina' AND random() < 0.6 THEN 'Me da urticaria y picazón'
            WHEN e.nombre = 'Mariscos' AND random() < 0.6 THEN 'Se me hinchan los labios'
            WHEN e.nombre = 'Dificultad para caminar' AND random() < 0.5 THEN 'Camina con bastón'
            ELSE NULL END,
       r.hoy - floor(random() * 200)::int
FROM elegidas e
JOIN condicion_salud c ON c.tipo = e.tipo AND c.nombre = e.nombre
CROSS JOIN seed_tmp.seed_reloj r;

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

-- 14b. Acompañamiento a las próximas citas: ~15% las requiere y ~10% no lo
--      requiere pero la persona quiere ir acompañada. En la mitad, uno de
--      sus acompañantes aceptados ya confirmó con el punto de encuentro.
WITH proximas AS (
    SELECT c.id_cita, c.id_persona_mayor, random() AS r
    FROM cita_medica c
    JOIN usuario u ON u.id_usuario = c.id_persona_mayor AND u.correo LIKE 'pm%@vitaplus.test'
    CROSS JOIN seed_tmp.seed_reloj rel
    WHERE c.fecha > rel.hoy
)
UPDATE cita_medica c
   SET acompanamiento = CASE WHEN p.r < 0.15 THEN 'REQUERIDO' ELSE 'PREFERIDO' END
  FROM proximas p
 WHERE p.id_cita = c.id_cita AND p.r < 0.25;

WITH sin_acompanante AS (
    SELECT c.id_cita, c.fecha, c.hora,
           (SELECT pa.id_acompanante FROM persona_mayor_acompanante pa
             WHERE pa.id_persona_mayor = c.id_persona_mayor AND pa.estado = 'ACEPTADA'
             ORDER BY pa.id_acompanante LIMIT 1) AS id_acompanante
    FROM cita_medica c
    JOIN usuario u ON u.id_usuario = c.id_persona_mayor AND u.correo LIKE 'pm%@vitaplus.test'
    WHERE c.acompanamiento IN ('REQUERIDO', 'PREFERIDO') AND random() + c.id_cita * 0 < 0.5
)
UPDATE cita_medica c
   SET id_acompanante_cita = s.id_acompanante,
       punto_encuentro = (ARRAY['En la portería de su casa',
                                'En el paradero de la esquina de su casa',
                                'Paso por ella en carro a su casa',
                                'Nos vemos directamente en la entrada del hospital'])
                             [1 + floor(random() * 4)::int + c.id_cita * 0]
                         || ', ' || to_char(s.hora - interval '45 minutes', 'FMHH12:MI')
                         || CASE WHEN s.hora - interval '45 minutes' < time '12:00' THEN ' a. m.' ELSE ' p. m.' END,
       fecha_acompanamiento_aceptado = (SELECT ahora FROM seed_tmp.seed_reloj) - interval '1 day'
  FROM sin_acompanante s
 WHERE s.id_cita = c.id_cita AND s.id_acompanante IS NOT NULL;

-- 15. Actividades de las organizaciones (16 plantillas x 4 rondas =
--     64 actividades). La ronda 1 cae en los próximos 10 días (para ver
--     actividades "de hoy" y "de esta semana"), la 2 en los últimos 4
--     meses, la 3 entre 2 meses atrás y 2 meses adelante y la 4 en los
--     últimos 10 días (participación reciente para el inicio de la
--     organización).
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
            WHEN 3 THEN r.hoy + (floor(random() * 121)::int - 60)
            ELSE        r.hoy - (1 + floor(random() * 10)::int)
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
CROSS JOIN generate_series(1, 4) AS ronda
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

-- 15c. Hora de fin: cada actividad dura una o dos horas.
UPDATE actividad
SET hora_fin = to_char(hora::time + make_interval(hours => 1 + floor(random() * 2)::int), 'HH24:MI')
WHERE hora IS NOT NULL AND hora_fin IS NULL
  AND id_organizacion IN (SELECT id_organizacion FROM usuario WHERE correo LIKE 'org%@vitaplus.test');

-- 16. Participación en actividades (inscripciones + asistencia).
--     - Solo se inscriben personas mayores cuya relación con la
--       organización de la actividad esté ACEPTADA, y solo en
--       actividades visibles (las de la organización y las propuestas
--       ACEPTADAS).
--     - Cada actividad llena entre 70% y 90% de sus cupos.
--     - asistio queda en NULL para las actividades que aún no pasan y en
--       true/false (85%/15%) para las que ya pasaron, salvo ~30% de las
--       de los últimos 10 días, que quedan sin asistencia registrada (así
--       el inicio de la organización muestra la alerta "Asistencia sin
--       registrar").
--     - recordatorio_enviado_para se llena con el inicio de la actividad:
--       así el scheduler cree que ya avisó y no manda SMS.
WITH actividades_ficticias AS (
    SELECT a.id_actividad, a.id_organizacion, a.fecha,
           a.fecha + a.hora::time AS inicio,
           greatest(1, floor(a.cupos * (0.7 + random() * 0.2))::int) AS n_inscritos,
           a.fecha >= (SELECT hoy - 10 FROM seed_tmp.seed_reloj) AND random() < 0.30 AS sin_registro
    FROM actividad a
    WHERE a.id_organizacion IN (SELECT id_organizacion FROM usuario WHERE correo LIKE 'org%@vitaplus.test')
      AND (a.estado IS NULL OR a.estado = 'ACEPTADA')
),
candidatos AS (
    SELECT af.id_actividad, af.fecha, af.inicio, af.n_inscritos, af.sin_registro,
           pmo.id_persona_mayor,
           row_number() OVER (PARTITION BY af.id_actividad ORDER BY random()) AS rn
    FROM actividades_ficticias af
    JOIN persona_mayor_organizacion pmo
      ON pmo.id_organizacion = af.id_organizacion AND pmo.estado = 'ACEPTADA'
    JOIN usuario u ON u.id_usuario = pmo.id_persona_mayor AND u.correo LIKE 'pm%@vitaplus.test'
)
INSERT INTO participacion (id_persona_mayor, id_actividad, asistio, recordatorio_enviado_para)
SELECT c.id_persona_mayor, c.id_actividad,
       CASE WHEN c.inicio >= r.ahora OR c.sin_registro THEN NULL
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
--       acompañantes (cada una queda también en la tabla emergencia; ~2%
--       son de las últimas horas, para las alertas del inicio del
--       acompañante, que muestra las de las últimas 24 horas).
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
-- Mismos textos que CitaMedicaRecordatorioScheduler.
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

-- Emergencias: ~2% en las últimas 10 horas y ~5% en los últimos 21 días.
CREATE TABLE seed_tmp.seed_alertas AS
WITH sorteo AS (
    SELECT p.id_usuario, p.nombre_usuario, random() AS r
    FROM seed_tmp.seed_perfil p
)
SELECT s.id_usuario, s.nombre_usuario,
       CASE WHEN s.r < 0.02
            THEN r.ahora - make_interval(mins => 15 + floor(random() * 585)::int)
            ELSE r.ahora - make_interval(days => 1 + floor(random() * 20)::int,
                                         hours => floor(random() * 12)::int)
       END AS fecha
FROM sorteo s CROSS JOIN seed_tmp.seed_reloj r
WHERE s.r < 0.07;

-- fecha_hora va en hora de Colombia, como la guarda persona-mayor-service.
INSERT INTO emergencia (id_persona_mayor, fecha_hora)
SELECT id_usuario, fecha FROM seed_tmp.seed_alertas;

-- Alertas de emergencia -> acompañantes aceptados
INSERT INTO seed_tmp.seed_notif
SELECT ua.celular,
       'ALERTA DE EMERGENCIA: ' || al.nombre_usuario
           || ' ha activado una alerta desde VITA+. Por favor, verifica que se encuentre bien.',
       al.fecha
FROM seed_tmp.seed_alertas al
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

-- 17b. Constancia de aceptación de los términos de cada cuenta ficticia:
--      la fecha y hora en que se registró, con la versión vigente.
INSERT INTO aceptacion_terminos (id_usuario, acepto, fecha_aceptacion, version_terminos, origen)
SELECT id_usuario, true, fecha_creacion, '2026-10', 'REGISTRO'
FROM usuario
WHERE correo LIKE '%@vitaplus.test';

-- 17c. Notas de las organizaciones: ~30% de las personas mayores con
--      vínculo aceptado tienen de 1 a 3 notas de los últimos 3 meses; ~20%
--      de ellas se editó después.
INSERT INTO nota_persona_mayor (id_organizacion, id_persona_mayor, texto, id_autor, fecha_creacion, fecha_actualizacion)
SELECT n.id_organizacion, n.id_persona_mayor, n.texto, n.id_autor, n.fecha,
       CASE WHEN random() < 0.2 THEN n.fecha + make_interval(days => 1 + floor(random() * 5)::int) ELSE n.fecha END
FROM (
    SELECT po.id_organizacion, po.id_persona_mayor, cuenta.id_usuario AS id_autor,
           (ARRAY['Visita en casa: está bien de ánimo y pidió ayuda con la próxima cita de control.',
                  'Llamada de seguimiento. Dice que se ha sentido sola; invitarla al bingo del viernes.',
                  'Faltó a las últimas dos actividades. Confirmar con su acompañante si está bien.',
                  'Le entregamos el mercado del mes. Necesita pañales para adulto la próxima vez.',
                  'Le cuesta usar el celular; programar un acompañamiento en el taller de tecnología.',
                  'Su hija informó que cambió de EPS; actualizar los datos de salud.',
                  'Participó con mucho entusiasmo en la clase de baile.',
                  'Comentó dolor en la rodilla al caminar; sugerir valoración médica.'])
               [1 + floor(random() * 8)::int + po.id_persona_mayor * 0] AS texto,
           (r.ahora - make_interval(days => floor(random() * 90)::int, hours => floor(random() * 9)::int)) AS fecha
    FROM persona_mayor_organizacion po
    JOIN usuario pm ON pm.id_usuario = po.id_persona_mayor AND pm.correo LIKE 'pm%@vitaplus.test'
    JOIN usuario cuenta ON cuenta.id_organizacion = po.id_organizacion AND cuenta.correo LIKE 'org%@vitaplus.test'
    CROSS JOIN LATERAL generate_series(1, 1 + floor(random() * 3)::int + po.id_persona_mayor * 0) AS s(i)
    CROSS JOIN seed_tmp.seed_reloj r
    WHERE po.estado = 'ACEPTADA' AND random() + po.id_persona_mayor * 0 < 0.30
) n;

-- 18. Retiros: entre 2 y 4 personas mayores por organización eliminaron su
--     cuenta en los últimos 6 meses. Ya no existen como usuarios: solo
--     queda su nombre, la razón y el comentario, que la organización ve en
--     "Personas mayores" (como los guarda auth-service al borrar la cuenta).
--     "+ o.id_organizacion * 0" hace que cada organización sortee su
--     propio número de retiros (sin eso, Postgres lo sortea una sola vez).
WITH retiros AS (
    SELECT o.id_organizacion, random() AS r_razon, random() AS r_comentario,
           (ARRAY['María','Rosa','Carmen','Luz','Blanca','Gloria','Cecilia','Teresa','Amparo','Nubia',
                  'José','Luis','Pedro','Manuel','Alberto','Rafael','Hernando','Gustavo','Julio','Arturo'])
               [1 + floor(random() * 20)::int]
           || ' ' || (ARRAY['González','Rodríguez','Martínez','López','Pérez','Sánchez','Ramírez','Torres',
                            'Rivera','Gómez','Díaz','Reyes','Morales','Ortiz','Vargas','Suárez'])
               [1 + floor(random() * 16)::int]
           || ' ' || (ARRAY['González','Rodríguez','Martínez','López','Pérez','Sánchez','Ramírez','Torres',
                            'Rivera','Gómez','Díaz','Reyes','Morales','Ortiz','Vargas','Suárez'])
               [1 + floor(random() * 16)::int] AS nombre,
           r.ahora - make_interval(days => floor(random() * 180)::int, hours => floor(random() * 10)::int) AS fecha
    FROM (SELECT DISTINCT id_organizacion FROM usuario WHERE correo LIKE 'org%@vitaplus.test') o
    CROSS JOIN LATERAL generate_series(1, 2 + floor(random() * 3)::int + o.id_organizacion * 0) AS s(i)
    CROSS JOIN seed_tmp.seed_reloj r
),
con_razon AS (
    SELECT *,
           CASE WHEN r_razon < 0.30 THEN 'FALLECIMIENTO'
                WHEN r_razon < 0.45 THEN 'SALUD'
                WHEN r_razon < 0.58 THEN 'HOGAR_CUIDADO'
                WHEN r_razon < 0.72 THEN 'CAMBIO_RESIDENCIA'
                WHEN r_razon < 0.82 THEN 'DIFICULTAD_USO'
                WHEN r_razon < 0.90 THEN 'NO_LA_NECESITA'
                WHEN r_razon < 0.95 THEN 'PRIVACIDAD'
                ELSE 'OTRA' END AS razon
    FROM retiros
)
INSERT INTO retiro_cuenta (id_organizacion, nombre, rol, razon, comentario, fecha)
SELECT id_organizacion, nombre, 'PERSONA_MAYOR', razon,
       -- ~60% deja un comentario; "Otra" siempre lo explica
       CASE WHEN r_comentario < 0.6 OR razon = 'OTRA' THEN
           CASE razon
             WHEN 'FALLECIMIENTO'     THEN 'Mi mamá falleció hace unas semanas. Gracias por todo el acompañamiento que le dieron.'
             WHEN 'SALUD'             THEN 'Su salud empeoró y ya no puede asistir a las actividades.'
             WHEN 'HOGAR_CUIDADO'     THEN 'Ahora vive en un hogar geriátrico donde la cuidan todo el día.'
             WHEN 'CAMBIO_RESIDENCIA' THEN 'Se fue a vivir con su hija a Villavicencio.'
             WHEN 'DIFICULTAD_USO'    THEN 'Se me hace difícil usar la aplicación en el celular.'
             WHEN 'NO_LA_NECESITA'    THEN 'Por ahora no la necesito.'
             WHEN 'PRIVACIDAD'        THEN 'Prefiero no tener mis datos de salud en internet.'
             ELSE 'Cambié de número de celular y voy a crear una cuenta nueva.'
           END
       END,
       fecha
FROM con_razon;

-- 19. Preferencias de notificación: algunos usuarios apagaron algún tipo
--     desde su perfil. Solo se guarda lo que cambian (sin fila = activo),
--     y cada rol solo tiene los tipos que le muestra su perfil.
WITH cuentas AS (
    SELECT u.id_usuario, ro.nombre AS rol
    FROM usuario u
    JOIN usuario_rol ur ON ur.id_usuario = u.id_usuario
    JOIN rol ro ON ro.id_rol = ur.id_rol
    WHERE u.correo LIKE '%@vitaplus.test'
),
tipos (rol, tipo, probabilidad) AS (
    VALUES ('PERSONA_MAYOR', 'MEDICAMENTO', 0.05), ('PERSONA_MAYOR', 'CITA_MEDICA', 0.04),
           ('PERSONA_MAYOR', 'ACTIVIDAD', 0.10),   ('PERSONA_MAYOR', 'CUMPLEANOS', 0.12),
           ('ACOMPANANTE', 'EMERGENCIA', 0.02),    ('ACOMPANANTE', 'MEDICAMENTO', 0.15),
           ('ACOMPANANTE', 'CITA_MEDICA', 0.08),   ('ACOMPANANTE', 'CUMPLEANOS_PERSONA_MAYOR', 0.15),
           ('ACOMPANANTE', 'CUMPLEANOS', 0.12),    ('VOLUNTARIO', 'CUMPLEANOS', 0.15),
           ('ORGANIZACION', 'EMERGENCIA', 0.0),    ('ORGANIZACION', 'CUMPLEANOS_PERSONA_MAYOR', 0.25)
)
INSERT INTO preferencia_notificacion (id_usuario, tipo, activa)
SELECT c.id_usuario, t.tipo, false
FROM cuentas c JOIN tipos t ON t.rol = c.rol
-- "+ c.id_usuario * 0" hace que se sortee por cada usuario: sin eso,
-- Postgres sortea una sola vez por tipo y casi nunca queda ninguna.
WHERE random() + c.id_usuario * 0 < t.probabilidad
ON CONFLICT (id_usuario, tipo) DO NOTHING;

-- 20. Donaciones de acompañantes y voluntarios.
--     Catálogo de lo que se dona (y se compra): unidad, rango de cantidad
--     y, para lo perecedero, cuántos días después de la entrega vence.
CREATE TABLE seed_tmp.seed_catalogo AS
SELECT * FROM (VALUES
    ('ALIMENTOS',    'Arroz',                 'kg',       5,  25, NULL::int, NULL::int),
    ('ALIMENTOS',    'Lentejas',              'kg',       2,  10, NULL,      NULL),
    ('ALIMENTOS',    'Fríjol',                'kg',       2,  10, NULL,      NULL),
    ('ALIMENTOS',    'Aceite',                'litros',   2,   6, 240,       480),
    ('ALIMENTOS',    'Azúcar',                'kg',       2,  10, NULL,      NULL),
    ('ALIMENTOS',    'Panela',                'unidades', 4,  20, NULL,      NULL),
    ('ALIMENTOS',    'Leche en polvo',        'bolsas',   2,   8, 60,        240),
    ('ALIMENTOS',    'Atún enlatado',         'latas',    6,  24, 200,       500),
    ('ALIMENTOS',    'Avena en hojuelas',     'bolsas',   2,  10, 90,        300),
    ('ALIMENTOS',    'Galletas',              'paquetes', 4,  20, 30,        150),
    ('ALIMENTOS',    'Café',                  'libras',   1,   5, NULL,      NULL),
    ('ASEO',         'Papel higiénico',       'paquetes', 2,   8, NULL,      NULL),
    ('ASEO',         'Jabón de baño',         'unidades', 6,  24, NULL,      NULL),
    ('ASEO',         'Crema dental',          'unidades', 4,  12, 300,       700),
    ('ASEO',         'Pañales para adulto',   'paquetes', 2,  10, NULL,      NULL),
    ('ASEO',         'Toallas húmedas',       'paquetes', 3,  12, 200,       500),
    ('ROPA',         'Cobijas',               'unidades', 1,   5, NULL,      NULL),
    ('ROPA',         'Chaquetas',             'unidades', 1,   4, NULL,      NULL),
    ('ROPA',         'Medias',                'pares',    3,  12, NULL,      NULL),
    ('ROPA',         'Sacos de lana',         'unidades', 1,   4, NULL,      NULL),
    ('ROPA',         'Pijamas',               'unidades', 1,   4, NULL,      NULL),
    ('MEDICAMENTOS', 'Acetaminofén',          'cajas',    2,  10, 120,       600),
    ('MEDICAMENTOS', 'Gasas estériles',       'paquetes', 2,  10, 300,       900),
    ('MEDICAMENTOS', 'Alcohol antiséptico',   'frascos',  1,   6, 300,       900),
    ('MEDICAMENTOS', 'Tapabocas',             'cajas',    1,   5, NULL,      NULL),
    ('OTRO',         'Juegos de mesa',        'unidades', 1,   3, NULL,      NULL),
    ('OTRO',         'Sillas plásticas',      'unidades', 2,   8, NULL,      NULL),
    ('OTRO',         'Libros de lectura',     'unidades', 2,  10, NULL,      NULL),
    ('OTRO',         'Lana para tejer',       'ovillos',  3,  15, NULL,      NULL)
) AS v(categoria, descripcion, unidad, minimo, maximo, vence_min, vence_max);

--     Quién puede donar a qué organización, igual que en donacion-service:
--     el voluntario a sus organizaciones (ACEPTADA); el acompañante a las
--     organizaciones de las personas mayores que acompaña (los dos
--     vínculos ACEPTADA).
CREATE TABLE seed_tmp.seed_donantes AS
SELECT vo.id_voluntario AS id_donante, 'VOLUNTARIO' AS rol, vo.id_organizacion
FROM voluntario_organizacion vo
JOIN usuario u ON u.id_usuario = vo.id_voluntario AND u.correo LIKE 'vol%@vitaplus.test'
WHERE vo.estado = 'ACEPTADA'
UNION
SELECT pa.id_acompanante, 'ACOMPANANTE', po.id_organizacion
FROM persona_mayor_acompanante pa
JOIN persona_mayor_organizacion po ON po.id_persona_mayor = pa.id_persona_mayor AND po.estado = 'ACEPTADA'
JOIN usuario u ON u.id_usuario = pa.id_acompanante AND u.correo LIKE 'acomp%@vitaplus.test'
WHERE pa.estado = 'ACEPTADA';

--     Cada voluntario hace de 1 a 3 donaciones a su organización; cada par
--     acompañante-organización, una con 35% de probabilidad. Estados:
--     12% programadas en las próximas 3 semanas, 3% programadas con la
--     fecha ya pasada (la organización debe resolverlas), 60% recibidas,
--     10% no entregadas y 15% canceladas. Los ids se asignan aquí para
--     poder enlazar los artículos y el inventario.
CREATE TABLE seed_tmp.seed_donaciones AS
WITH base AS (
    SELECT d.id_donante, d.rol, d.id_organizacion,
           random() AS r_estado, random() AS r_tipo, random() AS r_hora, random() AS r_obs, random() AS r_nota
    FROM seed_tmp.seed_donantes d
    CROSS JOIN LATERAL generate_series(
        1,
        CASE WHEN d.rol = 'VOLUNTARIO' THEN 1 + floor(random() * 3)::int
             ELSE (random() < 0.35)::int END + d.id_donante * 0
    ) AS s(i)
),
con_estado AS (
    SELECT b.*,
           CASE WHEN r_estado < 0.15 THEN 'PROGRAMADA'
                WHEN r_estado < 0.75 THEN 'RECIBIDA'
                WHEN r_estado < 0.85 THEN 'NO_ENTREGADA'
                ELSE 'CANCELADA' END AS estado,
           CASE WHEN r_estado < 0.12 THEN r.hoy + (1 + floor(random() * 21))::int
                WHEN r_estado < 0.15 THEN r.hoy - (1 + floor(random() * 6))::int
                WHEN r_estado < 0.85 THEN r.hoy - (1 + floor(random() * 180))::int
                ELSE r.hoy + (floor(random() * 200) - 180)::int
           END AS fecha_entrega,
           CASE WHEN r_hora < 0.7 THEN make_time(8 + floor(random() * 9)::int, (floor(random() * 2) * 30)::int, 0) END
               AS hora_entrega,
           r.ahora
    FROM base b CROSS JOIN seed_tmp.seed_reloj r
),
con_registro AS (
    SELECT c.*,
           -- Se programa de 1 a 14 días antes de la fecha de entrega, nunca en el futuro.
           LEAST(c.fecha_entrega - (1 + floor(random() * 14))::int
                     + make_interval(hours => 7 + floor(random() * 13)::int, mins => floor(random() * 60)::int),
                 c.ahora - make_interval(hours => 1 + floor(random() * 48)::int)) AS fecha_registro
    FROM con_estado c
)
SELECT (SELECT coalesce(max(id_donacion), 0) FROM donacion) + row_number() OVER (ORDER BY random()) AS id_donacion,
       c.*,
       CASE c.estado
         WHEN 'RECIBIDA' THEN c.fecha_entrega + coalesce(c.hora_entrega, time '10:00')
                              + make_interval(mins => floor(random() * 180)::int)
         WHEN 'NO_ENTREGADA' THEN LEAST(c.fecha_entrega + time '18:00' + make_interval(days => floor(random() * 3)::int),
                                        c.ahora)
         WHEN 'CANCELADA' THEN LEAST(c.fecha_registro + make_interval(days => floor(random() * 5)::int,
                                                                       hours => 1 + floor(random() * 8)::int),
                                     c.ahora)
       END AS fecha_respuesta
FROM con_registro c;

INSERT INTO donacion (id_donacion, id_donante, rol_donante, id_organizacion, fecha_entrega, hora_entrega,
                      observaciones, estado, nota_organizacion, fecha_registro, fecha_respuesta)
SELECT d.id_donacion, d.id_donante, d.rol, d.id_organizacion, d.fecha_entrega, d.hora_entrega,
       CASE WHEN d.r_obs < 0.40 THEN
           (ARRAY['Llevo las cosas en carro; necesito ayuda para descargar.',
                  'Paso en la mañana antes de ir al trabajo.',
                  'Es una colecta que hicimos en mi barrio.',
                  'Si no estoy a la hora, mi hermano lleva la donación.',
                  'Los alimentos son para el mercado de fin de mes.',
                  'Puedo llevar más si les hace falta algo.'])[1 + floor(d.r_obs * 15)::int]
       END,
       d.estado,
       CASE WHEN d.estado = 'NO_ENTREGADA' AND d.r_nota < 0.6 THEN
           (ARRAY['Te esperamos el día acordado, pero nadie vino.',
                  'Te llamamos al celular y no contestaste.',
                  'La sede estuvo cerrada ese día; puedes volver a programarla.'])[1 + floor(d.r_nota * 5)::int]
       END,
       d.fecha_registro,
       d.fecha_respuesta
FROM seed_tmp.seed_donaciones d;

--     Artículos de cada donación: ~18% son solo dinero; las demás traen de
--     1 a 3 cosas del catálogo (y ~10% además algo de dinero). De lo
--     recibido, ~15% de los artículos llegó incompleto.
CREATE TABLE seed_tmp.seed_articulos AS
WITH cosas AS (
    SELECT d.id_donacion, d.estado, d.fecha_entrega, c.categoria, c.descripcion, c.unidad,
           (c.minimo + floor(random() * (c.maximo - c.minimo + 1)))::numeric AS cantidad,
           CASE WHEN c.vence_min IS NOT NULL
                THEN d.fecha_entrega + (c.vence_min + floor(random() * (c.vence_max - c.vence_min + 1)))::int
           END AS fecha_vencimiento
    FROM seed_tmp.seed_donaciones d
    CROSS JOIN LATERAL (
        SELECT * FROM seed_tmp.seed_catalogo c
        WHERE d.r_tipo >= 0.18
        ORDER BY random() + d.id_donacion * 0
        LIMIT 1 + floor(random() * 3)::int
    ) c
    UNION ALL
    SELECT d.id_donacion, d.estado, d.fecha_entrega, 'DINERO',
           CASE WHEN random() < 0.6 THEN 'Aporte en efectivo' ELSE 'Transferencia bancaria' END,
           'COP', (20000 + floor(random() * 29) * 10000)::numeric, NULL::date
    FROM seed_tmp.seed_donaciones d
    WHERE d.r_tipo < 0.28
)
SELECT (SELECT coalesce(max(id_articulo), 0) FROM donacion_articulo)
           + row_number() OVER (ORDER BY c.id_donacion, c.categoria, c.descripcion) AS id_articulo,
       c.*,
       CASE WHEN c.estado <> 'RECIBIDA' THEN NULL
            WHEN c.categoria = 'DINERO' OR random() >= 0.15 THEN c.cantidad
            ELSE greatest(1, round(c.cantidad * (0.5 + random() * 0.4)))
       END AS cantidad_recibida
FROM cosas c;

INSERT INTO donacion_articulo (id_articulo, id_donacion, categoria, descripcion, cantidad, unidad,
                               fecha_vencimiento, cantidad_recibida)
SELECT id_articulo, id_donacion, categoria, descripcion, cantidad, unidad, fecha_vencimiento, cantidad_recibida
FROM seed_tmp.seed_articulos;

-- 21. Inventario de cada organización. Entradas: lo recibido en
--     donaciones (como lo suma donacion-service al marcarlas recibidas),
--     compras de los últimos 4 meses y aportes externos de dinero (rifas,
--     bazares). Un artículo es la combinación nombre + categoría + unidad.
CREATE TABLE seed_tmp.seed_cuentas_org AS
SELECT id_organizacion, min(id_usuario) AS id_usuario
FROM usuario WHERE correo LIKE 'org%@vitaplus.test'
GROUP BY id_organizacion;

CREATE TABLE seed_tmp.seed_entradas AS
SELECT d.id_organizacion, a.categoria, a.descripcion AS nombre, a.unidad, a.cantidad_recibida AS cantidad,
       a.fecha_vencimiento AS vence, d.fecha_respuesta AS fecha, 'DONACION' AS motivo,
       'Donación de ' || u.nombre_usuario AS detalle, d.id_donacion
FROM seed_tmp.seed_articulos a
JOIN seed_tmp.seed_donaciones d ON d.id_donacion = a.id_donacion
JOIN usuario u ON u.id_usuario = d.id_donante
WHERE d.estado = 'RECIBIDA' AND a.cantidad_recibida > 0
UNION ALL
-- Compras: cada organización compra de 4 a 7 cosas de alimentos, aseo y
-- medicamentos.
SELECT o.id_organizacion, c.categoria, c.descripcion, c.unidad,
       (2 * (c.minimo + floor(random() * (c.maximo - c.minimo + 1))))::numeric,
       CASE WHEN c.vence_min IS NOT NULL
            THEN c.fecha::date + (c.vence_min + floor(random() * (c.vence_max - c.vence_min + 1)))::int
       END,
       c.fecha, 'COMPRA',
       'Compra en ' || (ARRAY['el supermercado', 'la plaza de mercado de Usme', 'la droguería del barrio',
                              'el mayorista'])[1 + floor(random() * 4)::int],
       NULL::bigint
FROM seed_tmp.seed_cuentas_org o
CROSS JOIN LATERAL (
    SELECT cat.*, (r.hoy - (1 + floor(random() * 120))::int)
                      + make_interval(hours => 8 + floor(random() * 9)::int) AS fecha
    FROM seed_tmp.seed_catalogo cat CROSS JOIN seed_tmp.seed_reloj r
    WHERE cat.categoria IN ('ALIMENTOS', 'ASEO', 'MEDICAMENTOS')
    ORDER BY random() + o.id_organizacion * 0
    LIMIT 4 + floor(random() * 4)::int
) c
UNION ALL
-- Aportes de dinero por fuera de la aplicación (la mitad de las organizaciones).
SELECT o.id_organizacion, 'DINERO', 'Aporte en efectivo', 'COP',
       (50000 + floor(random() * 16) * 25000)::numeric, NULL,
       (r.hoy - (1 + floor(random() * 120))::int) + make_interval(hours => 10 + floor(random() * 8)::int),
       'DONACION_EXTERNA',
       (ARRAY['Rifa de la fundación', 'Bazar de fin de mes',
              'Aporte de la junta de acción comunal'])[1 + floor(random() * 3)::int],
       NULL
FROM seed_tmp.seed_cuentas_org o CROSS JOIN seed_tmp.seed_reloj r
WHERE random() < 0.5;

--     Salidas de cada artículo (después de su última entrada): ~10% se
--     agotó, ~15% quedó por debajo de su mínimo y el resto entregó o usó
--     entre 20% y 70%. De lo que ya venció, ~70% se sacó (la última salida
--     con motivo VENCIDO) y ~30% sigue guardado, para ver la alerta.
CREATE TABLE seed_tmp.seed_inventario AS
WITH articulos AS (
    SELECT id_organizacion, categoria, nombre, unidad,
           sum(cantidad) AS entradas, min(vence) AS vence, max(fecha) AS ultima_entrada,
           random() AS r_salida, random() AS r_minimo, random() AS r_motivo1, random() AS r_motivo2,
           random() AS r_fecha1, random() AS r_fecha2, random() AS r_detalle
    FROM seed_tmp.seed_entradas
    GROUP BY id_organizacion, categoria, nombre, unidad
),
con_salidas AS (
    SELECT a.*, r.ahora,
           (a.vence < r.hoy AND a.r_salida < 0.7) AS vencido_retirado,
           -- El dinero se redondea a miles; lo demás, a unidades.
           CASE WHEN a.vence < r.hoy AND a.r_salida < 0.7 THEN a.entradas
                WHEN a.r_salida < 0.10 THEN a.entradas
                WHEN a.r_salida < 0.25 THEN a.entradas - greatest(1, round(a.entradas * 0.1))
                WHEN a.unidad = 'COP' THEN round(a.entradas * (0.2 + a.r_minimo * 0.5) / 1000) * 1000
                ELSE round(a.entradas * (0.2 + a.r_minimo * 0.5))
           END AS salidas
    FROM articulos a CROSS JOIN seed_tmp.seed_reloj r
),
partidas AS (
    SELECT s.*,
           CASE WHEN s.salidas >= 2 AND s.unidad <> 'COP' THEN round(s.salidas * 0.6)
                WHEN s.salidas >= 2000 AND s.unidad = 'COP' THEN round(s.salidas * 0.6 / 1000) * 1000
                ELSE s.salidas END AS salida1,
           s.ultima_entrada + (s.ahora - s.ultima_entrada) * (0.2 + s.r_fecha1 * 0.5) AS fecha1
    FROM con_salidas s
)
SELECT (SELECT coalesce(max(id_inventario), 0) FROM inventario_articulo)
           + row_number() OVER (ORDER BY p.id_organizacion, p.categoria, p.nombre) AS id_inventario,
       p.*,
       p.salidas - p.salida1 AS salida2,
       p.fecha1 + (p.ahora - p.fecha1) * (0.2 + p.r_fecha2 * 0.7) AS fecha2,
       p.entradas - p.salidas AS existencia,
       CASE WHEN p.categoria = 'DINERO' THEN NULL
            -- Los "por agotarse" tienen un mínimo por encima de lo que queda.
            WHEN p.r_salida >= 0.10 AND p.r_salida < 0.25 AND NOT coalesce(p.vencido_retirado, false)
                 THEN (p.entradas - p.salidas) + greatest(1, round(p.entradas * 0.15))
            WHEN p.r_minimo < 0.4 THEN greatest(1, round(p.entradas * 0.2))
       END AS cantidad_minima
FROM partidas p;

INSERT INTO inventario_articulo (id_inventario, id_organizacion, nombre, categoria, unidad, cantidad,
                                 cantidad_minima, fecha_vencimiento, fecha_actualizacion)
SELECT i.id_inventario, i.id_organizacion, i.nombre, i.categoria, i.unidad, i.existencia,
       i.cantidad_minima, i.vence,
       CASE WHEN i.salidas > 0 THEN greatest(i.ultima_entrada, i.fecha1,
                                             CASE WHEN i.salida2 > 0 THEN i.fecha2 END)
            ELSE i.ultima_entrada END
FROM seed_tmp.seed_inventario i;

--     Movimientos: las entradas, registradas por la cuenta de la organización.
INSERT INTO inventario_movimiento (id_inventario, tipo, cantidad, motivo, detalle, id_donacion, id_usuario, fecha)
SELECT i.id_inventario, 'ENTRADA', e.cantidad, e.motivo, e.detalle, e.id_donacion, co.id_usuario, e.fecha
FROM seed_tmp.seed_entradas e
JOIN seed_tmp.seed_inventario i
  ON i.id_organizacion = e.id_organizacion AND i.categoria = e.categoria
 AND i.nombre = e.nombre AND i.unidad = e.unidad
JOIN seed_tmp.seed_cuentas_org co ON co.id_organizacion = e.id_organizacion;

--     Las salidas (una o dos por artículo), con un motivo y un detalle
--     coherentes con lo que es.
INSERT INTO inventario_movimiento (id_inventario, tipo, cantidad, motivo, detalle, id_donacion, id_usuario, fecha)
SELECT s.id_inventario, 'SALIDA', s.cantidad, s.motivo,
       CASE s.motivo
         WHEN 'VENCIDO' THEN 'Se venció antes de entregarlo'
         WHEN 'DANADO'  THEN 'Se mojó en la bodega'
         WHEN 'ENTREGA' THEN (ARRAY['Mercados del mes para las personas mayores',
                                    'Entrega en la jornada de los sábados',
                                    'Kits para las personas mayores que viven solas',
                                    'Entrega a domicilio a personas con movilidad reducida'])[1 + floor(s.r_detalle * 4)::int]
         ELSE CASE WHEN s.unidad = 'COP'
                   THEN (ARRAY['Compra de mercados',
                               'Transporte para la salida recreativa',
                               'Refrigerio de la jornada de salud',
                               'Compra de pañales para adulto'])[1 + floor(s.r_detalle * 4)::int]
                   ELSE (ARRAY['Refrigerio de la actividad de baile',
                               'Almuerzo comunitario',
                               'Taller de cocina tradicional',
                               'Jornada de salud'])[1 + floor(s.r_detalle * 4)::int]
              END
       END,
       NULL, co.id_usuario, s.fecha
FROM (
    SELECT i.id_inventario, i.id_organizacion, i.unidad, i.r_detalle, i.salida1 AS cantidad, i.fecha1 AS fecha,
           CASE WHEN i.unidad = 'COP' THEN 'USO'
                WHEN i.r_motivo1 < 0.65 THEN 'ENTREGA'
                WHEN i.r_motivo1 < 0.95 THEN 'USO'
                ELSE 'DANADO' END AS motivo
    FROM seed_tmp.seed_inventario i
    WHERE i.salida1 > 0
    UNION ALL
    SELECT i.id_inventario, i.id_organizacion, i.unidad, i.r_detalle, i.salida2, i.fecha2,
           CASE WHEN i.vencido_retirado THEN 'VENCIDO'
                WHEN i.unidad = 'COP' THEN 'USO'
                WHEN i.r_motivo2 < 0.65 THEN 'ENTREGA'
                ELSE 'USO' END
    FROM seed_tmp.seed_inventario i
    WHERE i.salida2 > 0
) s
JOIN seed_tmp.seed_cuentas_org co ON co.id_organizacion = s.id_organizacion;

--     Los ids de donaciones, artículos e inventario se asignaron a mano:
--     se corren las secuencias para que la aplicación siga después de ellos.
SELECT setval(pg_get_serial_sequence('donacion', 'id_donacion'), (SELECT max(id_donacion) FROM donacion));
SELECT setval(pg_get_serial_sequence('donacion_articulo', 'id_articulo'), (SELECT max(id_articulo) FROM donacion_articulo));
SELECT setval(pg_get_serial_sequence('inventario_articulo', 'id_inventario'), (SELECT max(id_inventario) FROM inventario_articulo));

-- 22. Calificaciones de la plataforma (RF-70): cerca de la mitad de las
--     cuentas calificó desde su perfil. La mayoría está contenta; unas
--     pocas dan 0 a 2 estrellas, casi siempre con una oportunidad de
--     mejora. Una de cada cinco cambió su calificación después.
CREATE TABLE seed_tmp.seed_calificaciones AS
SELECT u.id_usuario,
       CASE WHEN r < 0.40 THEN 5 WHEN r < 0.70 THEN 4 WHEN r < 0.85 THEN 3
            WHEN r < 0.93 THEN 2 WHEN r < 0.97 THEN 1 ELSE 0 END AS estrellas,
       random() AS r_comentario,
       random() AS r_texto,
       (SELECT ahora FROM seed_tmp.seed_reloj) - (floor(random() * 60) || ' days')::interval
           - (floor(random() * 600) || ' minutes')::interval AS creada,
       random() < 0.2 AS cambio_despues
FROM (SELECT id_usuario, random() AS r FROM usuario WHERE correo LIKE '%@vitaplus.test') u
WHERE random() < 0.5;

INSERT INTO calificacion_plataforma (id_usuario, estrellas, comentario, fecha_creacion, fecha_actualizacion)
SELECT c.id_usuario,
       c.estrellas,
       CASE
           WHEN c.estrellas <= 2 AND c.r_comentario < 0.9 THEN
               (ARRAY['La letra es pequeña en el celular y me cuesta leer los recordatorios.',
                      'A veces no me llega el SMS del medicamento.',
                      'No encontré cómo cambiar el horario de un medicamento.',
                      'Me gustaría que me explicaran mejor cómo usar el botón de emergencia.'])
                   [1 + floor(c.r_texto * 4)::int]
           WHEN c.estrellas = 3 AND c.r_comentario < 0.5 THEN
               (ARRAY['Está bien, pero me gustaría ver las actividades de todas las organizaciones.',
                      'Sería útil poder imprimir la lista de medicamentos.',
                      'Algunas pantallas tienen muchas opciones.'])
                   [1 + floor(c.r_texto * 3)::int]
           WHEN c.estrellas >= 4 AND c.r_comentario < 0.3 THEN
               (ARRAY['Me ayuda mucho con los horarios de los medicamentos.',
                      'Muy fácil de usar, y el lector de voz me sirve.',
                      'Gracias a la plataforma conocí actividades cerca de mi casa.',
                      'Me tranquiliza saber que mis acompañantes reciben las alertas.'])
                   [1 + floor(c.r_texto * 4)::int]
       END,
       c.creada,
       CASE WHEN c.cambio_despues
            THEN least(c.creada + (floor(1 + random() * 20) || ' days')::interval,
                       (SELECT ahora FROM seed_tmp.seed_reloj))
            ELSE c.creada END
FROM seed_tmp.seed_calificaciones c;

-- 23. Registro de auditoría de los datos de salud (RNF-25) de los últimos
--     30 días, como lo dejan salud, acompañante y analítica: quién
--     consultó o cambió los datos de salud de quién. Solo con vínculos
--     aceptados, salvo unos pocos intentos sin vínculo, que quedan con 403.
CREATE TABLE seed_tmp.seed_auditoria AS
-- Seguimiento del acompañante (acompanante-service): entre 0 y 8 consultas por vínculo.
SELECT pma.id_acompanante AS id_usuario, 'ACOMPANANTE' AS rol, pma.id_persona_mayor,
       (ARRAY['SIGNOS_VITALES', 'MEDICAMENTOS', 'CITAS_MEDICAS'])[1 + floor(random() * 3)::int] AS recurso,
       'CONSULTA' AS accion, 'GET' AS metodo, NULL::text AS ruta, 200 AS estado
FROM persona_mayor_acompanante pma
-- "+ 0 * pma.id_acompanante" hace que el LATERAL dependa de cada vínculo:
-- sin eso, PostgreSQL lo calcula una sola vez y todos tendrían las mismas.
CROSS JOIN LATERAL generate_series(1, floor(random() * 9)::int + 0 * pma.id_acompanante) AS veces(n)
WHERE pma.estado = 'ACEPTADA'
  AND pma.id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
UNION ALL
-- Lo que el acompañante gestiona por la persona mayor (salud-service): a veces crea o edita.
SELECT pma.id_acompanante, 'ACOMPANANTE', pma.id_persona_mayor,
       (ARRAY['MEDICAMENTOS', 'CITAS_MEDICAS', 'CONDICIONES_SALUD'])[1 + floor(random() * 3)::int],
       (ARRAY['CONSULTA', 'CONSULTA', 'CREACION', 'EDICION'])[1 + floor(random() * 4)::int],
       NULL, NULL, 200
FROM persona_mayor_acompanante pma
WHERE pma.estado = 'ACEPTADA' AND random() < 0.35
  AND pma.id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
UNION ALL
-- La organización consulta o registra signos vitales (salud-service).
SELECT co.id_usuario, 'ORGANIZACION', pmo.id_persona_mayor, 'SIGNOS_VITALES',
       CASE WHEN random() < 0.7 THEN 'CONSULTA' ELSE 'CREACION' END, NULL, NULL, 200
FROM persona_mayor_organizacion pmo
JOIN seed_tmp.seed_cuentas_org co ON co.id_organizacion = pmo.id_organizacion
WHERE pmo.estado = 'ACEPTADA' AND random() < 0.4
UNION ALL
-- Reportes de salud y de perfiles de cada organización (analitica-service): no son de una persona.
SELECT co.id_usuario, 'ORGANIZACION', NULL,
       CASE WHEN random() < 0.6 THEN 'REPORTE_SALUD' ELSE 'PERFILES' END, 'CONSULTA', 'GET', NULL, 200
FROM seed_tmp.seed_cuentas_org co
CROSS JOIN LATERAL generate_series(1, 3 + floor(random() * 6)::int + 0 * co.id_usuario) AS veces(n)
UNION ALL
-- Intentos sin vínculo: un acompañante pide el seguimiento de alguien que no acompaña.
SELECT a.id_usuario, 'ACOMPANANTE', p.id_usuario, 'MEDICAMENTOS', 'CONSULTA', 'GET', NULL, 403
FROM (SELECT id_usuario FROM usuario WHERE correo LIKE 'acomp%@vitaplus.test' ORDER BY random() LIMIT 6) a
CROSS JOIN LATERAL (
    SELECT u.id_usuario FROM usuario u
    WHERE u.correo LIKE 'pm%@vitaplus.test'
      AND NOT EXISTS (SELECT 1 FROM persona_mayor_acompanante x
                      WHERE x.id_acompanante = a.id_usuario AND x.id_persona_mayor = u.id_usuario)
    ORDER BY random() LIMIT 1
) p;

INSERT INTO auditoria_salud (fecha, id_usuario, rol, id_persona_mayor, recurso, accion, metodo, ruta, estado)
SELECT (SELECT ahora FROM seed_tmp.seed_reloj)
           - (floor(random() * 30) || ' days')::interval
           - (floor(random() * 720) || ' minutes')::interval,
       a.id_usuario, a.rol, a.id_persona_mayor, a.recurso, a.accion,
       CASE a.accion WHEN 'CONSULTA' THEN 'GET' WHEN 'CREACION' THEN 'POST' ELSE 'PUT' END,
       -- La ruta como la registra cada servicio.
       CASE
           WHEN a.rol = 'ORGANIZACION' AND a.recurso = 'REPORTE_SALUD' THEN '/api/analitica/salud'
           WHEN a.rol = 'ORGANIZACION' AND a.recurso = 'PERFILES' THEN '/api/analitica/perfiles'
           WHEN a.rol = 'ORGANIZACION' THEN '/api/organizacion/signos-vitales/' || a.id_persona_mayor
           WHEN a.metodo = 'GET' THEN '/api/acompanante/seguimiento/' || a.id_persona_mayor || '/'
               || CASE a.recurso WHEN 'SIGNOS_VITALES' THEN 'signos-vitales'
                                 WHEN 'MEDICAMENTOS' THEN 'medicamentos' ELSE 'citas-medicas' END
           ELSE '/api/acompanante/personas-mayores/' || a.id_persona_mayor || '/'
               || CASE a.recurso WHEN 'MEDICAMENTOS' THEN 'medicamentos'
                                 WHEN 'CITAS_MEDICAS' THEN 'citas-medicas' ELSE 'condiciones-salud' END
               -- Una edición lleva el id del registro, como en la aplicación.
               || CASE WHEN a.accion = 'EDICION' THEN '/' || (1 + floor(random() * 900)::int) ELSE '' END
       END,
       a.estado
FROM seed_tmp.seed_auditoria a;

-- 24. Más personas mayores para la Fundación Entrenubes Usme (org01), con
--     perfiles bien diferenciados, para que el perfilamiento con K-means
--     (Analítica -> Perfiles) muestre grupos claros. Son 160 personas
--     (pmen001 a pmen160), 40 de cada arquetipo:
--       1. Autónomas y estables: 61 a 69 años, casi sin enfermedades ni
--          medicamentos, signos vitales normales, viven acompañadas y
--          tienen dos acompañantes.
--       2. Salud compleja: 70 a 79 años, 4 a 6 enfermedades (algunas
--          severas) y 6 a 8 medicamentos, con la presión casi controlada.
--       3. Signos vitales por vigilar: 66 a 75 años, presión alta, pulso
--          rápido, saturación baja (EPOC) y obesidad; casi todas sus
--          mediciones quedan fuera de rango.
--       4. Edad avanzada que necesita acompañamiento: 86 a 95 años, viven
--          solas, sin acompañantes, con discapacidades, citas que requieren
--          acompañamiento y 3 a 5 emergencias en los últimos 90 días.
--     Cada persona tiene todo lo que usa el modelo (signos vitales de los
--     últimos 180 días, con peso y estatura en la más reciente, y citas),
--     así que ninguna queda excluida ni con valores imputados. Las personas
--     que las secciones anteriores le asignaron a la organización siguen
--     ahí, con perfiles al azar, repartidas entre los grupos.
--     Van al final para que las secciones anteriores no les den también un
--     perfil al azar; por eso no tienen notificaciones ni notas.
CREATE TABLE seed_tmp.seed_entrenubes AS
WITH base AS MATERIALIZED (
    SELECT s.i,
           1 + (s.i - 1) % 4 AS arquetipo,
           random() AS r_genero, random() AS r_edad, random() AS r_eps, random() AS r_est,
           random() AS r_a, random() AS r_b, random() AS r_c, random() AS r_d,
           random() AS r_e, random() AS r_f, random() AS r_g, random() AS r_h,
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
           -- Barrios cercanos a la sede (Usme Centro)
           (ARRAY['Usme Centro','Comuneros','Alfonso López','El Uval','La Aurora','Santa Librada',
                  'Yomasa','Tocaimita'])[1 + floor(random() * 8)::int] AS barrio,
           (ARRAY['Calle','Carrera','Diagonal','Transversal'])[1 + floor(random() * 4)::int]
               || ' ' || (80 + floor(random() * 30)::int) || ' Sur # '
               || (1 + floor(random() * 15)::int) || '-' || (2 + floor(random() * 80)::int) AS via
    FROM generate_series(1, 160) AS s(i)
)
SELECT b.i, b.arquetipo,
       NULL::integer AS id_usuario,
       CASE WHEN b.r_genero < 0.6 THEN 'Femenino' ELSE 'Masculino' END AS genero,
       (CASE WHEN b.r_genero < 0.6 THEN b.nombre_f ELSE b.nombre_m END)
           || ' ' || b.apellido1 || ' ' || b.apellido2 AS nombre_usuario,
       'pmen' || lpad(b.i::text, 3, '0') || '@vitaplus.test' AS correo,
       '+5700008' || lpad(b.i::text, 5, '0') AS celular,
       b.via || ', ' || b.barrio AS direccion,
       (CASE b.arquetipo WHEN 1 THEN 61 + floor(b.r_edad * 9)
                         WHEN 2 THEN 70 + floor(b.r_edad * 10)
                         WHEN 3 THEN 66 + floor(b.r_edad * 10)
                         ELSE        86 + floor(b.r_edad * 10) END)::int AS edad,
       CASE WHEN b.r_eps < 0.45 THEN 'Capital Salud'
            WHEN b.r_eps < 0.70 THEN 'Nueva EPS'
            WHEN b.r_eps < 0.88 THEN 'Compensar'
            ELSE 'Famisanar' END AS eps,
       CASE b.arquetipo WHEN 4 THEN true WHEN 3 THEN b.r_a < 0.15 ELSE false END AS vive_solo,
       -- Valores habituales de cada persona, alrededor de los cuales varían
       -- sus mediciones (como en la sección 11)
       CASE b.arquetipo WHEN 1 THEN 112 + b.r_b * 10 WHEN 2 THEN 126 + b.r_b * 8
                        WHEN 3 THEN 152 + b.r_b * 14 ELSE 118 + b.r_b * 12 END AS base_sis,
       CASE b.arquetipo WHEN 1 THEN 70 + b.r_c * 8 WHEN 2 THEN 76 + b.r_c * 6
                        WHEN 3 THEN 92 + b.r_c * 8 ELSE 70 + b.r_c * 8 END AS base_dia,
       CASE b.arquetipo WHEN 1 THEN 66 + b.r_d * 10 WHEN 2 THEN 72 + b.r_d * 10
                        WHEN 3 THEN 98 + b.r_d * 10 ELSE 70 + b.r_d * 10 END AS base_fc,
       CASE b.arquetipo WHEN 1 THEN 97 + b.r_e * 1.5 WHEN 2 THEN 96 + b.r_e * 1.5
                        WHEN 3 THEN 89 + b.r_e * 2.5 ELSE 95.5 + b.r_e * 1.5 END AS base_spo2,
       CASE b.arquetipo WHEN 1 THEN 21 + b.r_f * 3 WHEN 2 THEN 27 + b.r_f * 3
                        WHEN 3 THEN 32 + b.r_f * 5 ELSE 20 + b.r_f * 3 END AS imc,
       CASE WHEN b.r_genero < 0.6 THEN 148 + b.r_est * 14 ELSE 160 + b.r_est * 16 END AS estatura,
       (CASE b.arquetipo WHEN 1 THEN 10 + floor(b.r_g * 6) WHEN 2 THEN 20 + floor(b.r_g * 10)
                         WHEN 3 THEN 25 + floor(b.r_g * 10) ELSE 8 + floor(b.r_g * 6) END)::int AS n_signos,
       (CASE b.arquetipo WHEN 1 THEN floor(b.r_h * 2) WHEN 2 THEN 6 + floor(b.r_h * 3)
                         ELSE 2 + floor(b.r_h * 2) END)::int AS n_medicamentos,
       CASE b.arquetipo WHEN 1 THEN 2 WHEN 2 THEN 1
                        WHEN 3 THEN 1 + (b.r_c < 0.5)::int ELSE 0 END AS n_acompanantes,
       CASE b.arquetipo WHEN 1 THEN 0 WHEN 2 THEN 0.3 WHEN 3 THEN 0.15 ELSE 1 END AS p_cita_acompanada,
       (CASE b.arquetipo WHEN 1 THEN 0 WHEN 2 THEN (b.r_d < 0.25)::int
                         WHEN 3 THEN (b.r_e < 0.30)::int ELSE 3 + floor(b.r_f * 3) END)::int AS n_emergencias,
       CASE b.arquetipo WHEN 1 THEN 0.92 WHEN 2 THEN 0.75 WHEN 3 THEN 0.65 ELSE 0.35 END AS p_asiste
FROM base b;

-- Cuentas (una sola contraseña cifrada para todas: el hash es lento)
INSERT INTO usuario (nombre_usuario, contrasena_hash, correo, celular, fecha_nacimiento, genero,
                     direccion, activo, fecha_creacion)
SELECT e.nombre_usuario, h.hash, e.correo, e.celular,
       (r.hoy - make_interval(years => e.edad, days => floor(random() * 365)::int))::date,
       e.genero, e.direccion, true,
       r.ahora - make_interval(days => 30 + floor(random() * 300)::int)
FROM seed_tmp.seed_entrenubes e
CROSS JOIN seed_tmp.seed_reloj r
CROSS JOIN (SELECT crypt('Vita2025*', gen_salt('bf', 10)) AS hash) h;

UPDATE seed_tmp.seed_entrenubes e
   SET id_usuario = u.id_usuario
  FROM usuario u
 WHERE u.correo = e.correo;

INSERT INTO usuario_rol (id_usuario, id_rol)
SELECT e.id_usuario, r.id_rol
FROM seed_tmp.seed_entrenubes e JOIN rol r ON r.nombre = 'PERSONA_MAYOR'
ON CONFLICT DO NOTHING;

INSERT INTO persona_mayor (id_usuario, eps, ips, direccion_ips, vive_solo, uso_celular)
SELECT e.id_usuario, e.eps,
       CASE e.eps WHEN 'Capital Salud' THEN 'USS Usme - Subred Sur'
                  WHEN 'Nueva EPS'     THEN 'Virrey Solís IPS Santa Librada'
                  WHEN 'Compensar'     THEN 'Compensar Sede Usme'
                  ELSE 'Cafam Santa Librada' END,
       CASE e.eps WHEN 'Capital Salud' THEN 'Carrera 3 # 90A-30 Sur, Usme Centro'
                  WHEN 'Nueva EPS'     THEN 'Calle 75 Sur # 14-15, Santa Librada'
                  WHEN 'Compensar'     THEN 'Calle 90 Sur # 4-20, Usme Centro'
                  ELSE 'Calle 74 Sur # 14-60, Santa Librada' END,
       e.vive_solo,
       -- Nivel de uso del celular con la misma regla de la sección 4 (a más edad,
       -- menos uso; ~12% sin indicar).
       CASE WHEN e.r_dato_cel < 0.12 THEN NULL
            WHEN e.r_cel + (e.edad - 60) / 50.0 < 0.25 THEN 'AVANZADO'
            WHEN e.r_cel + (e.edad - 60) / 50.0 < 0.65 THEN 'INTERMEDIO'
            WHEN e.r_cel + (e.edad - 60) / 50.0 < 1.05 THEN 'BASICO'
            ELSE 'NO_USA'
       END
FROM (SELECT s.*, random() AS r_cel, random() AS r_dato_cel FROM seed_tmp.seed_entrenubes s) e;

INSERT INTO aceptacion_terminos (id_usuario, acepto, fecha_aceptacion, version_terminos, origen)
SELECT u.id_usuario, true, u.fecha_creacion, '2026-10', 'REGISTRO'
FROM usuario u JOIN seed_tmp.seed_entrenubes e ON e.id_usuario = u.id_usuario;

INSERT INTO persona_mayor_organizacion (id_persona_mayor, id_organizacion, estado, solicitada_por)
SELECT e.id_usuario, o.id_organizacion, 'ACEPTADA', 'ORGANIZACION'
FROM seed_tmp.seed_entrenubes e
CROSS JOIN (SELECT id_organizacion FROM usuario WHERE correo = 'org01@vitaplus.test') o
ON CONFLICT DO NOTHING;

-- Acompañantes (aceptados), entre las 60 cuentas de la sección 5
WITH ac AS (
    SELECT id_usuario, row_number() OVER (ORDER BY id_usuario) AS rn
    FROM usuario WHERE correo LIKE 'acomp%@vitaplus.test'
),
n_ac AS (SELECT count(*) AS n FROM ac),
vinculos AS (
    SELECT e.id_usuario AS id_persona_mayor, ac.id_usuario AS id_acompanante
    FROM seed_tmp.seed_entrenubes e CROSS JOIN n_ac
    JOIN ac ON ac.rn = 1 + ((e.i - 1) % n_ac.n)
    WHERE e.n_acompanantes >= 1
    UNION ALL
    SELECT e.id_usuario, ac.id_usuario
    FROM seed_tmp.seed_entrenubes e CROSS JOIN n_ac
    JOIN ac ON ac.rn = 1 + ((e.i + 16) % n_ac.n)
    WHERE e.n_acompanantes >= 2
)
INSERT INTO persona_mayor_acompanante (id_persona_mayor, id_acompanante, estado, solicitada_por, relacion)
SELECT id_persona_mayor, id_acompanante, 'ACEPTADA', 'PERSONA_MAYOR',
       (ARRAY['Hijo/a', 'Hijo/a', 'Hijo/a', 'Nieto/a', 'Sobrino/a', 'Hermano/a', 'Vecino/a de confianza'])
           [1 + floor(random() * 7)::int]
FROM vinculos
ON CONFLICT DO NOTHING;

-- Intereses según el arquetipo (describen los grupos; no entran al modelo)
-- y una profesión u oficio
WITH temas AS (
    SELECT * FROM (VALUES
        (1, ARRAY['Caminatas', 'Baile', 'Tejo', 'Fútbol por televisión', 'Juegos de mesa', 'Jardinería']),
        (2, ARRAY['Tejido', 'Cocina tradicional', 'Novelas de televisión', 'Bingo', 'Misa dominical', 'Bordado']),
        (3, ARRAY['Parqués', 'Tomar café con amigos', 'Música ranchera', 'Crucigramas', 'Repostería',
                  'Música carranguera']),
        (4, ARRAY['Boleros', 'Contar historias', 'Misa dominical', 'Lectura', 'Películas clásicas', 'Canto'])
    ) AS v(arquetipo, gustos)
),
elegidos AS (
    SELECT e.id_usuario, t.gustos[k] AS nombre
    FROM seed_tmp.seed_entrenubes e
    JOIN temas t ON t.arquetipo = e.arquetipo
    CROSS JOIN generate_series(1, 6) AS k
    WHERE random() < 0.55
    UNION ALL
    SELECT e.id_usuario,
           (ARRAY['Labores del hogar', 'Labores del hogar', 'Modistería o sastrería', 'Cocina',
                  'Comercio o ventas', 'Construcción', 'Agricultura o trabajo del campo',
                  'Conducción o transporte', 'Docencia', 'Vigilancia o seguridad'])[1 + floor(random() * 10)::int]
    FROM seed_tmp.seed_entrenubes e
    WHERE random() < 0.85
)
INSERT INTO persona_mayor_gusto (id_persona_mayor, id_gusto)
SELECT DISTINCT el.id_usuario, g.id_gusto
FROM elegidos el JOIN gusto g ON g.nombre = el.nombre
ON CONFLICT DO NOTHING;

-- Enfermedades y discapacidades según el arquetipo (p = probabilidad de
-- tenerla; p_severa = de que sea severa) y ~20% con una alergia
WITH catalogo AS (
    SELECT * FROM (VALUES
        (1, 'ENFERMEDAD',   'Colesterol o triglicéridos altos', 0.25, 0.0),
        (2, 'ENFERMEDAD',   'Hipertensión arterial',            1.0,  0.2),
        (2, 'ENFERMEDAD',   'Diabetes',                         1.0,  0.5),
        (2, 'ENFERMEDAD',   'Colesterol o triglicéridos altos', 0.9,  0.1),
        (2, 'ENFERMEDAD',   'Artrosis',                         0.8,  0.4),
        (2, 'ENFERMEDAD',   'Hipotiroidismo',                   0.6,  0.0),
        (2, 'ENFERMEDAD',   'Gastritis o reflujo',              0.5,  0.0),
        (2, 'DISCAPACIDAD', 'Dificultad para caminar',          0.3,  0.0),
        (3, 'ENFERMEDAD',   'Hipertensión arterial',            1.0,  0.0),
        (3, 'ENFERMEDAD',   'EPOC',                             0.8,  0.0),
        (3, 'ENFERMEDAD',   'Colesterol o triglicéridos altos', 0.3,  0.0),
        (4, 'ENFERMEDAD',   'Cataratas',                        0.7,  0.0),
        (4, 'ENFERMEDAD',   'Artrosis',                         0.6,  0.3),
        (4, 'DISCAPACIDAD', 'Dificultad para caminar',          0.6,  0.0),
        (4, 'DISCAPACIDAD', 'Usa silla de ruedas',              0.4,  0.0),
        (4, 'DISCAPACIDAD', 'Pérdida auditiva parcial',         0.7,  0.0),
        (4, 'DISCAPACIDAD', 'Baja visión',                      0.5,  0.0)
    ) AS v(arquetipo, tipo, nombre, p, p_severa)
),
elegidas AS (
    SELECT e.id_usuario, e.arquetipo, c.tipo, c.nombre, c.p_severa, random() AS r_sev
    FROM seed_tmp.seed_entrenubes e
    JOIN catalogo c ON c.arquetipo = e.arquetipo
    WHERE random() < c.p
    UNION ALL
    SELECT e.id_usuario, e.arquetipo, 'ALERGIA',
           (ARRAY['Penicilina', 'Aspirina', 'Sulfas', 'Mariscos', 'Polen', 'Ácaros del polvo'])
               [1 + floor(random() * 6)::int],
           0, 1
    FROM seed_tmp.seed_entrenubes e
    WHERE random() < 0.20
)
INSERT INTO persona_mayor_condicion_salud (id_persona_mayor, id_condicion_salud, severidad, detalle, fecha_registro)
SELECT DISTINCT ON (el.id_usuario, c.id_condicion_salud)
       el.id_usuario, c.id_condicion_salud,
       CASE WHEN el.tipo <> 'ENFERMEDAD' THEN NULL
            WHEN el.r_sev < el.p_severa THEN 'SEVERA'
            WHEN el.arquetipo IN (1, 4) OR random() < 0.4 THEN 'LEVE'
            ELSE 'MODERADA' END,
       CASE WHEN el.nombre = 'Usa silla de ruedas' THEN 'Necesita ayuda para trasladarse'
            WHEN el.nombre = 'Dificultad para caminar' AND random() < 0.5 THEN 'Camina con bastón'
            ELSE NULL END,
       r.hoy - floor(random() * 200)::int
FROM elegidas el
JOIN condicion_salud c ON c.tipo = el.tipo AND c.nombre = el.nombre
CROSS JOIN seed_tmp.seed_reloj r;

-- Medicamentos: los primeros n_medicamentos de la lista de su arquetipo,
-- todos activos y sin fecha de fin
WITH catalogo AS (
    SELECT * FROM (VALUES
        (1, 1, 'Calcio + Vitamina D',     '1 tableta',       24, '13:00'::time),
        (2, 1, 'Losartán',                '50 mg',           24, '08:00'::time),
        (2, 2, 'Metformina',              '850 mg',          12, '07:00'::time),
        (2, 3, 'Atorvastatina',           '20 mg',           24, '21:00'::time),
        (2, 4, 'Amlodipino',              '5 mg',            24, '08:00'::time),
        (2, 5, 'Levotiroxina',            '50 mcg',          24, '06:00'::time),
        (2, 6, 'Acetaminofén',            '500 mg',           8, '06:00'::time),
        (2, 7, 'Insulina glargina',       '20 UI',           24, '21:00'::time),
        (2, 8, 'Omeprazol',               '20 mg',           24, '06:30'::time),
        (3, 1, 'Losartán',                '50 mg',           24, '08:00'::time),
        (3, 2, 'Salbutamol inhalador',    '2 inhalaciones',   8, '06:00'::time),
        (3, 3, 'Hidroclorotiazida',       '25 mg',           24, '07:00'::time),
        (4, 1, 'Acetaminofén',            '500 mg',           8, '06:00'::time),
        (4, 2, 'Calcio + Vitamina D',     '1 tableta',       24, '13:00'::time),
        (4, 3, 'Omeprazol',               '20 mg',           24, '06:30'::time)
    ) AS v(arquetipo, orden, nombre, dosis, intervalo_horas, hora)
)
INSERT INTO medicamento (id_persona_mayor, nombre, dosis, frecuencia, intervalo_horas, hora,
                         fecha_inicio, fecha_fin, proxima_toma, ultima_toma, activo)
SELECT e.id_usuario, c.nombre, c.dosis,
       'Cada ' || c.intervalo_horas || ' horas',
       c.intervalo_horas, c.hora,
       r.hoy - (30 + floor(random() * 330)::int),
       NULL,
       (r.hoy + 3 + floor(random() * 4)::int) + c.hora,
       (r.hoy - 1) + c.hora,
       true
FROM seed_tmp.seed_entrenubes e
JOIN catalogo c ON c.arquetipo = e.arquetipo AND c.orden <= e.n_medicamentos
CROSS JOIN seed_tmp.seed_reloj r;

-- Signos vitales de los últimos 6 meses. La primera medición (k = 1) es de
-- los últimos 3 días y siempre trae peso y estatura, para el IMC.
WITH mediciones AS (
    SELECT e.*, k,
           CASE WHEN k = 1
                THEN r.ahora - make_interval(hours => 2 + floor(random() * 70)::int,
                                             mins  => floor(random() * 60)::int)
                ELSE (r.hoy - (1 + floor(random() * 175)::int)) + time '06:30'
                     + make_interval(mins => floor(random() * 780)::int)
           END AS fecha_hora,
           random() AS r_temp, random() AS r_peso, random() AS r_obs
    FROM seed_tmp.seed_entrenubes e
    CROSS JOIN seed_tmp.seed_reloj r
    CROSS JOIN LATERAL generate_series(1, e.n_signos) AS k
)
INSERT INTO signo_vital (id_persona_mayor, fecha_hora, presion_sistolica, presion_diastolica,
                         frecuencia_cardiaca, temperatura, saturacion_oxigeno,
                         frecuencia_respiratoria, peso, estatura, observaciones)
SELECT m.id_usuario, m.fecha_hora,
       round(m.base_sis + (random() - 0.5) * 12)::int,
       round(m.base_dia + (random() - 0.5) * 8)::int,
       round(m.base_fc + (random() - 0.5) * 10)::int,
       CASE WHEN m.r_temp < 0.5 THEN round((36.3 + random() * 0.8)::numeric, 1) END,
       least(100, round(m.base_spo2 + (random() - 0.5) * 2))::int,
       CASE WHEN m.r_temp < 0.3
            THEN (14 + floor(random() * 5) + CASE WHEN m.arquetipo = 3 THEN 5 ELSE 0 END)::int
       END,
       CASE WHEN m.k = 1 OR m.r_peso < 0.25
            THEN round((m.imc * power(m.estatura / 100, 2) + (random() - 0.5) * 2)::numeric, 1)
       END,
       CASE WHEN m.k = 1 OR m.r_peso < 0.25 THEN round(m.estatura::numeric, 1) END,
       CASE WHEN m.arquetipo = 3 AND m.r_obs < 0.15 THEN 'Refiere dolor de cabeza leve'
            WHEN m.arquetipo = 3 AND m.r_obs < 0.25 THEN 'Se fatiga al caminar'
            WHEN m.r_obs < 0.05 THEN 'Tomada en reposo, sentado'
            ELSE NULL END
FROM mediciones m;

-- Citas médicas: dos próximas y tres pasadas. Las del arquetipo 4
-- requieren acompañamiento; las de los arquetipos 2 y 3, a veces lo
-- prefieren. Sin acompañante confirmado (el arquetipo 4 no tiene).
WITH citas AS (
    SELECT e.id_usuario, e.arquetipo, e.p_cita_acompanada, pm.ips,
           CASE WHEN k = 1 THEN r.hoy + 1 + floor(random() * 14)::int
                WHEN k = 2 THEN r.hoy + 15 + floor(random() * 61)::int
                ELSE r.hoy - 1 - floor(random() * 300)::int END AS fecha,
           time '07:00' + make_interval(mins => 30 * floor(random() * 19)::int) AS hora,
           random() AS r_acomp, random() AS r_titulo, random() AS r_obs
    FROM seed_tmp.seed_entrenubes e
    JOIN persona_mayor pm ON pm.id_usuario = e.id_usuario
    CROSS JOIN seed_tmp.seed_reloj r
    CROSS JOIN LATERAL generate_series(1, 5) AS k
)
INSERT INTO cita_medica (id_persona_mayor, titulo, lugar, consultorio, fecha, hora, observaciones,
                         acompanamiento, recordatorio_dia_enviado_para, recordatorio_hora_enviado_para)
SELECT c.id_usuario,
       (CASE c.arquetipo
            WHEN 1 THEN ARRAY['Medicina general', 'Odontología', 'Optometría', 'Vacunación contra la influenza']
            WHEN 2 THEN ARRAY['Control de diabetes', 'Control de hipertensión',
                              'Laboratorio: hemoglobina glicosilada', 'Cardiología']
            WHEN 3 THEN ARRAY['Control de hipertensión', 'Neumología', 'Espirometría', 'Electrocardiograma']
            ELSE        ARRAY['Valoración por geriatría', 'Fisioterapia', 'Optometría', 'Medicina general']
        END)[1 + floor(c.r_titulo * 4)::int],
       c.ips,
       'Consultorio ' || (100 + floor(random() * 400)::int),
       c.fecha, c.hora,
       CASE WHEN c.arquetipo = 4 THEN 'Asistir con acompañante'
            WHEN c.r_obs < 0.1 THEN 'Llevar documento de identidad y carné de la EPS'
            ELSE NULL END,
       CASE WHEN c.r_acomp < c.p_cita_acompanada
            THEN CASE WHEN c.arquetipo = 4 THEN 'REQUERIDO' ELSE 'PREFERIDO' END
       END,
       c.fecha + c.hora, c.fecha + c.hora
FROM citas c;

-- Emergencias de los últimos 90 días (las del arquetipo 4, sobre todo)
INSERT INTO emergencia (id_persona_mayor, fecha_hora)
SELECT e.id_usuario,
       r.ahora - make_interval(days => 1 + floor(random() * 85)::int, hours => floor(random() * 12)::int)
FROM seed_tmp.seed_entrenubes e
CROSS JOIN seed_tmp.seed_reloj r
CROSS JOIN LATERAL generate_series(1, e.n_emergencias) AS k;

-- 20 actividades pasadas de la fundación (últimos 4 meses) con 45 cupos,
-- y la participación de estas personas: cada una se inscribe en ~1 de
-- cada 4, y asiste más o menos según su arquetipo (las autónomas casi
-- siempre; las de edad avanzada, poco).
WITH plantillas AS (
    SELECT * FROM (VALUES
      ('Caminata por el Parque Entrenubes',             'Deportiva',
       'Recorrido de bajo impacto por los senderos del parque ecológico Entrenubes, con pausas de hidratación.'),
      ('Clase de baile de salón',                       'Recreativa',
       'Baile en pareja y en grupo con música colombiana, a ritmo suave.'),
      ('Taller de tejido y bordado',                    'Manualidades',
       'Tejido en crochet y bordado de piezas para la feria de la fundación.'),
      ('Jornada de toma de signos vitales',             'Salud y bienestar',
       'Toma de presión, pulso y saturación con orientación de una enfermera.'),
      ('Ejercicios en silla',                           'Deportiva',
       'Rutina de movilidad y fuerza adaptada para hacer sentado.'),
      ('Tarde de boleros e historias de vida',          'Social',
       'Música de antes y espacio para contar y escuchar historias de vida.'),
      ('Charla: control de la presión y la diabetes',   'Salud y bienestar',
       'Alimentación, medicamentos y señales de alarma, con espacio para preguntas.'),
      ('Torneo de parqués y tejo',                      'Social',
       'Juegos por equipos para compartir y conocer a otros participantes.'),
      ('Taller de memoria',                             'Educativa',
       'Ejercicios y juegos para estimular la memoria y la concentración.'),
      ('Huerta comunitaria',                            'Recreativa',
       'Siembra y cuidado de plantas aromáticas y hortalizas en la huerta de la fundación.')
    ) AS v(nombre, tipo, descripcion)
),
nuevas AS (
    INSERT INTO actividad (id_organizacion, nombre, descripcion, fecha, hora, lugar, tipo, cupos, responsable)
    SELECT o.id_organizacion, p.nombre, p.descripcion,
           r.hoy - (5 + floor(random() * 115)::int),
           (ARRAY['08:00', '09:00', '10:00', '14:00', '15:00'])[1 + floor(random() * 5)::int],
           (ARRAY['Salón comunal Usme Centro', 'Salón parroquial Comuneros'])[1 + floor(random() * 2)::int],
           p.tipo, 45,
           (ARRAY['Sandra Melo', 'Diana Rojas', 'Julián Bermúdez', 'Marcela Duarte'])[1 + floor(random() * 4)::int]
    FROM plantillas p
    CROSS JOIN generate_series(1, 2) AS ronda
    CROSS JOIN (SELECT id_organizacion FROM usuario WHERE correo = 'org01@vitaplus.test') o
    CROSS JOIN seed_tmp.seed_reloj r
    RETURNING id_actividad, fecha, hora, cupos
),
candidatos AS (
    SELECT n.id_actividad, n.fecha + n.hora::time AS inicio, n.cupos, e.id_usuario, e.p_asiste,
           row_number() OVER (PARTITION BY n.id_actividad ORDER BY random()) AS rn
    FROM nuevas n
    CROSS JOIN seed_tmp.seed_entrenubes e
    WHERE random() < 0.25
)
INSERT INTO participacion (id_persona_mayor, id_actividad, asistio, recordatorio_enviado_para)
SELECT c.id_usuario, c.id_actividad, random() < c.p_asiste, c.inicio
FROM candidatos c
WHERE c.rn <= c.cupos
ON CONFLICT DO NOTHING;

-- Hora de fin de las actividades nuevas (como en la sección 15c)
UPDATE actividad
SET hora_fin = to_char(hora::time + make_interval(hours => 1 + floor(random() * 2)::int), 'HH24:MI')
WHERE hora IS NOT NULL AND hora_fin IS NULL
  AND id_organizacion IN (SELECT id_organizacion FROM usuario WHERE correo LIKE 'org%@vitaplus.test');

DROP SCHEMA seed_tmp CASCADE;

COMMIT;

-- Resumen: qué quedó cargado
SELECT 'organizaciones' AS dato, count(*) AS cantidad FROM usuario WHERE correo LIKE 'org%@vitaplus.test'
UNION ALL SELECT 'personas_mayores', count(*) FROM usuario WHERE correo LIKE 'pm%@vitaplus.test'
UNION ALL SELECT 'acompanantes', count(*) FROM usuario WHERE correo LIKE 'acomp%@vitaplus.test'
UNION ALL SELECT 'cuentas_con_dos_roles', count(*) FROM (SELECT id_usuario FROM usuario_rol GROUP BY id_usuario HAVING count(*) > 1) d
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
UNION ALL SELECT 'vinculos_acompanante_con_parentesco', count(*) FROM persona_mayor_acompanante
    WHERE relacion IS NOT NULL
      AND id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
UNION ALL SELECT 'vinculos_persona_mayor_organizacion', count(*) FROM persona_mayor_organizacion
    WHERE id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
UNION ALL SELECT 'vinculos_persona_mayor_organizacion_aceptados', count(*) FROM persona_mayor_organizacion
    WHERE estado = 'ACEPTADA'
      AND id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
UNION ALL SELECT 'solicitudes_de_personas_mayores_a_organizaciones', count(*) FROM persona_mayor_organizacion
    WHERE estado = 'PENDIENTE' AND solicitada_por = 'PERSONA_MAYOR'
      AND id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
UNION ALL SELECT 'vinculos_voluntario_gusto', count(*) FROM voluntario_gusto
    WHERE id_voluntario IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'vol%@vitaplus.test')
UNION ALL SELECT 'datos_de_salud', count(*) FROM persona_mayor_condicion_salud
    WHERE id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
UNION ALL SELECT 'emergencias', count(*) FROM emergencia
    WHERE id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
UNION ALL SELECT 'emergencias_ultimas_24_horas', count(*) FROM emergencia
    WHERE fecha_hora >= (now() AT TIME ZONE 'America/Bogota') - interval '24 hours'
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
UNION ALL SELECT 'notificaciones', count(*) FROM notificacion WHERE celular LIKE '+570000%'
UNION ALL SELECT 'personas_mayores_que_viven_solas', count(*) FROM persona_mayor pm
    JOIN usuario u ON u.id_usuario = pm.id_usuario AND u.correo LIKE 'pm%@vitaplus.test'
    WHERE pm.vive_solo
UNION ALL SELECT 'vinculos_pausados_o_de_cuentas_inactivas', count(*) FROM persona_mayor_organizacion
    WHERE estado IN ('INACTIVA', 'CUENTA_INACTIVA', 'PENDIENTE_CUENTA_INACTIVA')
      AND id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
UNION ALL SELECT 'retiros_de_personas_mayores', count(*) FROM retiro_cuenta
    WHERE id_organizacion IN (SELECT id_organizacion FROM usuario WHERE correo LIKE 'org%@vitaplus.test')
UNION ALL SELECT 'personas_mayores_con_profesion', count(DISTINCT pg.id_persona_mayor) FROM persona_mayor_gusto pg
    JOIN gusto g ON g.id_gusto = pg.id_gusto AND g.categoria = 'PROFESION'
    WHERE pg.id_persona_mayor IN (SELECT id_usuario FROM usuario WHERE correo LIKE 'pm%@vitaplus.test')
UNION ALL SELECT 'constancias_de_terminos', count(*) FROM aceptacion_terminos
    WHERE id_usuario IN (SELECT id_usuario FROM usuario WHERE correo LIKE '%@vitaplus.test')
UNION ALL SELECT 'notas_de_organizaciones', count(*) FROM nota_persona_mayor
    WHERE id_organizacion IN (SELECT id_organizacion FROM usuario WHERE correo LIKE 'org%@vitaplus.test')
UNION ALL SELECT 'notificaciones_apagadas', count(*) FROM preferencia_notificacion
    WHERE id_usuario IN (SELECT id_usuario FROM usuario WHERE correo LIKE '%@vitaplus.test')
UNION ALL SELECT 'donaciones_' || lower(estado), count(*) FROM donacion
    WHERE id_organizacion IN (SELECT id_organizacion FROM usuario WHERE correo LIKE 'org%@vitaplus.test')
    GROUP BY estado
UNION ALL SELECT 'articulos_donados', count(*) FROM donacion_articulo
    WHERE id_donacion IN (SELECT id_donacion FROM donacion
                           WHERE id_organizacion IN (SELECT id_organizacion FROM usuario WHERE correo LIKE 'org%@vitaplus.test'))
UNION ALL SELECT 'articulos_en_inventario', count(*) FROM inventario_articulo
    WHERE id_organizacion IN (SELECT id_organizacion FROM usuario WHERE correo LIKE 'org%@vitaplus.test')
UNION ALL SELECT 'articulos_agotados', count(*) FROM inventario_articulo
    WHERE cantidad = 0
      AND id_organizacion IN (SELECT id_organizacion FROM usuario WHERE correo LIKE 'org%@vitaplus.test')
UNION ALL SELECT 'movimientos_de_inventario', count(*) FROM inventario_movimiento
    WHERE id_inventario IN (SELECT id_inventario FROM inventario_articulo
                             WHERE id_organizacion IN (SELECT id_organizacion FROM usuario WHERE correo LIKE 'org%@vitaplus.test'))
UNION ALL SELECT 'calificaciones_de_la_plataforma', count(*) FROM calificacion_plataforma
    WHERE id_usuario IN (SELECT id_usuario FROM usuario WHERE correo LIKE '%@vitaplus.test')
UNION ALL SELECT 'registros_de_auditoria_de_salud', count(*) FROM auditoria_salud
    WHERE id_usuario IN (SELECT id_usuario FROM usuario WHERE correo LIKE '%@vitaplus.test')
UNION ALL SELECT 'intentos_sin_vinculo_en_la_auditoria', count(*) FROM auditoria_salud
    WHERE estado = 403 AND id_usuario IN (SELECT id_usuario FROM usuario WHERE correo LIKE '%@vitaplus.test');

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
