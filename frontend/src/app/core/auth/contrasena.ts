/**
 * Largo mínimo de una contraseña nueva (igual que LARGO_MINIMO_CONTRASENA
 * en auth-service). Las contraseñas que ya existían con menos siguen
 * sirviendo para entrar.
 */
export const LARGO_MINIMO_CONTRASENA = 8;

export const MENSAJE_CONTRASENA_CORTA =
  `La contraseña debe tener mínimo ${LARGO_MINIMO_CONTRASENA} caracteres.`;
