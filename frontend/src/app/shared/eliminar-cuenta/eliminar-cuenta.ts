import { Component, signal } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { MAX_COMENTARIO_RETIRO, RazonRetiro, razonesRetiroPara } from '../../core/auth/razones-retiro';

/**
 * Bloque "Zona de peligro" para eliminar la cuenta, para cualquier rol. Va
 * en la página de perfil de cada panel, y no en el menú del usuario, para
 * que no quede tan a la mano.
 *
 * Pide la razón del retiro: si es una persona mayor, sus organizaciones la
 * ven (por ejemplo, para saber que falleció).
 */
@Component({
  selector: 'app-eliminar-cuenta',
  templateUrl: './eliminar-cuenta.html',
  styleUrl: './eliminar-cuenta.css'
})
export class EliminarCuenta {

  /** La organización tiene su propia lista de razones. */
  protected readonly razones: RazonRetiro[];
  protected readonly maxComentario = MAX_COMENTARIO_RETIRO;

  protected readonly mostrandoModal = signal(false);
  protected readonly razon = signal('');
  protected readonly comentario = signal('');
  protected readonly textoConfirmacion = signal('');
  protected readonly eliminando = signal(false);
  protected readonly error = signal('');

  /** Solo a la persona mayor se le avisa que sus organizaciones verán la razón. */
  protected readonly esPersonaMayor: boolean;

  constructor(private authService: AuthService) {
    const rol = this.authService.getRol();
    this.esPersonaMayor = rol === 'PERSONA_MAYOR';
    this.razones = razonesRetiroPara(rol);
  }

  abrirModal(): void {
    this.razon.set('');
    this.comentario.set('');
    this.textoConfirmacion.set('');
    this.error.set('');
    this.mostrandoModal.set(true);
  }

  cerrarModal(): void {
    if (this.eliminando()) return;
    this.mostrandoModal.set(false);
  }

  /** Para confirmar hay que elegir una razón y escribir ELIMINAR. */
  confirmacionValida(): boolean {
    return this.razon() !== '' && this.textoConfirmacion().trim() === 'ELIMINAR';
  }

  confirmar(): void {
    if (!this.confirmacionValida()) return;

    this.eliminando.set(true);
    this.error.set('');

    this.authService.eliminarCuenta(this.razon(), this.comentario().trim() || null).subscribe({
      next: () => {
        this.eliminando.set(false);
        this.mostrandoModal.set(false);
        this.authService.logout();
      },
      error: (error) => {
        console.error('Error al eliminar la cuenta:', error);
        this.eliminando.set(false);
        this.error.set('No se pudo eliminar la cuenta. Intenta de nuevo más tarde.');
      }
    });
  }
}
