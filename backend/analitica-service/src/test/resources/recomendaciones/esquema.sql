-- Tablas que lee RecomendadorOrganizaciones, solo con las columnas que usa.
-- En la base real las crean los demás servicios (ddl-auto=update).

CREATE TABLE usuario (
    id_usuario      INTEGER PRIMARY KEY,
    nombre_usuario  VARCHAR(255),
    direccion       VARCHAR(255),
    celular         VARCHAR(20),
    correo          VARCHAR(255),
    id_organizacion INTEGER,
    activo          BOOLEAN
);

CREATE TABLE persona_mayor (
    id_usuario INTEGER PRIMARY KEY
);

CREATE TABLE rol (
    id_rol INTEGER PRIMARY KEY,
    nombre VARCHAR(30)
);

CREATE TABLE usuario_rol (
    id_usuario INTEGER,
    id_rol     INTEGER,
    PRIMARY KEY (id_usuario, id_rol)
);

CREATE TABLE organizacion (
    id_organizacion INTEGER PRIMARY KEY,
    nombre          VARCHAR(255),
    direccion       VARCHAR(255)
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

CREATE TABLE persona_mayor_organizacion (
    id_persona_mayor INTEGER,
    id_organizacion  INTEGER,
    estado           VARCHAR(30),
    PRIMARY KEY (id_persona_mayor, id_organizacion)
);

CREATE TABLE voluntario_organizacion (
    id_voluntario   INTEGER,
    id_organizacion INTEGER,
    estado          VARCHAR(30),
    PRIMARY KEY (id_voluntario, id_organizacion)
);

CREATE TABLE actividad (
    id_actividad    INTEGER PRIMARY KEY,
    id_organizacion INTEGER,
    nombre          VARCHAR(255),
    tipo            VARCHAR(100),
    descripcion     VARCHAR(500),
    fecha           DATE,
    estado          VARCHAR(20)
);
