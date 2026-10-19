-- Tablas que lee RedApoyo, solo con las columnas que usa. En la base real
-- las crean los demás servicios (ddl-auto=update).

CREATE TABLE usuario (
    id_usuario      INTEGER PRIMARY KEY,
    nombre_usuario  VARCHAR(255),
    direccion       VARCHAR(255),
    id_organizacion INTEGER,
    activo          BOOLEAN
);

CREATE TABLE organizacion (
    id_organizacion INTEGER PRIMARY KEY,
    nombre          VARCHAR(255),
    direccion       VARCHAR(255),
    latitud         DOUBLE PRECISION,
    longitud        DOUBLE PRECISION
);

CREATE TABLE persona_mayor_organizacion (
    id_persona_mayor INTEGER,
    id_organizacion  INTEGER,
    estado           VARCHAR(30),
    PRIMARY KEY (id_persona_mayor, id_organizacion)
);

CREATE TABLE persona_mayor_acompanante (
    id_persona_mayor INTEGER,
    id_acompanante   INTEGER,
    estado           VARCHAR(30),
    relacion         VARCHAR(100),
    PRIMARY KEY (id_persona_mayor, id_acompanante)
);

CREATE TABLE voluntario_organizacion (
    id_voluntario   INTEGER,
    id_organizacion INTEGER,
    estado          VARCHAR(30),
    PRIMARY KEY (id_voluntario, id_organizacion)
);

CREATE TABLE gusto (
    id_gusto  INTEGER PRIMARY KEY,
    nombre    VARCHAR(255),
    categoria VARCHAR(20)
);

CREATE TABLE persona_mayor_gusto (
    id_persona_mayor INTEGER,
    id_gusto         INTEGER,
    PRIMARY KEY (id_persona_mayor, id_gusto)
);

CREATE TABLE voluntario_gusto (
    id_voluntario INTEGER,
    id_gusto      INTEGER,
    PRIMARY KEY (id_voluntario, id_gusto)
);

CREATE TABLE actividad (
    id_actividad    INTEGER PRIMARY KEY,
    id_organizacion INTEGER,
    nombre          VARCHAR(255),
    fecha           DATE,
    lugar           VARCHAR(255),
    estado          VARCHAR(20),
    latitud         DOUBLE PRECISION,
    longitud        DOUBLE PRECISION
);

CREATE TABLE participacion (
    id_persona_mayor INTEGER,
    id_actividad     INTEGER,
    asistio          BOOLEAN,
    PRIMARY KEY (id_persona_mayor, id_actividad)
);

-- Barrio que dejó UbicadorAutomatico (sacado de la dirección con Google).
CREATE TABLE persona_mayor (
    id_usuario     INTEGER PRIMARY KEY,
    barrio         VARCHAR(100),
    barrio_buscado VARCHAR(255)
);
