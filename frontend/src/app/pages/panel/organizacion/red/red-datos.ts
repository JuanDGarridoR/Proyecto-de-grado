import { RedOrganizacion } from '../../../../core/analitica/analitica.service';
import { OpcionesGrafica } from '../../../../shared/echart/echart';
import { ESTADO, GRIS, SERIE, base, leyenda, tooltip } from '../../../../shared/echart/tema';
import { TablaGrafica } from '../analitica/componentes/tarjeta-grafica';

/**
 * Cálculos de «Red y mapa» (sin pantalla, para poder probarlos solos): el
 * grafo de la red de apoyo, el grafo por gustos, y lo que pinta el mapa
 * (personas mayores agrupadas por barrio y de qué barrio va la gente a
 * cada lugar de actividad).
 *
 * Colores (paleta validada de tema.ts, cada entidad con el mismo color en
 * todas las vistas): organización/sede azul, persona mayor naranja,
 * acompañante aqua, voluntario amarillo, gusto magenta y lugar de
 * actividad violeta. "Sin acompañante" es un estado: rojo de ESTADO, con
 * forma de rombo y su propia entrada en la leyenda (nunca solo color).
 */

export const COLOR = {
  organizacion: SERIE[0],
  persona: SERIE[1],
  acompanante: SERIE[2],
  voluntario: SERIE[3],
  gusto: SERIE[4],
  lugar: SERIE[6],
  sinAcompanante: ESTADO.critico
};

type Red = RedOrganizacion;
type Persona = Red['personasMayores'][number];

/** Personas mayores que no tienen ningún acompañante con vínculo aceptado. */
export function sinAcompanante(red: Red): Persona[] {
  const conAcompanante = new Set(red.vinculos.map((v) => v.idPersonaMayor));
  return red.personasMayores.filter((p) => !conAcompanante.has(p.id));
}

/** Personas mayores que tienen ese gusto (todas si idGusto es null). */
export function personasConGusto(red: Red, idGusto: number | null): Persona[] {
  return idGusto === null
    ? red.personasMayores
    : red.personasMayores.filter((p) => p.gustos.includes(idGusto));
}

// ---------------------------------------------------------------------------
// Grafo de la red de apoyo
// ---------------------------------------------------------------------------

const CATEGORIAS_RED = [
  { name: 'Organización', itemStyle: { color: COLOR.organizacion } },
  { name: 'Persona mayor', itemStyle: { color: COLOR.persona } },
  { name: 'Persona mayor sin acompañante', itemStyle: { color: COLOR.sinAcompanante }, symbol: 'diamond' },
  { name: 'Acompañante', itemStyle: { color: COLOR.acompanante } },
  { name: 'Voluntario', itemStyle: { color: COLOR.voluntario } }
];

/** Nodo del grafo: lo que muestra el tooltip va en `detalle`. */
interface Nodo {
  id: string;
  name: string;
  category: number;
  symbolSize: number;
  symbol?: string;
  detalle: string;
  label?: { show: boolean };
}

interface Enlace {
  source: string;
  target: string;
  detalle: string;
}

/**
 * La organización al centro, unida a sus personas mayores y voluntarios;
 * cada persona mayor, unida a sus acompañantes. Las personas sin
 * acompañante quedan colgando solo de la organización: es lo que hay que
 * mirar (posible aislamiento).
 */
