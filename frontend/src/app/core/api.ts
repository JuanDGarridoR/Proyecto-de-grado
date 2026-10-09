/**
 * URL base del backend. Todo pasa por el API Gateway, que valida el token
 * y reparte cada ruta al servicio que la atiende; el frontend nunca llama
 * a un servicio directamente.
 *
 * En el computador de desarrollo (localhost) se llama al gateway en el
 * puerto 8080. En el servidor la página y el API están en la misma
 * dirección: Caddy reenvía /api al gateway (ver frontend/Caddyfile).
 */
const enDesarrolloLocal =
  typeof window === 'undefined' || ['localhost', '127.0.0.1'].includes(window.location.hostname);

export const API_URL = enDesarrolloLocal ? 'http://localhost:8080/api' : '/api';
