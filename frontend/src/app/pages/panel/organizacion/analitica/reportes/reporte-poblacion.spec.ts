import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PoblacionAnalitica } from '../../../../../core/analitica/analitica.service';
import { ReportePoblacion } from './reporte-poblacion';

/** Cuatro personas: una sin fecha de nacimiento, una sin género y una sin EPS. */
function poblacion(): PoblacionAnalitica {
  return {
    personas: [
      { idUsuario: 10, nombre: 'Rosa Díaz', fechaNacimiento: '1950-03-14', genero: 'Femenino', eps: 'Capital Salud' },
      { idUsuario: 11, nombre: 'Luis Gómez', fechaNacimiento: '1944-07-01', genero: 'Masculino', eps: 'Capital Salud' },
      { idUsuario: 12, nombre: 'Marta Ruiz', fechaNacimiento: '1962-07-08', genero: 'Femenino', eps: 'Famisanar' },
      { idUsuario: 13, nombre: 'Oliva', fechaNacimiento: null, genero: null, eps: null }
    ],
    intereses: [
      { nombre: 'Baile', categoria: 'HOBBY', personas: 2 },
      { nombre: 'Cocinar', categoria: 'TALENTO', personas: 1 },
      { nombre: 'Escuchar música', categoria: 'GUSTO', personas: 1 }
    ],
    personasConIntereses: 2,
    acompanantesActivos: 3
  } as PoblacionAnalitica;
}

/** Acceso a los computed protegidos del reporte. */
interface VistaReporte {
  kpis(): Record<string, string | number>;
  tablaEdad(): { filas: (string | number)[][] };
  tablaGenero(): { filas: (string | number)[][] };
  tablaEps(): { filas: (string | number)[][] };
  tablaIntereses(): { filas: (string | number)[][] };
}

// Reporte de población de la analítica (RF-47): rangos de edad, género, EPS e
// intereses más comunes de las personas mayores de la organización. La fecha
// se fija en el 13 de octubre de 2026 para que las edades no cambien.
describe('ReportePoblacion', () => {
  let fixture: ComponentFixture<ReportePoblacion>;
  let reporte: VistaReporte;

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 13, 12, 0));

    TestBed.configureTestingModule({ imports: [ReportePoblacion] });
    TestBed.overrideComponent(ReportePoblacion, { set: { template: '', imports: [] } });
    fixture = TestBed.createComponent(ReportePoblacion);
    fixture.componentRef.setInput('datos', poblacion());
    reporte = fixture.componentInstance as unknown as VistaReporte;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('calcula la edad promedio y cuántos tienen EPS e intereses registrados', () => {
    expect(reporte.kpis()).toMatchObject({
      total: 4,
      // 76, 82 y 64 años
      edadPromedio: '74 años',
      detalleEdad: '1 sin fecha de nacimiento',
      conEps: '75 %',
      detalleEps: '3 de 4 tienen EPS registrada',
      conIntereses: '50 %'
    });
  });

  it('agrupa a las personas por rango de edad, género y EPS', () => {
    expect(reporte.tablaEdad().filas).toEqual([
      ['Menos de 60', 0], ['60 a 69', 1], ['70 a 79', 1], ['80 a 89', 1], ['90 o más', 0]
    ]);
    expect(reporte.tablaGenero().filas).toEqual([
      ['Femenino', 2, '50 %'], ['Masculino', 1, '25 %'], ['Sin dato', 1, '25 %']
    ]);
    expect(reporte.tablaEps().filas).toEqual([
      ['Capital Salud', 2], ['Famisanar', 1], ['Sin EPS registrada', 1]
    ]);
  });

  it('muestra los intereses más comunes con el nombre de su categoría', () => {
    expect(reporte.tablaIntereses().filas).toEqual([
      ['Baile', 'Hobbies', 2], ['Cocinar', 'Talentos', 1], ['Escuchar música', 'Gustos', 1]
    ]);
  });

  it('con más de ocho EPS deja las siete más comunes y agrupa el resto en «Otras»', () => {
    const muchas = poblacion();
    muchas.personas = Array.from({ length: 10 }, (_, i) => ({
      idUsuario: 100 + i, nombre: `Persona ${i}`, fechaNacimiento: null, genero: null, eps: `EPS ${i}`
    })) as PoblacionAnalitica['personas'];
    fixture.componentRef.setInput('datos', muchas);

    const filas = reporte.tablaEps().filas;
    expect(filas).toHaveLength(8);
    expect(filas[7]).toEqual(['Otras', 3]);
  });
});
