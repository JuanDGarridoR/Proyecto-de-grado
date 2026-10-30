import { Component, computed, input } from '@angular/core';

import { PoblacionAnalitica } from '../../../../../core/analitica/analitica.service';
import { OPCIONES_USO_CELULAR } from '../../../../../core/persona-mayor/uso-celular';
import {
  ANCHO_BARRA, tooltip, GRIS, RAMPA_AZUL, SERIE, base, ejeCategorias, ejeValores,
  estiloBarra, etiquetaValor, leyenda
} from '../../../../../shared/echart/tema';
import { OpcionesGrafica } from '../../../../../shared/echart/echart';
import { TarjetaGrafica, TablaGrafica } from '../componentes/tarjeta-grafica';
import { DatoKpi, Kpi } from '../componentes/kpi';
import { ContenidoReporte, ReporteExportable } from '../contenido-reporte';

/** Rangos de la gráfica de edades. */
const RANGOS_EDAD = [
  { etiqueta: 'Menos de 60', min: 0, max: 59 },
  { etiqueta: '60 a 69', min: 60, max: 69 },
  { etiqueta: '70 a 79', min: 70, max: 79 },
  { etiqueta: '80 a 89', min: 80, max: 89 },
  { etiqueta: '90 o más', min: 90, max: 200 }
];

/** Color fijo por categoría de interés: sigue a la categoría, no al puesto en el ranking. */
const CATEGORIAS: { valor: string; nombre: string; color: string }[] = [
  { valor: 'GUSTO', nombre: 'Gustos', color: SERIE[0] },
  { valor: 'TALENTO', nombre: 'Talentos', color: SERIE[1] },
  { valor: 'HOBBY', nombre: 'Hobbies', color: SERIE[2] }
];

/** Color fijo por género. */
const GENEROS: { valor: string; color: string }[] = [
  { valor: 'Femenino', color: SERIE[0] },
  { valor: 'Masculino', color: SERIE[1] },
  { valor: 'Otro', color: SERIE[2] },
  { valor: 'Sin dato', color: GRIS }
];

/**
 * Si vive sola. Quien vive sola va en naranja: es el dato que llama a más
 * seguimiento, no un "mal" estado, así que no se usan colores de estado.
 */
const CONVIVENCIA: { valor: boolean | null; nombre: string; color: string }[] = [
  { valor: true, nombre: 'Vive sola', color: SERIE[1] },
  { valor: false, nombre: 'Vive con otras personas', color: SERIE[0] },
  { valor: null, nombre: 'Sin dato', color: GRIS }
];

/** Niveles de uso del celular que se cuentan como "usa poco" (no lo usa o solo llama). */
const USO_BAJO = new Set(['NO_USA', 'BASICO']);

/** Porcentaje entero; 0 si el total es 0. */
const pct = (parte: number, total: number) => (total > 0 ? Math.round((parte / total) * 100) : 0);

/**
 * Reporte 3: quiénes son las personas mayores de la organización
 * (edad, género, si viven solas, uso del celular, EPS, intereses y
 * profesión u oficio). No depende del período.
 */
@Component({
  selector: 'app-reporte-poblacion',
  imports: [Kpi, TarjetaGrafica],
  templateUrl: './reporte-poblacion.html',
  styleUrl: '../reporte.css'
})
export class ReportePoblacion implements ReporteExportable {

  readonly datos = input.required<PoblacionAnalitica>();

  /** Edad de cada persona que tiene fecha de nacimiento. */
  private readonly edades = computed(() => {
    const hoy = new Date();
    return this.datos().personas
      .filter((p) => !!p.fechaNacimiento)
      .map((p) => {
        const [anio, mes, dia] = p.fechaNacimiento!.split('-').map(Number);
        let edad = hoy.getFullYear() - anio;
        if (hoy.getMonth() + 1 < mes || (hoy.getMonth() + 1 === mes && hoy.getDate() < dia)) edad--;
        return edad;
      });
  });

  // Indicadores

