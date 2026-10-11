import { Component, input, model } from '@angular/core';

import { Icono } from '../icono/icono';

/** Texto en minúsculas y sin tildes, para comparar nombres: "José" -> "jose". */
export function normalizar(texto: string | null | undefined): string {
  return (texto ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim();
}

/** Los elementos cuyo nombre contiene el texto buscado (sin importar mayúsculas ni tildes). */
export function filtrarPorNombre<T extends { nombre: string | null }>(lista: T[], texto: string): T[] {
  const buscado = normalizar(texto);
  return buscado ? lista.filter((item) => normalizar(item.nombre).includes(buscado)) : lista;
}

/**
 * Búsqueda rápida por nombre en las listas de cosas ya asociadas (mis
 * organizaciones, mis personas mayores...). Se usa con [(texto)] y la página
 * filtra su lista con filtrarPorNombre().
 */
@Component({
  selector: 'app-buscador-nombre',
  standalone: true,
  imports: [Icono],
  template: `
    <div class="buscador" [class.buscador--grande]="grande()">
      <app-icono name="search" class="buscador__icono" aria-hidden="true" />
      <input
        type="search"
        class="buscador__input"
        [placeholder]="placeholder()"
        [attr.aria-label]="placeholder()"
        [value]="texto()"
        (input)="texto.set($any($event.target).value)"
      />
      @if (texto()) {
        <button type="button" class="buscador__limpiar" (click)="texto.set('')" aria-label="Borrar búsqueda">
          <app-icono name="x" />
        </button>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
      width: 100%;
      max-width: 340px;
    }

    .buscador {
      position: relative;
      display: flex;
      align-items: center;
    }

    .buscador__icono {
      position: absolute;
      left: 12px;
      color: var(--vita-text-gray);
      pointer-events: none;
    }

    .buscador__input {
      width: 100%;
      height: 42px;
      padding: 0 38px 0 38px;
      box-sizing: border-box;
      border: 1px solid var(--vita-border);
      border-radius: 10px;
      background: var(--vita-white);
      color: var(--vita-navy);
      font: inherit;
      font-size: 0.95rem;
    }

    /* El navegador pone su propia "x" en los type="search": se usa la nuestra */
    .buscador__input::-webkit-search-cancel-button {
      display: none;
    }

    .buscador__input:focus {
      outline: none;
      border-color: var(--vita-navy);
      box-shadow: 0 0 0 3px rgba(18, 53, 91, 0.1);
    }

    .buscador__limpiar {
      position: absolute;
      right: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 30px;
      height: 30px;
      border: none;
      border-radius: 50%;
      background: transparent;
      color: var(--vita-text-gray);
      cursor: pointer;
    }

    .buscador__limpiar:hover {
      background: var(--vita-cream);
      color: var(--vita-navy);
    }

    /* Versión con letra más grande (panel de la persona mayor) */
    .buscador--grande .buscador__input {
      height: 50px;
      font-size: 1.05rem;
      padding-left: 42px;
    }

    .buscador--grande .buscador__icono {
      left: 14px;
      font-size: 1.15rem;
    }
  `
})
export class BuscadorNombre {
  /** Texto buscado; se enlaza con [(texto)]. */
  readonly texto = model('');

  readonly placeholder = input('Buscar por nombre...');

  /** Letra y alto mayores, para el panel de la persona mayor. */
  readonly grande = input(false);
}
