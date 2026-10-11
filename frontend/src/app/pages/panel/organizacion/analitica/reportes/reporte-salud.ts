import { Component, computed, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { MedicionAnalitica, SaludAnalitica } from '../../../../../core/analitica/analitica.service';
import {
  CampoSigno, Indicador, LIMITES, NOMBRE_INDICADOR, RANGOS, camposImposibles, evaluarIndicador
} from '../../../../../core/signos-vitales/rangos';
import {
  ANCHO_BARRA, ESTADO, GRIS_SUAVE, SERIE, base, ejeCategorias, ejeValores,
  estiloLinea, estiloSegmento, leyenda, tooltip
} from '../../../../../shared/echart/tema';
import { ClicGrafica, OpcionesGrafica } from '../../../../../shared/echart/echart';
import { Icono } from '../../../../../shared/icono/icono';
import { TarjetaGrafica, TablaGrafica } from '../componentes/tarjeta-grafica';
import { DatoKpi, Kpi } from '../componentes/kpi';
import { ContenidoReporte, ReporteExportable } from '../contenido-reporte';

const DIA = 24 * 60 * 60 * 1000;

/** Sin mediciones en más de estos días, la persona aparece como "requiere atención". */
const DIAS_SIN_MEDICION = 30;

/** Indicadores que muestra la analítica (la temperatura no). */
type IndicadorAnalitica = Exclude<Indicador, 'temperatura'>;
const INDICADORES: IndicadorAnalitica[] = ['presion', 'pulso', 'oxigeno'];

/** Campos de la medición que forman cada indicador. */
const CAMPOS: Record<IndicadorAnalitica, CampoSigno[]> = {
  presion: ['presionSistolica', 'presionDiastolica'],
  pulso: ['frecuenciaCardiaca'],
  oxigeno: ['saturacionOxigeno']
};

/**
 * Color para "posible error de registro": es un problema del dato, no de
 * salud, así que va en gris oscuro y no en los colores de estado.
 */
const COLOR_ERROR = '#6b7785';

/** Persona de la tabla "Personas que requieren atención". */
interface PersonaAtencion {
  idUsuario: number;
  nombre: string;
  motivos: string[];
  indicadores: IndicadorAnalitica[];  // fuera de rango en la última medición
  errores: number;                    // mediciones con valores imposibles en el período
  ultima: string | null;
}

/** Indicadores de la medición con algún valor imposible. */
function indicadoresConError(m: MedicionAnalitica): IndicadorAnalitica[] {
  const imposibles = camposImposibles(m);
  return INDICADORES.filter((i) => CAMPOS[i].some((c) => imposibles.includes(c)));
}

/**
 * Reporte 2: salud de las personas mayores asociadas a partir de sus
 * signos vitales (estado actual, seguimiento y evolución).
 */
@Component({
  selector: 'app-reporte-salud',
  imports: [FormsModule, Icono, Kpi, TarjetaGrafica],
  templateUrl: './reporte-salud.html',
  styleUrls: ['../reporte.css', './reporte-salud.css']
})
export class ReporteSalud implements ReporteExportable {

  readonly datos = input.required<SaludAnalitica>();
  /** Inicio del período (YYYY-MM-DD) o null = todo. */
  readonly desde = input<string | null>(null);

  protected readonly indicadores = INDICADORES;
  protected readonly nombreIndicador = NOMBRE_INDICADOR;

  // Filtros interactivos
  protected readonly filtroIndicador = signal<IndicadorAnalitica | null>(null);
  protected readonly personaElegida = signal<number | null>(null);
  protected readonly indicadorEvolucion = signal<IndicadorAnalitica>('presion');

  // Datos base

  private readonly medicionesPorPersona = computed(() => {
    const mapa = new Map<number, MedicionAnalitica[]>();
    for (const m of this.datos().mediciones) {
      const lista = mapa.get(m.idPersonaMayor) ?? [];
      lista.push(m);
      mapa.set(m.idPersonaMayor, lista);
    }
    return mapa;
  });

  private readonly ultimaPorPersona = computed(() => {
    const mapa = new Map<number, MedicionAnalitica>();
    for (const [id, lista] of this.medicionesPorPersona()) {
      mapa.set(id, lista[lista.length - 1]); // vienen ordenadas por fecha
    }
    return mapa;
  });

  private readonly medicionesPeriodo = computed(() => {
    const desde = this.desde();
    return desde
      ? this.datos().mediciones.filter((m) => m.fechaHora >= desde)
      : this.datos().mediciones;
  });

  /** Mediciones del período con algún valor imposible. */
  private readonly medicionesConError = computed(() =>
    this.medicionesPeriodo().filter((m) => camposImposibles(m).length > 0));

  // Personas que requieren atención

  /**
   * Personas con la última medición fuera de rango, sin medir hace más de
   * DIAS_SIN_MEDICION días, sin ninguna medición o con valores imposibles.
   */
  protected readonly atencion = computed<PersonaAtencion[]>(() => {
    const ahora = Date.now();
    const lista: PersonaAtencion[] = [];

    const erroresPorPersona = new Map<number, number>();
    for (const m of this.medicionesConError()) {
      erroresPorPersona.set(m.idPersonaMayor, (erroresPorPersona.get(m.idPersonaMayor) ?? 0) + 1);
    }

    for (const persona of this.datos().personas) {
      const ultima = this.ultimaPorPersona().get(persona.idUsuario);
      const errores = erroresPorPersona.get(persona.idUsuario) ?? 0;
      const motivos: string[] = [];
      let indicadores: IndicadorAnalitica[] = [];

      if (!ultima) {
        motivos.push('Nunca se le han medido signos vitales');
      } else {
        indicadores = INDICADORES.filter((i) => evaluarIndicador(ultima, i) === false);
        const conError = indicadoresConError(ultima);
        for (const i of indicadores) {
          const valor = this.valor(ultima, i);
          motivos.push(conError.includes(i)
            ? `${NOMBRE_INDICADOR[i]}: ${valor} (valor imposible)`
            : `${NOMBRE_INDICADOR[i]}: ${valor}`);
        }
        const dias = Math.floor((ahora - new Date(ultima.fechaHora).getTime()) / DIA);
        if (dias > DIAS_SIN_MEDICION) {
          motivos.push(`Sin medición hace ${dias} días`);
        }
      }

      if (errores > 0) {
        motivos.push(errores === 1
          ? '1 medición con valores imposibles (posible error de registro)'
          : `${errores} mediciones con valores imposibles (posible error de registro)`);
      }

      if (motivos.length > 0) {
        lista.push({
          idUsuario: persona.idUsuario,
          nombre: persona.nombre,
          motivos,
          indicadores,
          errores,
          ultima: ultima?.fechaHora ?? null
        });
      }
    }

    // Primero valores fuera de rango, luego errores de registro.
    return lista.sort((a, b) =>
      b.indicadores.length - a.indicadores.length || b.errores - a.errores || a.nombre.localeCompare(b.nombre));
  });

  protected readonly atencionFiltrada = computed(() => {
    const filtro = this.filtroIndicador();
    return filtro ? this.atencion().filter((p) => p.indicadores.includes(filtro)) : this.atencion();
  });

  // Indicadores

  protected readonly kpis = computed(() => {
    const total = this.datos().personas.length;
    const conSeguimiento = new Set(this.medicionesPeriodo().map((m) => m.idPersonaMayor)).size;
    const fueraDeRango = this.atencion().filter((p) => p.indicadores.length > 0).length;
    const sinReciente = this.atencion().filter((p) =>
      p.motivos.some((m) => m.startsWith('Sin medición') || m.startsWith('Nunca'))).length;

    return {
      total,
      conSeguimiento,
      mediciones: this.medicionesPeriodo().length,
      fueraDeRango,
      sinReciente,
      errores: this.medicionesConError().length,
      ejemploError: this.ejemploError()
    };
  });

  /** La medición imposible más reciente, como ejemplo real para el indicador. */
  private readonly ejemploError = computed(() => {
    const conError = this.medicionesConError();
    if (conError.length === 0) {
      return 'Ningún valor imposible en el período';
    }

    const m = conError[conError.length - 1]; // vienen ordenadas por fecha
    const campo = camposImposibles(m)[0];
    const { nombre, unidad } = LIMITES[campo];
    const valor = m[campo as keyof MedicionAnalitica];
    const persona = this.datos().personas.find((p) => p.idUsuario === m.idPersonaMayor)?.nombre ?? '';

    return `Más reciente: ${nombre.toLowerCase()} ${valor} ${unidad} (${persona}, ${this.fecha(m.fechaHora)})`;
  });

  // Gráfica 1: estado actual por indicador

  private readonly estadoActual = computed(() =>
    INDICADORES.map((indicador) => {
      let normal = 0;
      let revisar = 0;
      let error = 0;
      for (const m of this.ultimaPorPersona().values()) {
        const evaluacion = evaluarIndicador(m, indicador);
        if (evaluacion === null) continue;
        if (indicadoresConError(m).includes(indicador)) error++;
        else if (evaluacion) normal++;
        else revisar++;
      }
      return { indicador, normal, revisar, error };
    })
  );

  protected readonly graficaEstado = computed<OpcionesGrafica>(() => {
    const datos = [...this.estadoActual()].reverse();
    const hayErrores = datos.some((d) => d.error > 0);
    const series = [
      { nombre: 'Normal', color: ESTADO.bueno, valores: datos.map((d) => d.normal) },
      { nombre: 'Revisar', color: ESTADO.aviso, valores: datos.map((d) => d.revisar) },
      ...(hayErrores
        ? [{ nombre: 'Posible error de registro', color: COLOR_ERROR, valores: datos.map((d) => d.error) }]
        : [])
    ];

    return base({
      legend: { ...leyenda(), data: series.map((s) => s.nombre) },
      grid: { left: 8, right: 24, top: 36, bottom: 8, containLabel: true },
      tooltip: tooltip({ trigger: 'axis', axisPointer: { type: 'shadow' } }),
      xAxis: ejeValores(),
      yAxis: ejeCategorias(datos.map((d) => NOMBRE_INDICADOR[d.indicador])),
      series: series.map((s, i) => ({
        name: s.nombre, type: 'bar', stack: 'estado', barMaxWidth: ANCHO_BARRA,
        cursor: s.nombre === 'Normal' ? 'default' : 'pointer',
        itemStyle: estiloSegmento(s.color, i === series.length - 1),
        data: s.valores
      }))
    });
  });

  protected readonly tablaEstado = computed<TablaGrafica>(() => ({
    columnas: ['Indicador', 'Normal', 'Revisar', 'Posible error de registro'],
    filas: this.estadoActual().map((d) => [NOMBRE_INDICADOR[d.indicador], d.normal, d.revisar, d.error])
  }));

  /** Clic en una barra: filtra la tabla de atención por ese indicador. */
  protected filtrarPorEstado(evento: ClicGrafica): void {
    if (evento.serie === 'Normal') return;
    const indicador = INDICADORES.find((i) => NOMBRE_INDICADOR[i] === evento.nombre) ?? null;
    this.filtroIndicador.set(this.filtroIndicador() === indicador ? null : indicador);
  }

  // Gráfica 2: mediciones en el tiempo

  private readonly medicionesEnElTiempo = computed(() => {
    const mediciones = this.medicionesPeriodo();
    const desde = this.desde();
    if (mediciones.length === 0 && !desde) {
      return { porMes: false, puntos: [] as { etiqueta: string; total: number }[] };
    }

    const inicio = desde ? new Date(`${desde}T00:00`) : new Date(mediciones[0].fechaHora);
    const semanas = (Date.now() - inicio.getTime()) / (7 * DIA);
    const porMes = semanas > 26; // períodos largos: por mes

    const clave = (fecha: Date) => {
      if (porMes) return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}`;
      const lunes = new Date(fecha);
      lunes.setHours(0, 0, 0, 0);
      lunes.setDate(lunes.getDate() - ((lunes.getDay() + 6) % 7));
      return lunes.toLocaleDateString('en-CA');
    };

    // Todos los períodos (también los que no tienen mediciones)
    const conteo = new Map<string, number>();
    const cursor = new Date(inicio);
    while (cursor.getTime() <= Date.now()) {
      conteo.set(clave(cursor), 0);
      if (porMes) cursor.setMonth(cursor.getMonth() + 1, 1);
      else cursor.setDate(cursor.getDate() + 7);
    }
    conteo.set(clave(new Date()), conteo.get(clave(new Date())) ?? 0);

    for (const m of mediciones) {
      const k = clave(new Date(m.fechaHora));
      conteo.set(k, (conteo.get(k) ?? 0) + 1);
    }

    const formato = (k: string) => {
      const [anio, mes, dia] = k.split('-').map(Number);
      return porMes
        ? new Date(anio, mes - 1, 1).toLocaleDateString('es-CO', { month: 'short', year: 'numeric' })
        : new Date(anio, mes - 1, dia).toLocaleDateString('es-CO', { day: 'numeric', month: 'short' });
    };

    return {
      porMes,
      puntos: [...conteo.entries()]
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([k, total]) => ({ etiqueta: formato(k), total }))
    };
  });

  protected readonly graficaTiempo = computed<OpcionesGrafica>(() => {
    const { puntos } = this.medicionesEnElTiempo();
    return base({
      grid: { left: 8, right: 24, top: 16, bottom: 8, containLabel: true },
      tooltip: tooltip({ trigger: 'axis' }),
      xAxis: ejeCategorias(puntos.map((p) => p.etiqueta), {
        boundaryGap: false,
        // Que la primera y la última fecha no se corten en los bordes.
        axisLabel: { color: '#586576', fontSize: 12, hideOverlap: true, alignMinLabel: 'left', alignMaxLabel: 'right' }
      }),
      yAxis: ejeValores(),
      series: [{ name: 'Mediciones', ...estiloLinea(SERIE[0], true), data: puntos.map((p) => p.total) }]
    });
  });

  protected readonly tablaTiempo = computed<TablaGrafica>(() => ({
    columnas: [this.medicionesEnElTiempo().porMes ? 'Mes' : 'Semana del', 'Mediciones'],
    filas: this.medicionesEnElTiempo().puntos.map((p) => [p.etiqueta, p.total])
  }));

  protected readonly tituloTiempo = computed(() =>
    this.medicionesEnElTiempo().porMes ? 'Mediciones por mes' : 'Mediciones por semana');

  // Gráfica 3: evolución de una persona

  /** Personas con al menos una medición (para el selector). */
  protected readonly personasConMediciones = computed(() =>
    this.datos().personas.filter((p) => this.medicionesPorPersona().has(p.idUsuario)));

  protected readonly personaActual = computed(() => {
    const elegida = this.personaElegida();
    const disponibles = this.personasConMediciones();
    if (elegida !== null && disponibles.some((p) => p.idUsuario === elegida)) {
      return elegida;
    }
    // Por defecto: la primera que requiere atención por valores, o la primera con datos.
    const conAlerta = this.atencion().find((p) =>
      (p.indicadores.length > 0 || p.errores > 0) && this.medicionesPorPersona().has(p.idUsuario));
    return conAlerta?.idUsuario ?? disponibles[0]?.idUsuario ?? null;
  });

  private readonly serieEvolucion = computed(() => {
    const id = this.personaActual();
    const desde = this.desde();
    return (id !== null ? this.medicionesPorPersona().get(id) ?? [] : [])
      .filter((m) => !desde || m.fechaHora >= desde);
  });

  /** Valor de un campo para la gráfica: los imposibles no se dibujan. */
  private valorGraficable(m: MedicionAnalitica, campo: CampoSigno): number | null {
    const valor = m[campo as keyof MedicionAnalitica] as number | null;
    if (valor === null) return null;
    const { min, max } = LIMITES[campo];
    return valor < min || valor > max ? null : valor;
  }

  /** Cuántas mediciones del indicador elegido se omiten por imposibles. */
  protected readonly omitidas = computed(() => {
    const campos = CAMPOS[this.indicadorEvolucion()];
    return this.serieEvolucion().filter((m) => {
      const imposibles = camposImposibles(m);
      return campos.some((c) => imposibles.includes(c));
    }).length;
  });

  protected readonly descripcionEvolucion = computed(() => {
    const texto = 'Mediciones de la persona dentro del período. La franja gris es el rango normal de referencia.';
    const n = this.omitidas();
    if (n === 0) return texto;
    return `${texto} ${n === 1 ? '1 medición con un valor imposible no se grafica' : `${n} mediciones con valores imposibles no se grafican`} (ver tabla, marcadas con ⚠).`;
  });

  protected readonly graficaEvolucion = computed<OpcionesGrafica>(() => {
    const indicador = this.indicadorEvolucion();
    const lista = this.serieEvolucion();
    const punto = (m: MedicionAnalitica, campo: CampoSigno) =>
      [m.fechaHora.replace('T', ' '), this.valorGraficable(m, campo)] as [string, number | null];

    // Series a dibujar según el indicador
    const definiciones: { nombre: string; campo: CampoSigno; color: string; rango: { min: number; max: number }; franja: string }[] =
      indicador === 'presion'
        ? [
            { nombre: 'Sistólica (mmHg)', campo: 'presionSistolica', color: SERIE[0], rango: RANGOS.sistolica, franja: 'Normal sistólica' },
            { nombre: 'Diastólica (mmHg)', campo: 'presionDiastolica', color: SERIE[1], rango: RANGOS.diastolica, franja: 'Normal diastólica' }
          ]
        : indicador === 'pulso'
          ? [{ nombre: 'Pulso (lpm)', campo: 'frecuenciaCardiaca', color: SERIE[0], rango: RANGOS.pulso, franja: 'Rango normal' }]
          : [{ nombre: 'Oxígeno (%)', campo: 'saturacionOxigeno', color: SERIE[0], rango: RANGOS.oxigeno, franja: 'Rango normal' }];

    const series = definiciones.map((d) => ({
      ...d,
      puntos: lista.map((m) => punto(m, d.campo)).filter((p) => p[1] !== null)
    }));
    const valores = series.flatMap((s) => s.puntos.map((p) => p[1] as number));
    const sinDatos = valores.length === 0;

    // Eje Y: siempre incluye el rango normal (para que se vea la franja).
    const bajo = Math.min(...valores, ...definiciones.map((d) => d.rango.min));
    const alto = Math.max(...valores, ...definiciones.map((d) => d.rango.max));
    const margen = Math.max((alto - bajo) * 0.15, 2);
    const minimoY = Math.max(0, Math.floor(bajo - margen));
    const maximoY = indicador === 'oxigeno' ? 100 : Math.ceil(alto + margen);

    // Eje X: todo el período (o los últimos 30 días si es "Todo" y no hay datos).
    const ahora = new Date();
    const desde = this.desde();
    const inicio = desde
      ? new Date(`${desde}T00:00`)
      : lista.length > 0
        ? new Date(lista[0].fechaHora)
        : new Date(ahora.getTime() - 30 * DIA);

    const unidad = RANGOS[indicador === 'presion' ? 'sistolica' : indicador === 'pulso' ? 'pulso' : 'oxigeno'].unidad;

    return base({
      legend: definiciones.length > 1 ? leyenda() : undefined,
      grid: { left: 8, right: 24, top: definiciones.length > 1 ? 40 : 20, bottom: 8, containLabel: true },
      tooltip: tooltip({ trigger: 'axis', valueFormatter: (v: number | null) => (v === null ? '—' : `${v} ${unidad}`) }),
      xAxis: {
        type: 'time',
        min: inicio.getTime(),
        max: ahora.getTime(),
        axisLine: { lineStyle: { color: '#c3cad4' } },
        axisLabel: { color: '#586576', fontSize: 11, hideOverlap: true },
        splitLine: { show: false }
      },
      yAxis: ejeValores({ min: minimoY, max: maximoY, minInterval: 0 }),
      series: series.map((s) => ({
        name: s.nombre,
        ...estiloLinea(s.color),
        connectNulls: true,
        data: s.puntos,
        markArea: {
          silent: true,
          itemStyle: { color: GRIS_SUAVE, opacity: 0.6 },
          label: { show: true, position: 'insideTopLeft', color: '#586576', fontSize: 11 },
          data: [[{ name: s.franja, yAxis: s.rango.min }, { yAxis: s.rango.max }]]
        }
      })),
      // Sin datos: el marco completo con un aviso en el centro.
      graphic: sinDatos
        ? [{
            type: 'text',
            left: 'center',
            top: 'middle',
            silent: true,
            style: {
              text: `Sin mediciones de ${NOMBRE_INDICADOR[indicador].toLowerCase()} en este período`,
              fill: '#586576',
              fontSize: 14,
              fontWeight: 600,
              backgroundColor: 'rgba(255,255,255,0.85)',
              padding: [8, 14],
              borderRadius: 8
            }
          }]
        : []
    });
  });

  protected readonly tablaEvolucion = computed<TablaGrafica>(() => ({
    columnas: ['Fecha', 'Presión (mmHg)', 'Pulso (lpm)', 'Oxígeno (%)'],
    filas: this.serieEvolucion().map((m) => {
      const imposibles = camposImposibles(m);
      const marca = (campo: CampoSigno, texto: string) => (imposibles.includes(campo) ? `⚠ ${texto}` : texto);
      const presionErronea = imposibles.includes('presionSistolica') || imposibles.includes('presionDiastolica');
      return [
        m.fechaHora.replace('T', ' '),
        m.presionSistolica !== null || m.presionDiastolica !== null
          ? `${presionErronea ? '⚠ ' : ''}${m.presionSistolica ?? '-'}/${m.presionDiastolica ?? '-'}` : '—',
        m.frecuenciaCardiaca !== null ? marca('frecuenciaCardiaca', String(m.frecuenciaCardiaca)) : '—',
        m.saturacionOxigeno !== null ? marca('saturacionOxigeno', String(m.saturacionOxigeno)) : '—'
      ];
    })
  }));

  protected verEvolucion(idUsuario: number): void {
    this.personaElegida.set(idUsuario);
    document.getElementById('evolucion-persona')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  // Utilidades

  protected fecha(fechaHora: string | null): string {
    if (!fechaHora) return '—';
    return new Date(fechaHora).toLocaleDateString('es-CO', { day: 'numeric', month: 'short', year: 'numeric' });
  }

  private valor(m: MedicionAnalitica, i: IndicadorAnalitica): string {
    switch (i) {
      case 'presion': return `${m.presionSistolica ?? '-'}/${m.presionDiastolica ?? '-'} mmHg`;
      case 'pulso': return `${m.frecuenciaCardiaca} lpm`;
      case 'oxigeno': return `${m.saturacionOxigeno} %`;
    }
  }

  // ---------- Tarjetas (pantalla y PDF) ----------

  protected readonly tarjetas = computed<DatoKpi[]>(() => {
    const k = this.kpis();
    return [
      { etiqueta: 'Con seguimiento', icono: 'heart', tono: 'neutro',
        valor: `${k.conSeguimiento} de ${k.total}`, detalle: 'Personas con al menos una medición en el período' },
      { etiqueta: 'Mediciones registradas', icono: 'clipboard', tono: 'neutro',
        valor: String(k.mediciones), detalle: 'En el período seleccionado' },
      { etiqueta: 'Valores fuera de rango', icono: 'alert-triangle',
        tono: k.fueraDeRango > 0 ? 'alerta' : 'bueno', valor: String(k.fueraDeRango),
        detalle: k.fueraDeRango > 0 ? 'Personas con su última medición por revisar' : 'Todas las últimas mediciones son normales' },
      { etiqueta: 'Sin medición reciente', icono: 'clock',
        tono: k.sinReciente > 0 ? 'alerta' : 'bueno', valor: String(k.sinReciente),
        detalle: k.sinReciente > 0 ? 'Sin medición en los últimos 30 días' : 'Todas medidas en los últimos 30 días' },
      { etiqueta: 'Posibles errores de registro', icono: 'info',
        tono: k.errores > 0 ? 'alerta' : 'bueno', valor: String(k.errores), detalle: k.ejemploError }
    ];
  });

  // ---------- Explicaciones de las gráficas (PDF) ----------

  private explicacionEstado(): string {
    const datos = this.estadoActual();
    const evaluadas = datos.reduce((s, d) => s + d.normal + d.revisar + d.error, 0);
    if (evaluadas === 0) {
      return 'Todavía no hay mediciones de presión, pulso u oxígeno, por eso la gráfica no muestra datos. '
        + 'Cuando se registren signos vitales, aquí se verá cuántas personas tienen su última medición '
        + 'dentro del rango normal y cuántas conviene revisar.';
    }

    const hayErrores = datos.some((d) => d.error > 0);
    const peor = datos.reduce((a, b) => (b.revisar > a.revisar ? b : a));
    const medidas = peor.normal + peor.revisar + peor.error;
    const resumen = peor.revisar === 0
      ? 'Todas las últimas mediciones están dentro de los rangos normales de referencia.'
      : `${NOMBRE_INDICADOR[peor.indicador]} es el indicador con el mayor número de personas por revisar: `
        + `${peor.revisar} de ${medidas} ${medidas === 1 ? 'medida' : 'medidas'}.`;

    return 'Para cada indicador (presión, pulso y oxígeno), la barra divide a las personas según su última '
      + 'medición: en verde las que están dentro del rango normal y en amarillo las que conviene revisar'
      + (hayErrores ? ', y en gris los valores que parecen un error de registro. ' : '. ')
      + `${resumen} Los casos por revisar se detallan en la tabla "Personas que requieren atención".`;
  }

  private explicacionTiempo(): string {
    const { porMes, puntos } = this.medicionesEnElTiempo();
    const unidad = porMes ? 'mes' : 'semana';
    const total = puntos.reduce((s, p) => s + p.total, 0);
    if (total === 0) {
      return 'No se registraron mediciones de signos vitales en el período, por eso la línea se mantiene '
        + 'en cero. Registrar los signos vitales de forma periódica permite identificar a tiempo cambios en '
        + 'la salud de las personas mayores.';
    }

    const mayor = puntos.reduce((a, b) => (b.total > a.total ? b : a));
    const vacios = puntos.filter((p) => p.total === 0).length;
    const promedio = Math.round((total / puntos.length) * 10) / 10;

    return `La línea muestra cuántas mediciones de signos vitales se registraron cada ${unidad} del período: `
      + `${total} en total, con un promedio de ${String(promedio).replace('.', ',')} por ${unidad}. `
      + `${porMes ? 'El mes' : 'La semana'} con el mayor número de registros fue ${porMes ? '' : 'la del '}`
      + `${mayor.etiqueta} (${mayor.total})`
      + (vacios > 0
        ? `, y hubo ${vacios} ${vacios === 1 ? unidad : (porMes ? 'meses' : 'semanas')} sin ninguna medición. `
        : '. ')
      + `Los puntos donde la línea baja señalan los períodos con menor número de mediciones; identificarlos `
      + 'ayuda a planear un seguimiento continuo de la salud.';
  }

  private explicacionEvolucion(nombre: string): string {
    const indicador = this.indicadorEvolucion();
    const rango = indicador === 'presion'
      ? `${RANGOS.sistolica.min}–${RANGOS.sistolica.max} / ${RANGOS.diastolica.min}–${RANGOS.diastolica.max} mmHg`
      : indicador === 'pulso'
        ? `${RANGOS.pulso.min}–${RANGOS.pulso.max} lpm`
        : `${RANGOS.oxigeno.min} % o más`;

    // Mediciones válidas del indicador (sin valores imposibles)
    const validas = this.serieEvolucion().filter((m) =>
      evaluarIndicador(m, indicador) !== null && !indicadoresConError(m).includes(indicador));

    const inicio = `La gráfica sigue las mediciones de ${NOMBRE_INDICADOR[indicador].toLowerCase()} de `
      + `${nombre} a lo largo del período; la franja gris marca el rango normal de referencia (${rango}). `;

    if (validas.length === 0) {
      return `${inicio}No hay mediciones válidas de este indicador en el período, por eso no se dibuja `
        + 'ninguna línea. Conviene programar una nueva medición para conocer su estado actual.';
    }

    const fuera = validas.filter((m) => evaluarIndicador(m, indicador) === false).length;
    const ultima = validas[validas.length - 1];
    const ultimaNormal = evaluarIndicador(ultima, indicador);

    return inicio
      + `Se registraron ${validas.length} ${validas.length === 1 ? 'medición válida' : 'mediciones válidas'}, `
      + `de las cuales ${fuera} ${fuera === 1 ? 'quedó' : 'quedaron'} fuera del rango normal. La más reciente, `
      + `del ${this.fecha(ultima.fechaHora)}, fue de ${this.valor(ultima, indicador)}, `
      + `${ultimaNormal ? 'dentro del rango normal' : 'fuera del rango normal'}. Los puntos que salen de la `
      + 'franja son los que conviene revisar con el personal de salud.';
  }

  // ---------- PDF ----------

  contenidoPdf(): ContenidoReporte {
    const hayMediciones = this.datos().mediciones.length > 0;
    const persona = this.datos().personas.find((p) => p.idUsuario === this.personaActual());

    return {
      indicadores: this.tarjetas(),
      secciones: [
        { titulo: 'Estado actual por indicador',
          descripcion: 'Según la última medición de cada persona.',
          opciones: hayMediciones ? this.graficaEstado() : null, tabla: this.tablaEstado(),
          explicacion: this.explicacionEstado() },
        { titulo: this.tituloTiempo(),
          descripcion: 'Cuántas mediciones de signos vitales se registraron en el período.',
          opciones: this.tablaTiempo().filas.length > 0 ? this.graficaTiempo() : null, tabla: this.tablaTiempo(),
          explicacion: this.explicacionTiempo() },
        ...(persona
          ? [{ titulo: `Evolución de ${persona.nombre}: ${this.nombreIndicador[this.indicadorEvolucion()].toLowerCase()}`,
               descripcion: this.descripcionEvolucion(),
               opciones: this.graficaEvolucion(), tabla: this.tablaEvolucion(),
               explicacion: this.explicacionEvolucion(persona.nombre) }]
          : []),
        { titulo: 'Personas que requieren atención',
          descripcion: 'Última medición fuera de rango, sin medición en los últimos 30 días o con posibles errores de registro.',
          opciones: null,
          tabla: {
            columnas: ['Persona', 'Motivo', 'Última medición'],
            filas: this.atencion().map((p) => [p.nombre, p.motivos.join('; '), this.fecha(p.ultima)])
          } }
      ],
      nota: 'Los rangos "normales" son referencias generales para adultos (presión 90–139 / 60–89 mmHg, pulso 60–100 lpm, '
        + 'oxígeno 95 % o más). Son orientativos y no reemplazan el criterio médico. Un "posible error de registro" '
        + 'es un valor que no puede ser real y probablemente corresponde a un error de digitación.'
    };
  }
}
