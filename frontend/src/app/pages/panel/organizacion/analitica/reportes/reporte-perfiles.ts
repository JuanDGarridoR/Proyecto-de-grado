import { Component, computed, input, output } from '@angular/core';

import { GrupoPerfil, PerfilesAnalitica } from '../../../../../core/analitica/analitica.service';
import {
  ANCHO_BARRA, GRIS, SERIE, base, ejeCategorias, ejeValores, estiloBarra, estiloLinea, formatoNumero,
  leyenda, tooltip
} from '../../../../../shared/echart/tema';
import { OpcionesGrafica } from '../../../../../shared/echart/echart';
import { Icono } from '../../../../../shared/icono/icono';
import { TarjetaGrafica, TablaGrafica } from '../componentes/tarjeta-grafica';
import { DatoKpi, Kpi } from '../componentes/kpi';
import { ContenidoReporte, ReporteExportable } from '../contenido-reporte';

/** Cómo leer la silueta (la calcula analitica-service). */
const ESTRUCTURA: Record<string, { nombre: string; buena: boolean }> = {
  FUERTE: { nombre: 'Grupos bien separados', buena: true },
  RAZONABLE: { nombre: 'Separación razonable', buena: true },
  DEBIL: { nombre: 'Separación débil', buena: false },
  SIN_ESTRUCTURA: { nombre: 'Sin grupos claros', buena: false }
};

const NIVEL: Record<string, string> = {
  BUENA: 'Buena',
  ACEPTABLE: 'Aceptable',
  LIMITADA: 'Limitada',
  INSUFICIENTE: 'Insuficiente'
};

/** Color de cada grupo: la paleta en orden fijo (como máximo 6 grupos). */
export const colorGrupo = (numero: number) => SERIE[(numero - 1) % SERIE.length];

/** Valor con su unidad ("78 años", "35 %"). */
function conUnidad(valor: number, unidad: string): string {
  const numero = formatoNumero(valor);
  if (!unidad) return numero;
  return unidad === '%' ? `${numero} %` : `${numero} ${unidad}`;
}

/** "a, b y c". */
function unir(elementos: string[]): string {
  return elementos.length <= 1 ? (elementos[0] ?? '') : `${elementos.slice(0, -1).join(', ')} y ${elementos.at(-1)}`;
}

/**
 * Reporte 5: perfiles de las personas mayores encontrados con K-means
 * (analitica-service, ver docs/KMEANS_PERFILES.md). Primero muestra la calidad
 * de los datos y sus limitaciones, luego los grupos, qué los distingue y
 * qué atención y actividades se les pueden ofrecer. Solo orienta a la
 * organización: no es un diagnóstico.
 */
@Component({
  selector: 'app-reporte-perfiles',
  imports: [Icono, Kpi, TarjetaGrafica],
  templateUrl: './reporte-perfiles.html',
  styleUrls: ['../reporte.css', './reporte-salud.css', './reporte-perfiles.css']
})
export class ReportePerfiles implements ReporteExportable {

  readonly datos = input.required<PerfilesAnalitica>();
  /** La organización elige el número de grupos (null = el sugerido). */
  readonly elegirK = output<number | null>();

  protected readonly listo = computed(() => this.datos().estado === 'LISTO' && this.datos().modelo !== null);
  protected readonly colorGrupo = colorGrupo;

  /** Opciones del selector de número de grupos: 2..kMaximo. */
  protected readonly opcionesK = computed(() => {
    const modelo = this.datos().modelo;
    return modelo ? Array.from({ length: modelo.kMaximo - 1 }, (_, i) => i + 2) : [];
  });

  protected cambiarK(valor: string): void {
    this.elegirK.emit(valor ? Number(valor) : null);
  }

  // Indicadores

