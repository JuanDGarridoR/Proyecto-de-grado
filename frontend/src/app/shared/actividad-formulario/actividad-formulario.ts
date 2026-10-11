import { Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import {
  ActividadRequest,
  MAX_FRECUENCIA_DIAS,
  errorFrecuencia,
  fechaHoy,
  textoFrecuencia
} from '../../core/actividades/actividad.service';
import { Icono } from '../icono/icono';

/** Organización que se puede elegir en el formulario (propuestas). */
export interface OpcionOrganizacion {
  idOrganizacion: number;
  nombre: string;
}

/** Lo que entrega el formulario al confirmar. idOrganizacion solo viene si se pidió elegirla. */
export interface ActividadFormularioDatos extends ActividadRequest {
  idOrganizacion: number | null;
}

/**
 * Modal para crear una actividad: formulario, validación y confirmación
 * con el resumen. Lo usa la organización para crear sus actividades, y el
 * voluntario y la persona mayor para proponerlas; en ese caso recibe las
 * organizaciones y pide elegir a cuál se presenta.
 *
 * La página decide qué hacer con los datos (emitidos en guardar) y lo
 * cierra quitándolo del template cuando se guardan bien.
 */
@Component({
  selector: 'app-actividad-formulario',
  standalone: true,
  imports: [FormsModule, Icono],
  templateUrl: './actividad-formulario.html',
  styleUrls: ['../tarjeta-actividad/actividades-modales.css']
})
export class ActividadFormulario {

  readonly titulo = input('Crear actividad');
  readonly subtitulo = input('Registra una nueva actividad para tu organización.');
  readonly textoEnviar = input('Crear actividad');
  readonly preguntaConfirmacion = input('¿Crear esta actividad?');
  readonly textoConfirmar = input('Confirmar y crear');

  /** Si llega, el formulario pide elegir una de estas organizaciones. */
  readonly organizaciones = input<OpcionOrganizacion[] | null>(null);

  /** Error que devolvió el servidor al guardar. */
  readonly errorServidor = input<string | null>(null);

  readonly guardar = output<ActividadFormularioDatos>();
  readonly cerrar = output<void>();

  protected actividad: ActividadRequest = {
    nombre: '',
    descripcion: null,
    fecha: null,
    hora: null,
    lugar: null,
    tipo: null,
    cupos: null,
    responsable: null,
    frecuenciaDias: null
  };

  // Repetición: solo se envía cadaDias si está marcada.
  protected repetir = false;
  protected cadaDias: number | null = 7;
  protected readonly maxFrecuencia = MAX_FRECUENCIA_DIAS;
  protected readonly frecuencia = textoFrecuencia;

  protected idOrganizacion: number | null = null;

  protected readonly error = signal<string | null>(null);
  protected readonly confirmando = signal(false);

  protected nombreOrganizacion(): string {
    return this.organizaciones()?.find((o) => o.idOrganizacion === this.idOrganizacion)?.nombre ?? '';
  }

  /** Valida el formulario y pide confirmación antes de guardar. */
  protected revisar(): void {
    const error = this.validar();

    this.error.set(error);

    if (!error) {
      this.confirmando.set(true);
    }
  }

  protected confirmar(): void {
    this.confirmando.set(false);
    this.guardar.emit({
      ...this.actividad,
      frecuenciaDias: this.repetir ? this.cadaDias : null,
      idOrganizacion: this.idOrganizacion
    });
  }

  private validar(): string | null {
    if (this.organizaciones() && this.idOrganizacion === null) {
      return 'Selecciona la organización a la que quieres presentar la actividad';
    }

    if (!this.actividad.nombre.trim()) {
      return 'El nombre de la actividad es obligatorio';
    }

    if (!this.actividad.fecha) {
      return 'La fecha de la actividad es obligatoria';
    }

    if (!this.actividad.hora) {
      return 'La hora de la actividad es obligatoria';
    }

    if (!this.actividad.lugar?.trim()) {
      return 'El lugar de la actividad es obligatorio';
    }

    if (this.actividad.fecha < fechaHoy()) {
      return 'No se puede crear una actividad con una fecha anterior a hoy';
    }

    if (this.repetir) {
      return errorFrecuencia(this.cadaDias);
    }

    return null;
  }
}
