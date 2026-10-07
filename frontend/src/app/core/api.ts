/**
 * URL base del backend. Todo pasa por el API Gateway (puerto 8080), que
 * valida el token y reparte cada ruta al servicio que la atiende; el
 * frontend nunca llama a un servicio directamente.
 */
export const API_URL = 'http://localhost:8080/api';
