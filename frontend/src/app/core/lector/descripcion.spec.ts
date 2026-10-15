import { describir } from './descripcion';

/** Monta el HTML en la página y devuelve el elemento con ese selector. */
function montar(html: string, selector: string): Element {
  document.body.innerHTML = html;
  return document.querySelector(selector)!;
}

describe('frases del lector de voz', () => {
  it('dice para qué sirve un botón conocido', () => {
    const boton = montar('<button>Guardar cambios</button>', 'button');
    expect(describir(boton)).toBe('Botón Guardar cambios. Sirve para: guarda los cambios que hiciste.');
  });

  it('usa el aria-label de un botón que solo tiene icono, aunque se toque el icono', () => {
    const icono = montar('<button aria-label="Cerrar"><svg><path></path></svg></button>', 'path');
    expect(describir(icono)).toBe('Botón Cerrar. Sirve para: cierra esta ventana.');
  });

  it('avisa si el botón está deshabilitado', () => {
    const boton = montar('<button disabled>Enviar</button>', 'button');
    expect(describir(boton)).toContain('Por ahora no se puede usar');
  });

  it('explica qué escribir en un campo, con su etiqueta y el ejemplo', () => {
    const campo = montar(
      '<label for="cel">Número de celular *</label><input id="cel" type="tel" placeholder="3001234567" required>',
      'input'
    );
    expect(describir(campo)).toBe('Aquí escribe: Número de celular. Por ejemplo: 3001234567. Es obligatorio.');
  });

  it('al tocar la etiqueta describe su campo', () => {
    const etiqueta = montar('<label for="c">Contraseña</label><input id="c" type="password" placeholder="••••">', 'label');
    expect(describir(etiqueta)).toBe('Aquí escribe: Contraseña.');
  });

  it('sin etiqueta, se guía por el tipo del campo', () => {
    const campo = montar('<input type="email">', 'input');
    expect(describir(campo)).toBe('Aquí escribe tu correo electrónico.');
  });

  it('data-lector reemplaza la frase automática', () => {
    const boton = montar('<button data-lector="Abre tu lista de medicamentos.">Ir</button>', 'button');
    expect(describir(boton)).toBe('Abre tu lista de medicamentos.');
  });

  it('lee el texto común, pero no un bloque gigante', () => {
    expect(describir(montar('<p>Tu próxima cita es el lunes.</p>', 'p'))).toBe('Tu próxima cita es el lunes.');
    expect(describir(montar(`<div>${'texto '.repeat(200)}</div>`, 'div'))).toBe('');
  });
});
