import { Component, OnInit, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Icono } from '../icono/icono';

/** Lo mismo que acepta el backend. */
const LARGO_MAXIMO = 50;

/**
 * Modal para cambiar el parentesco entre una persona mayor y un acompañante
 * (por ejemplo, de "Vecina" a "Sobrina"). Lo usan la persona mayor desde sus
 * contactos y el acompañante desde sus personas mayores.
 *
 * Valida y emite guardar; la página hace la petición y le pasa guardando y
 * el error del servidor. Se cierra quitándolo del template.
 */
@Component({
  selector: 'app-editar-parentesco',
  standalone: true,
  imports: [FormsModule, Icono],
  templateUrl: './editar-parentesco.html',
  styleUrl: '../solicitud-acompanamiento/solicitud-acompanamiento.css'
})
export class EditarParentesco implements OnInit {

  /** Nombre de la otra persona del vínculo. */
  readonly nombre = input.required<string>();
  readonly relacionActual = input<string | null | undefined>(null);
  readonly descripcion = input.required<string>();
  readonly placeholder = input('Ej. Hijo, hija, sobrino, vecina...');

  readonly guardando = input(false);
  readonly errorServidor = input<string | null>(null);

  readonly guardar = output<string>();
  readonly cerrar = output<void>();

  protected readonly largoMaximo = LARGO_MAXIMO;
  protected relacion = '';
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.relacion = this.relacionActual() ?? '';
  }

  protected validarYGuardar(): void {
    this.error.set(null);
    const relacion = this.relacion.trim();

    if (!relacion) {
      this.error.set('Escribe el parentesco.');
      return;
    }
    if (relacion.length > LARGO_MAXIMO) {
      this.error.set(`El parentesco puede tener máximo ${LARGO_MAXIMO} caracteres.`);
      return;
    }
    if (relacion === (this.relacionActual() ?? '').trim()) {
      this.cerrar.emit();
      return;
    }

    this.guardar.emit(relacion);
  }
}
