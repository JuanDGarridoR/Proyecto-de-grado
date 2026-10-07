import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import {
  OrganizacionService,
  OrganizacionSolicitud
} from '../../../../core/organizacion/organizacion.service';

import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';
import { Icon } from '../../../../shared/icon/icon';
import { RecomendacionesOrganizacionesComponent } from '../../../../shared/recomendaciones-organizaciones/recomendaciones-organizaciones';

/**
 * Organizaciones de la persona mayor: invitaciones pendientes para aceptar o
 * rechazar, organizaciones con vínculo aceptado, solicitudes que ella envió
 * (puede cancelarlas), recomendaciones desde donde puede solicitar unirse y,
 * en un desplegable, todas las demás organizaciones.
 * Aceptar, rechazar o desvincularse pasa por un modal de confirmación.
 */
@Component({
  selector: 'app-organizaciones',
  standalone: true,
  imports: [Icon, CommonModule, FormsModule, RecomendacionesOrganizacionesComponent],
  templateUrl: './organizaciones.html',
  styleUrl: './organizaciones.css'
})
export class Organizaciones implements OnInit {

  protected readonly organizaciones =
    signal<OrganizacionSolicitud[]>([]);

  protected readonly solicitudes =
    signal<OrganizacionSolicitud[]>([]);

  /** Solicitudes que envió la persona mayor y la organización aún no responde. */
  protected readonly enviadas =
    signal<OrganizacionSolicitud[]>([]);

  /** Solicitud enviada que se está cancelando; deshabilita su botón. */
  protected readonly cancelandoEnviada =
    signal<number | null>(null);

  /** Organizaciones a las que puede pedir unirse ("Ver todas las organizaciones"). */
  protected readonly disponibles =
    signal<OrganizacionSolicitud[]>([]);

  /** Organización a la que se le está enviando la solicitud; deshabilita su botón. */
  protected readonly solicitando =
    signal<number | null>(null);

  protected busqueda = '';
  protected readonly filtro = signal('');

  protected readonly disponiblesFiltradas = computed(() => {
    const filtro = this.filtro().trim().toLowerCase();
    return this.disponibles().filter((o) => !filtro || o.nombre.toLowerCase().includes(filtro));
  });

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
      this.cargarEnviadas();
      this.cargarDisponibles();
    });
  }

  ngOnInit(): void {
    this.cargarOrganizaciones();
    this.cargarSolicitudes();
    this.cargarEnviadas();
    this.cargarDisponibles();
  }

  cargarDisponibles(): void {
    this.organizacionService.obtenerOrganizacionesDisponibles().subscribe({
      next: (organizaciones) => this.disponibles.set(organizaciones),
      error: () => this.disponibles.set([])
    });
  }

  /** Al enviar una solicitud (desde aquí o desde las recomendadas) cambian las dos listas. */
  alSolicitar(): void {
    this.cargarEnviadas();
    this.cargarDisponibles();
  }

  /** Pide unirse a una organización de "Ver todas las organizaciones". */
  solicitarVinculacion(organizacion: OrganizacionSolicitud): void {
    this.solicitando.set(organizacion.idOrganizacion);
    this.mensaje.set(null);
    this.error.set(null);

    this.organizacionService.solicitarVinculacionOrganizacion(organizacion.idOrganizacion).subscribe({
      next: (respuesta) => {
        this.solicitando.set(null);
        this.mensaje.set(`${organizacion.nombre}: ${respuesta}`);
        this.cargarOrganizaciones();
        this.alSolicitar();
      },
      error: (error) => {
        this.solicitando.set(null);
        this.error.set(typeof error?.error === 'string' && error.error ? error.error : 'No se pudo enviar la solicitud.');
      }
    });
  }

  cargarEnviadas(): void {
    this.organizacionService.obtenerSolicitudesEnviadasOrganizaciones().subscribe({
      next: (enviadas) => this.enviadas.set(enviadas),
      error: () => this.enviadas.set([])
    });
  }

  /** Retira una solicitud que envió y aún no tiene respuesta. */
  cancelarEnviada(organizacion: OrganizacionSolicitud): void {
    this.cancelandoEnviada.set(organizacion.idOrganizacion);
    this.mensaje.set(null);
    this.error.set(null);

    this.organizacionService.cancelarAsociacionOrganizacion(organizacion.idOrganizacion).subscribe({
      next: () => {
        this.cancelandoEnviada.set(null);
        this.mensaje.set(`Cancelaste tu solicitud a ${organizacion.nombre}.`);
        this.alSolicitar();
      },
      error: () => {
        this.cancelandoEnviada.set(null);
        this.error.set('No se pudo cancelar la solicitud.');
      }
    });
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