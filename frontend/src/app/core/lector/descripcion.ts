/**
 * Arma la frase que el lector de voz dice cuando la persona toca algo:
 * para qué sirve un botón o enlace, qué escribir en un campo, o el texto
 * de lo que tocó. Cualquier elemento puede dar su propia frase con el
 * atributo data-lector="..." (tiene prioridad sobre todo lo demás).
 */

/** Elementos que se explican por sí solos (no se lee el texto de su contenedor). */
const INTERACTIVOS =
  'button, a[href], input, textarea, select, label, [role="button"], [role="link"], ' +
  '[role="tab"], [role="checkbox"], [role="switch"], [role="option"], summary';

/** Más allá de esto, lo tocado es un contenedor grande y no se lee entero. */
const MAX_TEXTO = 400;

/**
 * Para qué sirve un botón según sus palabras. Se revisa en orden, así que
 * las frases más específicas ("cerrar sesión") van antes que las generales
 * ("cerrar").
 */
const ACCIONES: [RegExp, string][] = [
  [/emergencia|\bsos\b|auxilio/, 'pide ayuda de emergencia a tus contactos'],
  [/cerrar sesion|salir de la cuenta|^salir$/, 'sale de tu cuenta'],
  [/iniciar sesion|ingresar|^entrar/, 'entra a tu cuenta'],
  [/crear cuenta|registrarme|^registrarse/, 'crea tu cuenta en la plataforma'],
  [/olvide|recuperar/, 'te ayuda a recuperar tu contraseña'],
  [/reenviar/, 'te vuelve a enviar el código'],
  [/codigo/, 'te envía un código a tu celular'],
  [/guardar|actualizar/, 'guarda los cambios que hiciste'],
  [/cancelar/, 'cancela sin hacer ningún cambio'],
  [/cerrar|^x$/, 'cierra esta ventana'],
  [/eliminar|borrar|quitar/, 'lo elimina'],
  [/editar|modificar|cambiar/, 'te deja cambiar esta información'],
  [/confirmar|aceptar|^si\b/, 'confirma lo que elegiste'],
  [/enviar|solicitar/, 'envía tu solicitud'],
  [/buscar/, 'busca lo que escribiste'],
  [/filtrar/, 'muestra solo lo que elijas'],
  [/agregar|anadir|nuevo|nueva|crear|registrar/, 'agrega uno nuevo'],
  [/inscribir|unirme|participar|asistir/, 'te inscribe'],
  [/calificar/, 'te deja dar tu opinión'],
  [/donar/, 'te deja hacer una donación'],
  [/llamar/, 'hace una llamada'],
  [/descargar/, 'descarga el archivo'],
  [/subir|foto/, 'te deja elegir una foto'],
  [/volver|atras|regresar/, 'te lleva a la pantalla anterior'],
  [/siguiente|continuar/, 'pasa al siguiente paso'],
  [/anterior/, 'vuelve al paso anterior'],
  [/ver mas|ver detalle|detalle|^ver\b/, 'te muestra más información'],
  [/mostrar contrasena/, 'muestra la contraseña que escribiste'],
  [/ocultar contrasena/, 'oculta la contraseña'],
  [/mapa/, 'te muestra el mapa']
];

/** Qué escribir en un campo según su tipo, cuando no tiene nombre propio. */
const TIPOS_CAMPO: Record<string, string> = {
  email: 'tu correo electrónico',
  password: 'tu contraseña',
  tel: 'tu número de celular',
  date: 'la fecha',
  time: 'la hora',
  number: 'un número',
  search: 'lo que quieres buscar'
};

/** Minúsculas, sin tildes ni espacios de más: para comparar palabras. */
export function normalizar(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}

function limpio(texto: string | null | undefined): string {
  return (texto ?? '').replace(/\s+/g, ' ').trim();
}

/** Texto visible del elemento (innerText respeta lo oculto; jsdom no lo tiene). */
function textoDe(el: Element): string {
  const html = el as HTMLElement;
  return limpio(html.innerText ?? html.textContent);
}

/** Nombre accesible del elemento: aria-label, aria-labelledby, title o su texto. */
function nombreDe(el: Element): string {
  const aria = limpio(el.getAttribute('aria-label'));
  if (aria) {
    return aria;
  }

  const ids = el.getAttribute('aria-labelledby');
  if (ids) {
    const texto = ids
      .split(/\s+/)
      .map((id) => document.getElementById(id))
      .filter((e): e is HTMLElement => !!e)
      .map(textoDe)
      .join(' ');
    if (texto) {
      return texto;
    }
  }

  return textoDe(el) || limpio(el.getAttribute('title'));
}

/** El texto de la etiqueta <label> de un campo, sin asteriscos de obligatorio. */
function etiquetaDeCampo(campo: HTMLElement): string {
  const etiquetas = (campo as HTMLInputElement).labels;
  const etiqueta = etiquetas && etiquetas.length ? textoDe(etiquetas[0]) : '';

  return limpio(
    (etiqueta || limpio(campo.getAttribute('aria-label')) || limpio(campo.getAttribute('title')))
      .replace(/\*/g, '')
  );
}

