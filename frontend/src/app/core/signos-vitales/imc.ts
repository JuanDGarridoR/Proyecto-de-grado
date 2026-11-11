import { CategoriaImc, categoriaImc } from './rangos';

/**
 * Índice de masa corporal (IMC = peso en kg / estatura en m²) con los
 * puntos de corte para personas mayores (están en rangos.ts, con su
 * fuente). Es orientativo y no reemplaza la valoración médica.
 */
export interface Imc {
  valor: number;      // redondeado a un decimal
  categoria: CategoriaImc;
  normal: boolean;
}

/** IMC a partir del peso (kg) y la estatura (cm); null si falta alguno. */
export function calcularImc(peso: number | null, estaturaCm: number | null): Imc | null {
  if (peso === null || estaturaCm === null || estaturaCm <= 0) {
    return null;
  }

  const metros = estaturaCm / 100;
  const valor = Math.round((peso / (metros * metros)) * 10) / 10;
  const categoria = categoriaImc(valor);

  return { valor, categoria, normal: categoria === 'Normal' };
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
