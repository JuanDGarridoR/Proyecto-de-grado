import { Component, computed, input } from '@angular/core';

import { InventarioAnalitica } from '../../../../../core/analitica/analitica.service';
import {
  CATEGORIAS, formatearCantidad, formatearFechaCorta, textoCategoria, textoMotivo
} from '../../../../../core/donaciones/catalogo';
import {
  ANCHO_BARRA, ESTADO, SERIE, base, ejeCategorias, ejeValores, estiloSegmento, leyenda, tooltip
} from '../../../../../shared/echart/tema';
import { OpcionesGrafica } from '../../../../../shared/echart/echart';
import { Icono } from '../../../../../shared/icono/icono';
import { TarjetaGrafica, TablaGrafica } from '../componentes/tarjeta-grafica';
import { DatoKpi, Kpi } from '../componentes/kpi';
import { ContenidoReporte, ReporteExportable } from '../contenido-reporte';

type Articulo = InventarioAnalitica['articulos'][number];
type Movimiento = InventarioAnalitica['movimientos'][number];

/** Mismas reglas que la página de Inventario: aviso de vencimiento con 30 días. */
const DIAS_AVISO_VENCIMIENTO = 30;

/** Estado de existencias de un artículo (con su color de estado y su nombre). */
type Existencias = 'normal' | 'por-agotarse' | 'agotado';
const EXISTENCIAS: { valor: Existencias; nombre: string; color: string }[] = [
  { valor: 'normal', nombre: 'Con existencias', color: ESTADO.bueno },
  { valor: 'por-agotarse', nombre: 'Por agotarse', color: ESTADO.aviso },
  { valor: 'agotado', nombre: 'Agotados', color: ESTADO.critico }
];

/** Entradas y salidas, en el orden fijo de la paleta (no son estados: no llevan colores de estado). */
const TIPOS: { valor: Movimiento['tipo']; nombre: string; color: string }[] = [
  { valor: 'ENTRADA', nombre: 'Entradas', color: SERIE[0] },
  { valor: 'SALIDA', nombre: 'Salidas', color: SERIE[1] }
];

type Aviso = 'agotado' | 'por-agotarse' | 'vencido' | 'vence-pronto';
const TEXTO_AVISO: Record<Aviso, string> = {
  agotado: 'Agotado',
  'por-agotarse': 'Por agotarse',
  vencido: 'Vencido',
  'vence-pronto': 'Vence pronto'
};

const hoy = () => new Date().toLocaleDateString('en-CA');

function existencias(a: Articulo): Existencias {
  if (a.cantidad <= 0) return 'agotado';
  if (a.cantidadMinima !== null && a.cantidad <= a.cantidadMinima) return 'por-agotarse';
  return 'normal';
}

/** "oct 2026" a partir de "2026-10". */
function etiquetaMes(clave: string): string {
  const [anio, mes] = clave.split('-').map(Number);
  return new Date(anio, mes - 1, 1).toLocaleDateString('es-CO', { month: 'short', year: 'numeric' });
}

/**
 * Reporte de inventario: cómo está hoy (qué se agota, qué se vence, cuánto
 * dinero hay) y cómo se movió en el período (cuántas entradas y salidas, de
 * dónde llegan las cosas y a dónde van). Los movimientos se cuentan, no se
 * suman, porque cada artículo tiene su unidad (kg, unidades, pesos...).
 */
@Component({
  selector: 'app-reporte-inventario',
  imports: [Icono, Kpi, TarjetaGrafica],
  templateUrl: './reporte-inventario.html',
  styleUrls: ['../reporte.css', './reporte-salud.css', './reporte-recursos.css']
})
export class ReporteInventario implements ReporteExportable {

  readonly datos = input.required<InventarioAnalitica>();
  /** Inicio del período (YYYY-MM-DD) o null = todo. Solo filtra los movimientos. */
  readonly desde = input<string | null>(null);

  /** Movimientos del período. */
  protected readonly movimientos = computed<Movimiento[]>(() => {
    const desde = this.desde();
    return desde ? this.datos().movimientos.filter((m) => m.fecha.slice(0, 10) >= desde) : this.datos().movimientos;
  });

