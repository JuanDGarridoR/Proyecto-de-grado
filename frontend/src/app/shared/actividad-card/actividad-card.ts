import { Component, Input } from '@angular/core';
import { DatePipe } from '@angular/common';

import { Icon } from '../icon/icon';
import { fechaHoy, textoFrecuencia } from '../../core/actividades/actividad.service';

/** Campos que comparten Actividad y ActividadDisponible. */
export interface ActividadTarjeta {
  nombre: string;
  descripcion: string | null;
  fecha: string | null;
  hora: string | null;
  lugar: string | null;
  tipo: string | null;
  cupos: number | null;
  frecuenciaDias?: number | null;
}

/**
 * Tarjeta de actividad usada en organización, persona mayor y acompañante,
 * para que se vea igual en todos los perfiles. Los botones de cada perfil
 * se pasan como contenido:
 *
 *   <app-actividad-card [actividad]="a">
 *     <button ...>Inscribirme</button>
 *   </app-actividad-card>
 */
@Component({
  selector: 'app-actividad-card',
  imports: [DatePipe, Icon],
  templateUrl: './actividad-card.html',
  styleUrl: './actividad-card.css'
})
export class ActividadCard {

  @Input({ required: true }) actividad!: ActividadTarjeta;

  /** Las actividades que ya pasaron se ven atenuadas, como en el historial. */
  protected get pasada(): boolean {
    const fecha = this.actividad.fecha;
    return !!fecha && fecha < fechaHoy();
  }

  protected readonly frecuencia = textoFrecuencia;
}
