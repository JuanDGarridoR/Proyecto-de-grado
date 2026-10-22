import { Component, computed, input } from '@angular/core';

import { DonacionAnalitica, DonacionesAnalitica } from '../../../../../core/analitica/analitica.service';
import { formatearCantidad, formatearFechaCorta, textoCategoria } from '../../../../../core/donaciones/catalogo';
import {
  ANCHO_BARRA, ESTADO, GRIS, SERIE, base, ejeCategorias, ejeValores, estiloBarra, estiloSegmento, etiquetaValor,
  leyenda, tooltip
} from '../../../../../shared/echart/tema';
import { OpcionesGrafica } from '../../../../../shared/echart/echart';
import { Icono } from '../../../../../shared/icono/icono';
import { TarjetaGrafica, TablaGrafica } from '../componentes/tarjeta-grafica';
import { DatoKpi, Kpi } from '../componentes/kpi';
import { ContenidoReporte, ReporteExportable } from '../contenido-reporte';

type Estado = DonacionAnalitica['estado'];

/**
 * Estados de una donación, con su color de estado (siempre con su nombre en
 * la leyenda, nunca solo el color). El orden es el de las barras apiladas.
 */
const ESTADOS: { valor: Estado; nombre: string; color: string }[] = [
  { valor: 'RECIBIDA', nombre: 'Recibidas', color: ESTADO.bueno },
  { valor: 'PROGRAMADA', nombre: 'Por recibir', color: ESTADO.aviso },
  { valor: 'NO_ENTREGADA', nombre: 'No llegaron', color: ESTADO.critico },
  { valor: 'CANCELADA', nombre: 'Canceladas', color: GRIS }
];

const nombreEstado = (estado: Estado) => ESTADOS.find((e) => e.valor === estado)!.nombre;
const textoRol = (rol: string) => (rol === 'VOLUNTARIO' ? 'Voluntario' : 'Acompañante');

/** Porcentaje entero; 0 si el total es 0. */
const pct = (parte: number, total: number) => (total > 0 ? Math.round((parte / total) * 100) : 0);

/** Hoy en formato YYYY-MM-DD (hora local). */
const hoy = () => new Date().toLocaleDateString('en-CA');

/** "oct 2026" a partir de "2026-10". */
function etiquetaMes(clave: string): string {
  const [anio, mes] = clave.split('-').map(Number);
  return new Date(anio, mes - 1, 1).toLocaleDateString('es-CO', { month: 'short', year: 'numeric' });
}

/**
 * Reporte de donaciones: cuántas donaciones le programan a la organización
 * en el período (por fecha de entrega), cuántas llegan, qué se recibe y
 * quién dona. Las programadas para más adelante también cuentan: son las
 * que están por recibir.
 */
@Component({
  selector: 'app-reporte-donaciones',
  imports: [Icono, Kpi, TarjetaGrafica],
  templateUrl: './reporte-donaciones.html',
  styleUrls: ['../reporte.css', './reporte-salud.css', './reporte-recursos.css']
})
export class ReporteDonaciones implements ReporteExportable {

  readonly datos = input.required<DonacionesAnalitica>();
  /** Inicio del período (YYYY-MM-DD) o null = todo. */
  readonly desde = input<string | null>(null);

  /** Donaciones con fecha de entrega en el período (incluidas las programadas para después). */
  protected readonly donaciones = computed<DonacionAnalitica[]>(() => {
    const desde = this.desde();
    return desde ? this.datos().donaciones.filter((d) => d.fechaEntrega >= desde) : this.datos().donaciones;
  });

  private readonly conteo = computed(() => {
    const donaciones = this.donaciones();
    const de = (estado: Estado) => donaciones.filter((d) => d.estado === estado).length;
    const recibidas = de('RECIBIDA');
    const noLlegaron = de('NO_ENTREGADA');
    const porRecibir = donaciones.filter((d) => d.estado === 'PROGRAMADA');
    const dinero = donaciones
      .filter((d) => d.estado === 'RECIBIDA')
      .flatMap((d) => d.articulos)
      .filter((a) => a.categoria === 'DINERO')
      .reduce((suma, a) => suma + (a.cantidadRecibida ?? 0), 0);
    return {
      total: donaciones.length,
      recibidas,
      noLlegaron,
      // De las que ya tenían que llegar (recibidas o no), cuántas llegaron
      cumplimiento: pct(recibidas, recibidas + noLlegaron),
      conRespuesta: recibidas + noLlegaron,
      porRecibir: porRecibir.length,
      vencidas: porRecibir.filter((d) => d.fechaEntrega < hoy()).length,
      dinero
    };
  });

  // Indicadores

  protected readonly tarjetas = computed<DatoKpi[]>(() => {
    const c = this.conteo();
    return [
      { etiqueta: 'Donaciones', icono: 'gift', tono: 'neutro', valor: String(c.total),
        detalle: `${c.recibidas} ${c.recibidas === 1 ? 'recibida' : 'recibidas'}` },
      { etiqueta: 'Cumplimiento', icono: 'check-circle',
        tono: c.conRespuesta === 0 ? 'neutro' : c.cumplimiento >= 80 ? 'bueno' : 'alerta',
        valor: c.conRespuesta > 0 ? `${c.cumplimiento} %` : '—',
        detalle: c.conRespuesta > 0
          ? `${c.recibidas} de ${c.conRespuesta} llegaron`
          : 'Aún no hay donaciones recibidas o no entregadas' },
      { etiqueta: 'Dinero recibido', icono: 'tag', tono: 'neutro', valor: formatearCantidad(c.dinero, 'COP'),
        detalle: 'En donaciones recibidas' },
      { etiqueta: 'Por recibir', icono: 'hourglass', tono: c.vencidas > 0 ? 'alerta' : 'neutro',
        valor: String(c.porRecibir),
        detalle: c.vencidas > 0
          ? `${c.vencidas} con la fecha de entrega vencida`
          : c.porRecibir > 0 ? 'Ninguna con la fecha vencida' : 'No hay donaciones pendientes' }
    ];
  });