  protected readonly kpis = computed(() => {
    const total = this.datos().personas.length;
    const edades = this.edades();
    const promedio = edades.length ? Math.round(edades.reduce((s, e) => s + e, 0) / edades.length) : null;
    const conEps = this.datos().personas.filter((p) => !!p.eps).length;

    return {
      total,
      edadPromedio: promedio !== null ? `${promedio} años` : '—',
      detalleEdad: edades.length < total
        ? `${total - edades.length} sin fecha de nacimiento`
        : 'De todas las personas asociadas',
      conEps: `${pct(conEps, total)} %`,
      detalleEps: `${conEps} de ${total} tienen EPS registrada`,
      conIntereses: `${pct(this.datos().personasConIntereses, total)} %`,
      detalleIntereses: `${this.datos().personasConIntereses} de ${total} registraron sus intereses`
    };
  });

  // Gráfica 1: rangos de edad

  private readonly porEdad = computed(() =>
    RANGOS_EDAD.map((r) => ({
      etiqueta: r.etiqueta,
      personas: this.edades().filter((e) => e >= r.min && e <= r.max).length
    }))
  );

  protected readonly graficaEdad = computed<OpcionesGrafica>(() => {
    const datos = this.porEdad();
    return base({
      grid: { left: 8, right: 16, top: 28, bottom: 8, containLabel: true },
      tooltip: tooltip({ trigger: 'axis', axisPointer: { type: 'shadow' } }),
      xAxis: ejeCategorias(datos.map((d) => d.etiqueta)),
      yAxis: ejeValores(),
      series: [{
        name: 'Personas', type: 'bar', barMaxWidth: 40,
        label: etiquetaValor('top'),
        // Rangos ordenados: rampa azul de claro (jóvenes) a oscuro (mayores).
        data: datos.map((d, i) => ({ value: d.personas, itemStyle: estiloBarra(RAMPA_AZUL[i]) }))
      }]
    });
  });

  protected readonly tablaEdad = computed<TablaGrafica>(() => ({
    columnas: ['Rango de edad', 'Personas'],
    filas: this.porEdad().map((d) => [d.etiqueta, d.personas])
  }));

  // Gráfica 2: género

  private readonly porGenero = computed(() => {
    const total = this.datos().personas.length;
    return GENEROS
      .map((g) => {
        const personas = this.datos().personas.filter((p) =>
          g.valor === 'Sin dato' ? !p.genero : p.genero === g.valor).length;
        return { ...g, personas, porcentaje: pct(personas, total) };
      })
      .filter((g) => g.personas > 0);
  });

  protected readonly graficaGenero = computed<OpcionesGrafica>(() => {
    const datos = this.porGenero();
    const total = this.datos().personas.length;
    return base({
      legend: { ...leyenda(), top: 'auto', bottom: 0, left: 'center' },
      tooltip: tooltip({ trigger: 'item', formatter: '{b}: {c} ({d} %)' }),
      series: [{
        name: 'Género', type: 'pie', radius: ['52%', '74%'], center: ['50%', '45%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#ffffff', borderWidth: 2 },
        label: { show: true, color: '#12355b', fontSize: 12, fontWeight: 600, formatter: '{d} %' },
        data: datos.map((d) => ({ name: d.valor, value: d.personas, itemStyle: { color: d.color } }))
      }],
      // Total en el centro de la dona
      title: {
        text: String(total), subtext: 'personas', left: 'center', top: '36%',
        textStyle: { color: '#12355b', fontSize: 24, fontWeight: 700 },
        subtextStyle: { color: '#586576', fontSize: 12 }
      }
    });
  });

  protected readonly tablaGenero = computed<TablaGrafica>(() => ({
    columnas: ['Género', 'Personas', 'Porcentaje'],
    filas: this.porGenero().map((g) => [g.valor, g.personas, `${g.porcentaje} %`])
  }));

  // Gráfica 3: ¿viven solas?

  private readonly porConvivencia = computed(() => {
    const total = this.datos().personas.length;
    return CONVIVENCIA
      .map((c) => {
        const personas = this.datos().personas.filter((p) => (p.viveSolo ?? null) === c.valor).length;
        return { ...c, personas, porcentaje: pct(personas, total) };
      })
      .filter((c) => c.personas > 0);
  });

