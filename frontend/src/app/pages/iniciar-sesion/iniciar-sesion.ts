import {
  Component,
  signal,
  OnDestroy
} from '@angular/core';

import { FormsModule } from '@angular/forms';
import { CampoContrasena } from '../../shared/campo-contrasena/campo-contrasena';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

/** Método de inicio de sesión que está en pantalla. */
type ModoLogin = 'correo' | 'celular';

/**
 * Inicio de sesión con dos métodos: celular con código OTP o correo y
 * contraseña. También tiene el flujo para recuperar la contraseña con un
 * código enviado al celular.
 */
@Component({
  selector: 'app-iniciar-sesion',
  standalone: true,
  imports: [FormsModule, CampoContrasena, RouterLink],
  templateUrl: './iniciar-sesion.html',
  styleUrl: './iniciar-sesion.css'
})
export class IniciarSesion implements OnDestroy {

  modo = signal<ModoLogin>('celular');

  modoRecuperacion = signal(false);

  // Login con correo y contraseña
  correo = '';
  contrasena = '';


  // Login con celular y código OTP
  celularLocal = '';
  codigo = '';

// Recuperación de contraseña
recuperacionOtpEnviado = signal(false);

recuperacionCodigo = '';

nuevaContrasena = '';

confirmarNuevaContrasena = '';

  /** Si ya se pidió el código del login; con eso aparece el campo para escribirlo. */
  otpEnviado = signal(false);

  /** Segundos que faltan para poder pedir otro código (30, igual que en mensajeria-service). */
  segundosReenvio = signal(0);

  private intervaloReenvio: ReturnType<typeof setInterval> | null = null;


  // Estado de la pantalla
  cargando = signal(false);

  enviandoCodigo = signal(false);

  errorMensaje = signal<string | null>(null);

  infoMensaje = signal<string | null>(null);


  constructor(
    private authService: AuthService
  ) {}


  /** Celular con el indicativo de Colombia, como lo guarda el backend: +57 y 10 dígitos. */
  get celularCompleto(): string {

    return `+57${this.celularLocal.replace(/\D/g, '')}`;
  }


