import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { retry, throwError, timer } from 'rxjs';

/** Reintentos de una consulta antes de mostrar el error. */
export const REINTENTOS = 2;

/** Espera antes de cada reintento: 1 s, luego 2 s. */
export const ESPERA_BASE_MS = 1000;

/**
 * Errores que suelen ser pasajeros: sin conexión (0), o el servidor
 * reiniciándose o con un servicio caído (502, 503, 504).
 */
const PASAJEROS = [0, 502, 503, 504];

/**
 * Si una consulta (GET) falla por un corte pasajero, la repite sola un par
 * de veces antes de mostrar el error. Así un parpadeo de la red o un
 * servicio que se está reiniciando no le muestra un error a la persona.
 *
 * Solo GET: repetir un POST, PUT o DELETE podría hacer dos veces lo mismo
 * (dos registros, dos alertas). Esos ya los reintenta el gateway cuando es
 * seguro (si la petición ni siquiera llegó al servicio).
 */
export const reintentosInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.method !== 'GET') {
    return next(req);
  }

  return next(req).pipe(
    retry({
      count: REINTENTOS,
      delay: (error, intento) =>
        error instanceof HttpErrorResponse && PASAJEROS.includes(error.status)
          ? timer(ESPERA_BASE_MS * intento)
          : throwError(() => error)
    })
  );
};
