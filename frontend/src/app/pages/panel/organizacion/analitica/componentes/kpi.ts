import { Component, input } from '@angular/core';

import { Icono } from '../../../../../shared/icono/icono';

/** Estado de la tarjeta: neutro, bueno (verde) o alerta (rojo). */
export type TonoKpi = 'neutro' | 'bueno' | 'alerta';

/** Datos de una tarjeta; los usa la pantalla y el PDF del reporte. */
export interface DatoKpi {
  etiqueta: string;
  valor: string;
  detalle: string;
  icono: string;
  tono: TonoKpi;
}

/**
 * Tarjeta de indicador (KPI): etiqueta, valor grande y detalle. El tono
 * de estado siempre va con icono y texto, nunca solo con color.
 */
@Component({
  selector: 'app-kpi',
  imports: [Icono],
  template: `
    <div class="kpi card" [class.kpi--alerta]="tono() === 'alerta'" [class.kpi--bueno]="tono() === 'bueno'">
      <span class="kpi__etiqueta">
        <app-icono [name]="icono()" /> {{ etiqueta() }}
      </span>
      <strong class="kpi__valor">{{ valor() }}</strong>
      @if (detalle()) {
        <span class="kpi__detalle">
          @if (tono() === 'alerta') { <app-icono name="alert-circle" /> }
          @if (tono() === 'bueno') { <app-icono name="check-circle" /> }
          {{ detalle() }}
        </span>
      }
    </div>
  `,
  styles: [`
    :host { display: block; min-width: 0; }
    .kpi {
      display: flex; flex-direction: column; gap: 6px;
      height: 100%; padding: 18px 20px; box-sizing: border-box;
      border-top: 4px solid var(--vita-navy);
    }
    .kpi__etiqueta {
      display: inline-flex; align-items: center; gap: 8px;
      font-size: 0.85rem; font-weight: 600; color: var(--vita-text-gray);
    }
    .kpi__valor { font-size: 2rem; font-weight: 700; line-height: 1.1; color: var(--vita-navy); }
    .kpi__detalle {
      display: inline-flex; align-items: center; gap: 6px;
      font-size: 0.82rem; color: var(--vita-text-gray);
    }
    .kpi--alerta { border-top-color: #d03b3b; }
    .kpi--alerta .kpi__detalle { color: #9e2929; font-weight: 600; }
    .kpi--bueno { border-top-color: #0ca30c; }
    .kpi--bueno .kpi__detalle { color: #006300; font-weight: 600; }
  `]
})
export class Kpi {
  readonly etiqueta = input.required<string>();
  readonly valor = input.required<string>();
  readonly detalle = input('');
  readonly icono = input('bar-chart');
  readonly tono = input<TonoKpi>('neutro');
}
