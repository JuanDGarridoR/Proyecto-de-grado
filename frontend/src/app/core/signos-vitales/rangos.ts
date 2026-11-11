/**
 * Rangos clínicos de referencia para personas mayores. Es el único lugar
 * del frontend donde están (también las categorías del IMC, que usa
 * imc.ts); el backend tiene los mismos en RangosClinicos (analitica-service)
 * y deben coincidir. Son orientativos y no reemplazan el criterio médico.
 *
 * Fuentes:
 *  - Presión: ACC/AHA 2017 (Whelton et al., Hypertension 2018;71:e13-e115)
 *    para la clasificación; la hipotensión (sistólica ≤ 90) es la de NEWS2.
 *  - Pulso, frecuencia respiratoria y valores críticos: NEWS2 (Royal
 *    College of Physicians, 2017).
 *  - Saturación: BTS 2017 (O'Driscoll et al., Thorax 2017;72:i1-i90),
 *    objetivo ≥ 94 % y 88–92 % con EPOC; gravedad según NEWS2 (escala 1 y,
 *    con EPOC, escala 2).
 *  - Temperatura: fiebre ≥ 37,8 °C en personas mayores (IDSA 2008, High et
 *    al., Clin Infect Dis 2009;48:149-171); extremos según NEWS2.
 *  - IMC: puntos de corte para personas mayores de Minsalud (2024) y la
 *    OPS (2002); reemplazan la escala de la OMS para adultos.
 *
 * LIMITES (al final) es otra cosa: lo físicamente posible (RF-29).
 */
export const RANGOS = {
  presion: {
    sistolicaHipotension: 90, // ≤ 90: fuera (hipotensión)
    sistolicaElevada: 120,    // 120–129 con diastólica < 80: elevada
    sistolicaEstadio1: 130,   // 130–139: estadio 1
    sistolicaEstadio2: 140,   // ≥ 140: estadio 2, fuera
    diastolicaEstadio1: 80,   // 80–89: estadio 1
    diastolicaEstadio2: 90,   // ≥ 90: estadio 2, fuera
    unidad: 'mmHg'
  },
  pulso: { min: 60, max: 100, criticoBajo: 40, criticoAlto: 131, unidad: 'lpm' },
  temperatura: { baja: 36.0, fiebre: 37.8, criticaBaja: 35.0, criticaAlta: 39.1, unidad: '°C' },
  oxigeno: {
    min: 94,          // < 94: fuera
    leve: 95,         // 94–95: leve (aún dentro)
    critica: 91,      // ≤ 91: crítica
    epocMin: 88,      // con EPOC: objetivo 88–92
    epocMax: 92,
    epocCritica: 83,  // con EPOC, ≤ 83: crítica
    unidad: '%'
  },
  respiracion: { min: 12, max: 20, criticaBaja: 8, criticaAlta: 25, unidad: 'rpm' }
} as const;

/** Indicadores que se evalúan contra RANGOS. */
export type Indicador = 'presion' | 'pulso' | 'temperatura' | 'oxigeno' | 'respiracion';

/** Todos los indicadores, en el orden en que se muestran. */
export const INDICADORES: Indicador[] = ['presion', 'pulso', 'temperatura', 'oxigeno', 'respiracion'];

/** Nombre de cada indicador para mostrar en pantalla. */
export const NOMBRE_INDICADOR: Record<Indicador, string> = {
  presion: 'Presión',
  pulso: 'Pulso',
  temperatura: 'Temperatura corporal',
  oxigeno: 'Oxígeno',
  respiracion: 'Frecuencia respiratoria'
};

/** Nombre corto para los motivos ("Saturación 91 % (esperado ≥ 94 %)"). */
const NOMBRE_MOTIVO: Record<Indicador, string> = {
  presion: 'Presión',
  pulso: 'Pulso',
  temperatura: 'Temperatura',
  oxigeno: 'Saturación',
  respiracion: 'Frecuencia respiratoria'
};

