-- Tablas que lee RecursosController, solo con las columnas que usa. En la
-- base real las crean auth-service y donacion-service (ddl-auto=update).

CREATE TABLE usuario (
    id_usuario      INTEGER PRIMARY KEY,
    nombre_usuario  VARCHAR(255),
    id_organizacion INTEGER
);

CREATE TABLE donacion (
    id_donacion     INTEGER PRIMARY KEY,
    id_donante      INTEGER,
    rol_donante     VARCHAR(20),
    id_organizacion INTEGER,
    fecha_entrega   DATE,
    estado          VARCHAR(20)
);

CREATE TABLE donacion_articulo (
    id_articulo       INTEGER PRIMARY KEY,
    id_donacion       INTEGER,
    categoria         VARCHAR(20),
    descripcion       VARCHAR(120),
    cantidad          DECIMAL(12, 2),
    unidad            VARCHAR(30),
    cantidad_recibida DECIMAL(12, 2)
);

CREATE TABLE inventario_articulo (
    id_inventario     INTEGER PRIMARY KEY,
    id_organizacion   INTEGER,
    nombre            VARCHAR(120),
    categoria         VARCHAR(20),
    unidad            VARCHAR(30),
    cantidad          DECIMAL(12, 2),
    cantidad_minima   DECIMAL(12, 2),
    fecha_vencimiento DATE
);

CREATE TABLE inventario_movimiento (
    id_movimiento INTEGER PRIMARY KEY,
    id_inventario INTEGER,
    tipo          VARCHAR(10),
    cantidad      DECIMAL(12, 2),
    motivo        VARCHAR(30),
    fecha         TIMESTAMP
);
