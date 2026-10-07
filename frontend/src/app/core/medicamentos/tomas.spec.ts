import { tomasDeHoy } from './medicamento.service';

// 7 de octubre de 2026, 1:00 p. m. (hora local).
const AHORA = new Date(2026, 9, 7, 13, 0);

const BASE = {
  idMedicamento: 1,
  activo: true,
  fechaFin: null,
  intervaloHoras: 8,
  proximaToma: null as string | null,
  ultimaToma: null as string | null
};

// Tomas de hoy con las que se arma la agenda en el inicio de la persona
// mayor y del acompañante.
describe('tomasDeHoy', () => {
  it('reparte las tomas del día según el intervalo y marca las que ya pasaron', () => {
    const tomas = tomasDeHoy({ ...BASE, proximaToma: '2026-10-07T06:00' }, AHORA);

    expect(tomas.map((t) => [t.momento.getHours(), t.estado])).toEqual([
      [6, 'atrasado'],
      [14, 'pendiente'],
      [22, 'pendiente']
    ]);
  });

  it('incluye la toma ya hecha hoy', () => {
    const tomas = tomasDeHoy(
      { ...BASE, ultimaToma: '2026-10-07T08:05', proximaToma: '2026-10-07T16:00' },
      AHORA
    );

    expect(tomas.map((t) => t.estado)).toEqual(['hecho', 'pendiente']);
  });

  it('una toma pendiente desde ayer aparece una sola vez, atrasada', () => {
    const tomas = tomasDeHoy({ ...BASE, proximaToma: '2026-10-06T22:00' }, AHORA);

    expect(tomas.length).toBe(1);
    expect(tomas[0].estado).toBe('atrasado');
  });

  it('no devuelve tomas de medicamentos inactivos o ya terminados', () => {
    expect(tomasDeHoy({ ...BASE, activo: false, proximaToma: '2026-10-07T14:00' }, AHORA)).toEqual([]);
    expect(tomasDeHoy({ ...BASE, fechaFin: '2026-10-06', proximaToma: '2026-10-07T14:00' }, AHORA)).toEqual([]);
  });
});
