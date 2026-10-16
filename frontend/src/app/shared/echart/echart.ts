import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  effect,
  input,
  output,
  viewChild
} from '@angular/core';

import * as echarts from 'echarts/core';
import { BarChart, GraphChart, LineChart, PieChart } from 'echarts/charts';
import {
  AriaComponent,
  GraphicComponent,
  GridComponent,
  LegendComponent,
  MarkAreaComponent,
  MarkLineComponent,
  TitleComponent,
  TooltipComponent
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

// Se registran solo los módulos que se usan, para que el paquete de la
// página no crezca.
echarts.use([
  BarChart,
  GraphChart,
  LineChart,
  PieChart,
  AriaComponent,
  GraphicComponent,
  GridComponent,
  LegendComponent,
  MarkAreaComponent,
  MarkLineComponent,
  TitleComponent,
  TooltipComponent,
  CanvasRenderer
]);

/** Opciones de ECharts tal como las arma cada reporte. */
export type OpcionesGrafica = echarts.EChartsCoreOption;

/**
 * Dibuja una gráfica fuera de pantalla y la devuelve como PNG (data URL).
 * Sirve para exportar a PDF aunque la gráfica no esté visible (p. ej. si
 * en pantalla se está viendo su tabla).
 */
export function imagenGrafica(opciones: OpcionesGrafica, ancho = 1000, alto = 420): string {
  const contenedor = document.createElement('div');
  const grafica = echarts.init(contenedor, undefined, { renderer: 'canvas', width: ancho, height: alto });
  try {
    grafica.setOption({ ...opciones, animation: false });
    return grafica.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#ffffff' });
  } finally {
    grafica.dispose();
  }
}

/** Barra, punto o porción en la que se hizo clic. */
export interface ClicGrafica {
  nombre: string;
  serie?: string;
  indice: number;
}

/**
 * Envoltura mínima de Apache ECharts. Recibe las opciones ya armadas
 * (ver tema.ts) y se redimensiona sola con su contenedor.
 */
@Component({
  selector: 'app-echart',
  template: `<div #contenedor class="echart" [style.height.px]="alto()" role="img" [attr.aria-label]="descripcion()"></div>`,
  styles: [`.echart { width: 100%; }`]
})
export class EChart implements AfterViewInit, OnDestroy {

  readonly opciones = input.required<OpcionesGrafica>();
  readonly alto = input(300);
  /** Texto para lectores de pantalla (la tabla da el detalle). */
  readonly descripcion = input('');

  readonly clic = output<ClicGrafica>();

  private readonly contenedor = viewChild.required<ElementRef<HTMLDivElement>>('contenedor');
  private grafica?: echarts.ECharts;
  private observador?: ResizeObserver;

  constructor() {
    // Redibuja la gráfica cuando cambian las opciones.
    effect(() => {
      const opciones = this.opciones();
      this.grafica?.setOption(opciones, { notMerge: true });
    });
  }

  ngAfterViewInit(): void {
    const elemento = this.contenedor().nativeElement;

    this.grafica = echarts.init(elemento, undefined, { renderer: 'canvas' });
    this.grafica.setOption(this.opciones(), { notMerge: true });

    this.grafica.on('click', (p) => this.clic.emit({
      nombre: String(p.name ?? ''),
      serie: p.seriesName,
      indice: p.dataIndex ?? 0
    }));

    this.observador = new ResizeObserver(() => this.grafica?.resize());
    this.observador.observe(elemento);
  }

  ngOnDestroy(): void {
    this.observador?.disconnect();
    this.grafica?.dispose();
  }
}
