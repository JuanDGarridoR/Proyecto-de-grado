-- Organización 5 (Fundación Entrenubes, cuenta 30) y la 6, que no debe
-- aparecer en la red de la 5.
INSERT INTO organizacion VALUES (5, 'Fundación Entrenubes', 'Calle 91 Sur # 3-15, Usme Centro', 4.4805, -74.1255);
INSERT INTO organizacion VALUES (6, 'Comedor Comunitario', 'Calle 73 Sur # 14-30', NULL, NULL);

INSERT INTO usuario VALUES
 (30, 'Fundación Entrenubes', NULL, 5, TRUE),
 -- Personas mayores
 (10, 'Rosa Díaz',     'Carrera 4 Este # 76-20 Sur, Yomasa', NULL, TRUE),
 (11, 'Pedro Gómez',   'Diagonal 68 Sur # 1-45 Este, La Flora', NULL, TRUE),
 (12, 'Inés Cuenta Inactiva', 'Calle 80 Sur, Yomasa', NULL, FALSE),
 (13, 'Luis Pendiente', 'Calle 90 Sur, Comuneros', NULL, TRUE),
 (14, 'Marta Otra Org', 'Calle 70 Sur, Danubio', NULL, TRUE),
 (15, 'Ana Sin Barrio', 'Calle 1 # 2-3', NULL, TRUE),
 -- Acompañantes
 (50, 'Carmen Díaz', NULL, NULL, TRUE),
 (51, 'Jorge Pendiente', NULL, NULL, TRUE),
 -- Voluntarios
 (40, 'Laura Voluntaria', NULL, NULL, TRUE),
 (41, 'Mario Pendiente', NULL, NULL, TRUE);

INSERT INTO persona_mayor_organizacion VALUES
 (10, 5, 'ACEPTADA'), (11, 5, 'ACEPTADA'), (12, 5, 'ACEPTADA'),
 (13, 5, 'PENDIENTE'), (14, 6, 'ACEPTADA'), (15, 5, 'ACEPTADA');

INSERT INTO acompanante VALUES (50, 'Familiar'), (51, 'Vecino');
INSERT INTO persona_mayor_acompanante VALUES
 (10, 50, 'ACEPTADA', 'Hija'),
 (11, 51, 'PENDIENTE', NULL);

INSERT INTO voluntario_organizacion VALUES (40, 5, 'ACEPTADA'), (41, 5, 'PENDIENTE');

INSERT INTO gusto VALUES (1, 'Boleros', 'GUSTO'), (2, 'Tejido', 'TALENTO'), (3, 'Ajedrez', 'PASATIEMPO');
INSERT INTO persona_mayor_gusto VALUES (10, 1), (10, 2), (11, 1), (14, 3);
INSERT INTO voluntario_gusto VALUES (40, 2), (41, 3);

INSERT INTO actividad VALUES
 (100, 5, 'Caminata',          CURRENT_DATE + 5,   'Parque La Flora', NULL,        4.528, -74.085),
 (101, 5, 'Bingo de hace un año', CURRENT_DATE - 365, 'Salón',        NULL,        NULL,  NULL),
 (102, 6, 'Actividad de otra', CURRENT_DATE + 2,   'Salón',          NULL,        NULL,  NULL),
 (103, 5, 'Propuesta pendiente', CURRENT_DATE + 3, 'Salón',          'PENDIENTE', NULL,  NULL),
 (104, 5, 'Tejido',            CURRENT_DATE - 10,  'Salón principal', 'ACEPTADA', 4.4805, -74.1255);

INSERT INTO participacion VALUES (10, 100, NULL), (11, 100, NULL), (10, 104, TRUE), (10, 101, TRUE);

-- Sofía escribió la dirección sin barrio; UbicadorAutomatico le guardó Chuniza.
INSERT INTO usuario VALUES (16, 'Sofía Barrio Guardado', 'Calle 89 Sur # 4-10', NULL, TRUE);
INSERT INTO persona_mayor_organizacion VALUES (16, 5, 'ACEPTADA');
INSERT INTO persona_mayor VALUES
 (10, NULL, NULL), (11, NULL, NULL), (15, NULL, NULL),
 (16, 'Chuniza', 'Calle 89 Sur # 4-10');
