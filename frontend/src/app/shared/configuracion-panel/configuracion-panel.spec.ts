import { CONFIGURACION_PANEL } from './configuracion-panel';

const PANELES: Record<string, string> = {
  PERSONA_MAYOR: '/panel/persona-mayor',
  ORGANIZACION: '/panel/organizacion',
  ACOMPANANTE: '/panel/acompanante',
  VOLUNTARIO: '/panel/voluntario'
};

// Menú lateral de cada rol (RF-11): cada rol ve solo las opciones de
// su propio panel.
describe('CONFIGURACION_PANEL', () => {
  it('hay un menú para cada uno de los cuatro roles', () => {
    expect(Object.keys(CONFIGURACION_PANEL).sort()).toEqual(Object.keys(PANELES).sort());
  });

  for (const [rol, panel] of Object.entries(PANELES)) {
    it(`el menú de ${rol} solo lleva a su panel y empieza en Inicio y termina en Perfil`, () => {
      const opciones = CONFIGURACION_PANEL[rol].navItems;

      expect(opciones.every((opcion) => opcion.path?.startsWith(panel))).toBe(true);
      expect(opciones[0].label).toBe('Inicio');
      expect(opciones[opciones.length - 1].label).toBe('Perfil');
    });
  }

  it('las secciones en construcción no aparecen en el menú de la organización', () => {
    const rutas = CONFIGURACION_PANEL['ORGANIZACION'].navItems.map((opcion) => opcion.path);

    for (const seccion of ['alertas', 'medicamentos', 'acompanantes']) {
      expect(rutas).not.toContain(`/panel/organizacion/${seccion}`);
    }
  });

  it('la organización tiene Red y mapa en el menú, después de Analítica', () => {
    const rutas = CONFIGURACION_PANEL['ORGANIZACION'].navItems.map((opcion) => opcion.path);

    expect(rutas.indexOf('/panel/organizacion/mapa'))
      .toBe(rutas.indexOf('/panel/organizacion/analitica') + 1);
  });

  // RF-62 a RF-68
  it('la organización tiene Donaciones e Inventario en el menú', () => {
    const rutas = CONFIGURACION_PANEL['ORGANIZACION'].navItems.map((opcion) => opcion.path);

    expect(rutas).toContain('/panel/organizacion/donaciones');
    expect(rutas).toContain('/panel/organizacion/inventario');
  });

  it('el acompañante y el voluntario pueden donar desde su menú; la persona mayor no', () => {
    expect(CONFIGURACION_PANEL['ACOMPANANTE'].navItems.map((o) => o.path)).toContain('/panel/acompanante/donaciones');
    expect(CONFIGURACION_PANEL['VOLUNTARIO'].navItems.map((o) => o.path)).toContain('/panel/voluntario/donaciones');
    expect(CONFIGURACION_PANEL['PERSONA_MAYOR'].navItems.some((o) => o.path?.includes('donaciones'))).toBe(false);
  });

  it('el menú del voluntario no tiene secciones vacías', () => {
    const rutas = CONFIGURACION_PANEL['VOLUNTARIO'].navItems.map((opcion) => opcion.path);

    for (const seccion of ['personas', 'alertas']) {
      expect(rutas).not.toContain(`/panel/voluntario/${seccion}`);
    }
  });
});