  protected readonly graficaViveSolo = computed<OpcionesGrafica>(() => {
    const datos = this.porConvivencia();
    const solas = datos.find((d) => d.valor === true)?.personas ?? 0;
    return base({
      legend: { ...leyenda(), top: 'auto', bottom: 0, left: 'center' },
      tooltip: tooltip({ trigger: 'item', formatter: '{b}: {c} ({d} %)' }),
      series: [{
        name: '¿Vive sola?', type: 'pie', radius: ['52%', '74%'], center: ['50%', '45%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#ffffff', borderWidth: 2 },
        label: { show: true, color: '#12355b', fontSize: 12, fontWeight: 600, formatter: '{d} %' },
        data: datos.map((d) => ({ name: d.nombre, value: d.personas, itemStyle: { color: d.color } }))
      }],
      // En el centro, cuántas viven solas
      title: {
        text: String(solas), subtext: 'viven solas', left: 'center', top: '36%',
        textStyle: { color: '#12355b', fontSize: 24, fontWeight: 700 },
        subtextStyle: { color: '#586576', fontSize: 12 }
      }
    });
  });

  protected readonly tablaViveSolo = computed<TablaGrafica>(() => ({
    columnas: ['Situación', 'Personas', 'Porcentaje'],
    filas: this.porConvivencia().map((c) => [c.nombre, c.personas, `${c.porcentaje} %`])
  }));

  // Gráfica 4: uso del celular

  /** Un nivel por barra, de menos a más uso, y al final quienes no lo han indicado. */
  private readonly porUsoCelular = computed(() => {
    const personas = this.datos().personas;
    const total = personas.length;
    const niveles = OPCIONES_USO_CELULAR.map((o, i) => {
      const cuantas = personas.filter((p) => p.usoCelular === o.valor).length;
      return {
        valor: o.valor as string | null, nombre: o.corta, etiqueta: o.etiqueta,
        color: RAMPA_AZUL[i + 1], personas: cuantas, porcentaje: pct(cuantas, total)
      };
    });
    const sinDato = personas.filter((p) => !p.usoCelular).length;
    return sinDato > 0
      ? [...niveles, {
          valor: null, nombre: 'Sin dato', etiqueta: 'Sin dato',
          color: GRIS, personas: sinDato, porcentaje: pct(sinDato, total)
        }]
      : niveles;
  });

  protected readonly graficaUsoCelular = computed<OpcionesGrafica>(() => {
    const datos = this.porUsoCelular();
    return base({
      grid: { left: 8, right: 16, top: 28, bottom: 8, containLabel: true },
      tooltip: tooltip({ trigger: 'axis', axisPointer: { type: 'shadow' } }),
      xAxis: ejeCategorias(datos.map((d) => d.nombre)),
      yAxis: ejeValores(),
      series: [{
        name: 'Personas', type: 'bar', barMaxWidth: 40,
        label: etiquetaValor('top'),
        // Niveles ordenados: rampa azul de claro (poco uso) a oscuro (mucho uso).
        data: datos.map((d) => ({ value: d.personas, itemStyle: estiloBarra(d.color) }))
      }]
    });
  });

  protected readonly tablaUsoCelular = computed<TablaGrafica>(() => ({
    columnas: ['Nivel de uso', 'Personas', 'Porcentaje'],
    filas: this.porUsoCelular().map((d) => [d.nombre, d.personas, `${d.porcentaje} %`])
  }));

  // Gráfica 5: EPS

  private readonly porEps = computed(() => {
    const conteo = new Map<string, number>();
    for (const p of this.datos().personas) {
      const eps = p.eps?.trim() || 'Sin EPS registrada';
      conteo.set(eps, (conteo.get(eps) ?? 0) + 1);
    }
    const lista = [...conteo.entries()]
      .map(([eps, personas]) => ({ eps, personas }))
      .sort((a, b) => b.personas - a.personas);

    // Más de 8: el resto se agrupa en "Otras".
    if (lista.length <= 8) return lista;
    const otras = lista.slice(7).reduce((s, e) => s + e.personas, 0);
    return [...lista.slice(0, 7), { eps: 'Otras', personas: otras }];
  });