  /** Avisos de un artículo: agotado o por agotarse, vencido o por vencer. */
  protected avisos(a: Articulo): Aviso[] {
    const avisos: Aviso[] = [];
    const e = existencias(a);
    if (e !== 'normal') avisos.push(e);

    if (a.fechaVencimiento && a.cantidad > 0) {
      const limite = new Date(Date.now() + DIAS_AVISO_VENCIMIENTO * 86_400_000).toLocaleDateString('en-CA');
      if (a.fechaVencimiento < hoy()) avisos.push('vencido');
      else if (a.fechaVencimiento <= limite) avisos.push('vence-pronto');
    }
    return avisos;
  }

  /** Artículos con algún aviso, primero los más urgentes. */
  protected readonly conAvisos = computed(() => {
    const orden: Aviso[] = ['agotado', 'vencido', 'por-agotarse', 'vence-pronto'];
    return this.datos().articulos
      .map((a) => ({ articulo: a, avisos: this.avisos(a) }))
      .filter((x) => x.avisos.length > 0)
      .sort((x, y) => Math.min(...x.avisos.map((a) => orden.indexOf(a))) - Math.min(...y.avisos.map((a) => orden.indexOf(a)))
        || x.articulo.nombre.localeCompare(y.articulo.nombre));
  });

  private readonly conteo = computed(() => {
    const articulos = this.datos().articulos;
    const lista = this.conAvisos();
    const tiene = (aviso: Aviso[]) => lista.filter((x) => x.avisos.some((a) => aviso.includes(a))).length;
    return {
      articulos: articulos.length,
      categorias: new Set(articulos.map((a) => a.categoria)).size,
      agotandose: tiene(['agotado', 'por-agotarse']),
      agotados: tiene(['agotado']),
      venciendose: tiene(['vencido', 'vence-pronto']),
      vencidos: tiene(['vencido']),
      dinero: articulos.filter((a) => a.categoria === 'DINERO').reduce((s, a) => s + a.cantidad, 0),
      entradas: this.movimientos().filter((m) => m.tipo === 'ENTRADA').length,
      salidas: this.movimientos().filter((m) => m.tipo === 'SALIDA').length
    };
  });

  // Indicadores (estado de hoy)

  protected readonly tarjetas = computed<DatoKpi[]>(() => {
    const c = this.conteo();
    return [
      { etiqueta: 'Artículos', icono: 'clipboard', tono: 'neutro', valor: String(c.articulos),
        detalle: `En ${c.categorias} ${c.categorias === 1 ? 'categoría' : 'categorías'}` },
      { etiqueta: 'Agotados o por agotarse', icono: 'alert-triangle', tono: c.agotandose > 0 ? 'alerta' : 'bueno',
        valor: String(c.agotandose),
        detalle: c.agotandose > 0 ? `${c.agotados} ${c.agotados === 1 ? 'agotado' : 'agotados'}` : 'Todo tiene existencias' },
      { etiqueta: 'Vencidos o por vencer', icono: 'calendar', tono: c.venciendose > 0 ? 'alerta' : 'bueno',
        valor: String(c.venciendose),
        detalle: c.venciendose > 0
          ? `${c.vencidos} ${c.vencidos === 1 ? 'vencido' : 'vencidos'}`
          : `Nada vence en ${DIAS_AVISO_VENCIMIENTO} días` },
      { etiqueta: 'Dinero disponible', icono: 'tag', tono: 'neutro', valor: formatearCantidad(c.dinero, 'COP'),
        detalle: 'En el inventario hoy' }
    ];
  });

  // Gráfica 1: entradas y salidas por mes

  private readonly porMes = computed(() => {
    const movimientos = this.movimientos();
    const meses = new Map<string, { ENTRADA: number; SALIDA: number }>();

    const desde = this.desde() ?? movimientos[0]?.fecha.slice(0, 10);
    if (desde) {
      const cursor = new Date(Number(desde.slice(0, 4)), Number(desde.slice(5, 7)) - 1, 1);
      while (cursor.toLocaleDateString('en-CA').slice(0, 7) <= hoy().slice(0, 7)) {
        meses.set(cursor.toLocaleDateString('en-CA').slice(0, 7), { ENTRADA: 0, SALIDA: 0 });
        cursor.setMonth(cursor.getMonth() + 1);
      }
    }
    for (const m of movimientos) {
      const clave = m.fecha.slice(0, 7);
      const mes = meses.get(clave) ?? meses.set(clave, { ENTRADA: 0, SALIDA: 0 }).get(clave)!;
      mes[m.tipo]++;
    }
    return [...meses.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([clave, tipos]) => ({ etiqueta: etiquetaMes(clave), ...tipos }));
  });

