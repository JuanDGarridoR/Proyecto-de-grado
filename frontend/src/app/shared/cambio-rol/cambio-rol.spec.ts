import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { CambioRol } from './cambio-rol';

// Cambio de rol desde el óvalo de la barra superior (RF-76): persona mayor y
// acompañante pasan al otro rol con la misma cuenta, después de confirmar.
describe('CambioRol', () => {
  let auth: {
    getRol: () => string;
    roles: ReturnType<typeof vi.fn>;
    cambiarRol: ReturnType<typeof vi.fn>;
    redirigirSegunRol: ReturnType<typeof vi.fn>;
  };

  function crear(rol: string, roles: string[]): any {
    auth = {
      getRol: () => rol,
      roles: vi.fn(() => of(roles)),
      cambiarRol: vi.fn((nuevo: string) => of({ rol: nuevo, token: 't', mensaje: '', idUsuario: 15, nombreUsuario: 'Rosa' })),
      redirigirSegunRol: vi.fn()
    };

    TestBed.configureTestingModule({
      imports: [CambioRol],
      providers: [{ provide: AuthService, useValue: auth }]
    });

    const fixture = TestBed.createComponent(CambioRol);
    fixture.componentRef.setInput('roleLabel', 'Persona mayor');
    fixture.detectChanges();
    return { fixture, componente: fixture.componentInstance as any };
  }

  it('el óvalo de una persona mayor es un botón que pide confirmación antes de cambiar', () => {
    const { fixture, componente } = crear('PERSONA_MAYOR', ['PERSONA_MAYOR', 'ACOMPANANTE']);
    const boton: HTMLButtonElement = fixture.nativeElement.querySelector('button.rol');

    boton.click();
    fixture.detectChanges();

    expect(auth.cambiarRol).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[role="dialog"] h2').textContent).toContain('¿Cambiar a acompañante?');

    componente.cambiar();

    expect(auth.cambiarRol).toHaveBeenCalledWith('ACOMPANANTE');
    expect(auth.redirigirSegunRol).toHaveBeenCalledWith('ACOMPANANTE');
    expect(componente.confirmando()).toBe(false);
  });

  it('si aún no tiene el otro rol, la confirmación avisa que se activará con su misma información', () => {
    const { fixture, componente } = crear('ACOMPANANTE', ['ACOMPANANTE']);

    componente.abrir();
    fixture.detectChanges();

    expect(componente.tieneOtroRol()).toBe(false);
    expect(fixture.nativeElement.querySelector('[role="dialog"] h2').textContent)
      .toContain('¿Usar tu cuenta también como persona mayor?');
  });

  it('cancelar no cambia nada', () => {
    const { componente } = crear('PERSONA_MAYOR', ['PERSONA_MAYOR']);

    componente.abrir();
    componente.cerrar();

    expect(componente.confirmando()).toBe(false);
    expect(auth.cambiarRol).not.toHaveBeenCalled();
  });

  it('si el backend lo rechaza, muestra su mensaje y no redirige', () => {
    const { componente } = crear('PERSONA_MAYOR', ['PERSONA_MAYOR']);
    auth.cambiarRol.mockReturnValue(throwError(() => ({ error: { mensaje: 'Tu cuenta no puede cambiar a ese rol' } })));

    componente.abrir();
    componente.cambiar();

    expect(componente.error()).toBe('Tu cuenta no puede cambiar a ese rol');
    expect(componente.confirmando()).toBe(true);
    expect(auth.redirigirSegunRol).not.toHaveBeenCalled();
  });

  it('organizaciones y voluntarios solo ven la etiqueta de su rol', () => {
    const { fixture } = crear('ORGANIZACION', ['ORGANIZACION']);

    expect(fixture.nativeElement.querySelector('button.rol')).toBeNull();
    expect(fixture.nativeElement.querySelector('span.rol').textContent).toContain('Persona mayor');
    expect(auth.roles).not.toHaveBeenCalled();
  });
});