export function grafoRedApoyo(red: Red): OpcionesGrafica {
  const aisladas = new Set(sinAcompanante(red).map((p) => p.id));
  const acompanantesDe = new Map<number, number>();
  red.vinculos.forEach((v) => acompanantesDe.set(v.idPersonaMayor, (acompanantesDe.get(v.idPersonaMayor) ?? 0) + 1));

  const nodos: Nodo[] = [{
    id: 'o',
    name: red.organizacion.nombre,
    category: 0,
    symbolSize: 50,
    detalle: 'Tu organización',
    label: { show: true }
  }];
  const enlaces: Enlace[] = [];

  for (const p of red.personasMayores) {
    const aislada = aisladas.has(p.id);
    nodos.push({
      id: `p${p.id}`,
      name: p.nombre,
      category: aislada ? 2 : 1,
      symbolSize: aislada ? 28 : 24,
      ...(aislada ? { symbol: 'diamond' } : {}),
      detalle: [
        aislada ? 'Sin acompañante' : `${acompanantesDe.get(p.id)} acompañante(s)`,
        p.barrio ? `Barrio ${p.barrio}` : 'Barrio no reconocido',
        `${p.actividades.length} actividad(es) recientes o próximas`
      ].join(' · ')
    });
    enlaces.push({ source: 'o', target: `p${p.id}`, detalle: 'Vinculada a la organización' });
  }

  for (const a of red.acompanantes) {
    nodos.push({ id: `a${a.id}`, name: a.nombre, category: 3, symbolSize: 18, detalle: 'Acompañante' });
  }
  for (const v of red.vinculos) {
    enlaces.push({
      source: `p${v.idPersonaMayor}`,
      target: `a${v.idAcompanante}`,
      detalle: v.relacion ? `Acompañante (${v.relacion})` : 'Acompañante'
    });
  }

  for (const v of red.voluntarios) {
    nodos.push({ id: `v${v.id}`, name: v.nombre, category: 4, symbolSize: 20, detalle: 'Voluntario' });
    enlaces.push({ source: 'o', target: `v${v.id}`, detalle: 'Voluntario de la organización' });
  }

  // Es la vista principal: nodos grandes y bien separados para que se lea de lejos.
  return opcionesGrafo(nodos, enlaces, CATEGORIAS_RED, 110, 320);
}

// ---------------------------------------------------------------------------
// Grafo por gustos
// ---------------------------------------------------------------------------

const CATEGORIAS_GUSTOS = [
  { name: 'Persona mayor', itemStyle: { color: COLOR.persona } },
  { name: 'Voluntario', itemStyle: { color: COLOR.voluntario } },
  { name: 'Gusto', itemStyle: { color: COLOR.gusto } }
];

/**
 * La red completa de gustos: cada persona mayor y cada voluntario unido a
 * todos sus gustos, así que dos personas con un gusto en común quedan a un
 * paso. Las personas son los nodos grandes; los gustos, puntos pequeños con
 * nombre (crecen un poco cuanta más gente los tiene, sin pasar a una
 * persona). Para que no se vea enredado: líneas finas y claras, más
 * separación entre nodos y, al pasar el mouse, solo queda resaltado lo que
 * está conectado con ese punto.
 */
export function grafoGustos(red: Red): OpcionesGrafica {
  const conteo = conteoGustos(red);
  const nombre = new Map(red.gustos.map((g) => [g.id, g.nombre]));
  const listaGustos = (gustos: number[]) => gustos.map((g) => nombre.get(g)).filter(Boolean).join(', ');
  const nodos: Nodo[] = [];
  const enlaces: Enlace[] = [];

  for (const g of red.gustos.filter((g) => conteo.has(g.id))) {
    const c = conteo.get(g.id)!;
    nodos.push({
      id: `g${g.id}`,
      name: g.nombre,
      category: 2,
      symbolSize: Math.min(16, 7 + 2 * Math.sqrt(c.personas + c.voluntarios)),
      detalle: `Lo tienen ${c.personas} persona(s) mayor(es) y ${c.voluntarios} voluntario(s)`,
      label: { show: true }
    });
  }

  for (const p of red.personasMayores.filter((p) => p.gustos.length > 0)) {
    nodos.push({ id: `p${p.id}`, name: p.nombre, category: 0, symbolSize: 22, detalle: `Gustos: ${listaGustos(p.gustos)}` });
    p.gustos.forEach((g) => enlaces.push({ source: `p${p.id}`, target: `g${g}`, detalle: `${p.nombre}: ${nombre.get(g)}` }));
  }

  for (const v of red.voluntarios.filter((v) => v.gustos.length > 0)) {
    nodos.push({ id: `v${v.id}`, name: v.nombre, category: 1, symbolSize: 18, detalle: `Voluntario · Gustos: ${listaGustos(v.gustos)}` });
    v.gustos.forEach((g) => enlaces.push({ source: `v${v.id}`, target: `g${g}`, detalle: `${v.nombre}: ${nombre.get(g)}` }));
  }

  return opcionesGrafo(nodos, enlaces, CATEGORIAS_GUSTOS, 80, 220, 0.35);
}

function conteoGustos(red: Red): Map<number, { personas: number; voluntarios: number }> {
  const conteo = new Map<number, { personas: number; voluntarios: number }>();
  const de = (id: number) => conteo.get(id) ?? conteo.set(id, { personas: 0, voluntarios: 0 }).get(id)!;
  red.personasMayores.forEach((p) => p.gustos.forEach((g) => de(g).personas++));
  red.voluntarios.forEach((v) => v.gustos.forEach((g) => de(g).voluntarios++));
  return conteo;
}

