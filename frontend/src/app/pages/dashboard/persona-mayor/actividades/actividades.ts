import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import { RouterLink } from '@angular/router';
import localeEs from '@angular/common/locales/es-CO';
import {
  ActividadService,
  ActividadDisponible,
  EstadoPropuesta,
  PropuestaActividad,
  separarPorFecha
} from '../../../../core/actividades/actividad.service';
import { OrganizacionService } from '../../../../core/organizacion/organizacion.service';
import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';
import { Icon } from '../../../../shared/icon/icon';
import { ActividadCard } from '../../../../shared/actividad-card/actividad-card';
import {
  ActividadFormulario,
  ActividadFormularioDatos,
  OpcionOrganizacion
} from '../../../../shared/actividad-formulario/actividad-formulario';

registerLocaleData(localeEs);

/**
 * Actividades de las organizaciones de la persona mayor: se puede inscribir
 * o cancelar la inscripción mientras la actividad no haya pasado. También
 * puede proponer actividades a sus organizaciones, igual que un voluntario,
 * y ver si se las aceptaron o rechazaron.
 */
@Component({
  selector: 'app-actividades',
  standalone: true,
  imports: [RouterLink, Icon, ActividadCard, ActividadFormulario],
  templateUrl: './actividades.html',
  styleUrls: [
    '../../../../shared/actividad-card/actividades-pagina.css',
    '../../../../shared/actividad-card/actividades-propuestas.css',
    './actividades.css'
  ]
})
export class Actividades implements OnInit {

  private actividadService = inject(ActividadService);
  private organizacionService = inject(OrganizacionService);

  protected readonly actividades = signal<ActividadDisponible[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  /** Actividad cuya inscripción se está enviando; deshabilita su botón. */
  protected readonly procesandoId = signal<number | null>(null);

  // Próximas (de la más cercana a la más lejana) e historial (de la más
  // reciente a la más antigua).
  protected readonly separadas = computed(() => separarPorFecha(this.actividades()));

  // Propuestas de la persona mayor
  protected readonly propuestas = signal<PropuestaActividad[]>([]);
  protected readonly organizaciones = signal<OpcionOrganizacion[]>([]);
  protected readonly organizacionesCargadas = signal(false);
  protected readonly mostrarFormulario = signal(false);
  protected readonly errorFormulario = signal<string | null>(null);
  protected readonly mensaje = signal<string | null>(null);

  protected readonly pendientes = computed(() =>
    this.propuestas().filter((p) => p.estado === 'PENDIENTE').length
  );

  protected readonly textoEstado: Record<EstadoPropuesta, string> = {
    PENDIENTE: 'Pendiente',
    ACEPTADA: 'Aceptada',
    RECHAZADA: 'Rechazada'
  };

  constructor() {
    // Cambió una actividad o la organización respondió una propuesta
    alCambiar(['actividades'], () => {
      this.cargar(false);
      this.cargarPropuestas();
    });
    alCambiar(['organizaciones', 'usuarios'], () => this.cargarOrganizaciones());
  }

  ngOnInit(): void {
    this.cargar();
    this.cargarPropuestas();
    this.cargarOrganizaciones();
  }

  /** Con mostrarCargando en false, la lista se actualiza sin parpadear (cambios en vivo). */
  private cargar(mostrarCargando = true): void {
    if (mostrarCargando) {
      this.cargando.set(true);
    }
    this.error.set(null);

    this.actividadService.listarDisponibles().subscribe({
      next: (actividades) => {
        this.actividades.set(actividades);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set('No se pudieron cargar las actividades.');
        this.cargando.set(false);
      }
    });
  }

  inscribirse(actividad: ActividadDisponible): void {
    this.procesandoId.set(actividad.idActividad);

    this.actividadService.inscribirse(actividad.idActividad).subscribe({
      next: () => {
        this.procesandoId.set(null);
        this.cargar();
      },
      error: () => {
        this.procesandoId.set(null);
        this.error.set('No se pudo completar la inscripción.');
      }
    });
  }

  cancelarInscripcion(actividad: ActividadDisponible): void {
    this.procesandoId.set(actividad.idActividad);

    this.actividadService.cancelarInscripcion(actividad.idActividad).subscribe({
      next: () => {
        this.procesandoId.set(null);
        this.cargar();
      },
      error: () => {
        this.procesandoId.set(null);
        this.error.set('No se pudo cancelar la inscripción.');
      }
    });
  }

  // ---------- Propuestas ----------

  protected abrirFormulario(): void {
    this.mensaje.set(null);
    this.errorFormulario.set(null);
    this.mostrarFormulario.set(true);
  }

  protected proponer(datos: ActividadFormularioDatos): void {
    this.errorFormulario.set(null);

    this.actividadService.proponer(datos).subscribe({
      next: (propuesta) => {
        this.mostrarFormulario.set(false);
        this.mensaje.set(
          `Tu propuesta se envió a ${propuesta.nombreOrganizacion ?? 'la organización'}. ` +
          'Aquí verás cuando la acepten o la rechacen.'
        );
        this.cargarPropuestas();
      },
      error: (error) => {
        console.error('Error al proponer la actividad:', error);
        this.errorFormulario.set(
          typeof error?.error === 'string' && error.error
            ? error.error
            : 'No se pudo enviar la propuesta.'
        );
      }
    });
  }

  private cargarPropuestas(): void {
    this.actividadService.listarPropuestasMias().subscribe({
      next: (propuestas) => this.propuestas.set(propuestas),
      error: (error) => console.error('Error al cargar las propuestas:', error)
    });
  }

  /** Solo se puede proponer a las organizaciones con vínculo aceptado. */
  private cargarOrganizaciones(): void {
    this.organizacionService.obtenerOrganizaciones().subscribe({
      next: (organizaciones) => {
        this.organizaciones.set(
          organizaciones.map((o) => ({ idOrganizacion: o.idOrganizacion, nombre: o.nombre }))
        );
        this.organizacionesCargadas.set(true);
      },
      error: (error) => {
        console.error('Error al cargar las organizaciones:', error);
        this.organizacionesCargadas.set(true);
      }
    });
  }
}
