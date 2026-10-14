import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { CampoContrasena } from './campo-contrasena';

/** Formulario mínimo con un campo de contraseña, como en el inicio de sesión. */
@Component({
  imports: [CampoContrasena],
  template: `
    <app-campo-contrasena>
      <input type="password" value="secreta1" />
    </app-campo-contrasena>
  `
})
class FormularioDePrueba {}

// Mostrar u ocultar la contraseña (RF-08): el botón del ojo cambia el tipo
// del campo y lo anuncia a los lectores de pantalla.
describe('CampoContrasena', () => {
  it('el botón del ojo muestra la contraseña y la vuelve a ocultar', () => {
    TestBed.configureTestingModule({ imports: [FormularioDePrueba] });
    const fixture = TestBed.createComponent(FormularioDePrueba);
    fixture.detectChanges();

    const campo: HTMLInputElement = fixture.nativeElement.querySelector('input');
    const ojo: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    expect(campo.type).toBe('password');
    expect(ojo.getAttribute('aria-label')).toBe('Mostrar contraseña');

    ojo.click();
    fixture.detectChanges();
    expect(campo.type).toBe('text');
    expect(ojo.getAttribute('aria-label')).toBe('Ocultar contraseña');

    ojo.click();
    fixture.detectChanges();
    expect(campo.type).toBe('password');
  });
});