/** Grafo de fuerzas con el estilo común: se puede arrastrar, acercar y resaltar vecinos. */
function opcionesGrafo(
  nodos: Nodo[], enlaces: Enlace[], categorias: object[], largoEnlace: number, repulsion = 140, opacidadLinea = 0.8
): OpcionesGrafica {
  return base({
    legend: { ...leyenda(), data: categorias.map((c) => (c as { name: string }).name) },
    tooltip: tooltip({
      formatter: (p: { dataType: string; data: Nodo | Enlace; name: string }) =>
        p.dataType === 'node'
          ? `<strong>${escapar((p.data as Nodo).name)}</strong><br/>${escapar((p.data as Nodo).detalle)}`
          : escapar((p.data as Enlace).detalle)
    }),
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      top: 40,
      categories: categorias,
      data: nodos,
      links: enlaces,
      force: { repulsion, gravity: 0.08, edgeLength: [largoEnlace * 0.6, largoEnlace] },
      // Solo se rotulan la organización y los gustos (etiquetas selectivas); el resto, al pasar el mouse.
      label: { show: false, position: 'right', color: '#12355b', fontSize: 12, fontWeight: 600 },
      itemStyle: { borderColor: '#ffffff', borderWidth: 2 },
      lineStyle: { color: GRIS, width: opacidadLinea < 0.8 ? 1 : 1.5, opacity: opacidadLinea, curveness: 0.05 },
      emphasis: { focus: 'adjacency', label: { show: true }, lineStyle: { width: 2.5, color: '#586576' } }
    }]
  });
}

/** Los nombres los escriben los usuarios: se escapan antes de ponerlos en el HTML del tooltip. */
function escapar(texto: string): string {
  return texto.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);
}

// ---------------------------------------------------------------------------
// Tablas (vista accesible de cada grafo)
// ---------------------------------------------------------------------------

/** Una fila por persona mayor; primero las que no tienen acompañante. */
export function tablaRedApoyo(red: Red): TablaGrafica {
  const nombres = new Map(red.acompanantes.map((a) => [a.id, a.nombre]));
  const acompanantes = (id: number) => red.vinculos
    .filter((v) => v.idPersonaMayor === id)
    .map((v) => v.relacion ? `${nombres.get(v.idAcompanante)} (${v.relacion})` : nombres.get(v.idAcompanante));

  const filas = red.personasMayores
    .map((p) => ({ p, lista: acompanantes(p.id) }))
    .sort((a, b) => Number(a.lista.length > 0) - Number(b.lista.length > 0) || a.p.nombre.localeCompare(b.p.nombre))
    .map(({ p, lista }) => [
      p.nombre,
      p.barrio ?? 'No reconocido',
      lista.length > 0 ? lista.join(', ') : 'Sin acompañante',
      p.actividades.length
    ]);

  return { columnas: ['Persona mayor', 'Barrio', 'Acompañantes', 'Actividades'], filas };
}

/** Una fila por gusto, del más al menos común. */
export function tablaGustos(red: Red): TablaGrafica {
  const conteo = conteoGustos(red);
  const filas = red.gustos
    .map((g) => ({ g, c: conteo.get(g.id) ?? { personas: 0, voluntarios: 0 } }))
    .sort((a, b) => b.c.personas - a.c.personas || a.g.nombre.localeCompare(b.g.nombre))
    .map(({ g, c }) => [g.nombre, c.personas, c.voluntarios]);

  return { columnas: ['Gusto', 'Personas mayores', 'Voluntarios'], filas };
}

// ---------------------------------------------------------------------------
// Mapa de la red
// ---------------------------------------------------------------------------

/** Personas mayores de un barrio (círculo del mapa). */
export interface BarrioMapa {
  nombre: string;
  latitud: number;
  longitud: number;
  personas: number;
  sinAcompanante: number;
  /** Los 3 gustos más comunes del barrio. */
  gustosComunes: string[];
}

/** Lugar de actividades (varias actividades pueden ser en el mismo sitio). */
export interface LugarMapa {
  clave: string;
  nombre: string;
  latitud: number;
  longitud: number;
  actividades: string[];
}

/** Cuántas inscripciones van de un barrio a un lugar (línea del mapa). */
export interface FlujoMapa {
  barrio: string;
  lugar: string;
  inscripciones: number;
}

