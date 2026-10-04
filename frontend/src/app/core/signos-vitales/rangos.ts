/**
 * Rangos de referencia generales de signos vitales para adultos. Son
 * orientativos (no reemplazan el criterio médico) y se usan para marcar
 * "Normal" / "Revisar" en el inicio de la persona mayor y en la
 * analítica de la organización.
 */
export const RANGOS = {
  sistolica: { min: 90, max: 139, unidad: 'mmHg' },
  diastolica: { min: 60, max: 89, unidad: 'mmHg' },
  pulso: { min: 60, max: 100, unidad: 'lpm' },
  temperatura: { min: 36, max: 37.5, unidad: '°C' },
  oxigeno: { min: 95, max: 100, unidad: '%' }
} as const;

/** Indicadores que se evalúan contra RANGOS. */
export type Indicador = 'presion' | 'pulso' | 'temperatura' | 'oxigeno';

/** Nombre de cada indicador para mostrar en pantalla. */
export const NOMBRE_INDICADOR: Record<Indicador, string> = {
  presion: 'Presión',
  pulso: 'Pulso',
  temperatura: 'Temperatura corporal',
  oxigeno: 'Oxígeno'
};

/** Campos mínimos de una medición para evaluarla. */
export interface ValoresSignos {
  presionSistolica: number | null;
  presionDiastolica: number | null;
  frecuenciaCardiaca: number | null;
  temperatura: number | null;
  saturacionOxigeno: number | null;
}

function dentro(valor: number, rango: { min: number; max: number }): boolean {
  return valor >= rango.min && valor <= rango.max;
}

/**
 * true = normal, false = revisar, null = no se midió ese indicador. En el
 * oxígeno solo cuenta el mínimo.
 */
export function evaluarIndicador(s: ValoresSignos, indicador: Indicador): boolean | null {
  switch (indicador) {
    case 'presion': {
      const sis = s.presionSistolica;
      const dia = s.presionDiastolica;
      if (sis === null && dia === null) return null;
      return (sis === null || dentro(sis, RANGOS.sistolica))
        && (dia === null || dentro(dia, RANGOS.diastolica));
    }
    case 'pulso':
      return s.frecuenciaCardiaca === null ? null : dentro(s.frecuenciaCardiaca, RANGOS.pulso);
    case 'temperatura':
      return s.temperatura === null ? null : dentro(s.temperatura, RANGOS.temperatura);
    case 'oxigeno':
      return s.saturacionOxigeno === null ? null : s.saturacionOxigeno >= RANGOS.oxigeno.min;
  }
}

/** Todos los indicadores, en el orden en que se muestran. */
export const INDICADORES: Indicador[] = ['presion', 'pulso', 'temperatura', 'oxigeno'];

/** Indicadores medidos que están fuera de rango. */
export function indicadoresFueraDeRango(s: ValoresSignos): Indicador[] {
  return INDICADORES.filter((i) => evaluarIndicador(s, i) === false);
}

/**
 * Límites de lo físicamente posible: un valor por fuera no es una medición
 * real sino un error de digitación. Deben coincidir con los límites de
 * SignoVitalOrganizacionController en salud-service.
 */
export const LIMITES = {
  presionSistolica: { min: 60, max: 260, nombre: 'Presión sistólica', unidad: 'mmHg' },
  presionDiastolica: { min: 30, max: 160, nombre: 'Presión diastólica', unidad: 'mmHg' },
  frecuenciaCardiaca: { min: 30, max: 220, nombre: 'Frecuencia cardíaca', unidad: 'lpm' },
  temperatura: { min: 32, max: 43, nombre: 'Temperatura corporal', unidad: '°C' },
  saturacionOxigeno: { min: 50, max: 100, nombre: 'Saturación de oxígeno', unidad: '%' },
  frecuenciaRespiratoria: { min: 5, max: 60, nombre: 'Frecuencia respiratoria', unidad: 'rpm' },
  peso: { min: 20, max: 250, nombre: 'Peso', unidad: 'kg' },
  estatura: { min: 100, max: 220, nombre: 'Estatura', unidad: 'cm' }
} as const;

export type CampoSigno = keyof typeof LIMITES;

type ValoresCompletos = Partial<Record<CampoSigno, number | null>>;

/** Campos con un valor imposible (posible error de registro). */
export function camposImposibles(s: ValoresCompletos): CampoSigno[] {
  return (Object.keys(LIMITES) as CampoSigno[]).filter((campo) => {
    const valor = s[campo];
    return valor !== null && valor !== undefined && (valor < LIMITES[campo].min || valor > LIMITES[campo].max);
  });
}

/**
 * Valida una medición antes de guardarla. Devuelve el mensaje de error o
 * null si está bien.
 */
export function validarMedicion(s: ValoresCompletos): string | null {
  const medidos = (Object.keys(LIMITES) as CampoSigno[]).filter((c) => s[c] !== null && s[c] !== undefined);
  if (medidos.length === 0) {
    return 'Ingresa al menos un signo vital.';
  }

  const sis = s.presionSistolica ?? null;
  const dia = s.presionDiastolica ?? null;
  if ((sis === null) !== (dia === null)) {
    return 'La presión arterial se registra completa: ingresa la sistólica y la diastólica (por ejemplo 120/80).';
  }

  for (const campo of medidos) {
    const { min, max, nombre, unidad } = LIMITES[campo];
    const valor = s[campo]!;
    if (valor < min || valor > max) {
      return `${nombre}: ${valor} ${unidad} no es un valor posible. Debe estar entre ${min} y ${max} ${unidad}.`;
    }
  }

  if (sis !== null && dia !== null && sis <= dia) {
    return 'La presión sistólica (el número mayor) debe ser más alta que la diastólica.';
  }

  return null;
}
