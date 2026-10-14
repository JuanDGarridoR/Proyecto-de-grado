import { Component, computed, input } from '@angular/core';

import { RetiroAnalitica, RetirosAnalitica } from '../../../../../core/analitica/analitica.service';
import { textoRazonRetiro } from '../../../../../core/auth/razones-retiro';
import {
  ANCHO_BARRA, GRIS, SERIE, base, ejeCategorias, ejeValores, estiloBarra, etiquetaValor, leyenda, tooltip
} from '../../../../../shared/echart/tema';
import { OpcionesGrafica } from '../../../../../shared/echart/echart';
import { Icono } from '../../../../../shared/icono/icono';
import { TarjetaGrafica, TablaGrafica } from '../componentes/tarjeta-grafica';
import { DatoKpi, Kpi } from '../componentes/kpi';
import { ContenidoReporte, ReporteExportable } from '../contenido-reporte';

/**
 * Razones agrupadas en dos tipos: las de la vida de la persona (salud,
 * mudanza, fallecimiento...) y las que tienen que ver con la plataforma
 * (difícil de usar, privacidad, ya no la necesita), que son las que la
 * organización y el equipo de VITA+ pueden mejorar.
 */
type TipoRazon = 'personal' | 'plataforma' | 'otra';

const TIPOS: { valor: TipoRazon; nombre: string; color: string }[] = [
  { valor: 'personal', nombre: 'Situación de la persona', color: SERIE[0] },
  { valor: 'plataforma', nombre: 'Relacionadas con la plataforma', color: SERIE[1] },
  { valor: 'otra', nombre: 'Otra razón', color: GRIS }
];

/** Nombre corto de cada razón para la gráfica (el largo está en razones-retiro.ts). */
const RAZONES: Record<string, { corto: string; tipo: TipoRazon }> = {
  FALLECIMIENTO: { corto: 'Fallecimiento', tipo: 'personal' },
  SALUD: { corto: 'Problemas de salud', tipo: 'personal' },
  HOGAR_CUIDADO: { corto: 'Ingreso a hogar de cuidado', tipo: 'personal' },
  CAMBIO_RESIDENCIA: { corto: 'Cambio de residencia', tipo: 'personal' },
  DIFICULTAD_USO: { corto: 'Difícil de usar', tipo: 'plataforma' },
  NO_LA_NECESITA: { corto: 'Ya no la necesita', tipo: 'plataforma' },
  PRIVACIDAD: { corto: 'Privacidad de los datos', tipo: 'plataforma' },
  OTRA: { corto: 'Otra razón', tipo: 'otra' }
};

const razon = (codigo: string) => RAZONES[codigo] ?? { corto: textoRazonRetiro(codigo), tipo: 'otra' as TipoRazon };

/** Porcentaje entero; 0 si el total es 0. */
const pct = (parte: number, total: number) => (total > 0 ? Math.round((parte / total) * 100) : 0);

/**
 * Reporte 4: personas mayores que eliminaron su cuenta en el período y por
 * qué. Sirve para entender por qué se van y qué se puede mejorar.
 */
@Component({
  selector: 'app-reporte-retiros',
  imports: [Icono, Kpi, TarjetaGrafica],
  templateUrl: './reporte-retiros.html',
  styleUrls: ['../reporte.css', './reporte-salud.css', './reporte-retiros.css']
})
export class ReporteRetiros implements ReporteExportable {

  readonly datos = input.required<RetirosAnalitica>();
  /** Inicio del período (YYYY-MM-DD) o null = todo. */
  readonly desde = input<string | null>(null);

  /** Retiros del período, del más reciente al más antiguo. */
  protected readonly retiros = computed<RetiroAnalitica[]>(() => {
    const desde = this.desde();
    return desde ? this.datos().retiros.filter((r) => r.fecha >= desde) : this.datos().retiros;
  });

  // Indicadores

  private readonly conteo = computed(() => {
    const retiros = this.retiros();
    const total = retiros.length;
    const plataforma = retiros.filter((r) => razon(r.razon).tipo === 'plataforma').length;
    const fallecimientos = retiros.filter((r) => r.razon === 'FALLECIMIENTO').length;
    // De las personas que tuvo en el período (las de hoy más las que se fueron), cuántas se fueron.
    const base = this.datos().personasActuales + total;
    return { total, plataforma, fallecimientos, tasa: pct(total, base), base };
  });

