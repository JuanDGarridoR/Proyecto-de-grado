import { TestBed } from '@angular/core/testing';

import { HojaFecha } from './hoja-fecha';

// Hoja de calendario compartida (citas, seguimiento y actividades).
describe('HojaFecha', () => {
  function partes(fecha: string | null): { mes: string; dia: string } {
    const fixture = TestBed.createComponent(HojaFecha);
    fixture.componentRef.setInput('fecha', fecha);
    return (fixture.componentInstance as any).partes();
  }

  it('muestra el mes abreviado sin punto y el día, sin correr la fecha por la zona horaria', () => {
    // "2026-10-01" leído como UTC daría el 30 de septiembre en Colombia.
    expect(partes('2026-10-01')).toEqual({ mes: 'oct', dia: '1' });
    expect(partes('2026-10-21T09:30')).toEqual({ mes: 'oct', dia: '21' });
  });

  it('sin fecha muestra un guion', () => {
    expect(partes(null)).toEqual({ mes: '—', dia: '—' });
  });
});
