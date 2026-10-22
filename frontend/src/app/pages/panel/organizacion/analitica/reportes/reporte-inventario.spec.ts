import { ComponentFixture, TestBed } from '@angular/core/testing';

import { InventarioAnalitica } from '../../../../../core/analitica/analitica.service';
import { DatoKpi } from '../componentes/kpi';
import { ReporteInventario } from './reporte-inventario';

/**
 * Cinco artículos: arroz por agotarse, panela agotada, leche vencida,
 * jabón que vence pronto y dinero. Movimientos de este mes, de agosto y de
 * hace un año (fuera del período).
 */
function datos(): InventarioAnalitica {
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
  movimientos(): unknown[];
  tarjetas(): DatoKpi[];
  conAvisos(): { articulo: { nombre: string }; avisos: string[] }[];
  tablaMeses(): { filas: (string | number)[][] };
  tablaMotivos(): { filas: (string | number)[][] };
  tablaCategorias(): { columnas: string[]; filas: (string | number)[][] };
}

// Reporte de inventario de la analítica: qué se agota o se vence, el dinero
// disponible y las entradas y salidas del período. La fecha se fija en el
// 22 de octubre de 2026.
describe('ReporteInventario', () => {
  let fixture: ComponentFixture<ReporteInventario>;
  let reporte: VistaReporte;

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 22, 12, 0));

    TestBed.configureTestingModule({ imports: [ReporteInventario] });
    TestBed.overrideComponent(ReporteInventario, { set: { template: '', imports: [] } });
    fixture = TestBed.createComponent(ReporteInventario);
    fixture.componentRef.setInput('datos', datos());
    fixture.componentRef.setInput('desde', '2026-07-24');
    reporte = fixture.componentInstance as unknown as VistaReporte;
  });

  afterEach(() => vi.useRealTimers());

  it('los indicadores muestran el estado de hoy con las mismas reglas que la página de Inventario', () => {
    const [articulos, agotandose, venciendose, dinero] = reporte.tarjetas();

    expect(articulos.valor).toBe('5');
    expect(articulos.detalle).toBe('En 3 categorías');
    expect(agotandose.valor).toBe('2');
    expect(agotandose.detalle).toBe('1 agotado');
    expect(agotandose.tono).toBe('alerta');
    expect(venciendose.valor).toBe('2');
    expect(venciendose.detalle).toBe('1 vencido');
    expect(dinero.valor.replace(/\s/g, ' ')).toBe('$ 120.000');
  });

  it('la lista de atención pone primero lo agotado y lo vencido', () => {
    expect(reporte.conAvisos().map((x) => [x.articulo.nombre, x.avisos])).toEqual([
      ['Panela', ['agotado']],
      ['Leche', ['vencido']],
      ['Arroz', ['por-agotarse']],
      ['Jabón', ['vence-pronto']]
    ]);
  });

  it('el período solo filtra los movimientos', () => {
    expect(reporte.movimientos().length).toBe(4);
  });

  it('entradas y salidas se cuentan por mes, también los meses sin movimientos', () => {
    expect(reporte.tablaMeses().filas).toEqual([
      ['jul 2026', 0, 0],
      ['ago 2026', 1, 0],
      ['sept 2026', 0, 0],
      ['oct 2026', 1, 2]
    ].map(([mes, e, s]) => [expect.stringMatching(new RegExp(`^${String(mes).slice(0, 3)}`)), e, s]));
  });

  it('los motivos se cuentan con su texto de la aplicación', () => {
    expect(reporte.tablaMotivos().filas).toEqual([
      ['Entregado a personas mayores', 0, 2],
      ['Compra', 1, 0],
      ['Donación recibida', 1, 0]
    ]);
  });

  it('las existencias por categoría separan lo normal, lo que se agota y lo agotado', () => {
    const tabla = reporte.tablaCategorias();

    expect(tabla.columnas).toEqual(['Categoría', 'Con existencias', 'Por agotarse', 'Agotados', 'Total']);
    expect(tabla.filas).toEqual([
      ['Alimentos', 1, 1, 1, 3],
      ['Dinero', 1, 0, 0, 1],
      ['Aseo e higiene', 1, 0, 0, 1]
    ]);
  });
});
