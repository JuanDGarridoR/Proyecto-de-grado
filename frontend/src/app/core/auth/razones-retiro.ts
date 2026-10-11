/**
 * Razones por las que alguien elimina su cuenta. El código es el que guarda
 * auth-service (RetiroCuenta.RAZONES); el texto es el que ven el usuario al
 * eliminar su cuenta y las organizaciones en su lista de retiros.
 */
export interface RazonRetiro {
  codigo: string;
  texto: string;
}

export const RAZONES_RETIRO: RazonRetiro[] = [
  { codigo: 'SALUD', texto: 'Problemas de salud que impiden usar la plataforma' },
  { codigo: 'HOGAR_CUIDADO', texto: 'Ingreso a un hogar o centro de cuidado' },
  { codigo: 'CAMBIO_RESIDENCIA', texto: 'Cambio de ciudad o de residencia' },
  { codigo: 'DIFICULTAD_USO', texto: 'La plataforma es difícil de usar' },
  { codigo: 'NO_LA_NECESITA', texto: 'Ya no necesita la plataforma' },
  { codigo: 'PRIVACIDAD', texto: 'Preocupación por la privacidad de los datos' },
  { codigo: 'FALLECIMIENTO', texto: 'Fallecimiento de la persona' },
  { codigo: 'OTRA', texto: 'Otra razón' }
];

/** Las organizaciones se retiran por otras razones (RetiroCuenta.RAZONES_ORGANIZACION). */
export const RAZONES_RETIRO_ORGANIZACION: RazonRetiro[] = [
  { codigo: 'CIERRE_ORGANIZACION', texto: 'La organización cerró o dejó de funcionar' },
  { codigo: 'CAMBIO_ADMINISTRACION', texto: 'La organización se fusionó con otra o cambió de administración' },
  { codigo: 'OTRA_HERRAMIENTA', texto: 'Vamos a usar otra herramienta o sistema' },
  { codigo: 'SIN_PERSONAL', texto: 'No tenemos personal para administrar la plataforma' },
  { codigo: 'NO_SE_AJUSTA', texto: 'La plataforma no se ajusta a las necesidades de la organización' },
  { codigo: 'DIFICULTAD_USO', texto: 'La plataforma es difícil de usar' },
  { codigo: 'PRIVACIDAD', texto: 'Preocupación por la privacidad de los datos' },
  { codigo: 'OTRA', texto: 'Otra razón' }
];

/** Las razones que puede elegir cada rol al eliminar su cuenta. */
export function razonesRetiroPara(rol: string | null): RazonRetiro[] {
  return rol === 'ORGANIZACION' ? RAZONES_RETIRO_ORGANIZACION : RAZONES_RETIRO;
}

export const MAX_COMENTARIO_RETIRO = 500;

export function textoRazonRetiro(codigo: string): string {
  return [...RAZONES_RETIRO, ...RAZONES_RETIRO_ORGANIZACION]
    .find((razon) => razon.codigo === codigo)?.texto ?? codigo;
}
