import {
  Gravedad, Indicador, ValoresSignos, camposImposibles, categoriaImc, clasificarPresion, estadoIndicador,
  evaluarIndicador, franjaNormal, gravedadIndicador, gravedadMaxima, indicadoresFueraDeRango, motivoFueraDeRango,
  validarMedicion
} from './rangos';

const NORMAL: ValoresSignos = {
  presionSistolica: 118,
  presionDiastolica: 76,
  frecuenciaCardiaca: 72,
  temperatura: 36.6,
  saturacionOxigeno: 97,
  frecuenciaRespiratoria: 16
};

const CAMPO: Record<Exclude<Indicador, 'presion'>, keyof ValoresSignos> = {
  pulso: 'frecuenciaCardiaca',
  temperatura: 'temperatura',
  oxigeno: 'saturacionOxigeno',
  respiracion: 'frecuenciaRespiratoria'
};

/** Gravedad de un solo valor del indicador, sobre una medición normal. */
function gravedad(indicador: Exclude<Indicador, 'presion'>, valor: number, epoc = false): Gravedad | null {
  return gravedadIndicador({ ...NORMAL, [CAMPO[indicador]]: valor }, indicador, { epoc });
}

// Rangos clínicos de las personas mayores (los mismos de RangosClinicos en
// analitica-service, que prueba RangosClinicosTest con estos valores). Cada
// límite se prueba justo dentro y justo fuera.
describe('rangos de signos vitales', () => {
  it('una medición dentro de los rangos no tiene indicadores por revisar', () => {
    expect(indicadoresFueraDeRango(NORMAL)).toEqual([]);
    expect(gravedadMaxima(NORMAL)).toBe('normal');
  });

  it('un indicador que no se midió no cuenta como normal ni como revisar', () => {
    expect(evaluarIndicador({ ...NORMAL, presionSistolica: null, presionDiastolica: null }, 'presion')).toBeNull();
    expect(evaluarIndicador({ ...NORMAL, frecuenciaRespiratoria: null }, 'respiracion')).toBeNull();
    // Mediciones que no traen el campo de frecuencia respiratoria
    const { frecuenciaRespiratoria: _, ...sinCampo } = NORMAL;
    expect(evaluarIndicador(sinCampo, 'respiracion')).toBeNull();
  });

  describe('presión (fuera si sistólica ≥ 140, diastólica ≥ 90 o sistólica ≤ 90)', () => {
    it.each([
      [139, 70, true], [140, 70, false],
      [91, 60, true], [90, 60, false],
      [130, 89, true], [130, 90, false]
    ])('%i/%i mmHg → dentro: %s', (sis, dia, dentro) => {
      expect(evaluarIndicador({ ...NORMAL, presionSistolica: sis, presionDiastolica: dia }, 'presion')).toBe(dentro);
    });

    it.each([
      [119, 79, 'Normal'],
      [120, 79, 'Presión elevada'], [129, 79, 'Presión elevada'],
      [130, 79, 'Hipertensión estadio 1'], [119, 80, 'Hipertensión estadio 1'], [139, 89, 'Hipertensión estadio 1'],
      [140, 70, 'Hipertensión estadio 2'], [125, 90, 'Hipertensión estadio 2'],
      [91, 60, 'Normal'], [90, 60, 'Hipotensión']
    ])('clasificación ACC/AHA de %i/%i: %s', (sis, dia, clase) => {
      expect(clasificarPresion(sis, dia)).toBe(clase);
    });

    it('a la persona le muestra la clasificación cuando no es normal pero tampoco está fuera de rango', () => {
      expect(estadoIndicador({ ...NORMAL, presionSistolica: 118, presionDiastolica: 76 }, 'presion'))
        .toEqual({ etiqueta: 'Normal', tono: 'normal' });
      expect(estadoIndicador({ ...NORMAL, presionSistolica: 125, presionDiastolica: 76 }, 'presion'))
        .toEqual({ etiqueta: 'Elevada', tono: 'aviso' });
      expect(estadoIndicador({ ...NORMAL, presionSistolica: 135, presionDiastolica: 85 }, 'presion'))
        .toEqual({ etiqueta: 'Alta (estadio 1)', tono: 'aviso' });
      expect(estadoIndicador({ ...NORMAL, presionSistolica: 145, presionDiastolica: 85 }, 'presion'))
        .toEqual({ etiqueta: 'Revisar', tono: 'revisar' });
    });
  });

  it.each([
    [40, 'critica'], [41, 'moderada'],
    [59, 'moderada'], [60, 'normal'],
    [100, 'normal'], [101, 'moderada'],
    [130, 'moderada'], [131, 'critica']
  ] as const)('pulso %i lpm → %s', (valor, esperada) => {
    expect(gravedad('pulso', valor)).toBe(esperada);
  });

  it.each([
    [96, 'normal'],
    [95, 'leve'], [94, 'leve'],
    [93, 'moderada'], [92, 'moderada'],
    [91, 'critica']
  ] as const)('saturación %i %% → %s', (valor, esperada) => {
    expect(gravedad('oxigeno', valor)).toBe(esperada);
  });

  it('la saturación leve (94–95 %) no cuenta como fuera de rango', () => {
    expect(evaluarIndicador({ ...NORMAL, saturacionOxigeno: 94 }, 'oxigeno')).toBe(true);
    expect(evaluarIndicador({ ...NORMAL, saturacionOxigeno: 93 }, 'oxigeno')).toBe(false);
  });

  describe('saturación con EPOC (objetivo 88–92 %)', () => {
    it.each([
      [87, 'moderada'], [88, 'normal'],
      [92, 'normal'], [93, 'moderada'],
      [84, 'moderada'], [83, 'critica']
    ] as const)('%i %% → %s', (valor, esperada) => {
      expect(gravedad('oxigeno', valor, true)).toBe(esperada);
    });

    it('90 % es normal con EPOC y crítico sin ella; 97 % está por encima del objetivo', () => {
      expect(evaluarIndicador({ ...NORMAL, saturacionOxigeno: 90 }, 'oxigeno', { epoc: true })).toBe(true);
      expect(gravedad('oxigeno', 90)).toBe('critica');
      expect(indicadoresFueraDeRango(NORMAL, { epoc: true })).toEqual(['oxigeno']);
    });

    it('la franja de la gráfica cambia', () => {
      expect(franjaNormal('saturacionOxigeno')).toEqual({ min: 94, max: 100 });
      expect(franjaNormal('saturacionOxigeno', { epoc: true })).toEqual({ min: 88, max: 92 });
    });
  });

  it.each([
    [35.0, 'critica'], [35.1, 'moderada'],
    [36.0, 'moderada'], [36.1, 'normal'],
    [37.7, 'normal'], [37.8, 'moderada'],
    [39.0, 'moderada'], [39.1, 'critica']
  ] as const)('temperatura %f °C → %s', (valor, esperada) => {
    expect(gravedad('temperatura', valor)).toBe(esperada);
  });

  it.each([
    [8, 'critica'], [9, 'moderada'],
    [11, 'moderada'], [12, 'normal'],
    [20, 'normal'], [21, 'moderada'],
    [24, 'moderada'], [25, 'critica']
  ] as const)('frecuencia respiratoria %i rpm → %s', (valor, esperada) => {
    expect(gravedad('respiracion', valor)).toBe(esperada);
  });

  it('la frecuencia respiratoria cuenta como un indicador más fuera de rango', () => {
    expect(indicadoresFueraDeRango({ ...NORMAL, frecuenciaRespiratoria: 22 })).toEqual(['respiracion']);
  });

  it('la gravedad máxima es la del peor indicador', () => {
    expect(gravedadMaxima({ ...NORMAL, saturacionOxigeno: 95 })).toBe('leve');
    expect(gravedadMaxima({ ...NORMAL, presionSistolica: 150, frecuenciaRespiratoria: 26 })).toBe('critica');
  });

  it('el motivo dice el indicador, el valor y el rango esperado', () => {
    expect(motivoFueraDeRango({ ...NORMAL, saturacionOxigeno: 91 }, 'oxigeno')).toBe('Saturación 91 % (esperado ≥ 94 %)');
    expect(motivoFueraDeRango({ ...NORMAL, saturacionOxigeno: 86 }, 'oxigeno', { epoc: true }))
      .toBe('Saturación 86 % (esperado 88–92 % por EPOC)');
    expect(motivoFueraDeRango({ ...NORMAL, temperatura: 38.2 }, 'temperatura'))
      .toBe('Temperatura 38,2 °C (esperado > 36,0 y < 37,8 °C)');
    expect(motivoFueraDeRango({ ...NORMAL, presionSistolica: 150, presionDiastolica: 95 }, 'presion'))
      .toBe('Presión 150/95 mmHg (esperado 91–139 / < 90 mmHg)');
    expect(motivoFueraDeRango({ ...NORMAL, frecuenciaCardiaca: 52 }, 'pulso')).toBe('Pulso 52 lpm (esperado 60–100 lpm)');
    expect(motivoFueraDeRango({ ...NORMAL, frecuenciaRespiratoria: 22 }, 'respiracion'))
      .toBe('Frecuencia respiratoria 22 rpm (esperado 12–20 rpm)');
  });

  it.each([
    [23.0, 'Delgadez'], [23.1, 'Normal'],
    [27.9, 'Normal'], [28.0, 'Sobrepeso'],
    [31.9, 'Sobrepeso'], [32.0, 'Obesidad']
  ])('IMC de persona mayor %f → %s', (imc, categoria) => {
    expect(categoriaImc(imc)).toBe(categoria);
  });

  it('detecta los valores físicamente imposibles (RF-29, sin cambios)', () => {
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