  protected readonly graficaEps = computed<OpcionesGrafica>(() => {
    const datos = [...this.porEps()].reverse();
    return base({
      grid: { left: 8, right: 40, top: 8, bottom: 8, containLabel: true },
      tooltip: tooltip({ trigger: 'axis', axisPointer: { type: 'shadow' } }),
      xAxis: ejeValores(),
      yAxis: ejeCategorias(datos.map((d) => d.eps), {
        axisLabel: { color: '#586576', fontSize: 12, width: 150, overflow: 'truncate' }
      }),
      series: [{
        name: 'Personas', type: 'bar', barMaxWidth: ANCHO_BARRA,
        label: etiquetaValor('right'),
        data: datos.map((d) => ({
          value: d.personas,
          itemStyle: estiloBarra(d.eps === 'Sin EPS registrada' || d.eps === 'Otras' ? GRIS : SERIE[0], true)
        }))
      }]
    });
  });

  protected readonly tablaEps = computed<TablaGrafica>(() => ({
    columnas: ['EPS', 'Personas'],
    filas: this.porEps().map((d) => [d.eps, d.personas])
  }));

  // Gráfica 6: intereses más comunes

  private readonly topIntereses = computed(() => this.datos().intereses.slice(0, 10));

  protected readonly graficaIntereses = computed<OpcionesGrafica>(() => {
    const datos = [...this.topIntereses()].reverse();
    const presentes = CATEGORIAS.filter((c) => datos.some((d) => d.categoria === c.valor));
    return base({
      legend: leyenda(),
      grid: { left: 8, right: 40, top: 36, bottom: 8, containLabel: true },
      tooltip: tooltip({
        trigger: 'item',
        formatter: (p: { dataIndex: number }) => {
          const d = datos[p.dataIndex];
          const categoria = CATEGORIAS.find((c) => c.valor === d.categoria)?.nombre ?? d.categoria;
          return `<strong>${d.nombre}</strong><br>${categoria} · ${d.personas} personas`;
        }
      }),
      xAxis: ejeValores(),
      yAxis: ejeCategorias(datos.map((d) => d.nombre)),
      // Una serie por categoría, apiladas: cada barra solo tiene valor en
      // su categoría, así la leyenda explica los colores.
      series: presentes.map((c) => ({
        name: c.nombre, type: 'bar', stack: 'intereses', barMaxWidth: ANCHO_BARRA,
        itemStyle: estiloBarra(c.color, true),
        label: { ...etiquetaValor('right'), formatter: (p: { value: number | null }) => (p.value ? String(p.value) : '') },
        data: datos.map((d) => (d.categoria === c.valor ? d.personas : null))
      }))
    });
  });

  protected readonly tablaIntereses = computed<TablaGrafica>(() => ({
    columnas: ['Interés', 'Categoría', 'Personas'],
    filas: this.topIntereses().map((d) => [
      d.nombre,
      CATEGORIAS.find((c) => c.valor === d.categoria)?.nombre ?? d.categoria,
      d.personas
    ])
  }));

  // Gráfica 7: profesiones u oficios

  private readonly topProfesiones = computed(() => this.datos().profesiones.slice(0, 10));

  protected readonly graficaProfesiones = computed<OpcionesGrafica>(() => {
    const datos = [...this.topProfesiones()].reverse();
    return base({
      grid: { left: 8, right: 40, top: 8, bottom: 8, containLabel: true },
      tooltip: tooltip({ trigger: 'axis', axisPointer: { type: 'shadow' } }),
      xAxis: ejeValores(),
      yAxis: ejeCategorias(datos.map((d) => d.nombre), {
        axisLabel: { color: '#586576', fontSize: 12, width: 170, overflow: 'truncate' }
      }),
      series: [{
        name: 'Personas', type: 'bar', barMaxWidth: ANCHO_BARRA,
        label: etiquetaValor('right'),
        data: datos.map((d) => ({ value: d.personas, itemStyle: estiloBarra(SERIE[3], true) }))
      }]
    });
  });