  protected readonly tarjetas = computed<DatoKpi[]>(() => {
    const { calidad, modelo } = this.datos();
    const tarjetas: DatoKpi[] = [
      { etiqueta: 'Personas analizadas', icono: 'users', tono: calidad.personasExcluidas > 0 ? 'alerta' : 'neutro',
        valor: String(calidad.personasIncluidas),
        detalle: `de ${calidad.personasAsociadas} vinculadas`
          + (calidad.personasExcluidas > 0 ? ` · ${calidad.personasExcluidas} sin datos suficientes` : '') },
      { etiqueta: 'Calidad de los datos', icono: 'clipboard',
        tono: calidad.nivel === 'BUENA' ? 'bueno' : calidad.nivel === 'ACEPTABLE' ? 'neutro' : 'alerta',
        valor: NIVEL[calidad.nivel],
        detalle: calidad.nivel === 'INSUFICIENTE'
          ? `Se necesitan al menos ${calidad.minimoPersonas} personas con datos`
          : `${formatoNumero(calidad.porcentajeImputado)} % de valores completados`
            + (calidad.registrosDePrueba > 0 ? ` · ${calidad.registrosDePrueba} de prueba` : '') }
    ];
    if (modelo) {
      const estructura = ESTRUCTURA[modelo.estructura];
      tarjetas.splice(1, 0,
        { etiqueta: 'Perfiles encontrados', icono: 'target', tono: 'neutro', valor: String(modelo.k),
          detalle: modelo.kElegidoPorUsuario ? `Elegido por ti (sugerido: ${modelo.kSugerido})` : 'Sugerido por la silueta' },
        { etiqueta: 'Separación de los grupos', icono: 'bar-chart', tono: estructura.buena ? 'bueno' : 'alerta',
          valor: formatoNumero(Math.round(modelo.silueta * 100) / 100),
          detalle: `${estructura.nombre} (silueta de -1 a 1)` });
    }
    return tarjetas;
  });

  // Gráfica 1: mapa de los grupos (PCA)

  protected readonly graficaMapa = computed<OpcionesGrafica>(() => {
    const { personas, grupos, modelo } = this.datos();
    const varianza = modelo?.varianzaExplicada ?? [0, 0];
    // Cada eje es un componente principal; se nombra por lo que significa
    const eje = (i: number) => ejeValores({
      name: `${i === 0 ? 'Mayor diferencia entre personas' : 'Segunda mayor diferencia'} `
        + `(${Math.round(varianza[i] * 100)} %)`,
      // Sin números en el eje, el nombre va pegado a él; con más espacio se
      // salía del lienzo y se cortaba
      nameLocation: 'middle', nameGap: 10, minInterval: 0,
      nameTextStyle: { color: '#586576', fontSize: 12 },
      axisLabel: { show: false }
    });
    return base({
      // En una sola línea: con nombres largos la leyenda ocupaba varias y
      // tapaba los puntos
      legend: { ...leyenda(), type: 'scroll', right: 0 },
      grid: { left: 30, right: 16, top: 40, bottom: 30, containLabel: true },
      tooltip: tooltip({
        trigger: 'item',
        formatter: (p: { data: [number, number, string]; seriesName: string }) =>
          `<strong>${p.data[2]}</strong><br>${p.seriesName}`
      }),
      xAxis: eje(0),
      yAxis: eje(1),
      series: grupos.map((g) => ({
        name: `Grupo ${g.numero}: ${g.nombre}`,
        type: 'scatter',
        symbolSize: 11,
        itemStyle: { color: colorGrupo(g.numero), borderColor: '#ffffff', borderWidth: 1.5, opacity: 0.9 },
        emphasis: { scale: 1.4 },
        data: personas.filter((p) => p.grupo === g.numero).map((p) => [p.x, p.y, p.nombre])
      }))
    });
  });

  protected readonly tablaMapa = computed<TablaGrafica>(() => ({
    columnas: ['Persona', 'Grupo', 'Eje 1', 'Eje 2'],
    filas: this.datos().personas.map((p) => [p.nombre, `Grupo ${p.grupo}`, formatoNumero(p.x), formatoNumero(p.y)])
  }));

  // Gráfica 2: qué distingue a cada grupo (centroides en desviaciones)

  /** Variables que entraron al modelo, en su orden. */
  private readonly usadas = computed(() => this.datos().variables.filter((v) => v.usada));

  protected readonly altoPerfil = computed(() =>
    Math.max(320, this.usadas().length * (10 * this.datos().grupos.length + 14) + 60));

