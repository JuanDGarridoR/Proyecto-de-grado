/** Iniciales del nombre: "María Pérez" -> "MP". */
export function iniciales(nombre: string): string {
  return nombre
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((parte) => parte.charAt(0).toUpperCase())
    .join('');
}

/**
 * Mensaje de un error del backend para mostrar en pantalla. Los servicios
 * responden los errores de negocio como texto plano ("Ya enviaste una
 * solicitud..."); si no hay texto, se usa el mensaje por defecto.
 */
export function mensajeDeError(error: unknown, porDefecto: string): string {
  const cuerpo = (error as { error?: unknown } | null)?.error;
  return typeof cuerpo === 'string' && cuerpo ? cuerpo : porDefecto;
}