  protected readonly tarjetas = computed<DatoKpi[]>(() => {
    const c = this.conteo();
    const principal = this.porRazon()[0];
    return [
      { etiqueta: 'Retiros', icono: 'users', tono: 'neutro', valor: String(c.total),
        detalle: c.total === 1 ? 'persona eliminó su cuenta' : 'personas eliminaron su cuenta' },
      { etiqueta: 'Tasa de retiro', icono: 'bar-chart', tono: c.tasa >= 10 ? 'alerta' : 'neutro',
        valor: `${c.tasa} %`, detalle: `${c.total} de ${c.base} personas que tuvo la organización` },
      { etiqueta: 'Por la plataforma', icono: 'alert-circle', tono: c.plataforma > 0 ? 'alerta' : 'bueno',
        valor: String(c.plataforma),
        detalle: c.total > 0 ? `${pct(c.plataforma, c.total)} % de los retiros` : 'Sin retiros en el período' },
      { etiqueta: 'Razón más común', icono: 'info', tono: 'neutro',
        valor: principal ? principal.corto : '—',
        detalle: principal ? `${principal.personas} de ${c.total} retiros` : 'Sin retiros en el período' }
    ];
  });

  // Gráfica 1: razones

  protected readonly porRazon = computed(() => {
    const conteo = new Map<string, number>();
    for (const r of this.retiros()) {
      conteo.set(r.razon, (conteo.get(r.razon) ?? 0) + 1);
    }
    return [...conteo.entries()]
      .map(([codigo, personas]) => ({ codigo, ...razon(codigo), personas }))
      .sort((a, b) => b.personas - a.personas || a.corto.localeCompare(b.corto));
  });

  protected readonly graficaRazones = computed<OpcionesGrafica>(() => {
    const datos = [...this.porRazon()].reverse();
    const presentes = TIPOS.filter((t) => datos.some((d) => d.tipo === t.valor));
    return base({
      legend: leyenda(),
      grid: { left: 8, right: 40, top: 36, bottom: 8, containLabel: true },
      tooltip: tooltip({
        trigger: 'item',
        formatter: (p: { dataIndex: number }) => {
          const d = datos[p.dataIndex];
          return `<strong>${textoRazonRetiro(d.codigo)}</strong><br>${d.personas} `
            + `${d.personas === 1 ? 'persona' : 'personas'}`;
        }
      }),
      xAxis: ejeValores(),
      yAxis: ejeCategorias(datos.map((d) => d.corto)),
      // Una serie por tipo, apiladas: cada barra solo tiene valor en su tipo,
      // así la leyenda explica los colores.
      series: presentes.map((t) => ({
        name: t.nombre, type: 'bar', stack: 'razones', barMaxWidth: ANCHO_BARRA,
        itemStyle: estiloBarra(t.color, true),
        label: { ...etiquetaValor('right'), formatter: (p: { value: number | null }) => (p.value ? String(p.value) : '') },
        data: datos.map((d) => (d.tipo === t.valor ? d.personas : null))
      }))
    });
  });

  protected readonly tablaRazones = computed<TablaGrafica>(() => ({
    columnas: ['Razón', 'Tipo', 'Personas'],
    filas: this.porRazon().map((d) => [
      textoRazonRetiro(d.codigo), TIPOS.find((t) => t.valor === d.tipo)!.nombre, d.personas
    ])
  }));

  // Gráfica 2: retiros por mes

  private readonly porMes = computed(() => {
    const retiros = this.retiros();
    const desde = this.desde();
    const conteo = new Map<string, number>();

    // Todos los meses del período, también los que no tuvieron retiros.
    const inicio = desde
      ? new Date(`${desde}T00:00`)
      : retiros.length > 0 ? new Date(retiros[retiros.length - 1].fecha) : null;
    if (inicio) {
      const cursor = new Date(inicio.getFullYear(), inicio.getMonth(), 1);
      while (cursor <= new Date()) {
        conteo.set(`${cursor.getFullYear()}-${String(cursor.getMonth() + 1).padStart(2, '0')}`, 0);
        cursor.setMonth(cursor.getMonth() + 1);
      }
    }

    for (const r of retiros) {
      const clave = r.fecha.slice(0, 7);
      conteo.set(clave, (conteo.get(clave) ?? 0) + 1);
    }

    return [...conteo.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([clave, total]) => {
        const [anio, mes] = clave.split('-').map(Number);
        return {
          etiqueta: new Date(anio, mes - 1, 1).toLocaleDateString('es-CO', { month: 'short', year: 'numeric' }),
          total
        };
      });
  });