/**
 * normal y leve están dentro del rango; moderada y critica, fuera. Leve
 * solo existe en la saturación (94–95 %).
 */
export type Gravedad = 'normal' | 'leve' | 'moderada' | 'critica';

const ORDEN_GRAVEDAD: Record<Gravedad, number> = { normal: 0, leve: 1, moderada: 2, critica: 3 };

/** Lo que cambia el rango de una persona: con EPOC, su saturación objetivo. */
export interface ContextoClinico {
  epoc?: boolean;
}

/** Campos mínimos de una medición para evaluarla. */
export interface ValoresSignos {
  presionSistolica: number | null;
  presionDiastolica: number | null;
  frecuenciaCardiaca: number | null;
  temperatura: number | null;
  saturacionOxigeno: number | null;
  frecuenciaRespiratoria?: number | null;
}

function gravedadPresion(sis: number | null, dia: number | null): Gravedad {
  const p = RANGOS.presion;
  const fuera = (sis !== null && (sis >= p.sistolicaEstadio2 || sis <= p.sistolicaHipotension))
    || (dia !== null && dia >= p.diastolicaEstadio2);
  return fuera ? 'moderada' : 'normal';
}

function gravedadPulso(v: number): Gravedad {
  const r = RANGOS.pulso;
  if (v <= r.criticoBajo || v >= r.criticoAlto) return 'critica';
  if (v < r.min || v > r.max) return 'moderada';
  return 'normal';
}

function gravedadTemperatura(v: number): Gravedad {
  const r = RANGOS.temperatura;
  if (v <= r.criticaBaja || v >= r.criticaAlta) return 'critica';
  if (v <= r.baja || v >= r.fiebre) return 'moderada';
  return 'normal';
}

function gravedadOxigeno(v: number, epoc: boolean): Gravedad {
  const r = RANGOS.oxigeno;
  if (epoc) {
    if (v <= r.epocCritica) return 'critica';
    if (v < r.epocMin || v > r.epocMax) return 'moderada';
    return 'normal';
  }
  if (v <= r.critica) return 'critica';
  if (v < r.min) return 'moderada';
  if (v <= r.leve) return 'leve';
  return 'normal';
}

function gravedadRespiracion(v: number): Gravedad {
  const r = RANGOS.respiracion;
  if (v <= r.criticaBaja || v >= r.criticaAlta) return 'critica';
  if (v < r.min || v > r.max) return 'moderada';
  return 'normal';
}

/** Gravedad de un indicador; null si no se midió. */
export function gravedadIndicador(s: ValoresSignos, indicador: Indicador, ctx: ContextoClinico = {}): Gravedad | null {
  switch (indicador) {
    case 'presion':
      return s.presionSistolica === null && s.presionDiastolica === null
        ? null
        : gravedadPresion(s.presionSistolica, s.presionDiastolica);
    case 'pulso':
      return s.frecuenciaCardiaca === null ? null : gravedadPulso(s.frecuenciaCardiaca);
    case 'temperatura':
      return s.temperatura === null ? null : gravedadTemperatura(s.temperatura);
    case 'oxigeno':
      return s.saturacionOxigeno === null ? null : gravedadOxigeno(s.saturacionOxigeno, !!ctx.epoc);
    case 'respiracion': {
      const v = s.frecuenciaRespiratoria ?? null;
      return v === null ? null : gravedadRespiracion(v);
    }
  }
}

export function estaFuera(g: Gravedad | null): boolean {
  return g === 'moderada' || g === 'critica';
}

/** true = dentro del rango, false = fuera (revisar), null = no se midió. */
export function evaluarIndicador(s: ValoresSignos, indicador: Indicador, ctx: ContextoClinico = {}): boolean | null {
  const g = gravedadIndicador(s, indicador, ctx);
  return g === null ? null : !estaFuera(g);
}

/** Indicadores medidos que están fuera de rango. */
export function indicadoresFueraDeRango(s: ValoresSignos, ctx: ContextoClinico = {}): Indicador[] {
  return INDICADORES.filter((i) => evaluarIndicador(s, i, ctx) === false);
}

