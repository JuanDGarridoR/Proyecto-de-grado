-- Datos ficticios. Rosa (10) vive en La Flora, a dos cuadras del Centro Día
-- Renacer (organización 6), y ya pertenece al Comedor Comunitario (8). El
-- Hogar Cerrado (7) tiene su cuenta inactiva. Laura (40) es voluntaria de la
-- Fundación Entrenubes (5) y Pedro (50) es acompañante.

INSERT INTO rol VALUES (1, 'PERSONA_MAYOR'), (2, 'ACOMPANANTE'), (3, 'ORGANIZACION'), (4, 'VOLUNTARIO');

INSERT INTO organizacion VALUES (5, 'Fundación Entrenubes', 'Calle 91 Sur # 3-15, Usme Centro');
INSERT INTO organizacion VALUES (6, 'Centro Día Renacer', 'Diagonal 68 Sur # 1-45 Este, La Flora');
INSERT INTO organizacion VALUES (7, 'Hogar Cerrado', 'Calle 70 Sur # 2-10 Este, La Flora');
INSERT INTO organizacion VALUES (8, 'Comedor Comunitario', 'Calle 73 Sur # 13B-10 Este, Juan Rey');

-- Cuentas de las organizaciones
INSERT INTO usuario VALUES (30, 'Fundación Entrenubes', NULL, '+573003330000', 'contacto@entrenubes.org', 5, TRUE);
INSERT INTO usuario VALUES (31, 'Centro Día Renacer', NULL, '+573003331111', NULL, 6, TRUE);
INSERT INTO usuario VALUES (32, 'Hogar Cerrado', NULL, NULL, NULL, 7, FALSE);
INSERT INTO usuario VALUES (33, 'Comedor Comunitario', NULL, NULL, NULL, 8, TRUE);

-- Personas mayores: Luis (11) es del Centro Día Renacer y Marta (12), de la Fundación Entrenubes
INSERT INTO usuario VALUES (10, 'Rosa Díaz', 'Calle 69 Sur # 2-20 Este, La Flora', '+573001110000', NULL, NULL, TRUE);
INSERT INTO usuario VALUES (11, 'Luis Gómez', 'Calle 70 Sur # 1-10 Este, La Flora', NULL, NULL, NULL, TRUE);
INSERT INTO usuario VALUES (12, 'Marta Ruiz', 'Calle 90 Sur # 3-40, Usme Centro', NULL, NULL, NULL, TRUE);
INSERT INTO persona_mayor VALUES (10), (11), (12);
INSERT INTO usuario_rol VALUES (10, 1), (11, 1), (12, 1);

INSERT INTO persona_mayor_organizacion VALUES (10, 8, 'ACEPTADA');
INSERT INTO persona_mayor_organizacion VALUES (11, 6, 'ACEPTADA');
INSERT INTO persona_mayor_organizacion VALUES (12, 5, 'ACEPTADA');

-- Voluntaria y acompañante
INSERT INTO usuario VALUES (40, 'Laura Pérez', 'Calle 91 Sur # 3-20, Usme Centro', NULL, NULL, NULL, TRUE);
INSERT INTO usuario_rol VALUES (40, 4);
INSERT INTO voluntario_organizacion VALUES (40, 5, 'ACEPTADA');

INSERT INTO usuario VALUES (50, 'Pedro León', 'Calle 80 Sur # 5-10', NULL, NULL, NULL, TRUE);
INSERT INTO usuario_rol VALUES (50, 2);

-- Gustos
INSERT INTO gusto VALUES (1, 'Baile', 'HOBBY'), (2, 'Tejido', 'TALENTO'), (3, 'Lectura', 'HOBBY');
INSERT INTO persona_mayor_gusto VALUES (10, 1), (10, 2), (11, 2), (12, 3);
INSERT INTO voluntario_gusto VALUES (40, 2);

-- Profesiones (mismo catálogo, categoría PROFESION). Laura fue docente,
-- como Luis, del Centro Día Renacer. Rosa no registró profesión.
INSERT INTO gusto VALUES (4, 'Docencia', 'PROFESION');
INSERT INTO persona_mayor_gusto VALUES (11, 4);
INSERT INTO voluntario_gusto VALUES (40, 4);

-- Actividades: la propuesta pendiente del Centro Día Renacer no cuenta
INSERT INTO actividad VALUES (100, 6, 'Noche de música y baile', 'Recreativa', 'Música para bailar', CURRENT_DATE, NULL);
INSERT INTO actividad VALUES (101, 6, 'Tejido en grupo', 'Manualidades', NULL, CURRENT_DATE, 'PENDIENTE');
INSERT INTO actividad VALUES (102, 5, 'Charla de salud', 'Salud y bienestar', NULL, CURRENT_DATE, NULL);
INSERT INTO actividad VALUES (103, 6, 'Grupo de lectura', 'Educativa', NULL, CURRENT_DATE, NULL);