/** Un ejemplo del placeholder, si es un ejemplo legible (no puntos de contraseña). */
function ejemploDe(campo: HTMLElement): string {
  const placeholder = limpio(campo.getAttribute('placeholder'));
  return /[\p{L}\p{N}]/u.test(placeholder) ? placeholder : '';
}

function describirCampo(campo: HTMLElement): string {
  const tipo = (campo.getAttribute('type') ?? '').toLowerCase();
  const etiqueta = etiquetaDeCampo(campo);
  const ejemplo = ejemploDe(campo);

  if (tipo === 'checkbox' || tipo === 'radio') {
    const marcada = (campo as HTMLInputElement).checked;
    const que = etiqueta || 'esta opción';
    return tipo === 'checkbox'
      ? `Casilla: ${que}. Ahora está ${marcada ? 'marcada' : 'sin marcar'}. Tócala para cambiarla.`
      : `Opción: ${que}. ${marcada ? 'Está elegida.' : 'Tócala para elegirla.'}`;
  }

  if (tipo === 'file') {
    return `Aquí eliges un archivo${etiqueta ? ': ' + etiqueta : ''}.`;
  }

  if (tipo === 'submit' || tipo === 'button') {
    return describirBoton(campo, (campo as HTMLInputElement).value);
  }

  if (campo.tagName === 'SELECT') {
    const elegida = limpio((campo as HTMLSelectElement).selectedOptions?.[0]?.text);
    return (
      (etiqueta ? `Aquí elige de una lista: ${etiqueta}.` : 'Aquí elige una opción de una lista.') +
      (elegida ? ` Ahora dice: ${elegida}.` : '')
    );
  }

  const verbo = tipo === 'date' || tipo === 'time' ? 'elige' : 'escribe';
  let frase = etiqueta
    ? `Aquí ${verbo}: ${etiqueta}.`
    : `Aquí ${verbo} ${TIPOS_CAMPO[tipo] ?? 'la información que se pide'}.`;

  if (ejemplo && tipo !== 'password') {
    frase += ` Por ejemplo: ${ejemplo}.`;
  }
  if ((campo as HTMLInputElement).required) {
    frase += ' Es obligatorio.';
  }
  return frase;
}

function describirBoton(boton: Element, nombre = nombreDe(boton)): string {
  const sinNombre = !nombre;
  const accion = ACCIONES.find(([patron]) => patron.test(normalizar(nombre)))?.[1];
  const deshabilitado =
    (boton as HTMLButtonElement).disabled || boton.getAttribute('aria-disabled') === 'true';

  let frase = sinNombre ? 'Botón.' : `Botón ${nombre}.`;
  if (accion) {
    frase += ` Sirve para: ${accion}.`;
  }
  if (deshabilitado) {
    frase += ' Por ahora no se puede usar; primero completa lo que falta.';
  }
  return frase;
}

function describirEnlace(enlace: Element): string {
  const nombre = nombreDe(enlace);
  const accion = ACCIONES.find(([patron]) => patron.test(normalizar(nombre)))?.[1];
  if (!nombre) {
    return 'Enlace.';
  }
  return accion ? `${nombre}. Sirve para: ${accion}.` : `${nombre}. Te lleva a esa sección.`;
}

/**
 * La frase para lo que se tocó, o '' si no hay nada útil que decir (por
 * ejemplo, un espacio vacío de la página).
 */
export function describir(objetivo: Element): string {
  const propio = objetivo.closest('[data-lector]');
  if (propio) {
    return limpio(propio.getAttribute('data-lector'));
  }

  const interactivo = objetivo.closest(INTERACTIVOS);
  if (interactivo) {
    const tag = interactivo.tagName;
    const rol = interactivo.getAttribute('role');

    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') {
      return describirCampo(interactivo as HTMLElement);
    }
    if (tag === 'LABEL') {
      // Al tocar la etiqueta, el navegador también "toca" su campo: se
      // describe el campo para que ambas veces se diga lo mismo.
      const campo = (interactivo as HTMLLabelElement).control;
      return campo ? describir(campo) : textoDe(interactivo);
    }
    if (tag === 'A' || rol === 'link' || rol === 'tab') {
      return describirEnlace(interactivo);
    }
    return describirBoton(interactivo);
  }

  // Texto común: se lee lo que dice el elemento tocado (o el más cercano
  // con texto), salvo que sea un bloque tan grande que sea media página.
  let el: Element | null = objetivo;
  while (el && el !== document.body) {
    const texto = textoDe(el);
    if (texto) {
      return texto.length <= MAX_TEXTO ? texto : '';
    }
    const alt = limpio(el.getAttribute('alt'));
    if (alt) {
      return `Imagen: ${alt}.`;
    }
    el = el.parentElement;
  }
  return '';
}