  protected readonly graficaMeses = computed<OpcionesGrafica>(() => {
    const datos = this.porMes();
    return base({
      legend: leyenda(),
      grid: { left: 8, right: 16, top: 36, bottom: 8, containLabel: true },
      tooltip: tooltip({ trigger: 'axis', axisPointer: { type: 'shadow' } }),
      xAxis: ejeCategorias(datos.map((d) => d.etiqueta)),
      yAxis: ejeValores({ minInterval: 1 }),
      series: TIPOS.map((t) => ({
        name: t.nombre, type: 'bar', barMaxWidth: ANCHO_BARRA, barGap: '10%',
        itemStyle: { color: t.color, borderRadius: [4, 4, 0, 0] },
        data: datos.map((d) => d[t.valor])
      }))
    });
  });

  protected readonly tablaMeses = computed<TablaGrafica>(() => ({
    columnas: ['Mes', 'Entradas', 'Salidas'],
    filas: this.porMes().map((d) => [d.etiqueta, d.ENTRADA, d.SALIDA])
  }));

  // Gráfica 2: de dónde llega y a dónde va (motivos)

  protected readonly porMotivo = computed(() => {
    const conteo = new Map<string, { ENTRADA: number; SALIDA: number }>();
    for (const m of this.movimientos()) {
      const c = conteo.get(m.motivo) ?? conteo.set(m.motivo, { ENTRADA: 0, SALIDA: 0 }).get(m.motivo)!;
      c[m.tipo]++;
    }
    return [...conteo.entries()]
      .map(([motivo, c]) => ({ motivo, nombre: textoMotivo(motivo), ...c, total: c.ENTRADA + c.SALIDA }))
      .sort((a, b) => b.total - a.total || a.nombre.localeCompare(b.nombre));
  });

  protected readonly graficaMotivos = computed<OpcionesGrafica>(() => {
    const datos = [...this.porMotivo()].reverse();
    const presentes = TIPOS.filter((t) => datos.some((d) => d[t.valor] > 0));
    return base({
      legend: leyenda(),
      grid: { left: 8, right: 24, top: 36, bottom: 8, containLabel: true },
      tooltip: tooltip({ trigger: 'axis', axisPointer: { type: 'shadow' } }),
      xAxis: ejeValores({ minInterval: 1 }),
      yAxis: ejeCategorias(datos.map((d) => d.nombre)),
      series: presentes.map((t, i) => ({
        name: t.nombre, type: 'bar', stack: 'motivo', barMaxWidth: ANCHO_BARRA,
        itemStyle: estiloSegmento(t.color, i === presentes.length - 1),
        data: datos.map((d) => d[t.valor] || null)
      }))
    });
  });

  protected readonly tablaMotivos = computed<TablaGrafica>(() => ({
    columnas: ['Motivo', 'Entradas', 'Salidas'],
    filas: this.porMotivo().map((d) => [d.nombre, d.ENTRADA, d.SALIDA])
  }));

  // Gráfica 3: existencias por categoría (estado de hoy)

  private readonly porCategoria = computed(() =>
    CATEGORIAS
      .map((c) => {
        const articulos = this.datos().articulos.filter((a) => a.categoria === c.codigo);
        const de = (e: Existencias) => articulos.filter((a) => existencias(a) === e).length;
        return { nombre: c.texto, normal: de('normal'), 'por-agotarse': de('por-agotarse'), agotado: de('agotado'),
          total: articulos.length };
      })
      .filter((c) => c.total > 0)
  );

  protected readonly graficaCategorias = computed<OpcionesGrafica>(() => {
    const datos = [...this.porCategoria()].reverse();
    const presentes = EXISTENCIAS.filter((e) => datos.some((d) => d[e.valor] > 0));
    return base({
      legend: leyenda(),
      grid: { left: 8, right: 24, top: 36, bottom: 8, containLabel: true },
      tooltip: tooltip({ trigger: 'axis', axisPointer: { type: 'shadow' } }),
      xAxis: ejeValores({ minInterval: 1 }),
      yAxis: ejeCategorias(datos.map((d) => d.nombre)),
      series: presentes.map((e, i) => ({
        name: e.nombre, type: 'bar', stack: 'existencias', barMaxWidth: ANCHO_BARRA,
        itemStyle: estiloSegmento(e.color, i === presentes.length - 1),
        data: datos.map((d) => d[e.valor] || null)
      }))
    });
  });