  protected readonly graficaPerfil = computed<OpcionesGrafica>(() => {
    const variables = [...this.usadas()].reverse();
    const grupos = this.datos().grupos;
    return base({
      legend: leyenda(),
      grid: { left: 8, right: 24, top: 40, bottom: 8, containLabel: true },
      tooltip: tooltip({
        trigger: 'axis', axisPointer: { type: 'shadow' },
        formatter: (puntos: { dataIndex: number; seriesIndex: number; marker: string }[]) => {
          const variable = variables[puntos[0].dataIndex];
          const filas = puntos.map((p) => {
            const valor = grupos[p.seriesIndex].centroide.find((c) => c.clave === variable.clave)!;
            return `${p.marker} Grupo ${grupos[p.seriesIndex].numero}: ${conUnidad(valor.valor, variable.unidad)}`;
          });
          return `<strong>${variable.nombre}</strong><br>Promedio de la organización: `
            + `${conUnidad(variable.media ?? 0, variable.unidad)}<br>${filas.join('<br>')}`;
        }
      }),
      xAxis: ejeValores({
        minInterval: 0, min: -2, max: 2, interval: 1,
        name: '← Menos que el promedio · Más que el promedio →', nameLocation: 'middle', nameGap: 26,
        nameTextStyle: { color: '#586576', fontSize: 12 }
      }),
      yAxis: ejeCategorias(variables.map((v) => v.nombre)),
      series: grupos.map((g) => ({
        name: `Grupo ${g.numero}`, type: 'bar', barMaxWidth: 10, barGap: '20%',
        itemStyle: { color: colorGrupo(g.numero), borderRadius: 2 },
        data: variables.map((v) => {
          const z = g.centroide.find((c) => c.clave === v.clave)?.z ?? 0;
          return Math.max(-2, Math.min(2, z));
        })
      }))
    });
  });

  protected readonly tablaPerfil = computed<TablaGrafica>(() => ({
    columnas: ['Variable', 'Promedio organización', ...this.datos().grupos.map((g) => `Grupo ${g.numero}`)],
    filas: this.usadas().map((v) => [
      v.nombre,
      conUnidad(v.media ?? 0, v.unidad),
      ...this.datos().grupos.map((g) => {
        const c = g.centroide.find((x) => x.clave === v.clave);
        return c ? conUnidad(c.valor, v.unidad) : '—';
      })
    ])
  }));

  // Gráfica 3: elección del número de grupos (silueta por k)

  protected readonly graficaK = computed<OpcionesGrafica>(() => {
    const modelo = this.datos().modelo;
    const evaluaciones = modelo?.evaluaciones ?? [];
    return base({
      grid: { left: 8, right: 24, top: 28, bottom: 28, containLabel: true },
      tooltip: tooltip({
        trigger: 'axis',
        formatter: (p: { dataIndex: number }[]) => {
          const e = evaluaciones[p[0].dataIndex];
          return `<strong>${e.k} grupos</strong><br>Silueta: ${formatoNumero(e.silueta)}<br>`
            + `Inercia: ${formatoNumero(e.inercia)}${e.k === modelo?.kSugerido ? '<br>Sugerido' : ''}`;
        }
      }),
      xAxis: ejeCategorias(evaluaciones.map((e) => `${e.k} grupos`)),
      yAxis: ejeValores({ minInterval: 0, min: (v: { min: number }) => Math.min(0, Math.floor(v.min * 10) / 10), max: 1 }),
      series: [{
        name: 'Silueta', ...estiloLinea(SERIE[0], true),
        data: evaluaciones.map((e) => ({
          value: e.silueta,
          symbolSize: e.k === modelo?.kSugerido ? 14 : 8,
          label: e.k === modelo?.kSugerido
            ? { show: true, position: 'top', formatter: 'Sugerido', color: '#12355b', fontWeight: 600 }
            : undefined
        }))
      }]
    });
  });

  protected readonly tablaK = computed<TablaGrafica>(() => ({
    columnas: ['Grupos (k)', 'Silueta', 'Inercia'],
    filas: (this.datos().modelo?.evaluaciones ?? []).map((e) => [
      e.k === this.datos().modelo?.kSugerido ? `${e.k} (sugerido)` : String(e.k),
      formatoNumero(e.silueta), formatoNumero(e.inercia)
    ])
  }));