  protected readonly graficaMeses = computed<OpcionesGrafica>(() => {
    const datos = this.porMes();
    return base({
      grid: { left: 8, right: 16, top: 28, bottom: 8, containLabel: true },
      tooltip: tooltip({ trigger: 'axis', axisPointer: { type: 'shadow' } }),
      xAxis: ejeCategorias(datos.map((d) => d.etiqueta)),
      yAxis: ejeValores({ minInterval: 1 }),
      series: [{
        name: 'Retiros', type: 'bar', barMaxWidth: 40,
        label: { ...etiquetaValor('top'), formatter: (p: { value: number }) => (p.value ? String(p.value) : '') },
        data: datos.map((d) => ({ value: d.total, itemStyle: estiloBarra(SERIE[0]) }))
      }]
    });
  });

  protected readonly tablaMeses = computed<TablaGrafica>(() => ({
    columnas: ['Mes', 'Retiros'],
    filas: this.porMes().map((d) => [d.etiqueta, d.total])
  }));

  // Tabla de retiros

  protected fecha(fecha: string): string {
    const [anio, mes, dia] = fecha.slice(0, 10).split('-').map(Number);
    return new Date(anio, mes - 1, dia).toLocaleDateString('es-CO', { day: 'numeric', month: 'short', year: 'numeric' });
  }

  protected readonly textoRazon = textoRazonRetiro;

  protected esDeLaPlataforma(codigo: string): boolean {
    return razon(codigo).tipo === 'plataforma';
  }

  // ---------- Explicaciones (PDF) ----------

  private explicacionRazones(): string {
    const c = this.conteo();
    if (c.total === 0) {
      return 'Ninguna persona mayor de la organización eliminó su cuenta en el período, por eso la gráfica no '
        + 'muestra datos.';
    }

    const principal = this.porRazon()[0];
    const personales = c.total - c.plataforma - this.retiros().filter((r) => razon(r.razon).tipo === 'otra').length;
    return `La gráfica muestra por qué las ${c.total} personas mayores que se retiraron en el período `
      + `eliminaron su cuenta. La razón más común fue "${textoRazonRetiro(principal.codigo)}" `
      + `(${principal.personas} ${principal.personas === 1 ? 'persona' : 'personas'}). `
      + `${personales} ${personales === 1 ? 'retiro se debió' : 'retiros se debieron'} a la situación de la `
      + `persona (salud, mudanza, ingreso a un hogar de cuidado o fallecimiento) y ${c.plataforma} a razones `
      + 'relacionadas con la plataforma. '
      + (c.plataforma > 0
        ? 'Estas últimas son las que se pueden mejorar: acompañar a las personas en el uso de la aplicación y '
          + 'explicarles cómo se protegen sus datos puede evitar que se retiren.'
        : 'Ningún retiro se debió a la plataforma, lo que indica que las personas se van por circunstancias de su vida.');
  }

  private explicacionMeses(): string {
    const datos = this.porMes();
    const total = datos.reduce((s, d) => s + d.total, 0);
    if (total === 0) {
      return 'No hubo retiros en el período, por eso todas las barras están en cero.';
    }

    const mayor = datos.reduce((a, b) => (b.total > a.total ? b : a));
    return `Cada barra indica cuántas personas mayores eliminaron su cuenta en cada mes del período (${total} `
      + `en total). El mes con más retiros fue ${mayor.etiqueta} (${mayor.total}). Un aumento de retiros en un `
      + 'mes puede señalar un problema puntual que vale la pena revisar.';
  }

  // ---------- PDF ----------

  contenidoPdf(): ContenidoReporte {
    const hayRetiros = this.retiros().length > 0;
    return {
      indicadores: this.tarjetas(),
      secciones: [
        { titulo: 'Razones de retiro',
          descripcion: 'Por qué las personas mayores eliminaron su cuenta en el período.',
          opciones: hayRetiros ? this.graficaRazones() : null, tabla: this.tablaRazones(),
          explicacion: this.explicacionRazones() },
        { titulo: 'Retiros por mes',
          descripcion: 'Cuántas personas mayores eliminaron su cuenta cada mes.',
          opciones: hayRetiros ? this.graficaMeses() : null, tabla: this.tablaMeses(),
          explicacion: this.explicacionMeses() },
        { titulo: 'Personas que se retiraron',
          descripcion: 'Quién se retiró, cuándo, por qué y lo que comentó.',
          opciones: null,
          tabla: {
            columnas: ['Fecha', 'Persona', 'Razón', 'Comentario'],
            filas: this.retiros().map((r) => [
              this.fecha(r.fecha), r.nombre ?? '—', textoRazonRetiro(r.razon), r.comentario ?? '—'
            ])
          } }
      ],
      nota: 'La tasa de retiro compara las personas que eliminaron su cuenta en el período con las que tuvo la '
        + 'organización (las que tiene hoy más las que se retiraron). Las razones las elige cada persona al '
        + 'eliminar su cuenta.'
    };
  }
}
