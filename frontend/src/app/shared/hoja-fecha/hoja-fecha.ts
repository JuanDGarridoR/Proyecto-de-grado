import { Component, computed, input } from '@angular/core';

/**
 * Hoja de calendario con el mes en una franja y el día grande debajo
 * ("OCT / 21"). Es la misma en citas médicas, seguimiento y actividades.
 *
 * fecha: "YYYY-MM-DD" (se lee en hora local, sin correr el día por la zona
 * horaria) o un Date; si es null, muestra "—".
 * tono: "destacada" (franja verde: la próxima), "pasada" (gris) o "normal".
 */
@Component({
  selector: 'app-hoja-fecha',
  template: `
    <span class="hoja" [class.hoja--destacada]="tono() === 'destacada'" [class.hoja--pasada]="tono() === 'pasada'">
      <span class="hoja__mes">{{ partes().mes }}</span>
      <strong class="hoja__dia">{{ partes().dia }}</strong>
    </span>
  `,
  styles: `
    :host {
      display: block;
      flex-shrink: 0;
    }

    .hoja {
      width: 58px;
      display: flex;
      flex-direction: column;
      align-items: center;
      overflow: hidden;
      border: 1px solid var(--vita-border);
      border-radius: 12px;
      background: var(--vita-white);
    }

    .hoja__mes {
      width: 100%;
      padding: 3px 0;
      background: var(--vita-navy);
      color: var(--vita-white);
      font-size: 0.68rem;
      font-weight: 700;
      text-align: center;
      text-transform: uppercase;
      letter-spacing: 0.08em;
    }

    .hoja__dia {
      padding: 3px 0 5px;
      color: var(--vita-navy);
      font-size: 1.4rem;
      font-weight: 800;
      line-height: 1.1;
    }

    .hoja--destacada {
      border-color: color-mix(in srgb, var(--vita-green) 55%, white);
    }

    .hoja--destacada .hoja__mes {
      background: var(--vita-green);
    }

    .hoja--pasada .hoja__mes {
      background: #8996a8;
    }

    .hoja--pasada .hoja__dia {
      color: var(--vita-text-gray);
    }
  `,
  host: { 'aria-hidden': 'true' }
})
export class HojaFecha {
  readonly fecha = input<string | Date | null | undefined>(null);
  readonly tono = input<'normal' | 'destacada' | 'pasada'>('normal');

  protected readonly partes = computed(() => {
    const valor = this.fecha();
    if (!valor) {
      return { mes: '—', dia: '—' };
    }

    const fecha = valor instanceof Date ? valor : HojaFecha.leer(valor);
    return {
      mes: fecha.toLocaleDateString('es-CO', { month: 'short' }).replace('.', ''),
      dia: String(fecha.getDate())
    };
  });

  /** "2026-10-21" o "2026-10-21T09:30" en hora local. */
  private static leer(valor: string): Date {
    const [anio, mes, dia] = valor.slice(0, 10).split('-').map(Number);
    return new Date(anio, mes - 1, dia);
  }
}
