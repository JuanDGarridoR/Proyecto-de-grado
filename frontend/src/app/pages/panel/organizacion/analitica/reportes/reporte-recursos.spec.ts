import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DonacionesAnalitica, InventarioAnalitica } from '../../../../../core/analitica/analitica.service';
import { DatoKpi } from '../componentes/kpi';
import { ReporteRecursos } from './reporte-recursos';

/**
 * Seis donaciones: dos recibidas (una con dinero), una que no llegó, una
 * programada con la fecha vencida, una programada para después y una de
 * hace un año (fuera del período).
 */
function donaciones(): DonacionesAnalitica {
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

/**
 * Cinco artículos: arroz por agotarse, panela agotada, leche vencida,
 * jabón que vence pronto y dinero. Movimientos de este mes, de agosto y de
 * hace un año (fuera del período).
 */
function inventario(): InventarioAnalitica {
  return {
    articulos: [
      { id: 1, nombre: 'Arroz', categoria: 'ALIMENTOS', unidad: 'kg', cantidad: 2, cantidadMinima: 5, fechaVencimiento: null },
      { id: 2, nombre: 'Panela', categoria: 'ALIMENTOS', unidad: 'kg', cantidad: 0, cantidadMinima: 2, fechaVencimiento: null },
      { id: 3, nombre: 'Leche', categoria: 'ALIMENTOS', unidad: 'litros', cantidad: 6, cantidadMinima: null,
        fechaVencimiento: '2026-10-01' },
      { id: 4, nombre: 'Jabón', categoria: 'ASEO', unidad: 'unidades', cantidad: 10, cantidadMinima: 2,
        fechaVencimiento: '2026-11-10' },
      { id: 5, nombre: 'Dinero', categoria: 'DINERO', unidad: 'COP', cantidad: 120000, cantidadMinima: null,
        fechaVencimiento: null }
    ],
    movimientos: [
      { idArticulo: 1, tipo: 'ENTRADA', cantidad: 8, motivo: 'DONACION', fecha: '2025-09-01T10:00' },
      { idArticulo: 1, tipo: 'ENTRADA', cantidad: 8, motivo: 'DONACION', fecha: '2026-08-10T10:00' },
      { idArticulo: 2, tipo: 'ENTRADA', cantidad: 4, motivo: 'COMPRA', fecha: '2026-10-02T09:00' },
      { idArticulo: 1, tipo: 'SALIDA', cantidad: 6, motivo: 'ENTREGA', fecha: '2026-10-03T15:30' },
      { idArticulo: 2, tipo: 'SALIDA', cantidad: 4, motivo: 'ENTREGA', fecha: '2026-10-15T11:00' }
    ]
  };
}

/** Acceso a los computed protegidos del reporte. */
interface VistaReporte {
  donacionesPeriodo(): { id: number }[];
  movimientos(): unknown[];
  tarjetas(): DatoKpi[];
  porCategoriaRecibida(): { nombre: string; articulos: number }[];
  porDonante(): { nombre: string; donaciones: number; recibidas: number }[];
  conAvisos(): { articulo: { nombre: string }; avisos: string[] }[];
  tablaMeses(): { columnas: string[]; filas: (string | number)[][] };
  tablaMotivos(): { filas: (string | number)[][] };
  tablaExistencias(): { columnas: string[]; filas: (string | number)[][] };
}

// Reporte de donaciones e inventario de la analítica: lo que le llega a la
// organización (donaciones del período) y cómo está lo que tiene
// (inventario de hoy). La fecha se fija en el 22 de octubre de 2026.
describe('ReporteRecursos', () => {
  let fixture: ComponentFixture<ReporteRecursos>;
  let reporte: VistaReporte;

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 22, 12, 0));

    TestBed.configureTestingModule({ imports: [ReporteRecursos] });
    TestBed.overrideComponent(ReporteRecursos, { set: { template: '', imports: [] } });
    fixture = TestBed.createComponent(ReporteRecursos);
    fixture.componentRef.setInput('donaciones', donaciones());
    fixture.componentRef.setInput('inventario', inventario());
    fixture.componentRef.setInput('desde', '2026-07-24');
    reporte = fixture.componentInstance as unknown as VistaReporte;
  });

  afterEach(() => vi.useRealTimers());

  it('el período filtra las donaciones (por fecha de entrega, también las programadas para después) y los movimientos', () => {
    expect(reporte.donacionesPeriodo().map((d) => d.id)).toEqual([6, 5, 4, 3, 2]);
    expect(reporte.movimientos().length).toBe(4);
  });

  it('con "Todo" también entran la donación y el movimiento de hace un año', () => {
    fixture.componentRef.setInput('desde', null);

    expect(reporte.donacionesPeriodo().length).toBe(6);
    expect(reporte.movimientos().length).toBe(5);
  });

  it('los indicadores juntan las donaciones del período con el inventario de hoy', () => {
    const [llegaron, porRecibir, agotandose, venciendose, dinero] = reporte.tarjetas();

    // 2 recibidas de 3 que ya tenían respuesta (las programadas no cuentan)
    expect(llegaron.valor).toBe('67 %');
    expect(llegaron.detalle).toBe('2 de 3 llegaron');
    expect(llegaron.tono).toBe('alerta');
    expect(porRecibir.valor).toBe('2');
    expect(porRecibir.detalle).toBe('1 con la fecha de entrega vencida');
    expect(porRecibir.tono).toBe('alerta');
    expect(agotandose.valor).toBe('2');
    expect(agotandose.detalle).toBe('1 agotado de 5 artículos');
    expect(venciendose.valor).toBe('2');
    expect(venciendose.detalle).toBe('1 vencido');
    expect(dinero.valor.replace(/\s/g, ' ')).toBe('$ 120.000');
    expect(dinero.detalle.replace(/\s/g, ' ')).toBe('$ 50.000 recibido en donaciones del período');
  });

  it('lo que se recibe se cuenta por categoría, solo de las donaciones recibidas', () => {
    expect(reporte.porCategoriaRecibida()).toEqual([
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

  it('los motivos de las entradas y salidas se cuentan con su texto de la aplicación', () => {
    expect(reporte.tablaMotivos().filas).toEqual([
      ['Entregado a personas mayores', 0, 2],
      ['Compra', 1, 0],
      ['Donación recibida', 1, 0]
    ]);
  });

  it('las existencias por categoría separan lo normal, lo que se agota y lo agotado', () => {
    const tabla = reporte.tablaExistencias();

    expect(tabla.columnas).toEqual(['Categoría', 'Con existencias', 'Por agotarse', 'Agotados', 'Total']);
    expect(tabla.filas).toEqual([
      ['Alimentos', 1, 1, 1, 3],
      ['Dinero', 1, 0, 0, 1],
      ['Aseo e higiene', 1, 0, 0, 1]
    ]);
  });

  it('la lista de atención pone primero lo agotado y lo vencido', () => {
    expect(reporte.conAvisos().map((x) => [x.articulo.nombre, x.avisos])).toEqual([
      ['Panela', ['agotado']],
      ['Leche', ['vencido']],
      ['Arroz', ['por-agotarse']],
      ['Jabón', ['vence-pronto']]
    ]);
  });
});