  // Gráfica 1: donaciones por mes y estado

  private readonly porMes = computed(() => {
    const donaciones = this.donaciones();
    const meses = new Map<string, Record<Estado, number>>();
    const vacio = (): Record<Estado, number> => ({ RECIBIDA: 0, PROGRAMADA: 0, NO_ENTREGADA: 0, CANCELADA: 0 });

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

  protected readonly porCategoria = computed(() => {
    const conteo = new Map<string, { articulos: number; detalle: string[] }>();
    for (const d of this.donaciones().filter((d) => d.estado === 'RECIBIDA')) {
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

  protected readonly graficaCategorias = computed<OpcionesGrafica>(() => {
    const datos = [...this.porCategoria()].reverse();
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

  protected readonly tablaCategorias = computed<TablaGrafica>(() => ({
    columnas: ['Categoría', 'Artículos recibidos', 'Qué llegó'],
    filas: this.porCategoria().map((d) => [d.nombre, d.articulos, d.detalle.join(', ')])
  }));

  // Gráfica 3: quién dona

  protected readonly porDonante = computed(() => {
    const conteo = new Map<string, { nombre: string; rol: string; donaciones: number; recibidas: number }>();
    for (const d of this.donaciones().filter((d) => d.estado !== 'CANCELADA')) {
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

  // Tabla de donaciones

  protected readonly fecha = formatearFechaCorta;
  protected readonly nombreEstado = nombreEstado;
  protected readonly textoRol = textoRol;

  protected resumenArticulos(d: DonacionAnalitica): string {
    return d.articulos
      .map((a) => `${a.descripcion} (${formatearCantidad(a.cantidadRecibida ?? a.cantidad, a.unidad)})`)
      .join(', ');
  }

  protected vencida(d: DonacionAnalitica): boolean {
    return d.estado === 'PROGRAMADA' && d.fechaEntrega < hoy();
  }

  // ---------- Explicaciones (PDF) ----------

  private explicacionMeses(): string {
    const c = this.conteo();
    if (c.total === 0) {
      return 'No hubo donaciones con fecha de entrega en el período, por eso la gráfica no muestra datos.';
    }
    const mayor = this.porMes().reduce((a, b) =>
      ESTADOS.reduce((s, e) => s + b[e.valor], 0) > ESTADOS.reduce((s, e) => s + a[e.valor], 0) ? b : a);
    return `Cada barra muestra las donaciones con fecha de entrega en cada mes, por estado (${c.total} en total). `
      + `El mes con más donaciones fue ${mayor.etiqueta}. De las que ya debían llegar, llegaron ${c.recibidas} `
      + `de ${c.conRespuesta} (${c.cumplimiento} %). `
      + (c.vencidas > 0
        ? `Hay ${c.vencidas} ${c.vencidas === 1 ? 'donación programada' : 'donaciones programadas'} con la fecha de `
          + 'entrega vencida: conviene marcarlas como recibidas o no entregadas en Donaciones.'
        : 'No hay donaciones programadas con la fecha de entrega vencida.');
  }

  private explicacionCategorias(): string {
    const datos = this.porCategoria();
    if (datos.length === 0) {
      return 'No se recibieron artículos en el período.';
    }
    const total = datos.reduce((s, d) => s + d.articulos, 0);
    return `La gráfica cuenta los artículos recibidos de cada categoría (${total} en total). Lo que más llegó fue `
      + `${datos[0].nombre.toLowerCase()} (${datos[0].articulos}). Las categorías con pocos artículos pueden `
      + 'orientar qué pedir en las próximas campañas de donación.';
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

  // ---------- PDF ----------

  contenidoPdf(): ContenidoReporte {
    const hay = this.donaciones().length > 0;
    return {
      indicadores: this.tarjetas(),
      secciones: [
        { titulo: 'Donaciones por mes', descripcion: 'Donaciones con fecha de entrega en cada mes, por estado.',
          opciones: hay ? this.graficaMeses() : null, tabla: this.tablaMeses(), explicacion: this.explicacionMeses() },
        { titulo: 'Qué se recibe', descripcion: 'Artículos recibidos de cada categoría.',
          opciones: this.porCategoria().length > 0 ? this.graficaCategorias() : null, tabla: this.tablaCategorias(),
          explicacion: this.explicacionCategorias() },
        { titulo: 'Quién dona', descripcion: 'Donaciones programadas y recibidas de cada donante.',
          opciones: this.porDonante().length > 0 ? this.graficaDonantes() : null, tabla: this.tablaDonantes(),
          explicacion: this.explicacionDonantes() },
        { titulo: 'Donaciones del período', descripcion: 'Cada donación con su fecha, donante, estado y artículos.',
          opciones: null,
          tabla: {
            columnas: ['Entrega', 'Donante', 'Estado', 'Artículos'],
            filas: this.donaciones().map((d) => [
              this.fecha(d.fechaEntrega), `${d.donante ?? 'Cuenta eliminada'} (${textoRol(d.rolDonante).toLowerCase()})`,
              nombreEstado(d.estado), this.resumenArticulos(d)
            ])
          } }
      ],
      nota: 'Las donaciones se cuentan por su fecha de entrega, así que las programadas para después de hoy también '
        + 'aparecen (por recibir). El cumplimiento compara las recibidas con las que ya tenían respuesta (recibidas '
        + 'o no entregadas). Las canceladas no cuentan como no entregadas.'
    };
  }
}
