import { Component, Input, OnInit, signal } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';
import { mensajeDeError } from '../../core/formato/formato';
import { Icono } from '../icono/icono';

/** Rol al que puede pasar cada uno (RF-76): solo persona mayor ↔ acompañante. */
const OTRO_ROL: Record<string, string> = {
  PERSONA_MAYOR: 'ACOMPANANTE',
  ACOMPANANTE: 'PERSONA_MAYOR'
};

const NOMBRE_ROL: Record<string, string> = {
  PERSONA_MAYOR: 'persona mayor',
  ACOMPANANTE: 'acompañante'
};

/**
 * Óvalo del rol en la barra superior. Para una persona mayor o un
 * acompañante es un botón: al confirmarlo, la misma cuenta pasa al otro rol
 * (y si aún no lo tenía, el backend se lo activa con su misma información).
 * Para los demás roles es solo una etiqueta.
 */
@Component({
  selector: 'app-cambio-rol',
  imports: [Icono],
  templateUrl: './cambio-rol.html',
  styleUrls: ['../eliminar-cuenta/eliminar-cuenta.css', './cambio-rol.css']
})
export class CambioRol implements OnInit {

  @Input() roleLabel = '';

  /** ACOMPANANTE o PERSONA_MAYOR, o null si este rol no puede cambiar. */
  protected readonly otroRol: string | null;
  protected readonly nombreOtroRol: string;

  /** Si la cuenta ya tiene el otro rol; null mientras se consulta. */
  protected readonly tieneOtroRol = signal<boolean | null>(null);
  protected readonly confirmando = signal(false);
  protected readonly procesando = signal(false);
  protected readonly error = signal('');

  constructor(private authService: AuthService) {
    this.otroRol = OTRO_ROL[authService.getRol() ?? ''] ?? null;
    this.nombreOtroRol = this.otroRol ? NOMBRE_ROL[this.otroRol] : '';
  }

  ngOnInit(): void {
    if (!this.otroRol) {
      return;
    }

    this.authService.roles().subscribe({
      next: (roles) => this.tieneOtroRol.set(roles.includes(this.otroRol!)),
      // Si no se sabe, se ofrece igual: el backend decide si lo activa.
      error: () => this.tieneOtroRol.set(false)
    });
  }

  abrir(): void {
    this.error.set('');
    this.confirmando.set(true);
  }

  cerrar(): void {
    if (!this.procesando()) {
      this.confirmando.set(false);
    }
  }

  cambiar(): void {
    if (!this.otroRol) {
      return;
    }

    this.procesando.set(true);
    this.error.set('');

    this.authService.cambiarRol(this.otroRol).subscribe({
      next: (sesion) => {
        this.procesando.set(false);
        this.confirmando.set(false);
        this.authService.redirigirSegunRol(sesion.rol);
      },
      error: (error) => {
        this.procesando.set(false);
        // auth-service responde { mensaje } (como el login), no texto plano.
        const mensaje = (error as { error?: { mensaje?: string } } | null)?.error?.mensaje;
        this.error.set(mensaje || mensajeDeError(error, 'No se pudo cambiar de rol. Intenta de nuevo más tarde.'));
      }
    });
  }
}