  // Gráfica 4: completitud de las variables

  protected readonly altoCompletitud = computed(() => Math.max(280, this.datos().variables.length * 26 + 50));

  protected readonly graficaCompletitud = computed<OpcionesGrafica>(() => {
    const variables = [...this.datos().variables].reverse();
    const tipos = [
      { nombre: 'Entró al modelo', usada: true, color: SERIE[0] },
      { nombre: 'No se usó', usada: false, color: GRIS }
    ].filter((t) => variables.some((v) => v.usada === t.usada));
    return base({
      legend: leyenda(),
      grid: { left: 8, right: 48, top: 36, bottom: 8, containLabel: true },
      tooltip: tooltip({
        trigger: 'item',
        formatter: (p: { dataIndex: number }) => {
          const v = variables[p.dataIndex];
          return `<strong>${v.nombre}</strong><br>${formatoNumero(v.completitud)} % de las personas tiene el dato`
            + (v.motivoDescarte ? `<br>No se usó: ${v.motivoDescarte.toLowerCase()}` : '');
        }
      }),
      xAxis: ejeValores({ max: 100, axisLabel: { formatter: (v: number) => `${v} %`, color: '#93a0b0', fontSize: 11 } }),
      yAxis: ejeCategorias(variables.map((v) => v.nombre)),
      series: tipos.map((t) => ({
        name: t.nombre, type: 'bar', stack: 'completitud', barMaxWidth: ANCHO_BARRA,
        itemStyle: estiloBarra(t.color, true),
        label: { show: true, position: 'right', color: '#12355b', fontSize: 12, fontWeight: 600,
          formatter: (p: { value: number | null }) => (p.value === null ? '' : `${formatoNumero(p.value)} %`) },
        data: variables.map((v) => (v.usada === t.usada ? v.completitud : null))
      }))
    });
  });

  protected readonly tablaCompletitud = computed<TablaGrafica>(() => ({
    columnas: ['Variable', 'Dimensión', 'Con dato', '¿Entró al modelo?'],
    filas: this.datos().variables.map((v) => [
      v.nombre, v.dimension, `${formatoNumero(v.completitud)} %`, v.usada ? 'Sí' : `No: ${v.motivoDescarte ?? 'sin datos'}`
    ])
  }));

  // Personas por grupo

  protected readonly personasPorGrupo = computed(() =>
    this.datos().grupos.map((g) => ({
      grupo: g,
      personas: this.datos().personas.filter((p) => p.grupo === g.numero).map((p) => p.nombre)
        .sort((a, b) => a.localeCompare(b))
    })));

  protected readonly nota = NOTA;

  // ---------- Explicaciones (PDF) ----------

  private explicacionMapa(): string {
    const modelo = this.datos().modelo!;
    const explicada = Math.round((modelo.varianzaExplicada[0] + modelo.varianzaExplicada[1]) * 100);
    return `Cada punto es una persona mayor y su color es el grupo que le asignó K-means. Las ${this.usadas().length} `
      + 'variables se resumen en dos ejes (análisis de componentes principales): el horizontal es lo que más '
      + 'diferencia a las personas entre sí y el vertical, lo que sigue. Juntos conservan el '
      + `${explicada} % de la información, y puntos cercanos son personas con características parecidas. Los grupos `
      + 'se calcularon con todas las variables, así que en el plano pueden verse algo superpuestos.';
  }

  private explicacionPerfil(): string {
    const grupos = this.datos().grupos;
    return 'Cada barra muestra cuánto se aleja el promedio del grupo del promedio de la organización, en '
      + 'desviaciones estándar: a la derecha, más que el promedio; a la izquierda, menos. '
      + grupos.map((g) => `El grupo ${g.numero} (${g.nombre.toLowerCase()}) reúne a ${g.personas} `
        + `${g.personas === 1 ? 'persona' : 'personas'}${g.rasgos.length ? `; se distingue por: ${g.rasgos[0].toLowerCase()}` : ''}.`)
        .join(' ');
  }

