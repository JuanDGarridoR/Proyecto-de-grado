import { Component, computed, input } from '@angular/core';

import {
  DonacionAnalitica, DonacionesAnalitica, InventarioAnalitica
} from '../../../../../core/analitica/analitica.service';
import {
  CATEGORIAS, formatearCantidad, formatearFechaCorta, textoCategoria, textoMotivo
} from '../../../../../core/donaciones/catalogo';
import {
  ANCHO_BARRA, ESTADO, GRIS, SERIE, base, ejeCategorias, ejeValores, estiloBarra, estiloSegmento, etiquetaValor,
  leyenda, tooltip
} from '../../../../../shared/echart/tema';
import { OpcionesGrafica } from '../../../../../shared/echart/echart';
import { Icono } from '../../../../../shared/icono/icono';
import { TarjetaGrafica, TablaGrafica } from '../componentes/tarjeta-grafica';
import { DatoKpi, Kpi } from '../componentes/kpi';
import { ContenidoReporte, ReporteExportable } from '../contenido-reporte';

type EstadoDonacion = DonacionAnalitica['estado'];
type Articulo = InventarioAnalitica['articulos'][number];
type Movimiento = InventarioAnalitica['movimientos'][number];

/**
 * Estados de una donación, con su color de estado (siempre con su nombre en
 * la leyenda, nunca solo el color). El orden es el de las barras apiladas.
 */
const ESTADOS: { valor: EstadoDonacion; nombre: string; color: string }[] = [
  { valor: 'RECIBIDA', nombre: 'Recibidas', color: ESTADO.bueno },
  { valor: 'PROGRAMADA', nombre: 'Por recibir', color: ESTADO.aviso },
  { valor: 'NO_ENTREGADA', nombre: 'No llegaron', color: ESTADO.critico },
  { valor: 'CANCELADA', nombre: 'Canceladas', color: GRIS }
];

const textoRol = (rol: string) => (rol === 'VOLUNTARIO' ? 'Voluntario' : 'Acompañante');

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

/** Porcentaje entero; 0 si el total es 0. */
const pct = (parte: number, total: number) => (total > 0 ? Math.round((parte / total) * 100) : 0);

/** Hoy en formato YYYY-MM-DD (hora local). */
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
 * Reporte de donaciones e inventario: lo que llega a la organización (qué
 * donaciones le programan, cuántas llegan, qué se recibe y quién dona) y
 * cómo está lo que tiene (qué se agota o se vence, cuánto dinero hay y en
 * qué se usa). Las donaciones y los movimientos se filtran por el período;
 * el estado del inventario es el de hoy.
 */
@Component({
  selector: 'app-reporte-recursos',
  imports: [Icono, Kpi, TarjetaGrafica],
  templateUrl: './reporte-recursos.html',
  styleUrls: ['../reporte.css', './reporte-salud.css', './reporte-recursos.css']
})
export class ReporteRecursos implements ReporteExportable {

  readonly donaciones = input.required<DonacionesAnalitica>();
  readonly inventario = input.required<InventarioAnalitica>();
  /** Inicio del período (YYYY-MM-DD) o null = todo. */
  readonly desde = input<string | null>(null);

  /** Donaciones con fecha de entrega en el período (incluidas las programadas para después). */
  protected readonly donacionesPeriodo = computed<DonacionAnalitica[]>(() => {
    const desde = this.desde();
    const todas = this.donaciones().donaciones;
    return desde ? todas.filter((d) => d.fechaEntrega >= desde) : todas;
  });

  /** Movimientos del inventario en el período. */
  protected readonly movimientos = computed<Movimiento[]>(() => {
    const desde = this.desde();
    const todos = this.inventario().movimientos;
    return desde ? todos.filter((m) => m.fecha.slice(0, 10) >= desde) : todos;
  });

