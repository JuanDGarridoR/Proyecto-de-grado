import { Component, computed, input } from '@angular/core';

import { Icono } from '../icono/icono';
import { Avatar } from '../avatar/avatar';
import { Donacion } from '../../core/donaciones/donacion.service';
import {
  ESTADOS_DONACION,
  formatearCantidad,
  formatearFecha,
  formatearFechaCorta,
  iconoCategoria,
  textoCategoria,
} from '../../core/donaciones/catalogo';
import { fechaLocal } from '../../core/fechas/fechas';

/**
 * Una donación con lo que trae, cuándo se entrega y su estado. El donante
 * ve la organización (vista "donante"); la organización ve quién dona
 * (vista "organizacion"). Las acciones de cada página (cancelar, marcar
 * recibida...) se proyectan con el atributo tarjeta-acciones.
 */
@Component({
  selector: 'app-tarjeta-donacion',
  imports: [Icono, Avatar],
  templateUrl: './tarjeta-donacion.html',
  styleUrl: './tarjeta-donacion.css',
})
export class TarjetaDonacion {
  readonly donacion = input.required<Donacion>();
  readonly vista = input<'donante' | 'organizacion'>('donante');

  protected readonly estado = computed(() => ESTADOS_DONACION[this.donacion().estado]);

  /** Programada para un día que ya pasó: la organización debe resolverla. */
  protected readonly atrasada = computed(
    () =>
      this.donacion().estado === 'PROGRAMADA' && this.donacion().fechaEntrega < fechaLocal(new Date()),
  );

  protected readonly nombre = computed(() =>
    this.vista() === 'donante'
      ? this.donacion().nombreOrganizacion
      : (this.donacion().nombreDonante ?? 'Donante'),
  );

  protected readonly foto = computed(() =>
    this.vista() === 'donante' ? this.donacion().fotoOrganizacion : this.donacion().fotoDonante,
  );

  protected readonly detalle = computed(() => {
    const d = this.donacion();
    if (this.vista() === 'donante') {
      return d.direccionOrganizacion;
    }

    const rol = d.rolDonante === 'VOLUNTARIO' ? 'Voluntario' : 'Acompañante';
    return d.celularDonante ? `${rol} · ${d.celularDonante}` : rol;
  });

  protected readonly formatearCantidad = formatearCantidad;
  protected readonly formatearFecha = formatearFecha;
  protected readonly formatearFechaCorta = formatearFechaCorta;
  protected readonly iconoCategoria = iconoCategoria;
  protected readonly textoCategoria = textoCategoria;
}
