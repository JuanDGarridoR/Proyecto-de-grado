import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

import {
  OrganizacionService,
  OrganizacionSolicitud
} from '../../../../core/organizacion/organizacion.service';

import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';
import { Icon } from '../../../../shared/icon/icon';
import { RecomendacionesOrganizacionesComponent } from './recomendaciones/recomendaciones';

/**
 * Organizaciones de la persona mayor: solicitudes pendientes para aceptar o
 * rechazar y organizaciones con vínculo aceptado. Toda acción pasa por un
 * modal de confirmación.
 */
@Component({
  selector: 'app-organizaciones',
  standalone: true,
  imports: [Icon, CommonModule, RecomendacionesOrganizacionesComponent],
  templateUrl: './organizaciones.html',
  styleUrl: './organizaciones.css'
})
export class Organizaciones implements OnInit {

  protected readonly organizaciones =
    signal<OrganizacionSolicitud[]>([]);

  protected readonly solicitudes =
    signal<OrganizacionSolicitud[]>([]);

  /** Organización cuya solicitud se está enviando; deshabilita sus botones. */
  protected readonly procesandoSolicitud =
    signal<number | null>(null);

  protected readonly mensaje =
    signal<string | null>(null);

  protected readonly error =
    signal<string | null>(null);

    protected readonly modalAbierto =
  signal(false);

protected readonly organizacionSeleccionada =
  signal<OrganizacionSolicitud | null>(null);

/** Qué se confirma en el modal: aceptar, rechazar o cancelar el vínculo. */
protected readonly accionPendiente =
  signal<'aceptar' | 'rechazar' | 'cancelar' | null>(null);

  constructor(
    private organizacionService: OrganizacionService
  ) {
    alCambiar(['organizaciones', 'usuarios'], () => {
      this.cargarOrganizaciones();
      this.cargarSolicitudes();
    });
  }

  ngOnInit(): void {
    this.cargarOrganizaciones();
    this.cargarSolicitudes();
  }

  cargarOrganizaciones(): void {
    this.organizacionService.obtenerOrganizaciones().subscribe({
      next: (organizaciones) => {
        this.organizaciones.set(organizaciones);
      },

      error: (error) => {
        console.error(
          'Error al cargar organizaciones:',
          error
        );

        this.organizaciones.set([]);
      }
    });
  }

  cargarSolicitudes(): void {
    this.organizacionService
      .obtenerSolicitudesOrganizaciones()
      .subscribe({

        next: (solicitudes) => {
          this.solicitudes.set(solicitudes);
        },

        error: (error) => {
          console.error(
            'Error al cargar solicitudes:',
            error
          );

          this.solicitudes.set([]);
        }

      });
  }

/** Abre la confirmación para aceptar la solicitud. */
aceptarSolicitud(
  idOrganizacion: number
): void {

  const organizacion = this.solicitudes().find(
    (item) => item.idOrganizacion === idOrganizacion
  );

  if (!organizacion) {
    return;
  }

  this.organizacionSeleccionada.set(organizacion);
  this.accionPendiente.set('aceptar');
  this.modalAbierto.set(true);
}

  /** Abre la confirmación para rechazar la solicitud. */
  rechazarSolicitud(
  idOrganizacion: number
): void {

  const organizacion = this.solicitudes().find(
    (item) => item.idOrganizacion === idOrganizacion
  );

  if (!organizacion) {
    return;
  }

  this.organizacionSeleccionada.set(organizacion);
  this.accionPendiente.set('rechazar');
  this.modalAbierto.set(true);
}


/** Abre la confirmación para deshacer el vínculo con la organización. */
cancelarAsociacion(
  organizacion: OrganizacionSolicitud
): void {

  this.organizacionSeleccionada.set(organizacion);
  this.accionPendiente.set('cancelar');
  this.modalAbierto.set(true);
}

cerrarModal(): void {

  this.modalAbierto.set(false);
  this.organizacionSeleccionada.set(null);
  this.accionPendiente.set(null);
}

/** Ejecuta la acción que se confirmó en el modal. */
confirmarAccion(): void {

  const organizacion =
    this.organizacionSeleccionada();

  const accion =
    this.accionPendiente();

  if (!organizacion || !accion) {
    return;
  }

  this.mensaje.set(null);
  this.error.set(null);

  this.modalAbierto.set(false);

  if (accion === 'aceptar') {

    this.procesandoSolicitud.set(
      organizacion.idOrganizacion
    );

    this.organizacionService
      .aceptarSolicitudOrganizacion(
        organizacion.idOrganizacion
      )
      .subscribe({

        next: (respuesta) => {

          this.procesandoSolicitud.set(null);

          this.mensaje.set(respuesta);

          this.cargarSolicitudes();
          this.cargarOrganizaciones();
        },

        error: (error) => {

          this.procesandoSolicitud.set(null);

          const mensaje =
            error?.error ||
            'No se pudo aceptar la solicitud.';

          this.error.set(mensaje);
        }

      });

  } else if (accion === 'rechazar') {

    this.procesandoSolicitud.set(
      organizacion.idOrganizacion
    );

    this.organizacionService
      .rechazarSolicitudOrganizacion(
        organizacion.idOrganizacion
      )
      .subscribe({

        next: (respuesta) => {

          this.procesandoSolicitud.set(null);

          this.mensaje.set(respuesta);

          this.cargarSolicitudes();
        },

        error: (error) => {

          this.procesandoSolicitud.set(null);

          const mensaje =
            error?.error ||
            'No se pudo rechazar la solicitud.';

          this.error.set(mensaje);
        }

      });

  } else if (accion === 'cancelar') {

    this.organizacionService
      .cancelarAsociacionOrganizacion(
        organizacion.idOrganizacion
      )
      .subscribe({

        next: (respuesta) => {

          this.mensaje.set(respuesta);

          this.cargarOrganizaciones();
        },

        error: (error) => {

          console.error(
            'Error al cancelar la asociación:',
            error
          );

          const mensaje =
            error?.error ||
            'No se pudo cancelar la asociación.';

          this.error.set(mensaje);
        }

      });
  }

  this.organizacionSeleccionada.set(null);
  this.accionPendiente.set(null);
}

}