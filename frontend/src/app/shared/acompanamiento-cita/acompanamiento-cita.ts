import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';

import { AcompananteService } from '../../core/acompanantes/acompanante.service';
import { AuthService } from '../../core/auth/auth.service';
import { CitaMedica } from '../../core/citas-medicas/cita-medica.service';
import { mensajeDeError } from '../../core/formato/formato';
import { Icono } from '../icono/icono';

const MAX_PUNTO = 300;

/**
 * Acompañamiento a una cita médica, en el Seguimiento del acompañante. Si
 * la cita necesita acompañamiento y nadie se ha ofrecido, el acompañante
 * puede decir "Yo la acompaño" y escribir dónde se encuentran; la persona
 * mayor recibe un SMS. Quien aceptó puede cambiar el punto de encuentro o
 * decir que ya no puede ir. Los demás ven quién la acompaña.
 */
@Component({
  selector: 'app-acompanamiento-cita',
  imports: [FormsModule, Icono],
  templateUrl: './acompanamiento-cita.html',
  styleUrl: './acompanamiento-cita.css'
})
export class AcompanamientoCita {
  private readonly acompananteService = inject(AcompananteService);
  private readonly idUsuario = inject(AuthService).getIdUsuario();

  readonly cita = input.required<CitaMedica>();
  readonly idPersonaMayor = input.required<number>();
  readonly nombrePersona = input('');
  /** La cita con el acompañamiento actualizado, para refrescar la lista. */
  readonly actualizada = output<CitaMedica>();

  protected readonly esMia = computed(() => this.cita().idAcompananteCita === this.idUsuario);
  protected readonly tomada = computed(() => !!this.cita().idAcompananteCita && !this.esMia());

  /** Nombre corto (primer nombre) para los textos. */
  protected readonly nombre = computed(() => this.nombrePersona().trim().split(/\s+/)[0] || 'La persona');

  protected readonly formularioAbierto = signal(false);
  protected readonly confirmandoCancelacion = signal(false);
  protected readonly procesando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected punto = '';

  protected readonly MAX_PUNTO = MAX_PUNTO;

  protected abrirFormulario(): void {
    this.punto = this.esMia() ? (this.cita().puntoEncuentro ?? '') : '';
    this.error.set(null);
    this.confirmandoCancelacion.set(false);
    this.formularioAbierto.set(true);
  }

  protected confirmar(): void {
    const punto = this.punto.trim();
    if (!punto) {
      this.error.set('Escribe dónde se van a encontrar para salir a la cita.');
      return;
    }

    this.ejecutar(
      this.acompananteService.aceptarAcompanamientoCita(this.idPersonaMayor(), this.cita().idCita, punto),
      'No se pudo confirmar el acompañamiento.'
    );
  }

  protected cancelar(): void {
    this.ejecutar(
      this.acompananteService.cancelarAcompanamientoCita(this.idPersonaMayor(), this.cita().idCita),
      'No se pudo cancelar el acompañamiento.'
    );
  }

  private ejecutar(peticion: Observable<CitaMedica>, porDefecto: string): void {
    this.procesando.set(true);
    this.error.set(null);

    peticion.subscribe({
      next: (cita) => {
        this.procesando.set(false);
        this.formularioAbierto.set(false);
        this.confirmandoCancelacion.set(false);
        this.actualizada.emit(cita);
      },
      error: (err) => {
        this.procesando.set(false);
        this.error.set(mensajeDeError(err, porDefecto));
      }
    });
  }
}
