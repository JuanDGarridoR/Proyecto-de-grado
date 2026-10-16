import { RedOrganizacion } from '../../../../core/analitica/analitica.service';
import {
  COLOR,
  datosMapa,
  grafoGustos,
  grafoRedApoyo,
  sinAcompanante,
  tablaGustos,
  tablaMapa,
  tablaRedApoyo
} from './red-datos';

/**
 * Tres personas mayores: Rosa (Yomasa, con su hija de acompañante), Pedro
 * (Yomasa, sin acompañante) y Ana (sin barrio reconocido, sin
 * acompañante). Una voluntaria. La caminata es en el Parque La Flora y el
 * bingo aún no está ubicado.
 */
function red(): RedOrganizacion {
  return {
    organizacion: { id: 5, nombre: 'Fundación Entrenubes', latitud: 4.4805, longitud: -74.1255 },
    personasMayores: [
      { id: 15, nombre: 'Ana', barrio: null, gustos: [], actividades: [100] },
      { id: 11, nombre: 'Pedro', barrio: 'Yomasa', gustos: [1], actividades: [100, 101] },
      { id: 10, nombre: 'Rosa', barrio: 'Yomasa', gustos: [1, 2], actividades: [100] }
    ],
    acompanantes: [{ id: 50, nombre: 'Carmen' }],
    vinculos: [{ idPersonaMayor: 10, idAcompanante: 50, relacion: 'Hija' }],
    voluntarios: [{ id: 40, nombre: 'Laura', gustos: [2] }],
    gustos: [
      { id: 1, nombre: 'Boleros', categoria: 'GUSTO' },
      { id: 2, nombre: 'Tejido', categoria: 'TALENTO' }
    ],
    barrios: [{ nombre: 'Yomasa', latitud: 4.518, longitud: -74.1075 }],
    actividades: [
      { id: 100, nombre: 'Caminata', fecha: '2026-10-20', lugar: 'Parque La Flora', latitud: 4.528, longitud: -74.085 },
      { id: 101, nombre: 'Bingo', fecha: '2026-10-22', lugar: 'Salón', latitud: null, longitud: null }
    ]
  };
}

/** Serie del grafo (las opciones de ECharts tienen una sola). */
function serie(opciones: object): { data: { id: string; category: number; symbol?: string }[]; links: { source: string; target: string; detalle: string }[] } {
  return (opciones as { series: never[] }).series[0];
}

describe('Red y mapa', () => {

  it('las personas sin ningún acompañante aceptado son las posibles aisladas', () => {
    expect(sinAcompanante(red()).map((p) => p.nombre)).toEqual(['Ana', 'Pedro']);
  });

  it('el grafo une la organización con todos y a cada persona con sus acompañantes', () => {
    const { data, links } = serie(grafoRedApoyo(red()));

    expect(data.map((n) => n.id)).toEqual(['o', 'p15', 'p11', 'p10', 'a50', 'v40']);
    expect(links).toContainEqual({ source: 'o', target: 'p10', detalle: 'Vinculada a la organización' });
    expect(links).toContainEqual({ source: 'o', target: 'v40', detalle: 'Voluntario de la organización' });
    expect(links).toContainEqual({ source: 'p10', target: 'a50', detalle: 'Acompañante (Hija)' });
  });

  it('las personas sin acompañante tienen su propia categoría y forma de rombo (no solo color)', () => {
    const { data } = serie(grafoRedApoyo(red()));
    const pedro = data.find((n) => n.id === 'p11')!;
    const rosa = data.find((n) => n.id === 'p10')!;

    expect(pedro.category).toBe(2);
    expect(pedro.symbol).toBe('diamond');
    expect(rosa.category).toBe(1);
    expect(rosa.symbol).toBeUndefined();
  });

  it('el grafo por gustos une cada gusto con quien lo tiene', () => {
    const { data, links } = serie(grafoGustos(red()));

    // Ana no tiene gustos: no aparece
    expect(data.map((n) => n.id)).toEqual(['g1', 'g2', 'p11', 'p10', 'v40']);
    expect(links).toContainEqual({ source: 'v40', target: 'g2', detalle: 'Le gusta' });
  });

  it('la tabla de la red pone primero a quienes no tienen acompañante', () => {
    expect(tablaRedApoyo(red()).filas).toEqual([
      ['Ana', 'No reconocido', 'Sin acompañante', 1],
      ['Pedro', 'Yomasa', 'Sin acompañante', 2],
      ['Rosa', 'Yomasa', 'Carmen (Hija)', 1]
    ]);
  });

  it('la tabla de gustos cuenta personas mayores y voluntarios', () => {
    expect(tablaGustos(red()).filas).toEqual([
      ['Boleros', 2, 0],
      ['Tejido', 1, 1]
    ]);
  });

  it('el mapa agrupa por barrio y une cada barrio con los lugares a los que va su gente', () => {
    const mapa = datosMapa(red(), null);

    expect(mapa.sede).toEqual({ nombre: 'Fundación Entrenubes', latitud: 4.4805, longitud: -74.1255 });
    expect(mapa.barrios).toEqual([{
      nombre: 'Yomasa', latitud: 4.518, longitud: -74.1075,
      personas: 2, sinAcompanante: 1, gustosComunes: ['Boleros', 'Tejido']
    }]);
    // El bingo no está ubicado: solo sale el parque
    expect(mapa.lugares.map((l) => l.nombre)).toEqual(['Parque La Flora']);
    expect(mapa.flujos).toEqual([{ barrio: 'Yomasa', lugar: mapa.lugares[0].clave, inscripciones: 2 }]);
    expect(mapa.sinBarrio).toBe(1);
  });

  it('el filtro por gusto deja solo a quienes tienen ese gusto', () => {
    const mapa = datosMapa(red(), 2);

    expect(mapa.barrios[0].personas).toBe(1);
    expect(mapa.flujos[0].inscripciones).toBe(1);
    expect(mapa.sinBarrio).toBe(0);
  });

  it('la tabla del mapa resume cada barrio', () => {
    expect(tablaMapa(datosMapa(red(), null)).filas).toEqual([
      ['Yomasa', 2, 1, 'Boleros, Tejido', 'Parque La Flora (2)']
    ]);
  });

  it('cada entidad tiene un color distinto y el de aislamiento es el de estado crítico', () => {
    const colores = Object.values(COLOR);
    expect(new Set(colores).size).toBe(colores.length);
    expect(COLOR.sinAcompanante).toBe('#d03b3b');
  });
});
