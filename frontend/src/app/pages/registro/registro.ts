import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CampoContrasena } from '../../shared/campo-contrasena/campo-contrasena';
import { RouterLink } from '@angular/router';
import { LARGO_MINIMO_CONTRASENA, MENSAJE_CONTRASENA_CORTA } from '../../core/auth/contrasena';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Registro de cuenta para los cuatro roles. El celular es obligatorio para
 * todos; el correo y la contraseña son opcionales, pero van juntos. Valida
 * en el navegador las mismas reglas que AuthService en auth-service.
 */
@Component({
  selector: 'app-registro',
  standalone: true,
  imports: [FormsModule, CampoContrasena, RouterLink],
  templateUrl: './registro.html',
  styleUrl: './registro.css'
})
export class Registro {

  /** Para el minlength y el texto de ayuda de los campos de contraseña. */
  protected readonly largoMinimoContrasena = LARGO_MINIMO_CONTRASENA;

  // Campos comunes a todos los roles
  nombreUsuario = '';
  rol = '';
  aceptaTerminos = false;
  mostrarTerminos = false;

  // Correo y contraseña (opcionales, pero van juntos)
  correo = '';
  contrasena = '';

// Celular y datos personales
celularLocal = '';
fechaNacimiento = '';
genero = '';
direccion = '';

confirmarContrasena = '';

  // Opciones de los selectores
  roles = [
    { valor: 'ORGANIZACION', etiqueta: 'Organización' },
    { valor: 'VOLUNTARIO', etiqueta: 'Voluntario' },
    { valor: 'ACOMPANANTE', etiqueta: 'Acompañante' },
    { valor: 'PERSONA_MAYOR', etiqueta: 'Persona mayor' }
  ];

  generos = [
    { valor: 'Femenino', etiqueta: 'Femenino' },
    { valor: 'Masculino', etiqueta: 'Masculino' },
    { valor: 'Otro', etiqueta: 'Otro' }
  ];

  // Estado de la pantalla
  cargando = signal(false);
  errorMensaje = signal<string | null>(null);
  infoMensaje = signal<string | null>(null);



  constructor(private authService: AuthService) {}

  /** Celular con el indicativo de Colombia, como lo guarda el backend: +57 y 10 dígitos. */
  get celularCompleto(): string {

    const celular =
      this.celularLocal.replace(/\D/g, '');

    return `+57${celular}`;
  }

  /** Al cambiar de rol se limpian los campos, porque cada rol pide datos distintos. */
onCambioRol(): void {
  this.errorMensaje.set(null);
  this.infoMensaje.set(null);

  this.correo = '';
  this.contrasena = '';
  this.confirmarContrasena = '';
  this.celularLocal = '';
  this.fechaNacimiento = '';
  this.genero = '';
  this.direccion = '';
}

  abrirTerminos(): void {
  this.mostrarTerminos = true;
}

cerrarTerminos(): void {
  this.mostrarTerminos = false;
}

  /** Exige aceptar los términos antes de registrar. */
onSubmit(): void {
  this.errorMensaje.set(null);

  if (!this.aceptaTerminos) {
    this.errorMensaje.set(
      'Debes aceptar los términos y condiciones para crear tu cuenta.'
    );
    return;
  }

  this.registrar();
}

/** Valida el formulario, crea la cuenta y entra directo al panel del rol. */
private registrar(): void {

  const celular = this.celularLocal.replace(/\D/g, '');
  const correo = this.correo.trim();
  const contrasena = this.contrasena.trim();
  const confirmarContrasena = this.confirmarContrasena.trim();

  // Celular obligatorio para todos los roles.
  if (celular.length !== 10) {
    this.errorMensaje.set(
      'Ingresa un número de celular válido (10 dígitos).'
    );
    return;
  }

  // Correo y contraseña van juntos.
  if (correo && !contrasena) {
    this.errorMensaje.set(
      'Si ingresas un correo, debes ingresar una contraseña.'
    );
    return;
  }

  if (!correo && contrasena) {
    this.errorMensaje.set(
      'Si ingresas una contraseña, debes ingresar un correo.'
    );
    return;
  }

  if (contrasena && contrasena.length < LARGO_MINIMO_CONTRASENA) {
    this.errorMensaje.set(MENSAJE_CONTRASENA_CORTA);
    return;
  }

  if (contrasena && !confirmarContrasena) {
    this.errorMensaje.set(
      'Debes confirmar la contraseña.'
    );
    return;
  }

  if (contrasena && contrasena !== confirmarContrasena) {
    this.errorMensaje.set(
      'Las contraseñas no coinciden.'
    );
    return;
  }

  // Persona mayor, acompañante y voluntario: datos personales obligatorios.
  if (
    this.rol === 'PERSONA_MAYOR' ||
    this.rol === 'ACOMPANANTE' ||
    this.rol === 'VOLUNTARIO'
  ) {

    if (!this.fechaNacimiento) {
      this.errorMensaje.set(
        'La fecha de nacimiento es obligatoria.'
      );
      return;
    }

    if (!this.genero) {
      this.errorMensaje.set(
        'El género es obligatorio.'
      );
      return;
    }

    if (!this.direccion.trim()) {
      this.errorMensaje.set(
        'La dirección es obligatoria.'
      );
      return;
    }
  }

  // Organización: la dirección es obligatoria.
  if (
    this.rol === 'ORGANIZACION' &&
    !this.direccion.trim()
  ) {
    this.errorMensaje.set(
      'La dirección de la organización es obligatoria.'
    );
    return;
  }

  this.cargando.set(true);

  this.authService.registro({

    nombreUsuario: this.nombreUsuario.trim(),

    correo: correo || undefined,

    contrasena: contrasena || undefined,

    rol: this.rol,

    celular: this.celularCompleto,

    fechaNacimiento:
      this.rol === 'PERSONA_MAYOR' ||
      this.rol === 'ACOMPANANTE' ||
      this.rol === 'VOLUNTARIO'
        ? this.fechaNacimiento
        : undefined,

    genero:
      this.rol === 'PERSONA_MAYOR' ||
      this.rol === 'ACOMPANANTE' ||
      this.rol === 'VOLUNTARIO'
        ? this.genero
        : undefined,

    direccion: this.direccion.trim(),

    aceptaTerminos: this.aceptaTerminos

  }).subscribe({

    next: (response) => {

      this.cargando.set(false);

      this.authService.redirigirSegunRol(
        response.rol
      );
    },

    error: (err) => {

      this.cargando.set(false);

      if (err.status === 400 || err.status === 409) {

        this.errorMensaje.set(
          err.error?.mensaje ||
          'Revisa los datos ingresados.'
        );

      } else {

        this.errorMensaje.set(
          'No se pudo conectar con el servidor. Intenta de nuevo.'
        );
      }
    }

  });
}
}