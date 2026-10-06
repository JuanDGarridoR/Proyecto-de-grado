import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

// Acceso a los paneles según la sesión y el rol (RF-03 y RNF-08).
describe('authGuard', () => {
  let sesion: { autenticado: boolean; rol: string | null };
  const redirigirSegunRol = vi.fn();

  beforeEach(() => {
    sesion = { autenticado: false, rol: null };
    redirigirSegunRol.mockClear();

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            estaAutenticado: () => sesion.autenticado,
            getRol: () => sesion.rol,
            redirigirSegunRol
          }
        }
      ]
    });
  });

  function entrarA(rolRequerido?: string) {
    const ruta = { data: rolRequerido ? { rol: rolRequerido } : {} } as unknown as ActivatedRouteSnapshot;
    return TestBed.runInInjectionContext(() => authGuard(ruta, {} as RouterStateSnapshot));
  }

  it('sin sesión envía al inicio de sesión', () => {
    const resultado = entrarA('ORGANIZACION');

    expect(resultado).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(resultado as UrlTree)).toBe('/login');
  });

  it('deja entrar al panel del propio rol', () => {
    sesion = { autenticado: true, rol: 'ORGANIZACION' };

    expect(entrarA('ORGANIZACION')).toBe(true);
    expect(redirigirSegunRol).not.toHaveBeenCalled();
  });

  it('con otro rol no deja entrar y lo envía a su propio panel', () => {
    sesion = { autenticado: true, rol: 'PERSONA_MAYOR' };

    expect(entrarA('ORGANIZACION')).toBe(false);
    expect(redirigirSegunRol).toHaveBeenCalledWith('PERSONA_MAYOR');
  });
});
