import { Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Icono } from '../icono/icono';

/** Lo que entrega el formulario: celular ya con +57 y la relación. */
export interface DatosSolicitudAcompanamiento {
  celular: string;
  relacion: string;
}

/**
 * Formulario para enviar una solicitud de acompañamiento por celular. Lo
 * usan la persona mayor (agrega a un acompañante) y el acompañante (pide
 * acompañar a una persona mayor); la otra parte la acepta o la rechaza.
 *
 * Valida los campos y emite enviar; la página hace la petición y le pasa
 * enviando y el error del servidor. Se cierra quitándolo del template.
 */
@Component({
  selector: 'app-solicitud-acompanamiento',
  standalone: true,
  imports: [FormsModule, Icono],
  templateUrl: './solicitud-acompanamiento.html',
  styleUrl: './solicitud-acompanamiento.css'
})
export class SolicitudAcompanamientoModal {

  readonly titulo = input.required<string>();
  readonly descripcion = input.required<string>();
  readonly etiquetaCelular = input.required<string>();
  readonly placeholderRelacion = input('Ej. Hijo, hija, hermano...');

  readonly enviando = input(false);
  readonly errorServidor = input<string | null>(null);

  readonly enviar = output<DatosSolicitudAcompanamiento>();
  readonly cerrar = output<void>();

  protected celular = '';
  protected relacion = '';

  protected readonly error = signal<string | null>(null);

  protected validarYEnviar(): void {
    this.error.set(null);

    if (!this.celular.trim() || !this.relacion.trim()) {
      this.error.set('Por favor completa todos los campos.');
      return;
    }

    const celular = this.celular.trim();

    if (!/^\d{10}$/.test(celular)) {
      this.error.set('Ingresa un número de celular válido de 10 dígitos.');
      return;
    }

    this.enviar.emit({ celular: '+57' + celular, relacion: this.relacion.trim() });
  }
}
