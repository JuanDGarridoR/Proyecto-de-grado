import { Component, OnInit, signal } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';
import { alCambiar } from '../../core/tiempo-real/tiempo-real.service';
import { mensajeDeError } from '../../core/formato/formato';

/**
 * Bloque para inactivar o reactivar la cuenta, para cualquier rol. Va en la
 * página de perfil, encima de "Eliminar cuenta". Mientras la cuenta está
 * inactiva, el usuario puede entrar, pero sus organizaciones, acompañantes
 * y demás vínculos no lo ven (ver CuentaService en auth-service).
 */
@Component({
  selector: 'app-inactivar-cuenta',
  templateUrl: './inactivar-cuenta.html',
  styleUrls: ['../eliminar-cuenta/eliminar-cuenta.css', './inactivar-cuenta.css']
})
export class InactivarCuenta implements OnInit {

  /** null mientras carga. */
  protected readonly activa = signal<boolean | null>(null);
  protected readonly mostrandoModal = signal(false);
  protected readonly procesando = signal(false);
  protected readonly error = signal('');

  constructor(private authService: AuthService) {
    // El aviso del panel también puede reactivarla.
    alCambiar(['usuarios'], () => this.cargar());
  }

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.authService.cuentaActiva().subscribe({
      next: (activa) => this.activa.set(activa),
      error: () => this.activa.set(null)
    });
  }

  abrirModal(): void {
    this.error.set('');
    this.mostrandoModal.set(true);
  }

  cerrarModal(): void {
    if (this.procesando()) return;
    this.mostrandoModal.set(false);
  }

  inactivar(): void {
    this.procesando.set(true);
    this.error.set('');

    this.authService.inactivarCuenta().subscribe({
      next: () => {
        this.procesando.set(false);
        this.mostrandoModal.set(false);
        this.activa.set(false);
      },
      error: (error) => {
        this.procesando.set(false);
        this.error.set(mensajeDeError(error, 'No se pudo inactivar la cuenta. Intenta de nuevo más tarde.'));
      }
    });
  }

  reactivar(): void {
    this.procesando.set(true);
    this.error.set('');

    this.authService.reactivarCuenta().subscribe({
      next: () => {
        this.procesando.set(false);
        this.activa.set(true);
      },
      error: (error) => {
        this.procesando.set(false);
        this.error.set(mensajeDeError(error, 'No se pudo reactivar la cuenta. Intenta de nuevo más tarde.'));
      }
    });
  }
}