  protected readonly tablaProfesiones = computed<TablaGrafica>(() => ({
    columnas: ['Profesión u oficio', 'Personas'],
    filas: this.topProfesiones().map((d) => [d.nombre, d.personas])
  }));

  // ---------- Tarjetas (pantalla y PDF) ----------

  protected readonly tarjetas = computed<DatoKpi[]>(() => {
    const k = this.kpis();
    return [
      { etiqueta: 'Personas asociadas', icono: 'users', tono: 'neutro',
        valor: String(k.total), detalle: 'Con asociación aceptada' },
      { etiqueta: 'Edad promedio', icono: 'calendar', tono: 'neutro',
        valor: k.edadPromedio, detalle: k.detalleEdad },
      { etiqueta: 'Con EPS registrada', icono: 'clipboard', tono: 'neutro',
        valor: k.conEps, detalle: k.detalleEps },
      { etiqueta: 'Con intereses registrados', icono: 'sparkles', tono: 'neutro',
        valor: k.conIntereses, detalle: k.detalleIntereses }
    ];
  });

  // ---------- Explicaciones de las gráficas (PDF) ----------

  private readonly sinPersonas = 'Aún no hay personas mayores asociadas a la organización, por eso la gráfica '
    + 'no muestra datos. Cuando se acepten las primeras asociaciones, aquí se verá ';

  private explicacionEdad(): string {
    const total = this.datos().personas.length;
    if (total === 0) {
      return `${this.sinPersonas}cuántas personas hay en cada rango de edad.`;
    }

    const edades = this.edades();
    if (edades.length === 0) {
      return 'Ninguna de las personas asociadas tiene fecha de nacimiento registrada, por eso no es posible '
        + 'agruparlas por edad. Completar ese dato en sus perfiles permitirá conocer la distribución por edad '
        + 'de la población que atiende la organización.';
    }

    const mayor = this.porEdad().reduce((a, b) => (b.personas > a.personas ? b : a));
    const sinFecha = total - edades.length;

    return 'La gráfica agrupa a las personas mayores asociadas según su edad. El rango que agrupa el mayor número de personas es '
      + `"${mayor.etiqueta}" (${mayor.personas}, el ${pct(mayor.personas, edades.length)} %) y la edad promedio `
      + `es de ${this.kpis().edadPromedio}. `
      + (sinFecha > 0
        ? `${sinFecha} ${sinFecha === 1 ? 'persona no tiene' : 'personas no tienen'} fecha de nacimiento registrada y no se incluyen. `
        : '')
      + 'Conocer esta distribución ayuda a planear actividades acordes a las capacidades y necesidades de cada grupo.';
  }

  private explicacionGenero(): string {
    const total = this.datos().personas.length;
    if (total === 0) {
      return `${this.sinPersonas}su distribución por género.`;
    }

    const partes = this.porGenero().map((g) => `${g.valor}: ${g.porcentaje} % (${g.personas})`);
    const lista = partes.length > 1
      ? `${partes.slice(0, -1).join(', ')} y ${partes[partes.length - 1]}`
      : partes[0];

    return 'La dona muestra la proporción de personas asociadas según su género; el número del centro es '
      + `el total de personas (${total}). La distribución es la siguiente: ${lista}. Tenerla en cuenta `
      + 'permite diseñar actividades y campañas de salud que respondan a las necesidades de cada grupo.';
  }

