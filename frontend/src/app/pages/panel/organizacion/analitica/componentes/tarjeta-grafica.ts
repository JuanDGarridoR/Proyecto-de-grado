import { Component, input, output, signal } from '@angular/core';

import { ClicGrafica, EChart, OpcionesGrafica } from '../../../../../shared/echart/echart';

/** Los mismos datos de la gráfica, en forma de tabla. */
export interface TablaGrafica {
  columnas: string[];
  filas: (string | number)[][];
}

/**
 * Tarjeta de una gráfica del reporte: título, descripción, controles
 * propios (proyectados con [acciones]) y un botón para ver los mismos
 * datos como tabla (la vista accesible de cada gráfica).
 */
@Component({
  selector: 'app-tarjeta-grafica',
  imports: [EChart],
  templateUrl: './tarjeta-grafica.html',
  styleUrl: './tarjeta-grafica.css'
})
export class TarjetaGrafica {

  readonly titulo = input.required<string>();
  readonly descripcion = input('');
  readonly opciones = input.required<OpcionesGrafica>();
  readonly tabla = input.required<TablaGrafica>();
  readonly alto = input(300);
  /** Si es true, en lugar de la gráfica se muestra textoVacio. */
  readonly vacio = input(false);
  readonly textoVacio = input('No hay datos para mostrar en este período.');

  readonly clic = output<ClicGrafica>();

  protected readonly verTabla = signal(false);
}
