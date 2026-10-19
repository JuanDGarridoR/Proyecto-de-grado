import { errorHorario } from './actividad.service';

// Horario de una actividad: las mismas reglas que valida actividad-service.
describe('errorHorario', () => {
  it('acepta una actividad sin horario o con la hora de fin después del inicio', () => {
    expect(errorHorario(null, null)).toBeNull();
    expect(errorHorario('09:00', '10:30')).toBeNull();
  });

  it('pide la hora que falta', () => {
    expect(errorHorario('09:00', null)).toBe('Indica a qué hora termina la actividad');
    expect(errorHorario(null, '10:00')).toBe('Indica a qué hora empieza la actividad');
  });

  it('no deja que termine antes de empezar o a la misma hora', () => {
    expect(errorHorario('10:00', '09:00')).toBe('La hora de fin debe ser posterior a la hora de inicio');
    expect(errorHorario('10:00', '10:00')).toBe('La hora de fin debe ser posterior a la hora de inicio');
  });
});
