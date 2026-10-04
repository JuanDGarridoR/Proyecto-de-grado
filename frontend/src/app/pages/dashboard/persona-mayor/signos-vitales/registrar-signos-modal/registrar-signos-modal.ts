import { Component, HostListener, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { timeout } from 'rxjs';

import {
  SignosVitalesService,
  SignoVitalRequest
} from '../../../../../core/signos-vitales/signos-vitales.services';
import { validarMedicion } from '../../../../../core/signos-vitales/rangos';
import { Imc, calcularImc } from '../../../../../core/signos-vitales/imc';
import { Icon } from '../../../../../shared/icon/icon';

/**
 * Formulario emergente para que la persona mayor registre sus propios
 * signos vitales. Sigue la misma lógica que el registro de la
 * organización: valida los valores, muestra un resumen para confirmar y
 * solo entonces guarda.
 */
@Component({
  selector: 'app-registrar-signos-modal',
  standalone: true,
  imports: [FormsModule, Icon],
  templateUrl: './registrar-signos-modal.html',
  styleUrl: './registrar-signos-modal.css'
})
export class RegistrarSignosModal {
  private signosVitalesService = inject(SignosVitalesService);

  readonly cerrar = output<void>();
  readonly registrado = output<void>();

  presionSistolica: number | null = null;
  presionDiastolica: number | null = null;
  frecuenciaCardiaca: number | null = null;
  temperatura: number | null = null;
  saturacionOxigeno: number | null = null;
  frecuenciaRespiratoria: number | null = null;
  peso: number | null = null;
  estatura: number | null = null;

  observaciones = '';

  protected readonly confirmando = signal(false);
  protected readonly guardando = signal(false);
  protected readonly mensajeError = signal('');

  @HostListener('document:keydown.escape')
  protected alPresionarEscape(): void {
    this.cancelar();
  }

  protected revisar(): void {
    this.mensajeError.set('');

    // Valores posibles y presión completa (sistólica + diastólica)
    const errorMedicion = validarMedicion(this.datos());

    if (errorMedicion) {
      this.mensajeError.set(errorMedicion);
      return;
    }

    this.confirmando.set(true);
  }

  protected volverAlFormulario(): void {
    if (this.guardando()) return;
    this.confirmando.set(false);
  }

  protected confirmarRegistro(): void {
    if (this.guardando()) return;

    this.guardando.set(true);

    this.signosVitalesService
      .registrarPropio(this.datos())
      .pipe(timeout(20000))
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.registrado.emit();
        },

        error: (error) => {
          console.error('Error registrando signos vitales:', error);

          this.guardando.set(false);
          this.confirmando.set(false);

          if (error.status === 400 && typeof error.error === 'string') {
            // Valores imposibles o presión incompleta (validación del servidor)
            this.mensajeError.set(error.error);
          } else if (error.status === 0 || error.status >= 500 || error.name === 'TimeoutError') {
            this.mensajeError.set(
              'El servidor no confirmó el registro. Revisa tu historial antes de volver a intentarlo para no duplicarlo.'
            );
          } else {
            this.mensajeError.set('No se pudieron registrar los signos vitales.');
          }
        }
      });
  }

  protected cancelar(): void {
    if (this.guardando()) return;
    this.cerrar.emit();
  }

  /** IMC con el peso y la estatura ingresados, para el resumen. */
  protected imc(): Imc | null {
    return calcularImc(this.peso, this.estatura);
  }

  limpiarFormulario(): void {
    this.presionSistolica = null;
    this.presionDiastolica = null;
    this.frecuenciaCardiaca = null;
    this.temperatura = null;
    this.saturacionOxigeno = null;
    this.frecuenciaRespiratoria = null;
    this.peso = null;
    this.estatura = null;

    this.observaciones = '';
    this.mensajeError.set('');
  }

  private datos(): SignoVitalRequest {
    return {
      presionSistolica: this.presionSistolica,
      presionDiastolica: this.presionDiastolica,
      frecuenciaCardiaca: this.frecuenciaCardiaca,
      temperatura: this.temperatura,
      saturacionOxigeno: this.saturacionOxigeno,
      frecuenciaRespiratoria: this.frecuenciaRespiratoria,
      peso: this.peso,
      estatura: this.estatura,
      observaciones: this.observaciones
    };
  }
}
