/**
 * Índice de masa corporal (IMC = peso en kg / estatura en m²) con la
 * clasificación general de la OMS para adultos. Es orientativo y no
 * reemplaza la valoración médica.
 */
export interface Imc {
  valor: number;      // redondeado a un decimal
  categoria: string;
  normal: boolean;
}

const CATEGORIAS = [
  { hasta: 18.5, categoria: 'Bajo peso' },
  { hasta: 25, categoria: 'Peso normal' },
  { hasta: 30, categoria: 'Sobrepeso' },
  { hasta: Infinity, categoria: 'Obesidad' }
];

/** IMC a partir del peso (kg) y la estatura (cm); null si falta alguno. */
export function calcularImc(peso: number | null, estaturaCm: number | null): Imc | null {
  if (peso === null || estaturaCm === null || estaturaCm <= 0) {
    return null;
  }

  const metros = estaturaCm / 100;
  const valor = Math.round((peso / (metros * metros)) * 10) / 10;
  const categoria = CATEGORIAS.find((c) => valor < c.hasta)!.categoria;

  return { valor, categoria, normal: categoria === 'Peso normal' };
}

/**
 * IMC de cada registro (ordenados del más reciente al más antiguo). La
 * estatura casi no cambia y no siempre se mide, así que si el registro
 * tiene peso pero no estatura se usa la estatura más reciente anterior.
 */
export function imcPorRegistro(
  registros: { peso: number | null; estatura: number | null }[]
): (Imc | null)[] {
  const resultado: (Imc | null)[] = new Array(registros.length).fill(null);
  let estatura: number | null = null;

  // Del más antiguo al más reciente, recordando la última estatura vista
  for (let i = registros.length - 1; i >= 0; i--) {
    estatura = registros[i].estatura ?? estatura;
    resultado[i] = calcularImc(registros[i].peso, estatura);
  }

  return resultado;
}
