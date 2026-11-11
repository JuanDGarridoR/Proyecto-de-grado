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
    estatura: null,
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
  critico: boolean;
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
  tablaImc(): { columnas: string[]; filas: (string | number)[][] };
}

// Reporte de salud de la analítica (RF-46): quién requiere
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
    // El motivo dice el indicador, el valor y el rango esperado.
    expect(atencion[0].motivos).toEqual(['Presión 150/95 mmHg (esperado 91–139 / < 90 mmHg)']);
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
  it('primero quien tiene un valor crítico, aunque tenga menos indicadores fuera de rango', () => {
    const conCritico = datos();
    // Rosa: presión y pulso fuera de rango (2 indicadores, ninguno crítico).
    conCritico.mediciones.push(medicion(10, 0, { presionSistolica: 150, presionDiastolica: 95, frecuenciaCardiaca: 105 }));
    // Luis: solo la saturación, pero en 91 % (crítica).
    conCritico.mediciones.push(medicion(11, 0, { ...NORMAL, saturacionOxigeno: 91 }));
    fixture.componentRef.setInput('datos', conCritico);

    const atencion = reporte.atencion();
    expect(atencion.map((p) => p.nombre).slice(0, 2)).toEqual(['Luis Gómez', 'Rosa Díaz']);
    expect(atencion[0].critico).toBe(true);
    expect(atencion[0].motivos).toEqual(['Saturación 91 % (esperado ≥ 94 %) · valor crítico']);
    expect(atencion[1].critico).toBe(false);
    expect(atencion[1].indicadores).toEqual(['presion', 'pulso']);
  });

  it('con EPOC la saturación se evalúa contra 88–92 %', () => {
    const conEpoc = datos();
    conEpoc.personas[1] = { idUsuario: 11, nombre: 'Luis Gómez', tieneEpoc: true };
    // 90 % es normal para Luis (EPOC); 97 % está por encima de su objetivo.
    conEpoc.mediciones.push(medicion(11, 0, { ...NORMAL, saturacionOxigeno: 90 }));
    fixture.componentRef.setInput('datos', conEpoc);
    expect(reporte.atencion().map((p) => p.nombre)).not.toContain('Luis Gómez');

    conEpoc.mediciones.push(medicion(11, 0, { ...NORMAL, saturacionOxigeno: 97 }));
    fixture.componentRef.setInput('datos', { ...conEpoc, mediciones: [...conEpoc.mediciones] });
    const luis = reporte.atencion().find((p) => p.nombre === 'Luis Gómez');
    expect(luis?.motivos).toEqual(['Saturación 97 % (esperado 88–92 % por EPOC)']);
  });

  it('la frecuencia respiratoria fuera de rango entra en la lista con su motivo', () => {
    const conRespiracion = datos();
    conRespiracion.mediciones.push(medicion(11, 0, { ...NORMAL, frecuenciaRespiratoria: 22 }));
    fixture.componentRef.setInput('datos', conRespiracion);

    const luis = reporte.atencion().find((p) => p.nombre === 'Luis Gómez');
    expect(luis?.indicadores).toEqual(['respiracion']);
    expect(luis?.motivos).toEqual(['Frecuencia respiratoria 22 rpm (esperado 12–20 rpm)']);

    reporte.filtroIndicador.set('respiracion');
    expect(reporte.atencionFiltrada().map((p) => p.nombre)).toEqual(['Luis Gómez']);
  });

  it('una frecuencia respiratoria de 25 o más es crítica', () => {
    const conRespiracion = datos();
    conRespiracion.mediciones.push(medicion(11, 0, { ...NORMAL, frecuenciaRespiratoria: 25 }));
    fixture.componentRef.setInput('datos', conRespiracion);

    expect(reporte.atencion()[0]).toMatchObject({ nombre: 'Luis Gómez', critico: true });
  });

  // RF-70: IMC más reciente de cada persona, con los puntos de corte para personas mayores.
  it('agrupa a las personas por su IMC más reciente y no usa pesos imposibles', () => {
    const conPeso = datos();
    conPeso.mediciones = [
      // Marta: peso imposible (error de registro), no cuenta.
      medicion(12, 45, { ...NORMAL, peso: 300, estatura: 150 }),
      // Rosa: 80 kg y 155 cm → IMC 33,3 (obesidad). Su última medición no trae peso.
      medicion(10, 40, { ...NORMAL, peso: 80, estatura: 155 }),
      // Luis: 70 kg y 170 cm → IMC 24,2 (normal en persona mayor: más de 23 y menos de 28).
      medicion(11, 3, { ...NORMAL, peso: 70, estatura: 170 }),
      medicion(10, 2, { presionSistolica: 150, presionDiastolica: 95 })
    ];
    fixture.componentRef.setInput('datos', conPeso);

    expect(reporte.tablaImc().filas).toEqual([
      ['Delgadez', '23,0 o menos', 0, '0 %'],
      ['Normal', 'Más de 23 y menos de 28', 1, '25 %'],
      ['Sobrepeso', '28 a menos de 32', 0, '0 %'],
      ['Obesidad', '32 o más', 1, '25 %'],
      // Marta (peso imposible) y Pedro (sin mediciones)
      ['Sin peso o estatura', '—', 2, '50 %']
    ]);
  });
});
