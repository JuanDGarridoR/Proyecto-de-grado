/**
 * Categorías, estados, unidades y motivos de donaciones e inventario, con
 * el texto y el ícono que muestra la aplicación. Los códigos son los mismos
 * de donacion-service (Catalogo.java).
 */

export type Categoria = 'ALIMENTOS' | 'DINERO' | 'ROPA' | 'MEDICAMENTOS' | 'ASEO' | 'OTRO';

export const CATEGORIAS: { codigo: Categoria; texto: string; icono: string }[] = [
  { codigo: 'ALIMENTOS', texto: 'Alimentos', icono: 'chef-hat' },
  { codigo: 'DINERO', texto: 'Dinero', icono: 'tag' },
  { codigo: 'ROPA', texto: 'Ropa', icono: 'yarn' },
  { codigo: 'MEDICAMENTOS', texto: 'Medicamentos', icono: 'pill' },
  { codigo: 'ASEO', texto: 'Aseo e higiene', icono: 'sparkles' },
  { codigo: 'OTRO', texto: 'Otro', icono: 'gift' },
];

export function textoCategoria(codigo: string): string {
  return CATEGORIAS.find((c) => c.codigo === codigo)?.texto ?? codigo;
}

export function iconoCategoria(codigo: string): string {
  return CATEGORIAS.find((c) => c.codigo === codigo)?.icono ?? 'gift';
}

/** Las categorías donde tiene sentido pedir fecha de vencimiento. */
export function tieneVencimiento(categoria: string): boolean {
  return categoria === 'ALIMENTOS' || categoria === 'MEDICAMENTOS' || categoria === 'ASEO';
}

/** Sugerencias para el campo de unidad (se puede escribir otra). */
export const UNIDADES_SUGERIDAS = [
  'unidades',
  'kg',
  'g',
  'litros',
  'paquetes',
  'cajas',
  'bolsas',
  'pares',
  'frascos',
  'tabletas',
];

export type EstadoDonacion = 'PROGRAMADA' | 'RECIBIDA' | 'NO_ENTREGADA' | 'CANCELADA';

export const ESTADOS_DONACION: Record<EstadoDonacion, { texto: string; tipo: 'pendiente' | 'activo' | 'rechazado' }> = {
  PROGRAMADA: { texto: 'Programada', tipo: 'pendiente' },
  RECIBIDA: { texto: 'Recibida', tipo: 'activo' },
  NO_ENTREGADA: { texto: 'No entregada', tipo: 'rechazado' },
  CANCELADA: { texto: 'Cancelada', tipo: 'rechazado' },
};

export type TipoMovimiento = 'ENTRADA' | 'SALIDA';

/** Motivos que la organización elige al registrar una entrada a mano. */
export const MOTIVOS_ENTRADA = [
  { codigo: 'COMPRA', texto: 'Compra' },
  { codigo: 'DONACION_EXTERNA', texto: 'Donación por fuera de la aplicación' },
  { codigo: 'AJUSTE', texto: 'Ajuste (corregir el conteo)' },
  { codigo: 'OTRO', texto: 'Otro' },
];

export const MOTIVOS_SALIDA = [
  { codigo: 'ENTREGA', texto: 'Entregado a personas mayores' },
  { codigo: 'USO', texto: 'Usado en actividades' },
  { codigo: 'VENCIDO', texto: 'Se venció' },
  { codigo: 'DANADO', texto: 'Se dañó' },
  { codigo: 'AJUSTE', texto: 'Ajuste (corregir el conteo)' },
  { codigo: 'OTRO', texto: 'Otro' },
];

/** Texto de cualquier motivo, incluido DONACION, que solo pone el sistema. */
export function textoMotivo(codigo: string): string {
  if (codigo === 'DONACION') {
    return 'Donación recibida';
  }

  return (
    [...MOTIVOS_ENTRADA, ...MOTIVOS_SALIDA].find((m) => m.codigo === codigo)?.texto ?? codigo
  );
}

const NUMERO = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 });
const PESOS = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0,
});

/** "10 kg", "2,5 litros" o "$ 50.000" si es dinero. */
export function formatearCantidad(cantidad: number, unidad: string): string {
  return unidad === 'COP' ? PESOS.format(cantidad) : `${NUMERO.format(cantidad)} ${unidad}`;
}

/** "lunes, 14 de octubre de 2026" a partir de "2026-10-14" (sin correr el día por la zona horaria). */
export function formatearFecha(fecha: string): string {
  const [anio, mes, dia] = fecha.split('-').map(Number);
  return new Date(anio, mes - 1, dia).toLocaleDateString('es-CO', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });
}

/** "14 oct 2026" a partir de "2026-10-14". */
export function formatearFechaCorta(fecha: string): string {
  const [anio, mes, dia] = fecha.slice(0, 10).split('-').map(Number);
  return new Date(anio, mes - 1, dia).toLocaleDateString('es-CO', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}