export interface DatosMapaRed {
  sede: { nombre: string; latitud: number; longitud: number } | null;
  barrios: BarrioMapa[];
  lugares: LugarMapa[];
  flujos: FlujoMapa[];
  /** Personas (del filtro) cuya dirección no tiene un barrio reconocido. */
  sinBarrio: number;
}

/**
 * Lo que pinta el mapa con el filtro de gusto (null = todas): las personas
 * mayores agrupadas por barrio (nunca su casa), los lugares de actividad
 * que ya están ubicados y, por cada barrio, a qué lugares fue su gente.
 */
export function datosMapa(red: Red, idGusto: number | null): DatosMapaRed {
  const personas = personasConGusto(red, idGusto);
  const aisladas = new Set(sinAcompanante(red).map((p) => p.id));
  const nombreGusto = new Map(red.gustos.map((g) => [g.id, g.nombre]));
  const centro = new Map(red.barrios.map((b) => [b.nombre, b]));

  // Personas por barrio
  const porBarrio = new Map<string, Persona[]>();
  personas.filter((p) => p.barrio && centro.has(p.barrio))
    .forEach((p) => porBarrio.set(p.barrio!, [...(porBarrio.get(p.barrio!) ?? []), p]));

  const barrios: BarrioMapa[] = [...porBarrio.entries()]
    .map(([nombre, gente]) => {
      const veces = new Map<number, number>();
      gente.forEach((p) => p.gustos.forEach((g) => veces.set(g, (veces.get(g) ?? 0) + 1)));
      return {
        nombre,
        latitud: centro.get(nombre)!.latitud,
        longitud: centro.get(nombre)!.longitud,
        personas: gente.length,
        sinAcompanante: gente.filter((p) => aisladas.has(p.id)).length,
        gustosComunes: [...veces.entries()]
          .sort((a, b) => b[1] - a[1])
          .slice(0, 3)
          .map(([g]) => nombreGusto.get(g) ?? '')
      };
    })
    .sort((a, b) => b.personas - a.personas);

  // Lugares: las actividades ubicadas, agrupadas por punto
  const lugares = new Map<string, LugarMapa>();
  const lugarDeActividad = new Map<number, string>();
  for (const a of red.actividades) {
    if (a.latitud === null || a.longitud === null) {
      continue;
    }
    const clave = `${a.latitud.toFixed(5)},${a.longitud.toFixed(5)}`;
    const lugar = lugares.get(clave)
      ?? lugares.set(clave, { clave, nombre: a.lugar ?? 'Lugar de actividad', latitud: a.latitud, longitud: a.longitud, actividades: [] }).get(clave)!;
    lugar.actividades.push(a.nombre);
    lugarDeActividad.set(a.id, clave);
  }

  // Flujos barrio -> lugar (inscripciones)
  const flujos = new Map<string, FlujoMapa>();
  for (const p of personas) {
    if (!p.barrio || !porBarrio.has(p.barrio)) {
      continue;
    }
    for (const idActividad of p.actividades) {
      const clave = lugarDeActividad.get(idActividad);
      if (!clave) {
        continue;
      }
      const id = `${p.barrio}|${clave}`;
      const flujo = flujos.get(id) ?? flujos.set(id, { barrio: p.barrio, lugar: clave, inscripciones: 0 }).get(id)!;
      flujo.inscripciones++;
    }
  }

  const { latitud, longitud, nombre } = red.organizacion;

  return {
    sede: latitud !== null && longitud !== null ? { nombre, latitud, longitud } : null,
    barrios,
    lugares: [...lugares.values()],
    flujos: [...flujos.values()],
    sinBarrio: personas.filter((p) => !p.barrio || !centro.has(p.barrio)).length
  };
}

/** Vista accesible del mapa: una fila por barrio. */
export function tablaMapa(datos: DatosMapaRed): TablaGrafica {
  const nombreLugar = new Map(datos.lugares.map((l) => [l.clave, l.nombre]));
  return {
    columnas: ['Barrio', 'Personas mayores', 'Sin acompañante', 'Gustos más comunes', 'Van a'],
    filas: datos.barrios.map((b) => [
      b.nombre,
      b.personas,
      b.sinAcompanante,
      b.gustosComunes.join(', ') || '—',
      datos.flujos
        .filter((f) => f.barrio === b.nombre)
        .sort((x, y) => y.inscripciones - x.inscripciones)
        .map((f) => `${nombreLugar.get(f.lugar)} (${f.inscripciones})`)
        .join(', ') || '—'
    ])
  };
}
