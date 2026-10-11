import { Component, input, output } from '@angular/core';

import { SignoVitalResponse } from '../../core/signos-vitales/signos-vitales.services';
import { SignosVitalesLista } from '../signos-vitales-lista/signos-vitales-lista';

/**
 * Modal estándar con los últimos registros de signos vitales de una persona
 * mayor. Solo los muestra: cada página los carga con el endpoint de su rol.
 * La lista es SignosVitalesLista, la misma del historial de la persona mayor.
 */
@Component({
  selector: 'app-modal-signos-vitales',
  imports: [SignosVitalesLista],
  templateUrl: './modal-signos-vitales.html',
  styleUrl: './modal-signos-vitales.css'
})
export class ModalSignosVitales {

  readonly nombre = input<string | null | undefined>(null);
  readonly registros = input<SignoVitalResponse[]>([]);
  readonly cargando = input(false);
  readonly error = input<string | null>(null);

  readonly cerrar = output<void>();
}
