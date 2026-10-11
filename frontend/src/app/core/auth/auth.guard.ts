import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Protege las rutas de los paneles: exige sesión y, si la ruta pide un rol
 * (data.rol), que sea el del usuario.
 */
export const authGuard: CanActivateFn = (route) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.estaAutenticado()) {
    return router.parseUrl('/iniciar-sesion');
  }

  const rolRequerido = route.data['rol'] as string | undefined;
  const rolUsuario = authService.getRol();

  if (rolRequerido && rolUsuario !== rolRequerido) {
    // Tiene sesión, pero con otro rol: se le envía a su propio panel.
    authService.redirigirSegunRol(rolUsuario ?? '');
    return false;
  }

  return true;
};