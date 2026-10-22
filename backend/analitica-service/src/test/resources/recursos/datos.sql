-- Organización 5 (cuenta 30) y la 6, cuyos datos no deben aparecer.
INSERT INTO usuario VALUES
 (30, 'Fundación Entrenubes', 5),
 (31, 'Comedor Comunitario', 6),
 (50, 'Carmen Díaz', NULL),
 (40, 'Laura Voluntaria', NULL);

INSERT INTO donacion VALUES
 (1, 50, 'ACOMPANANTE', 5, DATE '2026-10-01', 'RECIBIDA'),
 (2, 40, 'VOLUNTARIO',  5, DATE '2026-10-10', 'NO_ENTREGADA'),
 (3, 40, 'VOLUNTARIO',  5, DATE '2026-10-25', 'PROGRAMADA'),
 (4, 50, 'ACOMPANANTE', 6, DATE '2026-10-05', 'RECIBIDA');

INSERT INTO donacion_articulo VALUES
 (10, 1, 'ALIMENTOS', 'Arroz', 10, 'kg', 8),
 (11, 1, 'DINERO', 'Aporte', 50000, 'COP', 50000),
 (12, 2, 'ROPA', 'Chaquetas', 5, 'unidades', NULL),
 (13, 3, 'ASEO', 'Jabón', 12, 'unidades', NULL),
 (14, 4, 'ALIMENTOS', 'Panela', 3, 'kg', 3);

INSERT INTO inventario_articulo VALUES
 (100, 5, 'Arroz', 'ALIMENTOS', 'kg', 2, 5, DATE '2027-01-10'),
 (101, 5, 'Dinero', 'DINERO', 'COP', 50000, NULL, NULL),
 (102, 6, 'Panela', 'ALIMENTOS', 'kg', 3, NULL, NULL);

INSERT INTO inventario_movimiento VALUES
 (1000, 100, 'ENTRADA', 8, 'DONACION', TIMESTAMP '2026-10-01 10:00:00'),
 (1001, 100, 'SALIDA', 6, 'ENTREGA', TIMESTAMP '2026-10-03 15:30:00'),
 (1002, 101, 'ENTRADA', 50000, 'DONACION', TIMESTAMP '2026-10-01 10:00:00'),
 (1003, 102, 'ENTRADA', 3, 'DONACION', TIMESTAMP '2026-10-05 09:00:00');