/** La peor gravedad de la medición (normal si no se midió nada). */
export function gravedadMaxima(s: ValoresSignos, ctx: ContextoClinico = {}): Gravedad {
  return INDICADORES.reduce<Gravedad>((peor, i) => {
    const g = gravedadIndicador(s, i, ctx);
    return g !== null && ORDEN_GRAVEDAD[g] > ORDEN_GRAVEDAD[peor] ? g : peor;
  }, 'normal');
}

/** Clasificación informativa de la presión (ACC/AHA 2017 más la hipotensión de NEWS2). */
export type ClasePresion = 'Normal' | 'Presión elevada' | 'Hipertensión estadio 1' | 'Hipertensión estadio 2' | 'Hipotensión';

export function clasificarPresion(sis: number | null, dia: number | null): ClasePresion | null {
  if (sis === null || dia === null) return null;
  const p = RANGOS.presion;
  if (sis <= p.sistolicaHipotension) return 'Hipotensión';
  if (sis >= p.sistolicaEstadio2 || dia >= p.diastolicaEstadio2) return 'Hipertensión estadio 2';
  if (sis >= p.sistolicaEstadio1 || dia >= p.diastolicaEstadio1) return 'Hipertensión estadio 1';
  if (sis >= p.sistolicaElevada) return 'Presión elevada';
  return 'Normal';
}

/**
 * Lo que se le muestra a la persona junto al valor: "Normal" o "Revisar",
 * salvo una presión elevada o en estadio 1, que no está fuera de rango
 * pero tampoco es normal: se muestra su clasificación con tono de aviso.
 */
export interface Estado {
  etiqueta: string;
  tono: 'normal' | 'aviso' | 'revisar';
}

export function estadoIndicador(s: ValoresSignos, indicador: Indicador, ctx: ContextoClinico = {}): Estado | null {
  const g = gravedadIndicador(s, indicador, ctx);
  if (g === null) return null;
  if (estaFuera(g)) return { etiqueta: 'Revisar', tono: 'revisar' };
  if (indicador === 'presion') {
    const clase = clasificarPresion(s.presionSistolica, s.presionDiastolica);
    if (clase === 'Presión elevada' || clase === 'Hipertensión estadio 1') {
      return { etiqueta: clase === 'Presión elevada' ? 'Elevada' : 'Alta (estadio 1)', tono: 'aviso' };
    }
  }
  return { etiqueta: 'Normal', tono: 'normal' };
}

/** Temperatura con un decimal: 37.8 → "37,8", 36 → "36,0" */
function numero(v: number): string {
  return v.toFixed(1).replace('.', ',');
}

/** Rango esperado del indicador, para mostrar junto al valor. */
export function rangoEsperado(indicador: Indicador, ctx: ContextoClinico = {}): string {
  switch (indicador) {
    case 'presion': {
      const p = RANGOS.presion;
      return `${p.sistolicaHipotension + 1}–${p.sistolicaEstadio2 - 1} / < ${p.diastolicaEstadio2} ${p.unidad}`;
    }
    case 'pulso':
      return `${RANGOS.pulso.min}–${RANGOS.pulso.max} ${RANGOS.pulso.unidad}`;
    case 'temperatura': {
      const t = RANGOS.temperatura;
      return `> ${numero(t.baja)} y < ${numero(t.fiebre)} ${t.unidad}`;
    }
    case 'oxigeno': {
      const o = RANGOS.oxigeno;
      return ctx.epoc ? `${o.epocMin}–${o.epocMax} ${o.unidad} por EPOC` : `≥ ${o.min} ${o.unidad}`;
    }
    case 'respiracion':
      return `${RANGOS.respiracion.min}–${RANGOS.respiracion.max} ${RANGOS.respiracion.unidad}`;
  }
}

