-- Datos ficticios. La organización 5 (cuenta 30) tiene vinculadas a Rosa
-- (10) y a Luis (11); Marta (12) está pendiente y Ana (14) rechazó. Pedro
-- (13) es de la organización 6 (cuenta 31).

-- Cuentas de las organizaciones
INSERT INTO usuario (id_usuario, nombre_usuario, id_organizacion) VALUES (30, 'Fundación Entrenubes', 5);
INSERT INTO usuario (id_usuario, nombre_usuario, id_organizacion) VALUES (31, 'Comedor Comunitario', 6);

-- Personas mayores: la EPS de Luis quedó en blanco
INSERT INTO usuario (id_usuario, nombre_usuario, fecha_nacimiento, genero) VALUES (10, 'Rosa Díaz', '1950-03-14', 'Femenino');
INSERT INTO usuario (id_usuario, nombre_usuario, fecha_nacimiento, genero) VALUES (11, 'Luis Gómez', '1944-07-01', 'Masculino');
INSERT INTO usuario (id_usuario, nombre_usuario, fecha_nacimiento, genero) VALUES (12, 'Marta Ruiz', '1952-01-20', 'Femenino');
INSERT INTO usuario (id_usuario, nombre_usuario, fecha_nacimiento, genero) VALUES (13, 'Pedro León', '1948-11-02', 'Masculino');
INSERT INTO usuario (id_usuario, nombre_usuario, fecha_nacimiento, genero) VALUES (14, 'Ana Torres', '1955-05-05', 'Femenino');

INSERT INTO persona_mayor (id_usuario, eps) VALUES (10, 'Capital Salud');
INSERT INTO persona_mayor (id_usuario, eps) VALUES (11, '  ');
INSERT INTO persona_mayor (id_usuario) VALUES (12);
INSERT INTO persona_mayor (id_usuario) VALUES (13);
INSERT INTO persona_mayor (id_usuario) VALUES (14);

-- Vínculos con las organizaciones
INSERT INTO persona_mayor_organizacion VALUES (10, 5, 'ACEPTADA');
INSERT INTO persona_mayor_organizacion VALUES (11, 5, 'ACEPTADA');
INSERT INTO persona_mayor_organizacion VALUES (12, 5, 'PENDIENTE');
INSERT INTO persona_mayor_organizacion VALUES (14, 5, 'RECHAZADA');
INSERT INTO persona_mayor_organizacion VALUES (13, 6, 'ACEPTADA');

-- Acompañantes: el 20 acompaña a Rosa y a Luis (cuenta una vez), el 21 a
-- Luis; el 22 tiene la solicitud pendiente y el 23 acompaña a Pedro (otra
-- organización).
INSERT INTO persona_mayor_acompanante VALUES (10, 20, 'ACEPTADA');
INSERT INTO persona_mayor_acompanante VALUES (11, 20, 'ACEPTADA');
INSERT INTO persona_mayor_acompanante VALUES (11, 21, 'ACEPTADA');
INSERT INTO persona_mayor_acompanante VALUES (10, 22, 'PENDIENTE');
INSERT INTO persona_mayor_acompanante VALUES (13, 23, 'ACEPTADA');

-- Actividades: dos visibles de la organización 5, propuestas pendiente y
-- rechazada, una sin fecha y una de la organización 6
INSERT INTO actividad VALUES (100, 5, 'Yoga en el parque', 'Recreativa', '2026-09-10', 20, NULL);
INSERT INTO actividad VALUES (101, 5, 'Taller de memoria', 'Cognitiva', '2026-09-25', 10, 'ACEPTADA');
INSERT INTO actividad VALUES (102, 5, 'Baile', 'Recreativa', '2026-10-02', NULL, 'PENDIENTE');
INSERT INTO actividad VALUES (103, 5, 'Caminata', 'Física', '2026-10-15', NULL, 'RECHAZADA');
INSERT INTO actividad VALUES (104, 5, 'Sin fecha', 'Recreativa', NULL, NULL, NULL);
INSERT INTO actividad VALUES (200, 6, 'Cine al aire libre', 'Cultural', '2026-09-12', 30, NULL);

-- Inscripciones: en el yoga asistió Rosa, Luis no y a Marta no le han tomado asistencia
INSERT INTO participacion VALUES (10, 100, TRUE);
INSERT INTO participacion VALUES (11, 100, FALSE);
INSERT INTO participacion VALUES (12, 100, NULL);
INSERT INTO participacion VALUES (10, 101, TRUE);
INSERT INTO participacion VALUES (13, 200, TRUE);

-- Signos vitales; los de Marta y Pedro no son de personas vinculadas a la organización 5
INSERT INTO signo_vital (id_signo_vital, id_persona_mayor, fecha_hora, presion_sistolica, presion_diastolica, frecuencia_cardiaca, temperatura, saturacion_oxigeno, peso)
VALUES (1, 10, '2026-09-01 08:00:00', 120, 80, 70, 36.5, 97, 60.0);
INSERT INTO signo_vital (id_signo_vital, id_persona_mayor, fecha_hora, presion_sistolica, presion_diastolica)
VALUES (2, 10, '2026-09-20 08:00:00', 150, 95);
INSERT INTO signo_vital (id_signo_vital, id_persona_mayor, fecha_hora, saturacion_oxigeno)
VALUES (3, 11, '2026-09-15 09:30:00', 92);
INSERT INTO signo_vital (id_signo_vital, id_persona_mayor, fecha_hora, saturacion_oxigeno)
VALUES (4, 12, '2026-09-16 10:00:00', 98);
INSERT INTO signo_vital (id_signo_vital, id_persona_mayor, fecha_hora, saturacion_oxigeno)
VALUES (5, 13, '2026-09-17 10:00:00', 96);

-- Gustos
INSERT INTO gusto VALUES (1, 'Boleros', 'GUSTO');
INSERT INTO gusto VALUES (2, 'Tejer', 'TALENTO');
INSERT INTO gusto VALUES (3, 'Caminar', 'HOBBY');

INSERT INTO persona_mayor_gusto VALUES (10, 1);
INSERT INTO persona_mayor_gusto VALUES (11, 1);
INSERT INTO persona_mayor_gusto VALUES (10, 2);
INSERT INTO persona_mayor_gusto VALUES (13, 3);
