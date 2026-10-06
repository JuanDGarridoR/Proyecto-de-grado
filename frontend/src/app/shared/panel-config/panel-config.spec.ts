import { PANEL_CONFIG } from './panel-config';

const PANELES: Record<string, string> = {
  PERSONA_MAYOR: '/panel/persona-mayor',
  ORGANIZACION: '/panel/organizacion',
  ACOMPANANTE: '/panel/acompanante',
  VOLUNTARIO: '/panel/voluntario'
};

// Menú lateral de cada rol (RF-03 y UI-02): cada rol ve solo las opciones de
// su propio panel.
describe('PANEL_CONFIG', () => {
  it('hay un menú para cada uno de los cuatro roles', () => {
    expect(Object.keys(PANEL_CONFIG).sort()).toEqual(Object.keys(PANELES).sort());
  });

  for (const [rol, panel] of Object.entries(PANELES)) {
    it(`el menú de ${rol} solo lleva a su panel y empieza en Inicio y termina en Perfil`, () => {
      const opciones = PANEL_CONFIG[rol].navItems;

      expect(opciones.every((opcion) => opcion.path?.startsWith(panel))).toBe(true);
      expect(opciones[0].label).toBe('Inicio');
      expect(opciones[opciones.length - 1].label).toBe('Perfil');
    });
  }

  it('las secciones en construcción no aparecen en el menú de la organización', () => {
    const rutas = PANEL_CONFIG['ORGANIZACION'].navItems.map((opcion) => opcion.path);

    for (const seccion of ['donaciones', 'alertas', 'mapa', 'medicamentos', 'acompanantes']) {
      expect(rutas).not.toContain(`/panel/organizacion/${seccion}`);
    }
  });
});
