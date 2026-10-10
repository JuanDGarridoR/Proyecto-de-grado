-- Quita de persona_mayor las columnas fecha_nacimiento, genero y direccion.
--
-- Esos datos son de la tabla usuario (registro y "Mi información"). Las
-- columnas aparecían en persona_mayor porque persona-mayor-service las
-- mapeaba y Hibernate (ddl-auto=update) las creaba al arrancar. El código
-- ya no las usa, pero Hibernate no borra columnas: hay que correr esto una
-- vez en la base de datos.
--
-- Antes de borrarlas, si usuario no tiene el dato y persona_mayor sí, se
-- copia a usuario para no perder nada. Si los dos lo tienen, se queda el de
-- usuario, que es el que edita la página de perfil.
--
-- Se puede correr más de una vez sin problema.

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'persona_mayor' AND column_name = 'fecha_nacimiento') THEN
        UPDATE usuario u
           SET fecha_nacimiento = pm.fecha_nacimiento
          FROM persona_mayor pm
         WHERE pm.id_usuario = u.id_usuario
           AND u.fecha_nacimiento IS NULL
           AND pm.fecha_nacimiento IS NOT NULL;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'persona_mayor' AND column_name = 'genero') THEN
        UPDATE usuario u
           SET genero = pm.genero
          FROM persona_mayor pm
         WHERE pm.id_usuario = u.id_usuario
           AND NULLIF(TRIM(u.genero), '') IS NULL
           AND NULLIF(TRIM(pm.genero), '') IS NOT NULL;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'persona_mayor' AND column_name = 'direccion') THEN
        UPDATE usuario u
           SET direccion = pm.direccion
          FROM persona_mayor pm
         WHERE pm.id_usuario = u.id_usuario
           AND NULLIF(TRIM(u.direccion), '') IS NULL
           AND NULLIF(TRIM(pm.direccion), '') IS NOT NULL;
    END IF;
END $$;

ALTER TABLE persona_mayor
    DROP COLUMN IF EXISTS fecha_nacimiento,
    DROP COLUMN IF EXISTS genero,
    DROP COLUMN IF EXISTS direccion;

COMMIT;
