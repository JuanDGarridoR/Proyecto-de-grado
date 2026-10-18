import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Rutas que no llevan token. Un 401 en ellas no es un token vencido, así que no se renueva. */
const RUTAS_PUBLICAS = [
  '/api/auth/login',
  '/api/auth/login-otp',
  '/api/auth/registro',
  '/api/auth/sesion/',
  '/api/otp/send'
];

function conToken(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

/**
 * Agrega el token (Authorization: Bearer ...) a cada petición al backend,
 * menos a las públicas. Si el backend responde 401 (el token de acceso dura
 * 15 minutos), lo renueva una vez y repite la petición; si la sesión ya no
 * se puede renovar, AuthService lleva a iniciar sesión.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {

  if (RUTAS_PUBLICAS.some(ruta => req.url.includes(ruta))) {
    return next(req);
  }

  // Se lee a través de AuthService para que, si la pestaña se acaba de
  // abrir, primero se restaure la sesión recordada.
  const authService = inject(AuthService);
  const token = authService.getToken();

  if (!token) {
    return next(req);
  }

  return next(conToken(req, token)).pipe(
    catchError((error) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || !authService.puedeRenovar()) {
        return throwError(() => error);
      }

      // Se repite una sola vez: si con el token nuevo vuelve a dar 401, el
      // error es otro (por ejemplo, "la contraseña actual es incorrecta").
      return authService.renovarToken().pipe(
        switchMap((nuevo) => next(conToken(req, nuevo)))
      );
    })
  );
};