  private explicacionViveSolo(): string {
    const total = this.datos().personas.length;
    if (total === 0) {
      return `${this.sinPersonas}cuántas viven solas.`;
    }

    const datos = this.porConvivencia();
    const solas = datos.find((d) => d.valor === true);
    const sinDato = datos.find((d) => d.valor === null)?.personas ?? 0;
    const principal = solas
      ? `${solas.personas} ${solas.personas === 1 ? 'persona vive sola' : 'personas viven solas'} `
        + `(${solas.porcentaje} % de las asociadas). `
      : 'Ninguna de las personas que lo indicaron vive sola. ';
    const faltantes = sinDato > 0
      ? `${sinDato} ${sinDato === 1 ? 'persona no ha indicado' : 'personas no han indicado'} si vive sola; `
        + 'completar ese dato en su perfil ayuda a identificar a quién priorizar. '
      : '';

    return 'La dona muestra cuántas personas asociadas viven solas y cuántas viven con otras personas. '
      + principal + faltantes
      + 'Las personas mayores que viven solas tienen más riesgo de aislamiento y de que una emergencia pase '
      + 'desapercibida, por lo que conviene darles prioridad en las llamadas, visitas y actividades de la organización.';
  }

  private explicacionUsoCelular(): string {
    const total = this.datos().personas.length;
    if (total === 0) {
      return `${this.sinPersonas}su nivel de uso del celular.`;
    }

    const datos = this.porUsoCelular();
    const sinDato = datos.find((d) => d.valor === null)?.personas ?? 0;
    const conDato = total - sinDato;
    if (conDato === 0) {
      return 'Ninguna de las personas asociadas ha indicado su nivel de uso del celular. Completar ese dato en su '
        + 'perfil ayuda a elegir el mejor medio para comunicarse con cada una.';
    }

    const usoBajo = datos.filter((d) => d.valor !== null && USO_BAJO.has(d.valor)).reduce((s, d) => s + d.personas, 0);
    const mayor = datos.filter((d) => d.valor !== null).reduce((a, b) => (b.personas > a.personas ? b : a));
    const faltantes = sinDato > 0
      ? `${sinDato} ${sinDato === 1 ? 'persona no ha indicado' : 'personas no han indicado'} su nivel de uso. `
      : '';

    return 'Las barras muestran el nivel de uso del celular de las personas asociadas, de menor a mayor. El nivel '
      + `más frecuente es "${mayor.etiqueta}" (${mayor.personas} de ${conDato} que lo indicaron). `
      + `${usoBajo} ${usoBajo === 1 ? 'persona no lo usa o tiene' : 'personas no lo usan o tienen'} un uso bajo, `
      + `solo para llamadas (${pct(usoBajo, conDato)} % de quienes lo indicaron). ${faltantes}`
      + 'Con quienes usan poco el celular conviene avisar las actividades y recordatorios por llamada o a través '
      + 'de su acompañante, y quienes ya usan mensajes o aplicaciones pueden aprovechar mejor la plataforma o '
      + 'participar en talleres para sacarle más provecho.';
  }

  private explicacionEps(): string {
    const total = this.datos().personas.length;
    if (total === 0) {
      return `${this.sinPersonas}a qué entidades de salud están afiliadas.`;
    }

    const conEps = this.porEps().filter((e) => e.eps !== 'Sin EPS registrada' && e.eps !== 'Otras');
    const sinEps = this.porEps().find((e) => e.eps === 'Sin EPS registrada')?.personas ?? 0;
    const principal = conEps.length > 0
      ? `"${conEps[0].eps}" es la EPS con el mayor número de afiliados: ${conEps[0].personas} `
        + `${conEps[0].personas === 1 ? 'persona' : 'personas'} (${pct(conEps[0].personas, total)} %). `
      : '';
    const faltantes = sinEps > 0
      ? `${sinEps} ${sinEps === 1 ? 'persona no tiene' : 'personas no tienen'} EPS registrada; completar `
        + 'ese dato facilita la gestión de citas y servicios de salud.'
      : 'Todas las personas tienen su EPS registrada, lo que facilita la gestión de citas y servicios de salud.';

    return 'Cada barra indica cuántas personas mayores están afiliadas a cada EPS (si hay más de ocho, '
      + `las de menor número de afiliados se agrupan en "Otras"). ${principal}${faltantes}`;
  }

