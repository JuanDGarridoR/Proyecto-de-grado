import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ActividadAnalitica } from '../../../../../core/analitica/analitica.service';
import { ReporteActividades } from './reporte-actividades';

/** Las dos actividades de septiembre que devuelve analitica-service en sus pruebas. */
function actividades(): ActividadAnalitica[] {
  return [
    { idActividad: 100, nombre: 'Yoga en el parque', tipo: 'Recreativa', fecha: '2026-09-10', cupos: 20, inscritos: 3, asistentes: 1, conRegistro: 2 },
    { idActividad: 101, nombre: 'Taller de memoria', tipo: 'Cognitiva', fecha: '2026-09-25', cupos: 10, inscritos: 1, asistentes: 1, conRegistro: 1 }
  ];
}

/** Acceso a los computed protegidos del reporte. */
interface VistaReporte {
  kpis(): Record<string, string | number>;
  tablaMeses(): { columnas: string[]; filas: (string | number)[][] };
  tablaTipos(): { columnas: string[]; filas: (string | number)[][] };
}

// Reporte de actividades de la analítica (RF-19, RF-21 y RF-33): indicadores
// de participación y las tablas que acompañan a las gráficas.
describe('ReporteActividades', () => {
  let fixture: ComponentFixture<ReporteActividades>;
  let reporte: VistaReporte;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ReporteActividades] });
    TestBed.overrideComponent(ReporteActividades, { set: { template: '', imports: [] } });

    fixture = TestBed.createComponent(ReporteActividades);
    fixture.componentRef.setInput('actividades', actividades());
    reporte = fixture.componentInstance as unknown as VistaReporte;
  });

  it('calcula inscritos, asistencia y ocupación de cupos', () => {
    expect(reporte.kpis()).toMatchObject({
      actividades: 2,
      inscritos: 4,
      promedio: 2,
      asistencia: '67 %',
      detalleAsistencia: '2 de 3 con asistencia tomada',
      ocupacion: '13 %',
      detalleOcupacion: '4 de 30 cupos ocupados'
    });
  });

  it('las tablas de las gráficas corresponden a los datos', () => {
    expect(reporte.tablaMeses().filas).toEqual([['sep 2026', 2, 4, 2]]);
    expect(reporte.tablaTipos().filas).toEqual([['Recreativa', 1, 3], ['Cognitiva', 1, 1]]);
  });

  it('al registrar más asistencia los indicadores se actualizan', () => {
    const conAsistencia = actividades();
    conAsistencia[0] = { ...conAsistencia[0], asistentes: 2, conRegistro: 3 };

    fixture.componentRef.setInput('actividades', conAsistencia);

    expect(reporte.kpis()['asistencia']).toBe('75 %');
    expect(reporte.kpis()['detalleAsistencia']).toBe('3 de 4 con asistencia tomada');
  });

  it('sin asistencia tomada no inventa un porcentaje', () => {
    fixture.componentRef.setInput('actividades', actividades().map((a) => ({ ...a, asistentes: 0, conRegistro: 0 })));

    expect(reporte.kpis()['asistencia']).toBe('—');
    expect(reporte.kpis()['detalleAsistencia']).toBe('Aún no se ha tomado asistencia');
  });
});
