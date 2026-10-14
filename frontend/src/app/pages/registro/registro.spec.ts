import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { Registro } from './registro';

// Registro de cuenta (RF-01 y RF-02): exige aceptar los términos y
// condiciones y valida en el navegador las mismas reglas que auth-service
// antes de enviar el formulario.
describe('Registro', () => {
  const registro = vi.fn();
  const redirigirSegunRol = vi.fn();
  let formulario: Registro;

  beforeEach(() => {
    registro.mockReset().mockReturnValue(of({ rol: 'PERSONA_MAYOR' }));
    redirigirSegunRol.mockReset();

    TestBed.configureTestingModule({
      imports: [Registro],
      providers: [{ provide: AuthService, useValue: { registro, redirigirSegunRol } }]
    });
    TestBed.overrideComponent(Registro, { set: { template: '', imports: [] } });
    formulario = TestBed.createComponent(Registro).componentInstance;

    // Persona mayor con todos sus datos
    formulario.nombreUsuario = 'Rosa Díaz';
    formulario.rol = 'PERSONA_MAYOR';
    formulario.celularLocal = '300 111 2233';
    formulario.fechaNacimiento = '1950-03-14';
    formulario.genero = 'Femenino';
    formulario.direccion = 'Calle 91 Sur # 3-20';
  });

  it('sin aceptar los términos y condiciones no crea la cuenta', () => {
    formulario.onSubmit();

    expect(formulario.errorMensaje()).toBe('Debes aceptar los términos y condiciones para crear tu cuenta.');
    expect(registro).not.toHaveBeenCalled();
  });

  it('con los términos aceptados crea la cuenta con el celular +57 y entra al panel del rol', () => {
    formulario.aceptaTerminos = true;
    formulario.onSubmit();

    expect(registro).toHaveBeenCalledWith(expect.objectContaining({
      nombreUsuario: 'Rosa Díaz',
      rol: 'PERSONA_MAYOR',
      celular: '+573001112233',
      fechaNacimiento: '1950-03-14',
      genero: 'Femenino',
      correo: undefined,
      contrasena: undefined
    }));
    expect(redirigirSegunRol).toHaveBeenCalledWith('PERSONA_MAYOR');
  });

  it('el correo y la contraseña van juntos y la contraseña se confirma', () => {
    formulario.aceptaTerminos = true;
    formulario.correo = 'rosa@vitamas.co';
    formulario.onSubmit();
    expect(formulario.errorMensaje()).toBe('Si ingresas un correo, debes ingresar una contraseña.');

    formulario.contrasena = 'secreta1';
    formulario.confirmarContrasena = 'otra123';
    formulario.onSubmit();
    expect(formulario.errorMensaje()).toBe('Las contraseñas no coinciden.');

    expect(registro).not.toHaveBeenCalled();
  });

  it('la organización solo necesita su dirección además del celular', () => {
    formulario.aceptaTerminos = true;
    formulario.rol = 'ORGANIZACION';
    formulario.onCambioRol();
    formulario.celularLocal = '3004445566';

    formulario.onSubmit();
    expect(formulario.errorMensaje()).toBe('La dirección de la organización es obligatoria.');

    formulario.direccion = 'Carrera 5 # 80-10 Sur';
    formulario.onSubmit();
    expect(registro).toHaveBeenCalledWith(expect.objectContaining({
      rol: 'ORGANIZACION',
      fechaNacimiento: undefined,
      genero: undefined
    }));
  });
});
