import { Component, Input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HojaFecha } from '../hoja-fecha/hoja-fecha';

import { Icono } from '../icono/icono';
import { fechaHoy, textoFrecuencia } from '../../core/actividades/actividad.service';
import { coordenadasDe, enlaceComoLlegar } from '../../core/mapa/mapa';

/** Campos que comparten Actividad y ActividadDisponible. */
export interface ActividadTarjeta {
  nombre: string;
  descripcion: string | null;
  fecha: string | null;
  hora: string | null;
  horaFin?: string | null;
  lugar: string | null;
  tipo: string | null;
  cupos: number | null;
  frecuenciaDias?: number | null;
  latitud?: number | null;
  longitud?: number | null;
}

/**
 * Tarjeta de actividad usada en organización, persona mayor y acompañante,
 * para que se vea igual en todos los perfiles. Los botones de cada perfil
 * se pasan como contenido:
 *
 *   <app-tarjeta-actividad [actividad]="a">
 *     <button ...>Inscribirme</button>
 *   </app-tarjeta-actividad>
 */
@Component({
  selector: 'app-tarjeta-actividad',
  imports: [DatePipe, Icono, HojaFecha],
  templateUrl: './tarjeta-actividad.html',
  styleUrl: './tarjeta-actividad.css'
})
export class TarjetaActividad {

  @Input({ required: true }) actividad!: ActividadTarjeta;

  /** Las actividades que ya pasaron se ven atenuadas, como en el historial. */
  protected get pasada(): boolean {
    const fecha = this.actividad.fecha;
    return !!fecha && fecha < fechaHoy();
  }

  /** Ruta en Google Maps hasta el punto de encuentro, o null si no tiene. */
  protected get comoLlegar(): string | null {
    const punto = coordenadasDe(this.actividad);
    return punto ? enlaceComoLlegar(punto) : null;
  }

  protected readonly frecuencia = textoFrecuencia;
}