  private explicacionK(): string {
    const modelo = this.datos().modelo!;
    return `Se entrenó K-means con 2 a ${modelo.kMaximo} grupos y se comparó la silueta de cada opción, que mide qué `
      + 'tan parecida es cada persona a su grupo frente al grupo vecino (de -1 a 1; más alto es mejor). '
      + `La mejor fue ${modelo.kSugerido} grupos. `
      + (modelo.kElegidoPorUsuario ? `El reporte usa ${modelo.k} grupos porque así se eligió. ` : '')
      + 'La inercia (en la tabla) siempre baja al agregar grupos, por eso no sirve sola para elegir.';
  }

  private explicacionCompletitud(): string {
    const { calidad, variables } = this.datos();
    const bajas = variables.filter((v) => v.completitud < 60).map((v) => v.nombre.toLowerCase());
    return `Porcentaje de las ${calidad.personasAsociadas} personas vinculadas que tiene cada dato. `
      + (bajas.length ? `Los datos con menos registros son: ${unir(bajas)}. ` : 'Todos los datos tienen buena cobertura. ')
      + 'Los conteos (enfermedades, medicamentos, acompañantes, emergencias) siempre aparecen completos porque un 0 '
      + 'significa que no hay ninguno registrado, lo que no garantiza que la persona no los tenga.';
  }

  private filaGrupo(g: GrupoPerfil): string[] {
    return [
      `Grupo ${g.numero}: ${g.nombre}`,
      `${g.personas} (${formatoNumero(g.porcentaje)} %)`,
      g.rasgos.join('. ') || g.descripcion,
      g.recomendacionesAtencion.join(' '),
      g.recomendacionesActividades.join(' ')
    ];
  }

  // ---------- PDF ----------

  contenidoPdf(): ContenidoReporte {
    const datos = this.datos();
    const secciones: ContenidoReporte['secciones'] = [];

    if (this.listo()) {
      secciones.push(
        { titulo: 'Mapa de los perfiles', descripcion: 'Cada punto es una persona mayor, coloreada según su grupo.',
          opciones: this.graficaMapa(), tabla: this.tablaMapa(), explicacion: this.explicacionMapa() },
        { titulo: 'Perfiles encontrados', descripcion: 'Qué distingue a cada grupo y qué se le puede ofrecer.',
          opciones: null,
          tabla: { columnas: ['Grupo', 'Personas', 'Rasgos', 'Atención sugerida', 'Actividades sugeridas'],
            filas: datos.grupos.map((g) => this.filaGrupo(g)) } },
        { titulo: 'Qué distingue a cada grupo', descripcion: 'Promedio de cada grupo frente al de la organización.',
          opciones: this.graficaPerfil(), tabla: this.tablaPerfil(), explicacion: this.explicacionPerfil() },
        { titulo: 'Número de grupos', descripcion: 'Silueta del modelo según el número de grupos.',
          opciones: this.graficaK(), tabla: this.tablaK(), explicacion: this.explicacionK() }
      );
    }

    secciones.push(
      { titulo: 'Calidad de los datos', descripcion: 'Qué porcentaje de las personas tiene cada dato.',
        opciones: this.graficaCompletitud(), tabla: this.tablaCompletitud(), explicacion: this.explicacionCompletitud() },
      { titulo: 'Personas sin datos suficientes', descripcion: 'No entraron al análisis; esto es lo que les falta registrar.',
        opciones: null,
        tabla: { columnas: ['Persona', 'Le falta'], filas: datos.excluidas.map((e) => [e.nombre, e.faltantes.join(', ')]) } }
    );

    return {
      indicadores: this.tarjetas(),
      secciones,
      nota: [...datos.advertencias, NOTA].join(' ')
    };
  }
}

/** Limitaciones generales (también en pantalla y en docs/KMEANS_PERFILES.md). */
export const NOTA = 'Los perfiles se calculan con K-means a partir de la edad, las condiciones de salud, los '
  + 'signos vitales de los últimos 180 días y las necesidades de acompañamiento registradas en VITA+. Son una '
  + 'orientación para planear la atención y las actividades: no son un diagnóstico ni reemplazan el criterio de '
  + 'un profesional de salud, y dependen de que los datos estén completos y al día.';