  protected readonly tablaCategorias = computed<TablaGrafica>(() => ({
    columnas: ['Categoría', ...EXISTENCIAS.map((e) => e.nombre), 'Total'],
    filas: this.porCategoria().map((d) => [d.nombre, ...EXISTENCIAS.map((e) => d[e.valor]), d.total])
  }));

  // Tabla de artículos que requieren atención

  protected readonly textoAviso = TEXTO_AVISO;
  protected readonly textoCategoria = textoCategoria;
  protected readonly cantidad = formatearCantidad;
  protected readonly fecha = formatearFechaCorta;

  // ---------- Explicaciones (PDF) ----------

  private explicacionMeses(): string {
    const c = this.conteo();
    if (c.entradas + c.salidas === 0) {
      return 'No hubo entradas ni salidas del inventario en el período.';
    }
    return `En el período hubo ${c.entradas} ${c.entradas === 1 ? 'entrada' : 'entradas'} y ${c.salidas} `
      + `${c.salidas === 1 ? 'salida' : 'salidas'} del inventario. Se cuentan los movimientos y no las cantidades, `
      + 'porque cada artículo tiene su propia unidad (kilos, unidades, pesos...). Si las salidas superan a las '
      + 'entradas por varios meses, el inventario se está gastando más rápido de lo que se repone.';
  }

  private explicacionMotivos(): string {
    const datos = this.porMotivo();
    if (datos.length === 0) {
      return 'No hubo movimientos en el período.';
    }
    const donaciones = datos.find((d) => d.motivo === 'DONACION')?.ENTRADA ?? 0;
    const entradas = datos.reduce((s, d) => s + d.ENTRADA, 0);
    return `El motivo más frecuente fue "${datos[0].nombre}" (${datos[0].total} movimientos). `
      + (entradas > 0
        ? `${donaciones} de las ${entradas} entradas vinieron de donaciones recibidas en VITA+.`
        : 'No hubo entradas en el período.');
  }

  private explicacionCategorias(): string {
    const c = this.conteo();
    if (c.articulos === 0) {
      return 'El inventario no tiene artículos registrados.';
    }
    return `De los ${c.articulos} artículos del inventario, ${c.agotandose} están agotados o por agotarse y `
      + `${c.venciendose} están vencidos o vencen en los próximos ${DIAS_AVISO_VENCIMIENTO} días. Esta gráfica `
      + 'muestra el estado de hoy, sin importar el período elegido.';
  }

  // ---------- PDF ----------

  contenidoPdf(): ContenidoReporte {
    return {
      indicadores: this.tarjetas(),
      secciones: [
        { titulo: 'Entradas y salidas por mes', descripcion: 'Cuántos movimientos hubo cada mes del período.',
          opciones: this.movimientos().length > 0 ? this.graficaMeses() : null, tabla: this.tablaMeses(),
          explicacion: this.explicacionMeses() },
        { titulo: 'De dónde llega y a dónde va', descripcion: 'Movimientos del período según su motivo.',
          opciones: this.porMotivo().length > 0 ? this.graficaMotivos() : null, tabla: this.tablaMotivos(),
          explicacion: this.explicacionMotivos() },
        { titulo: 'Existencias por categoría', descripcion: 'Estado de hoy de los artículos de cada categoría.',
          opciones: this.porCategoria().length > 0 ? this.graficaCategorias() : null, tabla: this.tablaCategorias(),
          explicacion: this.explicacionCategorias() },
        { titulo: 'Artículos que requieren atención',
          descripcion: 'Agotados, por agotarse, vencidos o que vencen pronto.',
          opciones: null,
          tabla: {
            columnas: ['Artículo', 'Categoría', 'Cantidad', 'Vence', 'Aviso'],
            filas: this.conAvisos().map(({ articulo: a, avisos }) => [
              a.nombre, textoCategoria(a.categoria), formatearCantidad(a.cantidad, a.unidad),
              a.fechaVencimiento ? formatearFechaCorta(a.fechaVencimiento) : '—',
              avisos.map((x) => TEXTO_AVISO[x]).join(', ')
            ])
          } }
      ],
      nota: 'Los indicadores, las existencias y la lista de atención muestran el inventario de hoy; el período solo '
        + `filtra las entradas y salidas. "Por agotarse" es llegar a la cantidad mínima de aviso de cada artículo, y `
        + `"vence pronto", vencer en los próximos ${DIAS_AVISO_VENCIMIENTO} días.`
    };
  }
}
