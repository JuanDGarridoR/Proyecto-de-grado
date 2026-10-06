import {
  ValoresSignos, camposImposibles, evaluarIndicador, indicadoresFueraDeRango, validarMedicion
} from './rangos';

const NORMAL: ValoresSignos = {
  presionSistolica: 120,
  presionDiastolica: 80,
  frecuenciaCardiaca: 72,
  temperatura: 36.6,
  saturacionOxigeno: 97
};

// Criterios con los que se marca "Normal" o "Revisar" un signo vital y se
// detectan valores imposibles (RF-15 y RF-16).
describe('rangos de signos vitales', () => {
  it('una medición dentro de los rangos no tiene indicadores por revisar', () => {
    expect(indicadoresFueraDeRango(NORMAL)).toEqual([]);
  });

  it('marca para revisar la presión alta, el pulso bajo y el oxígeno bajo', () => {
    const medicion = { ...NORMAL, presionSistolica: 150, frecuenciaCardiaca: 52, saturacionOxigeno: 92 };

    expect(indicadoresFueraDeRango(medicion)).toEqual(['presion', 'pulso', 'oxigeno']);
  });

  it('un indicador que no se midió no cuenta como normal ni como revisar', () => {
    const sinPresion = { ...NORMAL, presionSistolica: null, presionDiastolica: null };

    expect(evaluarIndicador(sinPresion, 'presion')).toBeNull();
  });

  it('respeta los extremos de cada rango', () => {
    expect(evaluarIndicador({ ...NORMAL, presionSistolica: 139 }, 'presion')).toBe(true);
    expect(evaluarIndicador({ ...NORMAL, presionSistolica: 140 }, 'presion')).toBe(false);
    expect(evaluarIndicador({ ...NORMAL, frecuenciaCardiaca: 60 }, 'pulso')).toBe(true);
    expect(evaluarIndicador({ ...NORMAL, frecuenciaCardiaca: 59 }, 'pulso')).toBe(false);
    expect(evaluarIndicador({ ...NORMAL, temperatura: 37.5 }, 'temperatura')).toBe(true);
    expect(evaluarIndicador({ ...NORMAL, temperatura: 37.6 }, 'temperatura')).toBe(false);
  });

  it('en el oxígeno solo cuenta el mínimo', () => {
    expect(evaluarIndicador({ ...NORMAL, saturacionOxigeno: 100 }, 'oxigeno')).toBe(true);
    expect(evaluarIndicador({ ...NORMAL, saturacionOxigeno: 95 }, 'oxigeno')).toBe(true);
    expect(evaluarIndicador({ ...NORMAL, saturacionOxigeno: 94 }, 'oxigeno')).toBe(false);
  });

  it('detecta los valores físicamente imposibles', () => {
    expect(camposImposibles({ presionSistolica: 300, saturacionOxigeno: 97, peso: 15 }))
      .toEqual(['presionSistolica', 'peso']);
    expect(camposImposibles({ presionSistolica: 120, temperatura: null })).toEqual([]);
  });
});

// Misma validación que hace salud-service antes de guardar una medición.
describe('validarMedicion', () => {
  it('acepta una medición normal', () => {
    expect(validarMedicion({ ...NORMAL, peso: 61, estatura: 158 })).toBeNull();
  });

  it('exige al menos un signo vital', () => {
    expect(validarMedicion({})).toBe('Ingresa al menos un signo vital.');
  });

  it('exige la presión completa y la sistólica mayor que la diastólica', () => {
    expect(validarMedicion({ presionSistolica: 120 })).toContain('se registra completa');
    expect(validarMedicion({ presionSistolica: 80, presionDiastolica: 90 })).toContain('debe ser más alta');
  });

  it('rechaza un valor por fuera de lo posible con un mensaje claro', () => {
    expect(validarMedicion({ saturacionOxigeno: 120 }))
      .toBe('Saturación de oxígeno: 120 % no es un valor posible. Debe estar entre 50 y 100 %.');
  });
});
