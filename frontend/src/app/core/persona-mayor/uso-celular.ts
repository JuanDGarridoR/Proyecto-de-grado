/**
 * Nivel de uso del celular de la persona mayor (caracterización del uso
 * del celular). Es opcional: null es "no lo ha indicado". Los valores son
 * los mismos que acepta auth-service (PersonaMayor.USOS_CELULAR).
 */
export type UsoCelular = 'NO_USA' | 'BASICO' | 'INTERMEDIO' | 'AVANZADO';

/**
 * Niveles de menos a más uso, en el orden en que se muestran. corta es
 * para los ejes de las gráficas de analítica.
 */
export const OPCIONES_USO_CELULAR: { valor: UsoCelular; etiqueta: string; corta: string }[] = [
  { valor: 'NO_USA', etiqueta: 'No lo usa', corta: 'No lo usa' },
  { valor: 'BASICO', etiqueta: 'Bajo (solo llamadas)', corta: 'Bajo' },
  { valor: 'INTERMEDIO', etiqueta: 'Medio (llamadas y WhatsApp)', corta: 'Medio' },
  { valor: 'AVANZADO', etiqueta: 'Alto (varias aplicaciones)', corta: 'Alto' }
];

/** Texto de la opción, o '' si no la ha indicado. */
export function textoUsoCelular(uso: string | null | undefined): string {
  return OPCIONES_USO_CELULAR.find((o) => o.valor === uso)?.etiqueta ?? '';
}
