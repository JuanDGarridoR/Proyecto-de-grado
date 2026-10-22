import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DonacionesAnalitica } from '../../../../../core/analitica/analitica.service';
import { DatoKpi } from '../componentes/kpi';
import { ReporteDonaciones } from './reporte-donaciones';

/**
 * Cinco donaciones: dos recibidas (una con dinero), una que no llegó, una
 * programada con la fecha vencida, una programada para después y una de
 * hace un año (fuera del período).
 */
function datos(): DonacionesAnalitica {
  const articulo = (categoria: string, descripcion: string, cantidad: number, unidad: string, recibida: number | null) =>
    ({ categoria, descripcion, cantidad, unidad, cantidadRecibida: recibida });
  return {
    donaciones: [
      { id: 6, fechaEntrega: '2026-11-05', estado: 'PROGRAMADA', rolDonante: 'VOLUNTARIO', donante: 'Laura',
        articulos: [articulo('ASEO', 'Jabón', 12, 'unidades', null)] },
      { id: 5, fechaEntrega: '2026-10-20', estado: 'PROGRAMADA', rolDonante: 'ACOMPANANTE', donante: 'Carmen',
        articulos: [articulo('ROPA', 'Cobijas', 3, 'unidades', null)] },
      { id: 4, fechaEntrega: '2026-10-10', estado: 'NO_ENTREGADA', rolDonante: 'VOLUNTARIO', donante: 'Laura',
        articulos: [articulo('ROPA', 'Chaquetas', 5, 'unidades', null)] },
      { id: 3, fechaEntrega: '2026-10-01', estado: 'RECIBIDA', rolDonante: 'ACOMPANANTE', donante: 'Carmen',
        articulos: [articulo('ALIMENTOS', 'Arroz', 10, 'kg', 8), articulo('DINERO', 'Aporte', 50000, 'COP', 50000)] },
      { id: 2, fechaEntrega: '2026-09-15', estado: 'RECIBIDA', rolDonante: 'VOLUNTARIO', donante: 'Laura',
        articulos: [articulo('ALIMENTOS', 'Panela', 4, 'kg', 4)] },
      { id: 1, fechaEntrega: '2025-08-01', estado: 'RECIBIDA', rolDonante: 'ACOMPANANTE', donante: 'Carmen',
        articulos: [articulo('DINERO', 'Aporte viejo', 999999, 'COP', 999999)] }
    ]
  };
}

/** Acceso a los computed protegidos del reporte. */
interface VistaReporte {
  donaciones(): { id: number }[];
  tarjetas(): DatoKpi[];
  porCategoria(): { nombre: string; articulos: number }[];
  porDonante(): { nombre: string; donaciones: number; recibidas: number }[];
  tablaMeses(): { columnas: string[]; filas: (string | number)[][] };
}

// Reporte de donaciones de la analítica: cuántas donaciones le programan a la
// organización en el período, cuántas llegan, qué se recibe y quién dona.
// La fecha se fija en el 22 de octubre de 2026.
describe('ReporteDonaciones', () => {
  let fixture: ComponentFixture<ReporteDonaciones>;
  let reporte: VistaReporte;

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 22, 12, 0));

    TestBed.configureTestingModule({ imports: [ReporteDonaciones] });
    TestBed.overrideComponent(ReporteDonaciones, { set: { template: '', imports: [] } });
    fixture = TestBed.createComponent(ReporteDonaciones);
    fixture.componentRef.setInput('datos', datos());
    fixture.componentRef.setInput('desde', '2026-07-24');
    reporte = fixture.componentInstance as unknown as VistaReporte;
  });

  afterEach(() => vi.useRealTimers());

  it('cuenta las donaciones del período por fecha de entrega, también las programadas para después', () => {
    expect(reporte.donaciones().map((d) => d.id)).toEqual([6, 5, 4, 3, 2]);
  });

  it('los indicadores muestran el cumplimiento, el dinero recibido y lo que está por recibir', () => {
    const [donaciones, cumplimiento, dinero, porRecibir] = reporte.tarjetas();

    expect(donaciones.valor).toBe('5');
    expect(donaciones.detalle).toBe('2 recibidas');
    // 2 recibidas de 3 que ya tenían respuesta (la cancelada y las programadas no cuentan)
    expect(cumplimiento.valor).toBe('67 %');
    expect(cumplimiento.tono).toBe('alerta');
    expect(dinero.valor.replace(/\s/g, ' ')).toBe('$ 50.000');
    expect(porRecibir.valor).toBe('2');
    expect(porRecibir.detalle).toBe('1 con la fecha de entrega vencida');
    expect(porRecibir.tono).toBe('alerta');
  });

  it('lo que se recibe se cuenta por categoría, solo de las donaciones recibidas', () => {
    expect(reporte.porCategoria()).toEqual([
      expect.objectContaining({ nombre: 'Alimentos', articulos: 2 }),
      expect.objectContaining({ nombre: 'Dinero', articulos: 1 })
    ]);
  });

  it('quién dona: primero quien más donaciones hizo llegar; si empatan, quien más programó', () => {
    expect(reporte.porDonante()).toEqual([
      expect.objectContaining({ nombre: 'Laura', donaciones: 3, recibidas: 1 }),
      expect.objectContaining({ nombre: 'Carmen', donaciones: 2, recibidas: 1 })
    ]);
  });

  it('por mes muestra todos los meses del período, también el de la entrega programada para noviembre', () => {
    const tabla = reporte.tablaMeses();

    expect(tabla.columnas).toEqual(['Mes', 'Recibidas', 'Por recibir', 'No llegaron', 'Canceladas', 'Total']);
    expect(tabla.filas.length).toBe(5);  // jul, ago, sep, oct y nov
    expect(tabla.filas[3].slice(1)).toEqual([1, 1, 1, 0, 3]);  // octubre
  });

  it('con "Todo" también entra la donación de hace un año', () => {
    fixture.componentRef.setInput('desde', null);

    expect(reporte.donaciones().length).toBe(6);
  });
});
