import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RetirosAnalitica } from '../../../../../core/analitica/analitica.service';
import { DatoKpi } from '../componentes/kpi';
import { ReporteRetiros } from './reporte-retiros';

/** Tres retiros (uno hace más de un año) y 16 personas que siguen en la organización. */
function datos(): RetirosAnalitica {
  return {
    retiros: [
      { nombre: 'Jorge Ríos', razon: 'DIFICULTAD_USO', comentario: 'No entendía el celular', fecha: '2026-10-05T15:30' },
      { nombre: 'Carmen Peña', razon: 'FALLECIMIENTO', comentario: null, fecha: '2026-09-10T09:00' },
      { nombre: 'Elvia Mora', razon: 'FALLECIMIENTO', comentario: null, fecha: '2026-08-20T10:00' },
      { nombre: 'Rafael Gil', razon: 'CAMBIO_RESIDENCIA', comentario: null, fecha: '2025-06-01T10:00' }
    ],
    personasActuales: 16
  };
}

/** Acceso a los computed protegidos del reporte. */
interface VistaReporte {
  retiros(): { nombre: string }[];
  tarjetas(): DatoKpi[];
  tablaRazones(): { filas: (string | number)[][] };
  tablaMeses(): { filas: (string | number)[][] };
}

// Reporte de retiros de la analítica (RF-73): cuántas personas mayores
// eliminaron su cuenta en el período, por qué y qué comentaron. La fecha se
// fija en el 14 de octubre de 2026.
describe('ReporteRetiros', () => {
  let fixture: ComponentFixture<ReporteRetiros>;
  let reporte: VistaReporte;

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 14, 12, 0));

    TestBed.configureTestingModule({ imports: [ReporteRetiros] });
    TestBed.overrideComponent(ReporteRetiros, { set: { template: '', imports: [] } });
    fixture = TestBed.createComponent(ReporteRetiros);
    fixture.componentRef.setInput('datos', datos());
    fixture.componentRef.setInput('desde', '2026-07-16');
    reporte = fixture.componentInstance as unknown as VistaReporte;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('solo cuenta los retiros del período', () => {
    expect(reporte.retiros().map((r) => r.nombre)).toEqual(['Jorge Ríos', 'Carmen Peña', 'Elvia Mora']);

    fixture.componentRef.setInput('desde', null);
    expect(reporte.retiros().length).toBe(4);
  });

  it('calcula la tasa de retiro y separa las razones de la plataforma', () => {
    const [retiros, tasa, plataforma, principal] = reporte.tarjetas();

    expect(retiros.valor).toBe('3');
    // 3 de 19 personas (16 de hoy + 3 que se fueron)
    expect(tasa.valor).toBe('16 %');
    expect(tasa.tono).toBe('alerta');
    expect(plataforma.valor).toBe('1');
    expect(plataforma.tono).toBe('alerta');
    expect(principal.valor).toBe('Fallecimiento');
  });

  it('agrupa por razón, de la más común a la menos común, con su tipo', () => {
    expect(reporte.tablaRazones().filas).toEqual([
      ['Fallecimiento de la persona', 'Situación de la persona', 2],
      ['La plataforma es difícil de usar', 'Relacionadas con la plataforma', 1]
    ]);
  });

  it('muestra todos los meses del período, también los que no tuvieron retiros', () => {
    expect(reporte.tablaMeses().filas.map((f) => f[1])).toEqual([0, 1, 1, 1]);
  });
});
