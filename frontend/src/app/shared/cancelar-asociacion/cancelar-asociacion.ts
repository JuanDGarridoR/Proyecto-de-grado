import { Component, input, output } from '@angular/core';
import { Icono } from '../icono/icono';

/**
 * Confirmación estándar para cancelar el vínculo con una persona mayor, un
 * acompañante o una organización. La página decide cuándo mostrarla y qué
 * hacer al confirmar. Los textos se pueden cambiar para confirmar otra
 * acción sobre el vínculo (por ejemplo, inactivarlo).
 */
@Component({
  selector: 'app-cancelar-asociacion',
  imports: [Icono],
  templateUrl: './cancelar-asociacion.html',
  styleUrl: './cancelar-asociacion.css'
})
export class CancelarAsociacion {

  /** Con quién se cancela el vínculo. */
  readonly nombre = input<string | null | undefined>(null);

  /** Qué pasa después de cancelar, visto desde quien cancela. */
  readonly advertencia = input('');

  readonly titulo = input('¿Cancelar asociación?');
  /** Va antes del nombre: "Estás a punto de {accion} {nombre}." */
  readonly accion = input('cancelar la asociación con');
  readonly textoConfirmar = input('Cancelar asociación');
  readonly icono = input('x');

  readonly confirmar = output<void>();
  readonly cerrar = output<void>();
}