  /** Cambia entre celular y correo, y reinicia el flujo del código. */
cambiarModo(modo: ModoLogin): void {

  this.modo.set(modo);

  this.otpEnviado.set(false);
  this.codigo = '';

  this.errorMensaje.set(null);
  this.infoMensaje.set(null);

  this.detenerContador();
  this.segundosReenvio.set(0);
}

/** Abre el flujo de recuperación de contraseña con el formulario limpio. */
abrirRecuperacion(): void {

  this.modoRecuperacion.set(true);

  this.recuperacionOtpEnviado.set(false);

  this.recuperacionCodigo = '';

  this.nuevaContrasena = '';

  this.confirmarNuevaContrasena = '';

  this.celularLocal = '';

  this.errorMensaje.set(null);

  this.infoMensaje.set(null);

  this.detenerContador();

  this.segundosReenvio.set(0);
}

/** Vuelve al login con correo, que es el método que usa contraseña. */
volverAlLogin(): void {

  this.modoRecuperacion.set(false);
  this.modo.set('correo');

  this.recuperacionOtpEnviado.set(false);

  this.recuperacionCodigo = '';

  this.nuevaContrasena = '';

  this.confirmarNuevaContrasena = '';

  this.celularLocal = '';

  this.errorMensaje.set(null);

  this.infoMensaje.set(null);

  this.detenerContador();

  this.segundosReenvio.set(0);
}

/** Paso 1 de la recuperación: comprueba que el celular tenga cuenta y pide el código. */
enviarCodigoRecuperacion(): void {

  this.errorMensaje.set(null);
  this.infoMensaje.set(null);

  const celular =
    this.celularLocal.replace(/\D/g, '');

  if (celular.length !== 10) {

    this.errorMensaje.set(
      'Ingresa un número de celular válido (10 dígitos)'
    );

    return;
  }

  if (this.segundosReenvio() > 0) {
    return;
  }

  this.enviandoCodigo.set(true);

  // Primero se comprueba que el celular tenga cuenta, para no enviar un SMS en vano.
  this.authService
    .celularExiste(this.celularCompleto)
    .subscribe({

      next: (existe) => {

        if (!existe) {

          this.enviandoCodigo.set(false);

          this.errorMensaje.set(
            'No existe una cuenta asociada a este número de celular.'
          );

          return;
        }

        // Se muestra de una vez la pantalla del código y la nueva contraseña,
        // porque el SMS puede tardar unos segundos.
        this.recuperacionOtpEnviado.set(true);

        this.infoMensaje.set(
          'Código solicitado. Puede tardar unos segundos en llegar.'
        );

        this.iniciarContador();

        // No se deja la pantalla en "Enviando..." mientras TextBee responde.
        this.enviandoCodigo.set(false);

        this.authService
          .enviarOtp(this.celularCompleto)
          .subscribe({

            next: () => {

              console.log(
                'Código de recuperación enviado correctamente'
              );

            },

            error: (error) => {

              console.error(
                'Error al enviar código de recuperación:',
                error
              );

              this.errorMensaje.set(
                'No se pudo enviar el código. Intenta nuevamente.'
              );

              this.recuperacionOtpEnviado.set(false);

              this.detenerContador();

              this.segundosReenvio.set(0);
            }

          });

      },

      error: (error) => {

        this.enviandoCodigo.set(false);

        console.error(
          'Error al verificar celular:',
          error
        );

        this.errorMensaje.set(
          'No se pudo verificar el número. Intenta nuevamente.'
        );
      }

    });
}

/** Paso 2: valida el formulario y cambia la contraseña con el código recibido. */
restablecerContrasena(): void {

  this.errorMensaje.set(null);
  this.infoMensaje.set(null);


  if (!this.recuperacionCodigo.trim()) {

    this.errorMensaje.set(
      'Ingresa el código de verificación'
    );

    return;
  }


  if (!this.nuevaContrasena.trim()) {

    this.errorMensaje.set(
      'Ingresa una nueva contraseña'
    );

    return;
  }


  if (this.nuevaContrasena.length < 6) {

    this.errorMensaje.set(
      'La contraseña debe tener mínimo 6 caracteres'
    );

    return;
  }


  if (!this.confirmarNuevaContrasena.trim()) {

    this.errorMensaje.set(
      'Confirma tu nueva contraseña'
    );

    return;
  }


  if (
    this.nuevaContrasena !==
    this.confirmarNuevaContrasena
  ) {

    this.errorMensaje.set(
      'Las contraseñas no coinciden'
    );

    return;
  }


  this.cargando.set(true);


  this.authService
    .restablecerContrasena({

      celular: this.celularCompleto,

      codigo: this.recuperacionCodigo,

      contrasena: this.nuevaContrasena,

      confirmarContrasena:
        this.confirmarNuevaContrasena

    })
    .subscribe({

      next: () => {

        this.cargando.set(false);

        this.infoMensaje.set(
          'Contraseña actualizada correctamente. Ya puedes iniciar sesión.'
        );

        this.detenerContador();

        this.segundosReenvio.set(0);

        this.recuperacionOtpEnviado.set(false);

        this.nuevaContrasena = '';

        this.confirmarNuevaContrasena = '';

        this.recuperacionCodigo = '';

      },

      error: (err) => {

        this.cargando.set(false);

        console.error('ERROR RESTABLECER CONTRASEÑA:', err);
        console.error('STATUS:', err.status);
        console.error('ERROR BODY:', err.error);

        if (err.status === 400) {

          this.errorMensaje.set(
            err.error?.mensaje ||
            err.error?.message ||
            'Código incorrecto o expirado.'
          );

        } else {

          this.errorMensaje.set(
            'No se pudo actualizar la contraseña. Intenta nuevamente.'
          );
        }

      }

    });
}