/** Valor medido con su unidad ("150/95 mmHg", "38,2 °C"). */
export function textoValor(s: ValoresSignos, indicador: Indicador): string {
  switch (indicador) {
    case 'presion': return `${s.presionSistolica ?? '-'}/${s.presionDiastolica ?? '-'} ${RANGOS.presion.unidad}`;
    case 'pulso': return `${s.frecuenciaCardiaca} ${RANGOS.pulso.unidad}`;
    case 'temperatura': return `${numero(s.temperatura!)} ${RANGOS.temperatura.unidad}`;
    case 'oxigeno': return `${s.saturacionOxigeno} ${RANGOS.oxigeno.unidad}`;
    case 'respiracion': return `${s.frecuenciaRespiratoria} ${RANGOS.respiracion.unidad}`;
  }
}

/** "Saturación 91 % (esperado ≥ 94 %)" */
export function motivoFueraDeRango(s: ValoresSignos, indicador: Indicador, ctx: ContextoClinico = {}): string {
  return `${NOMBRE_MOTIVO[indicador]} ${textoValor(s, indicador)} (esperado ${rangoEsperado(indicador, ctx)})`;
}

/**
 * Franja del rango normal en las gráficas de evolución, por campo. min
 * null = sin límite inferior (la franja llega hasta abajo del eje).
 */
export function franjaNormal(
  campo: 'presionSistolica' | 'presionDiastolica' | 'frecuenciaCardiaca' | 'saturacionOxigeno' | 'frecuenciaRespiratoria',
  ctx: ContextoClinico = {}
): { min: number | null; max: number } {
  switch (campo) {
    case 'presionSistolica':
      return { min: RANGOS.presion.sistolicaHipotension, max: RANGOS.presion.sistolicaEstadio2 };
    case 'presionDiastolica':
      return { min: null, max: RANGOS.presion.diastolicaEstadio2 };
    case 'frecuenciaCardiaca':
      return { min: RANGOS.pulso.min, max: RANGOS.pulso.max };
    case 'saturacionOxigeno':
      return ctx.epoc
        ? { min: RANGOS.oxigeno.epocMin, max: RANGOS.oxigeno.epocMax }
        : { min: RANGOS.oxigeno.min, max: 100 };
    case 'frecuenciaRespiratoria':
      return { min: RANGOS.respiracion.min, max: RANGOS.respiracion.max };
  }
}

/**
 * Categorías del IMC de la persona mayor (Minsalud 2024 / OPS 2002):
 * Delgadez ≤ 23,0; Normal > 23 y < 28; Sobrepeso ≥ 28 y < 32; Obesidad ≥ 32.
 */
export type CategoriaImc = 'Delgadez' | 'Normal' | 'Sobrepeso' | 'Obesidad';

export const CATEGORIAS_IMC: { categoria: CategoriaImc; rango: string }[] = [
  { categoria: 'Delgadez', rango: '23,0 o menos' },
  { categoria: 'Normal', rango: 'Más de 23 y menos de 28' },
  { categoria: 'Sobrepeso', rango: '28 a menos de 32' },
  { categoria: 'Obesidad', rango: '32 o más' }
];

export function categoriaImc(imc: number): CategoriaImc {
  if (imc <= 23) return 'Delgadez';
  if (imc < 28) return 'Normal';
  if (imc < 32) return 'Sobrepeso';
  return 'Obesidad';
}

/** Texto de la nota de los reportes con los rangos que se usan. */
export const NOTA_RANGOS = 'Rangos de referencia para personas mayores: presión fuera de rango desde 140/90 mmHg o con '
  + 'sistólica de 90 o menos (ACC/AHA 2017 y NEWS2), pulso 60–100 lpm, temperatura entre 36,0 y 37,8 °C (IDSA 2008), '
  + 'saturación de oxígeno 94 % o más, o 88–92 % con EPOC (BTS 2017), y frecuencia respiratoria 12–20 rpm (NEWS2). '
  + 'El IMC usa los puntos de corte para personas mayores (Minsalud 2024 / OPS 2002): normal entre 23 y 28.';

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