  private explicacionIntereses(): string {
    const total = this.datos().personas.length;
    const conIntereses = this.datos().personasConIntereses;
    const top = this.topIntereses();
    if (top.length === 0) {
      return 'Ninguna persona asociada ha registrado sus gustos, talentos o hobbies, por eso la gráfica no '
        + 'muestra datos. Invitar a las personas mayores a completar sus intereses ayudará a proponer '
        + 'actividades que respondan a lo que disfrutan y saben hacer.';
    }

    const primero = top[0];
    return 'La gráfica muestra los intereses registrados por el mayor número de personas asociadas (hasta 10); '
      + 'el color indica si se trata de un gusto, un talento o un hobby. El primero de la lista es '
      + `"${primero.nombre}", con ${primero.personas} ${primero.personas === 1 ? 'persona' : 'personas'}, y `
      + `${conIntereses} de ${total} personas han registrado sus intereses. Estos datos sirven para `
      + 'proponer actividades que respondan a lo que las personas mayores disfrutan y saben hacer.';
  }

  private explicacionProfesiones(): string {
    const total = this.datos().personas.length;
    const conProfesion = this.datos().personasConProfesion;
    const top = this.topProfesiones();
    if (top.length === 0) {
      return 'Ninguna persona asociada ha registrado su profesión u oficio, por eso la gráfica no muestra '
        + 'datos. Conocer a qué se dedicaron ayuda a proponer actividades en las que puedan compartir su '
        + 'experiencia.';
    }

    const primero = top[0];
    return 'La gráfica muestra a qué se dedicaron las personas asociadas (hasta 10 profesiones u oficios; una '
      + 'persona puede tener más de uno). El más común es '
      + `"${primero.nombre}", con ${primero.personas} ${primero.personas === 1 ? 'persona' : 'personas'}, y `
      + `${conProfesion} de ${total} personas han registrado su profesión u oficio. Estos datos sirven para `
      + 'proponer actividades en las que las personas mayores puedan aportar lo que saben hacer.';
  }

  // ---------- PDF ----------

  contenidoPdf(): ContenidoReporte {
    const hayPersonas = this.datos().personas.length > 0;
    return {
      indicadores: this.tarjetas(),
      secciones: [
        { titulo: 'Rangos de edad', descripcion: 'Cuántas personas mayores hay en cada rango de edad.',
          opciones: hayPersonas ? this.graficaEdad() : null, tabla: this.tablaEdad(),
          explicacion: this.explicacionEdad() },
        { titulo: 'Género', descripcion: 'Distribución por género de las personas asociadas.',
          opciones: hayPersonas ? this.graficaGenero() : null, tabla: this.tablaGenero(),
          explicacion: this.explicacionGenero() },
        { titulo: '¿Viven solas?', descripcion: 'Cuántas personas asociadas viven solas.',
          opciones: hayPersonas ? this.graficaViveSolo() : null, tabla: this.tablaViveSolo(),
          explicacion: this.explicacionViveSolo() },
        { titulo: 'Nivel de uso del celular', descripcion: 'Nivel de uso del celular de las personas asociadas.',
          opciones: hayPersonas ? this.graficaUsoCelular() : null, tabla: this.tablaUsoCelular(),
          explicacion: this.explicacionUsoCelular() },
        { titulo: 'EPS', descripcion: 'Entidades de salud de las personas asociadas.',
          opciones: hayPersonas ? this.graficaEps() : null, tabla: this.tablaEps(),
          explicacion: this.explicacionEps() },
        { titulo: 'Intereses más comunes',
          descripcion: 'Los 10 gustos, talentos y hobbies registrados por el mayor número de personas.',
          opciones: this.datos().intereses.length > 0 ? this.graficaIntereses() : null,
          tabla: this.tablaIntereses(),
          explicacion: this.explicacionIntereses() },
        { titulo: 'Profesiones u oficios',
          descripcion: 'A qué se dedicaron las personas asociadas (hasta 10).',
          opciones: this.datos().profesiones.length > 0 ? this.graficaProfesiones() : null,
          tabla: this.tablaProfesiones(),
          explicacion: this.explicacionProfesiones() }
      ]
    };
  }
}
