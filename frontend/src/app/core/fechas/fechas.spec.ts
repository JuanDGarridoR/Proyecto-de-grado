import { diasDesde, fechaLocal, haceDias, proximoCumpleanos } from './fechas';
import { iniciales, mensajeDeError } from '../formato/formato';

// 7 de octubre de 2026, 1:00 p. m. (hora local).
const AHORA = new Date(2026, 9, 7, 13, 0);

describe('utilidades de fechas', () => {
  it('fechaLocal usa la fecha local, no la UTC', () => {
    expect(fechaLocal(new Date(2026, 9, 7, 23, 30))).toBe('2026-10-07');
  });

  it('cuenta días de calendario y los dice en palabras', () => {
    expect(diasDesde('2026-10-07T06:00', AHORA)).toBe(0);
    expect(haceDias('2026-10-06T23:59', AHORA)).toBe('ayer');
    expect(haceDias('2026-10-02T08:00', AHORA)).toBe('hace 5 días');
  });

  it('calcula el próximo cumpleaños y la edad que se cumple', () => {
    expect(proximoCumpleanos('1950-10-07', AHORA)).toEqual({ dias: 0, edad: 76, fecha: '2026-10-07' });
    expect(proximoCumpleanos('1950-10-10', AHORA)).toEqual({ dias: 3, edad: 76, fecha: '2026-10-10' });
    // Ya pasó este año: el próximo es el del año que viene.
    expect(proximoCumpleanos('1950-01-15', AHORA).fecha).toBe('2027-01-15');
    expect(proximoCumpleanos('1950-01-15', AHORA).edad).toBe(77);
  });
});

describe('utilidades de formato', () => {
  it('iniciales toma las dos primeras palabras', () => {
    expect(iniciales('  rosa elvira gómez ')).toBe('RE');
  });

  it('mensajeDeError usa el texto del backend o el mensaje por defecto', () => {
    expect(mensajeDeError({ error: 'Ya enviaste una solicitud' }, 'Falló')).toBe('Ya enviaste una solicitud');
    expect(mensajeDeError({ error: { status: 500 } }, 'Falló')).toBe('Falló');
    expect(mensajeDeError(null, 'Falló')).toBe('Falló');
  });
});
