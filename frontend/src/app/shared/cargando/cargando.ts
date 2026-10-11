import { Component, input } from '@angular/core';

import { Icono } from '../icono/icono';

/**
 * Mensaje de carga con el reloj de arena girando: <app-cargando texto="Cargando actividades..." />.
 * Toma el tamaño y el color del texto de donde se ponga.
 */
@Component({
  selector: 'app-cargando',
  standalone: true,
  imports: [Icono],
  template: `
    <span class="cargando" role="status" aria-live="polite">
      <app-icono name="hourglass" class="icono-cargando cargando__icono" aria-hidden="true" />
      {{ texto() }}
    </span>
  `,
  styles: `
    :host {
      display: block;
    }

    .cargando {
      display: inline-flex;
      align-items: center;
      gap: 10px;
    }

    .cargando__icono {
      font-size: 1.25em;
      color: var(--vita-navy);
    }
  `
})
export class Cargando {
  readonly texto = input('Cargando...');
}
