import { Component, input, output } from '@angular/core';

import { Icono } from '../icono/icono';

/** Acompañante tal como lo muestra el modal. */
export interface AcompananteResumen {
  idUsuario: number;
  nombre: string;
  celular: string;
  relacion: string | null;
}

/**
 * Modal estándar con los acompañantes de una persona mayor. Solo
 * presenta: cada página carga la lista con el endpoint que le
 * corresponde a su rol.
 */
@Component({
  selector: 'app-modal-acompanantes',
  imports: [Icono],
  templateUrl: './modal-acompanantes.html',
  styleUrl: './modal-acompanantes.css'
})
export class ModalAcompanantes {

  readonly nombre = input<string | null | undefined>(null);
  readonly acompanantes = input<AcompananteResumen[]>([]);
  readonly cargando = input(false);
  readonly error = input<string | null>(null);

  readonly cerrar = output<void>();
}
