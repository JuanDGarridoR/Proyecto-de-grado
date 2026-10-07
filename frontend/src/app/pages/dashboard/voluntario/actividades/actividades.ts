import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import {
  ActividadService,
  EstadoPropuesta,
  PropuestaActividad
} from '../../../../core/actividades/actividad.service';
import { VoluntarioService } from '../../../../core/voluntario/voluntario.service';
import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';
import { Icon } from '../../../../shared/icon/icon';
import { ActividadCard } from '../../../../shared/actividad-card/actividad-card';
import {
  ActividadFormulario,
  ActividadFormularioDatos,
  OpcionOrganizacion
} from '../../../../shared/actividad-formulario/actividad-formulario';
import { mensajeDeError } from '../../../../core/formato/formato';

/**
 * Actividades del voluntario: propone actividades a las organizaciones en
 * las que está vinculado (con el mismo formulario que usan las
 * organizaciones) y consulta si se las aceptaron o rechazaron.
 */
@Component({
  selector: 'app-voluntario-actividades',
  standalone: true,
  imports: [RouterLink, Icon, ActividadCard, ActividadFormulario],
  templateUrl: './actividades.html',
  styleUrls: [
    '../../../../shared/actividad-card/actividades-pagina.css',
    '../../../../shared/actividad-card/actividades-modales.css',
    '../../../../shared/actividad-card/actividades-propuestas.css',
    './actividades.css'
  ]
})
export class VoluntarioActividades implements OnInit {
  private actividadService = inject(ActividadService);
  private voluntarioService = inject(VoluntarioService);

  protected readonly propuestas = signal<PropuestaActividad[]>([]);
  protected readonly organizaciones = signal<OpcionOrganizacion[]>([]);
  protected readonly cargando = signal(true);

  protected readonly mensaje = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly mostrarFormulario = signal(false);
  protected readonly errorFormulario = signal<string | null>(null);

  protected readonly pendientes = computed(() =>
    this.propuestas().filter((p) => p.estado === 'PENDIENTE').length
  );

  protected readonly textoEstado: Record<EstadoPropuesta, string> = {
    PENDIENTE: 'Pendiente',
    ACEPTADA: 'Aceptada',
    RECHAZADA: 'Rechazada'
  };

  constructor() {
    // La organización respondió una propuesta, o cambió el vínculo con ella
    alCambiar(['actividades'], () => this.cargarPropuestas());
    alCambiar(['voluntarios', 'usuarios'], () => this.cargarOrganizaciones());
  }

  ngOnInit(): void {
    this.cargarPropuestas();
    this.cargarOrganizaciones();
  }

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
          'Te avisaremos aquí cuando la acepten o la rechacen.'
        );
        this.cargarPropuestas();
      },
      error: (error) => {
        console.error('Error al proponer la actividad:', error);
        this.errorFormulario.set(
          mensajeDeError(error, 'No se pudo enviar la propuesta.')
        );
      }
    });
  }

  private cargarPropuestas(): void {
    this.actividadService.listarPropuestasMias().subscribe({
      next: (propuestas) => {
        this.propuestas.set(propuestas);
        this.error.set(null);
        this.cargando.set(false);
      },
      error: (error) => {
        console.error('Error al cargar las propuestas:', error);
        this.error.set('No se pudieron cargar tus propuestas.');
        this.cargando.set(false);
      }
    });
  }

  /** Solo se puede proponer a las organizaciones con vínculo aceptado. */
  private cargarOrganizaciones(): void {
    this.voluntarioService.listarOrganizaciones().subscribe({
      next: (organizaciones) =>
        this.organizaciones.set(
          organizaciones
            .filter((o) => o.estado === 'ACEPTADA')
            .map((o) => ({ idOrganizacion: o.idOrganizacion, nombre: o.nombre }))
        ),
      error: (error) => console.error('Error al cargar las organizaciones:', error)
    });
  }
}
