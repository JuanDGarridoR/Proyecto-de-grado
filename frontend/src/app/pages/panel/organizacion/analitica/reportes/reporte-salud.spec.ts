import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MedicionAnalitica, SaludAnalitica } from '../../../../../core/analitica/analitica.service';
import { ReporteSalud } from './reporte-salud';

const DIA = 24 * 60 * 60 * 1000;

/** Fecha y hora de hace unos días, en el formato que envía el backend (YYYY-MM-DDTHH:mm). */
function haceDias(dias: number): string {
  return new Date(Date.now() - dias * DIA).toISOString().slice(0, 16);
}

function medicion(idPersonaMayor: number, dias: number, valores: Partial<MedicionAnalitica>): MedicionAnalitica {
  return {
    idPersonaMayor,
    fechaHora: haceDias(dias),
    presionSistolica: null,
    presionDiastolica: null,
    frecuenciaCardiaca: null,
    temperatura: null,
    saturacionOxigeno: null,
    frecuenciaRespiratoria: null,
    peso: null,
    ...valores
  };
}

const NORMAL = { presionSistolica: 120, presionDiastolica: 80, frecuenciaCardiaca: 70, saturacionOxigeno: 97 };

/** Rosa tiene la presión alta, Luis está bien, a Marta no la miden hace 45 días y a Pedro nunca. */
function datos(): SaludAnalitica {
  return {
    personas: [
      { idUsuario: 10, nombre: 'Rosa Díaz' },
      { idUsuario: 11, nombre: 'Luis Gómez' },
      { idUsuario: 12, nombre: 'Marta Ruiz' },
      { idUsuario: 13, nombre: 'Pedro León' }
    ],
    // Ordenadas por fecha, como las devuelve analitica-service.
    mediciones: [
      medicion(12, 45, NORMAL),
      medicion(10, 40, NORMAL),
      medicion(11, 3, NORMAL),
      medicion(10, 2, { presionSistolica: 150, presionDiastolica: 95 })
    ]
  };
}

interface PersonaAtencion {
  nombre: string;
  motivos: string[];
  indicadores: string[];
}

/** Acceso a los computed protegidos del reporte. */
interface VistaReporte {
  atencion(): PersonaAtencion[];
  atencionFiltrada(): PersonaAtencion[];
  kpis(): { total: number; conSeguimiento: number; mediciones: number; fueraDeRango: number; sinReciente: number; errores: number };
  filtroIndicador: { set(valor: string | null): void };
  personaActual(): number | null;
  serieEvolucion(): MedicionAnalitica[];
  tablaTiempo(): { columnas: string[]; filas: (string | number)[][] };
}

// Reporte de salud de la analítica (RF-15, RF-16 y RF-19): quién requiere
// atención, los indicadores y los filtros. Solo se prueba la lógica; las
// gráficas no se dibujan.
describe('ReporteSalud', () => {
  let fixture: ComponentFixture<ReporteSalud>;
  let reporte: VistaReporte;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ReporteSalud] });
    TestBed.overrideComponent(ReporteSalud, { set: { template: '', imports: [] } });

    fixture = TestBed.createComponent(ReporteSalud);
    fixture.componentRef.setInput('datos', datos());
    reporte = fixture.componentInstance as unknown as VistaReporte;
  });

  it('prioriza a las personas que requieren atención y explica por qué', () => {
    const atencion = reporte.atencion();

    // Primero los valores fuera de rango; luego, por nombre.
    expect(atencion.map((p) => p.nombre)).toEqual(['Rosa Díaz', 'Marta Ruiz', 'Pedro León']);
    expect(atencion[0].indicadores).toEqual(['presion']);
    expect(atencion[0].motivos).toEqual(['Presión: 150/95 mmHg']);
    expect(atencion[1].motivos[0]).toMatch(/^Sin medición hace 4[45] días$/);
    expect(atencion[2].motivos).toEqual(['Nunca se le han medido signos vitales']);
  });

  it('calcula los indicadores del reporte', () => {
    expect(reporte.kpis()).toMatchObject({
      total: 4,
      conSeguimiento: 3,
      mediciones: 4,
      fueraDeRango: 1,
      sinReciente: 2,
      errores: 0
    });
  });

  it('el período deja por fuera las mediciones anteriores a la fecha de inicio', () => {
    fixture.componentRef.setInput('desde', haceDias(30).slice(0, 10));

    expect(reporte.kpis()).toMatchObject({ total: 4, conSeguimiento: 2, mediciones: 2 });
  });

  it('el filtro por indicador deja solo a quien lo tiene fuera de rango', () => {
    reporte.filtroIndicador.set('presion');
    expect(reporte.atencionFiltrada().map((p) => p.nombre)).toEqual(['Rosa Díaz']);

    reporte.filtroIndicador.set('oxigeno');
    expect(reporte.atencionFiltrada()).toEqual([]);

    // Sin filtro vuelven a aparecer todos.
    reporte.filtroIndicador.set(null);
    expect(reporte.atencionFiltrada().length).toBe(3);
  });

  it('una medición nueva dentro del rango saca a la persona de la lista', () => {
    const conMedicionNueva = datos();
    conMedicionNueva.mediciones.push(medicion(10, 0, NORMAL));

    fixture.componentRef.setInput('datos', conMedicionNueva);

    expect(reporte.atencion().map((p) => p.nombre)).toEqual(['Marta Ruiz', 'Pedro León']);
    expect(reporte.kpis().fueraDeRango).toBe(0);
  });

  it('la evolución muestra primero a quien tiene valores fuera de rango, en orden de fecha', () => {
    expect(reporte.personaActual()).toBe(10);
    expect(reporte.serieEvolucion().map((m) => m.presionSistolica)).toEqual([120, 150]);

    // Con un período de 30 días queda solo la medición reciente.
    fixture.componentRef.setInput('desde', haceDias(30).slice(0, 10));
    expect(reporte.serieEvolucion().map((m) => m.presionSistolica)).toEqual([150]);
  });

  it('las mediciones por semana cuentan todas las del período y se actualizan con las nuevas', () => {
    fixture.componentRef.setInput('desde', haceDias(30).slice(0, 10));
    const totalPorSemanas = () => reporte.tablaTiempo().filas.reduce((suma, fila) => suma + Number(fila[1]), 0);

    expect(reporte.tablaTiempo().columnas).toEqual(['Semana del', 'Mediciones']);
    expect(totalPorSemanas()).toBe(2);

    const conMedicionNueva = datos();
    conMedicionNueva.mediciones.push(medicion(13, 0, NORMAL));
    fixture.componentRef.setInput('datos', conMedicionNueva);

    expect(totalPorSemanas()).toBe(3);
  });

  it('un valor imposible se marca como posible error de registro', () => {
    const conError = datos();
    conError.mediciones.push(medicion(11, 0, { saturacionOxigeno: 30 }));

    fixture.componentRef.setInput('datos', conError);

    const luis = reporte.atencion().find((p) => p.nombre === 'Luis Gómez');
    expect(luis?.motivos).toContain('1 medición con valores imposibles (posible error de registro)');
    expect(reporte.kpis().errores).toBe(1);
  });
});
