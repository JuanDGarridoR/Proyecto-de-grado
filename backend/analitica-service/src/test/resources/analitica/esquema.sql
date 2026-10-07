-- Tablas de los otros servicios que lee analitica-service, solo con las
-- columnas que usan sus consultas.

-- auth-service
CREATE TABLE usuario (
    id_usuario       INTEGER PRIMARY KEY,
    nombre_usuario   VARCHAR(255),
    fecha_nacimiento DATE,
    genero           VARCHAR(50),
    id_organizacion  INTEGER
);

CREATE TABLE persona_mayor (
    id_usuario       INTEGER PRIMARY KEY,
    fecha_nacimiento DATE,
    genero           VARCHAR(50),
    eps              VARCHAR(120)
);

-- persona-mayor-service y organizacion-service
CREATE TABLE persona_mayor_organizacion (
    id_persona_mayor INTEGER,
    id_organizacion  INTEGER,
    estado           VARCHAR(20),
    PRIMARY KEY (id_persona_mayor, id_organizacion)
);

-- acompanante-service
CREATE TABLE persona_mayor_acompanante (
    id_persona_mayor INTEGER,
    id_acompanante   INTEGER,
    estado           VARCHAR(20),
    PRIMARY KEY (id_persona_mayor, id_acompanante)
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

-- actividad-service
CREATE TABLE actividad (
    id_actividad    INTEGER PRIMARY KEY,
    id_organizacion INTEGER,
    nombre          VARCHAR(255),
    tipo            VARCHAR(100),
    fecha           DATE,
    cupos           INTEGER,
    estado          VARCHAR(20)
);

CREATE TABLE participacion (
    id_persona_mayor INTEGER,
    id_actividad     INTEGER,
    asistio          BOOLEAN,
    PRIMARY KEY (id_persona_mayor, id_actividad)
);

-- salud-service
CREATE TABLE signo_vital (
    id_signo_vital          INTEGER PRIMARY KEY,
    id_persona_mayor        INTEGER,
    fecha_hora              TIMESTAMP,
    presion_sistolica       INTEGER,
    presion_diastolica      INTEGER,
    frecuencia_cardiaca     INTEGER,
    temperatura             DOUBLE PRECISION,
    saturacion_oxigeno      INTEGER,
    frecuencia_respiratoria INTEGER,
    peso                    DOUBLE PRECISION
);