  /** Avisos de un artículo: agotado o por agotarse, vencido o por vencer. */
  private avisos(a: Articulo): Aviso[] {
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
    return this.inventario().articulos
      .map((a) => ({ articulo: a, avisos: this.avisos(a) }))
      .filter((x) => x.avisos.length > 0)
      .sort((x, y) => Math.min(...x.avisos.map((a) => orden.indexOf(a))) - Math.min(...y.avisos.map((a) => orden.indexOf(a)))
        || x.articulo.nombre.localeCompare(y.articulo.nombre));
  });

  private readonly conteo = computed(() => {
    const donaciones = this.donacionesPeriodo();
    const de = (estado: EstadoDonacion) => donaciones.filter((d) => d.estado === estado).length;
    const recibidas = de('RECIBIDA');
    const noLlegaron = de('NO_ENTREGADA');
    const porRecibir = donaciones.filter((d) => d.estado === 'PROGRAMADA');
    const lista = this.conAvisos();
    const tiene = (aviso: Aviso[]) => lista.filter((x) => x.avisos.some((a) => aviso.includes(a))).length;
    return {
      donaciones: donaciones.length,
      recibidas,
      // De las que ya tenían que llegar (recibidas o no), cuántas llegaron
      conRespuesta: recibidas + noLlegaron,
      cumplimiento: pct(recibidas, recibidas + noLlegaron),
      porRecibir: porRecibir.length,
      vencidas: porRecibir.filter((d) => d.fechaEntrega < hoy()).length,
      dineroRecibido: donaciones
        .filter((d) => d.estado === 'RECIBIDA')
        .flatMap((d) => d.articulos)
        .filter((a) => a.categoria === 'DINERO')
        .reduce((suma, a) => suma + (a.cantidadRecibida ?? 0), 0),
      articulos: this.inventario().articulos.length,
      agotandose: tiene(['agotado', 'por-agotarse']),
      agotados: tiene(['agotado']),
      venciendose: tiene(['vencido', 'vence-pronto']),
      vencidos: tiene(['vencido']),
      dinero: this.inventario().articulos.filter((a) => a.categoria === 'DINERO').reduce((s, a) => s + a.cantidad, 0)
    };
  });

  // Indicadores

  protected readonly tarjetas = computed<DatoKpi[]>(() => {
    const c = this.conteo();
    return [
      { etiqueta: 'Donaciones que llegaron', icono: 'gift',
        tono: c.conRespuesta === 0 ? 'neutro' : c.cumplimiento >= 80 ? 'bueno' : 'alerta',
        valor: c.conRespuesta > 0 ? `${c.cumplimiento} %` : '—',
        detalle: c.conRespuesta > 0
          ? `${c.recibidas} de ${c.conRespuesta} llegaron`
          : 'Aún no hay donaciones recibidas o no entregadas' },
      { etiqueta: 'Donaciones por recibir', icono: 'hourglass', tono: c.vencidas > 0 ? 'alerta' : 'neutro',
        valor: String(c.porRecibir),
        detalle: c.vencidas > 0
          ? `${c.vencidas} con la fecha de entrega vencida`
          : c.porRecibir > 0 ? 'Ninguna con la fecha vencida' : 'No hay donaciones pendientes' },
      { etiqueta: 'Agotados o por agotarse', icono: 'alert-triangle', tono: c.agotandose > 0 ? 'alerta' : 'bueno',
        valor: String(c.agotandose),
        detalle: c.agotandose > 0
          ? `${c.agotados} ${c.agotados === 1 ? 'agotado' : 'agotados'} de ${c.articulos} artículos`
          : 'Todo tiene existencias' },
      { etiqueta: 'Vencidos o por vencer', icono: 'calendar', tono: c.venciendose > 0 ? 'alerta' : 'bueno',
        valor: String(c.venciendose),
        detalle: c.venciendose > 0
          ? `${c.vencidos} ${c.vencidos === 1 ? 'vencido' : 'vencidos'}`
          : `Nada vence en ${DIAS_AVISO_VENCIMIENTO} días` },
      { etiqueta: 'Dinero disponible', icono: 'tag', tono: 'neutro', valor: formatearCantidad(c.dinero, 'COP'),
        detalle: `${formatearCantidad(c.dineroRecibido, 'COP')} recibido en donaciones del período` }
    ];
  });

  // Gráfica 1: donaciones por mes y estado

  private readonly porMes = computed(() => {
    const donaciones = this.donacionesPeriodo();
    const meses = new Map<string, Record<EstadoDonacion, number>>();
    const vacio = (): Record<EstadoDonacion, number> => ({ RECIBIDA: 0, PROGRAMADA: 0, NO_ENTREGADA: 0, CANCELADA: 0 });

    // Todos los meses del período (también los que no tuvieron donaciones),
    // hasta el de la última entrega programada si es posterior a hoy.
    const fechas = donaciones.map((d) => d.fechaEntrega).sort();
    const desde = this.desde() ?? fechas[0];
    const hasta = [hoy(), fechas[fechas.length - 1] ?? hoy()].sort()[1];
    if (desde) {
      const cursor = new Date(Number(desde.slice(0, 4)), Number(desde.slice(5, 7)) - 1, 1);
      while (cursor.toLocaleDateString('en-CA').slice(0, 7) <= hasta.slice(0, 7)) {
        meses.set(cursor.toLocaleDateString('en-CA').slice(0, 7), vacio());
        cursor.setMonth(cursor.getMonth() + 1);
      }
    }

    for (const d of donaciones) {
      const clave = d.fechaEntrega.slice(0, 7);
      const mes = meses.get(clave) ?? meses.set(clave, vacio()).get(clave)!;
      mes[d.estado]++;
    }

    return [...meses.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([clave, estados]) => ({ etiqueta: etiquetaMes(clave), ...estados }));
  });

  protected readonly graficaMeses = computed<OpcionesGrafica>(() => {
    const datos = this.porMes();
    const presentes = ESTADOS.filter((e) => datos.some((d) => d[e.valor] > 0));
    return base({
      legend: leyenda(),
      grid: { left: 8, right: 16, top: 36, bottom: 8, containLabel: true },
      tooltip: tooltip({ trigger: 'axis', axisPointer: { type: 'shadow' } }),
      xAxis: ejeCategorias(datos.map((d) => d.etiqueta)),
      yAxis: ejeValores({ minInterval: 1 }),
      series: presentes.map((e, i) => ({
        name: e.nombre, type: 'bar', stack: 'estados', barMaxWidth: 40,
        itemStyle: estiloSegmento(e.color, i === presentes.length - 1, false),
        data: datos.map((d) => d[e.valor] || null)
      }))
    });
  });

  protected readonly tablaMeses = computed<TablaGrafica>(() => ({
    columnas: ['Mes', ...ESTADOS.map((e) => e.nombre), 'Total'],
    filas: this.porMes().map((d) => [
      d.etiqueta, ...ESTADOS.map((e) => d[e.valor]), ESTADOS.reduce((s, e) => s + d[e.valor], 0)
    ])
  }));

  // Gráfica 2: qué se recibe (artículos recibidos por categoría)

  protected readonly porCategoriaRecibida = computed(() => {
    const conteo = new Map<string, { articulos: number; detalle: string[] }>();
    for (const d of this.donacionesPeriodo().filter((d) => d.estado === 'RECIBIDA')) {
      for (const a of d.articulos.filter((a) => (a.cantidadRecibida ?? 0) > 0)) {
        const c = conteo.get(a.categoria) ?? conteo.set(a.categoria, { articulos: 0, detalle: [] }).get(a.categoria)!;
        c.articulos++;
        c.detalle.push(`${a.descripcion} (${formatearCantidad(a.cantidadRecibida!, a.unidad)})`);
      }
    }
    return [...conteo.entries()]
      .map(([categoria, c]) => ({ categoria, nombre: textoCategoria(categoria), ...c }))
      .sort((a, b) => b.articulos - a.articulos || a.nombre.localeCompare(b.nombre));
  });

  protected readonly graficaRecibido = computed<OpcionesGrafica>(() => {
    const datos = [...this.porCategoriaRecibida()].reverse();
    return base({
      grid: { left: 8, right: 40, top: 16, bottom: 8, containLabel: true },
      tooltip: tooltip({
        trigger: 'item',
        formatter: (p: { dataIndex: number }) => {
          const d = datos[p.dataIndex];
          return `<strong>${d.nombre}</strong><br>${d.articulos} ${d.articulos === 1 ? 'artículo' : 'artículos'} recibidos`;
        }
      }),
      xAxis: ejeValores({ minInterval: 1 }),
      yAxis: ejeCategorias(datos.map((d) => d.nombre)),
      series: [{
        name: 'Artículos recibidos', type: 'bar', barMaxWidth: ANCHO_BARRA,
        itemStyle: estiloBarra(SERIE[0], true),
        label: etiquetaValor('right'),
        data: datos.map((d) => d.articulos)
      }]
    });
  });

  protected readonly tablaRecibido = computed<TablaGrafica>(() => ({
    columnas: ['Categoría', 'Artículos recibidos', 'Qué llegó'],
    filas: this.porCategoriaRecibida().map((d) => [d.nombre, d.articulos, d.detalle.join(', ')])
  }));

  // Gráfica 3: quién dona

  protected readonly porDonante = computed(() => {
    const conteo = new Map<string, { nombre: string; rol: string; donaciones: number; recibidas: number }>();
    for (const d of this.donacionesPeriodo().filter((d) => d.estado !== 'CANCELADA')) {
      const nombre = d.donante ?? 'Cuenta eliminada';
      const clave = `${nombre}|${d.rolDonante}`;
      const c = conteo.get(clave) ?? conteo.set(clave, { nombre, rol: textoRol(d.rolDonante), donaciones: 0, recibidas: 0 }).get(clave)!;
      c.donaciones++;
      if (d.estado === 'RECIBIDA') c.recibidas++;
    }
    return [...conteo.values()]
      .sort((a, b) => b.recibidas - a.recibidas || b.donaciones - a.donaciones || a.nombre.localeCompare(b.nombre));
  });

  /** Los 8 que más aportan (el resto queda en la tabla). */
  protected readonly graficaDonantes = computed<OpcionesGrafica>(() => {
    const datos = this.porDonante().slice(0, 8).reverse();
    const series = [
      { nombre: 'Recibidas', color: ESTADO.bueno, valor: (d: (typeof datos)[number]) => d.recibidas },
      { nombre: 'Sin recibir', color: GRIS, valor: (d: (typeof datos)[number]) => d.donaciones - d.recibidas }
    ];
    return base({
      legend: leyenda(),
      grid: { left: 8, right: 24, top: 36, bottom: 8, containLabel: true },
      tooltip: tooltip({ trigger: 'axis', axisPointer: { type: 'shadow' } }),
      xAxis: ejeValores({ minInterval: 1 }),
      yAxis: ejeCategorias(datos.map((d) => `${d.nombre} (${d.rol.toLowerCase()})`)),
      series: series.map((s, i) => ({
        name: s.nombre, type: 'bar', stack: 'donante', barMaxWidth: ANCHO_BARRA,
        itemStyle: estiloSegmento(s.color, i === series.length - 1),
        data: datos.map((d) => s.valor(d) || null)
      }))
    });
  });

  protected readonly tablaDonantes = computed<TablaGrafica>(() => ({
    columnas: ['Donante', 'Rol', 'Donaciones', 'Recibidas'],
    filas: this.porDonante().map((d) => [d.nombre, d.rol, d.donaciones, d.recibidas])
  }));

  // Gráfica 4: de dónde llega y a dónde va (motivos de los movimientos)

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

  // Gráfica 5: existencias por categoría (estado de hoy)

  private readonly porCategoria = computed(() =>
    CATEGORIAS
      .map((c) => {
        const articulos = this.inventario().articulos.filter((a) => a.categoria === c.codigo);
        const de = (e: Existencias) => articulos.filter((a) => existencias(a) === e).length;
        return { nombre: c.texto, normal: de('normal'), 'por-agotarse': de('por-agotarse'), agotado: de('agotado'),
          total: articulos.length };
      })
      .filter((c) => c.total > 0)
  );

  protected readonly graficaExistencias = computed<OpcionesGrafica>(() => {
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

  protected readonly tablaExistencias = computed<TablaGrafica>(() => ({
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
    if (c.donaciones === 0) {
      return 'No hubo donaciones con fecha de entrega en el período, por eso la gráfica no muestra datos.';
    }
    const mayor = this.porMes().reduce((a, b) =>
      ESTADOS.reduce((s, e) => s + b[e.valor], 0) > ESTADOS.reduce((s, e) => s + a[e.valor], 0) ? b : a);
    return `Cada barra muestra las donaciones con fecha de entrega en cada mes, por estado (${c.donaciones} en total). `
      + `El mes con más donaciones fue ${mayor.etiqueta}. De las que ya debían llegar, llegaron ${c.recibidas} `
      + `de ${c.conRespuesta} (${c.cumplimiento} %). `
      + (c.vencidas > 0
        ? `Hay ${c.vencidas} ${c.vencidas === 1 ? 'donación programada' : 'donaciones programadas'} con la fecha de `
          + 'entrega vencida: conviene marcarlas como recibidas o no entregadas en Donaciones.'
        : 'No hay donaciones programadas con la fecha de entrega vencida.');
  }

  private explicacionRecibido(): string {
    const datos = this.porCategoriaRecibida();
    if (datos.length === 0) {
      return 'No se recibieron artículos en el período.';
    }
    const total = datos.reduce((s, d) => s + d.articulos, 0);
    return `La gráfica cuenta los artículos recibidos de cada categoría (${total} en total). Lo que más llegó fue `
      + `${datos[0].nombre.toLowerCase()} (${datos[0].articulos}). Comparado con lo que se está agotando, ayuda a `
      + 'decidir qué pedir en las próximas campañas de donación.';
  }

  private explicacionDonantes(): string {
    const datos = this.porDonante();
    if (datos.length === 0) {
      return 'Nadie programó donaciones en el período.';
    }
    const acompanantes = datos.filter((d) => d.rol === 'Acompañante').length;
    return `${datos.length} ${datos.length === 1 ? 'persona programó' : 'personas programaron'} donaciones en el `
      + `período (${acompanantes} ${acompanantes === 1 ? 'acompañante' : 'acompañantes'} y `
      + `${datos.length - acompanantes} ${datos.length - acompanantes === 1 ? 'voluntario' : 'voluntarios'}). `
      + `Quien más aportó fue ${datos[0].nombre} (${datos[0].recibidas} ${datos[0].recibidas === 1 ? 'recibida' : 'recibidas'}).`;
  }

  private explicacionMotivos(): string {
    const datos = this.porMotivo();
    if (datos.length === 0) {
      return 'No hubo entradas ni salidas del inventario en el período.';
    }
    const donaciones = datos.find((d) => d.motivo === 'DONACION')?.ENTRADA ?? 0;
    const entradas = datos.reduce((s, d) => s + d.ENTRADA, 0);
    const salidas = datos.reduce((s, d) => s + d.SALIDA, 0);
    return `En el período hubo ${entradas} ${entradas === 1 ? 'entrada' : 'entradas'} y ${salidas} `
      + `${salidas === 1 ? 'salida' : 'salidas'} del inventario. El motivo más frecuente fue "${datos[0].nombre}" `
      + `(${datos[0].total} movimientos)`
      + (entradas > 0 ? ` y ${donaciones} de las ${entradas} entradas vinieron de donaciones recibidas en VITA+.` : '.')
      + ' Se cuentan los movimientos y no las cantidades, porque cada artículo tiene su propia unidad.';
  }

  private explicacionExistencias(): string {
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
        { titulo: 'Donaciones por mes', descripcion: 'Donaciones con fecha de entrega en cada mes, por estado.',
          opciones: this.donacionesPeriodo().length > 0 ? this.graficaMeses() : null, tabla: this.tablaMeses(),
          explicacion: this.explicacionMeses() },
        { titulo: 'Qué se recibe', descripcion: 'Artículos recibidos de cada categoría.',
          opciones: this.porCategoriaRecibida().length > 0 ? this.graficaRecibido() : null, tabla: this.tablaRecibido(),
          explicacion: this.explicacionRecibido() },
        { titulo: 'Quién dona', descripcion: 'Donaciones programadas y recibidas de cada donante.',
          opciones: this.porDonante().length > 0 ? this.graficaDonantes() : null, tabla: this.tablaDonantes(),
          explicacion: this.explicacionDonantes() },
        { titulo: 'De dónde llega y a dónde va', descripcion: 'Entradas y salidas del inventario según su motivo.',
          opciones: this.porMotivo().length > 0 ? this.graficaMotivos() : null, tabla: this.tablaMotivos(),
          explicacion: this.explicacionMotivos() },
        { titulo: 'Existencias por categoría', descripcion: 'Estado de hoy de los artículos de cada categoría.',
          opciones: this.porCategoria().length > 0 ? this.graficaExistencias() : null, tabla: this.tablaExistencias(),
          explicacion: this.explicacionExistencias() },
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
      nota: 'Las donaciones se cuentan por su fecha de entrega, así que las programadas para después de hoy también '
        + 'aparecen (por recibir); el porcentaje de las que llegaron compara las recibidas con las que ya tenían '
        + 'respuesta (recibidas o no entregadas) y las canceladas no cuentan. El inventario, sus existencias y la '
        + 'lista de atención muestran el estado de hoy; el período solo filtra las donaciones y los movimientos. '
        + `"Por agotarse" es llegar a la cantidad mínima de aviso de cada artículo, y "vence pronto", vencer en los `
        + `próximos ${DIAS_AVISO_VENCIMIENTO} días.`
    };
  }
}