  /** Pide (o vuelve a pedir) el código para entrar con el celular. */
  enviarCodigo(): void {

    this.errorMensaje.set(null);

    this.infoMensaje.set(null);


    // Mientras corre el contador no se puede pedir otro código.
    if (this.segundosReenvio() > 0) {

      return;
    }


    const celular =
      this.celularLocal.replace(/\D/g, '');

    if (celular.length !== 10) {

      this.errorMensaje.set(
        'Ingresa un número de celular válido (10 dígitos)'
      );

      return;
    }


    this.enviandoCodigo.set(true);

this.authService
  .celularExiste(this.celularCompleto)
  .subscribe({

    next: (existe) => {

      if (!existe) {
        this.enviandoCodigo.set(false);

        this.errorMensaje.set(
          'No existe una cuenta asociada a este número de celular.'
        );

        return;
      }

      this.authService
        .enviarOtp(this.celularCompleto)
        .subscribe({

          next: () => {

            this.enviandoCodigo.set(false);

            this.otpEnviado.set(true);

            this.infoMensaje.set(
              'Código solicitado. Puede tardar unos segundos en llegar.'
            );

            this.iniciarContador();
          },

          error: (error) => {

            this.enviandoCodigo.set(false);

            console.error(
              'Error al enviar código:',
              error
            );

            this.errorMensaje.set(
              'No se pudo enviar el código. Verifica el número.'
            );
          }
        });
    },

    error: (error) => {

      this.enviandoCodigo.set(false);

      console.error(
        'Error al verificar el celular:',
        error
      );

      this.errorMensaje.set(
        'No se pudo verificar el número. Intenta nuevamente.'
      );
    }
  });
  }

  /** Arranca el contador de 30 segundos para volver a pedir el código. */
  private iniciarContador(): void {

    // Evita que queden dos intervalos corriendo.
    this.detenerContador();

    this.segundosReenvio.set(30);


    this.intervaloReenvio = setInterval(() => {

      const segundosActuales =
        this.segundosReenvio();


      if (segundosActuales <= 1) {

        this.segundosReenvio.set(0);

        this.detenerContador();

        return;
      }


      this.segundosReenvio.set(
        segundosActuales - 1
      );

    }, 1000);
  }


  private detenerContador(): void {

    if (this.intervaloReenvio !== null) {

      clearInterval(this.intervaloReenvio);

      this.intervaloReenvio = null;
    }
  }


  /** Envía el formulario según el método elegido. */
  onSubmit(): void {

    this.errorMensaje.set(null);


    if (this.modo() === 'correo') {

      this.loginPorCorreo();

    } else {

      this.loginPorCelular();
    }
  }


  /** Login con correo y contraseña. */
  private loginPorCorreo(): void {

    this.cargando.set(true);

    this.authService
      .login({
        correo: this.correo,
        contrasena: this.contrasena
      })
      .subscribe({

        next: (response) => {

          this.cargando.set(false);

          this.authService
            .redirigirSegunRol(response.rol);
        },

        error: (err) => {

          this.cargando.set(false);

if (err.status === 401) {

  // Pensado para cuentas sin contraseña (registradas solo con celular). Hoy
  // el backend responde "Correo o contraseña incorrectos" en ambos casos.
  const mensaje =
    err.error?.mensaje ||
    err.error?.message ||
    '';

  if (
    mensaje.toLowerCase().includes('contraseña') &&
    (
      mensaje.toLowerCase().includes('no tiene') ||
      mensaje.toLowerCase().includes('no registrada') ||
      mensaje.toLowerCase().includes('no registrada')
    )
  ) {
    this.errorMensaje.set(
      'Esta cuenta no tiene una contraseña registrada. Intenta iniciar sesión con tu número de celular.'
    );

  } else {
    this.errorMensaje.set(
      'Correo o contraseña incorrectos'
    );
  }

} else {

            this.errorMensaje.set(
              'No se pudo conectar con el servidor. Intenta de nuevo.'
            );
          }
        }
      });
  }


  /** Login con el celular y el código que llegó por SMS. */
  private loginPorCelular(): void {

    if (!this.codigo.trim()) {

      this.errorMensaje.set(
        'Ingresa el código que te llegó por SMS'
      );

      return;
    }


    this.cargando.set(true);

    this.authService
      .loginOtp({
        celular: this.celularCompleto,
        codigo: this.codigo
      })
      .subscribe({

        next: (response) => {

          this.cargando.set(false);

          this.detenerContador();

          this.segundosReenvio.set(0);

          this.authService
            .redirigirSegunRol(response.rol);
        },

        error: (err) => {

          this.cargando.set(false);

          if (err.status === 401) {

            this.errorMensaje.set(
              'Código incorrecto o expirado'
            );

          } else if (err.status === 404) {

            this.errorMensaje.set(
              'No existe una cuenta con ese celular'
            );

          } else {

            this.errorMensaje.set(
              'No se pudo conectar con el servidor. Intenta de nuevo.'
            );
          }
        }
      });
  }


  ngOnDestroy(): void {

    this.detenerContador();
  }
}