# Guía de comentarios

Así se comenta el código de Vita+. La idea es que cualquier persona del equipo, o quien revise el proyecto, entienda qué hace cada archivo y por qué está escrito así sin tener que leerlo completo.

## Reglas generales

- Los comentarios se escriben en español, con tildes y ñ. Los nombres de clases, métodos, tablas, endpoints y servicios van tal como aparecen en el código (`auth-service`, `idPersonaMayor`, `/api/auth/login`).
- Se comenta lo que el código no dice por sí solo: para qué existe algo, una regla del negocio, un caso borde o una limitación de otro servicio o librería. Si un comentario repite lo que dice la línea de abajo, sobra.
- Frases cortas y directas: "Calcula la próxima toma…", "Solo la organización dueña puede editarla…".
- Los comentarios con frases completas empiezan con mayúscula y terminan en punto. Los títulos de sección y las notas cortas al final de una línea no llevan punto.
- No se usan separadores decorativos (`=====`, `-----`), títulos en mayúsculas ni emojis.
- No se deja código comentado. Lo que ya no se usa se borra; queda en el historial de Git.
- Si se cambia el código, se actualiza su comentario.

## Java (backend)

- **Clase, interfaz o record**: comentario `/** ... */` justo antes de sus anotaciones. Dice qué es y qué papel cumple en el servicio, en una a tres líneas.
- **Método**: `/** ... */` de una o dos frases en los endpoints, en la lógica de negocio y en los métodos privados que no se entienden solo con el nombre. No se comentan getters, setters ni constructores que solo asignan dependencias.
- **Campo o constante**: `/** ... */` solo si su significado, formato o valores posibles no son evidentes. Para varios campos relacionados basta un `//` encima del grupo.
- **Dentro de un método**: `//` en su propia línea, encima del bloque que explica.
- **Al final de una línea**: solo notas cortas, como un formato (`// "HH:mm"`) o una unidad.
- `@param` y `@return` solo se usan cuando el nombre del parámetro o del valor no basta.

```java
/**
 * Endpoints con los que la persona mayor gestiona sus medicamentos.
 * El id del usuario llega en el encabezado X-User-Id, que agrega el gateway.
 */
@RestController
@RequestMapping("/api/persona-mayor/medicamentos")
public class MedicamentoController {

    /** Crea el medicamento y calcula su próxima toma. */
    @PostMapping
    public ResponseEntity<MedicamentoResponse> crear(...) {
        ...
        // La hora registrada puede ser de una toma que ya pasó: se avanza
        // hasta la siguiente toma pendiente.
        ...
    }
}
```

## TypeScript (frontend)

Las mismas reglas de Java: `/** ... */` antes de cada componente, servicio, interfaz, función o constante que lo necesite, y `//` dentro de los métodos.

```ts
/** Formulario y lista de medicamentos de la persona mayor. */
@Component({ ... })
export class Recordatorios { ... }
```

## Plantillas HTML

- `<!-- Comentario -->` antes de cada bloque principal de la vista: secciones, formularios, modales y estados que no son evidentes (cargando, vacío, error).
- No se comenta cada campo ni cada botón.

## CSS

- `/* Nombre de la sección */` antes de cada grupo de reglas que corresponde a una parte de la vista, con los mismos nombres que se usan en el HTML.
- Comentario junto a una propiedad solo cuando el valor no es evidente, por ejemplo `min-width: 0` para que un grid no se desborde.
- Los archivos de estilos compartidos o globales empiezan con un comentario que dice quién los usa.

## Configuración, SQL y scripts

- En `.properties` y `.yml`, un comentario `#` antes de cada grupo de propiedades que diga para qué sirve.
- En los scripts SQL, `--` antes de cada paso.
- En los `.bat`, `REM` para lo que no sea evidente.
