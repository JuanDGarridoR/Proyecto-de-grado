import { Component, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';

import {
  ActividadService,
  Actividad,
  ActividadRequest,
  ParticipanteActividad,
  PropuestaActividad,
  MAX_FRECUENCIA_DIAS,
  errorFrecuencia,
  fechaHoy,
  separarPorFecha,
  textoFrecuencia
} from '../../../../core/actividades/actividad.service';

import { AuthService } from '../../../../core/auth/auth.service';
import { OrganizacionService } from '../../../../core/organizacion/organizacion.service';
import { Icono } from '../../../../shared/icono/icono';
import { TarjetaActividad } from '../../../../shared/tarjeta-actividad/tarjeta-actividad';
import { ActividadFormulario } from '../../../../shared/actividad-formulario/actividad-formulario';
import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';
import { mensajeDeError } from '../../../../core/formato/formato';
import { Cargando } from '../../../../shared/cargando/cargando';

/**
 * Actividades de la organización: crear, editar y borrar (siempre con
 * confirmación), ver los inscritos y registrar su asistencia. Las
 * actividades que ya pasaron no se pueden editar. Arriba aparecen las
 * propuestas pendientes de voluntarios y personas mayores, para aceptarlas
 * o rechazarlas.
 */
@Component({
  selector: 'app-actividades',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DatePipe,
    Icono,
    TarjetaActividad,
    ActividadFormulario,
    Cargando
  ],
  templateUrl: './actividades.html',
  styleUrls: ['../../../../shared/tarjeta-actividad/actividades-pagina.css', '../../../../shared/tarjeta-actividad/actividades-modales.css']
})
export class Actividades implements OnInit {

  protected readonly actividades = signal<Actividad[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly cargando = signal(false);

  protected readonly nombreUsuario = signal('');

  // Propuestas de voluntarios y personas mayores pendientes de respuesta
  protected readonly propuestas = signal<PropuestaActividad[]>([]);
  protected readonly propuestaProcesando = signal<number | null>(null);
  protected readonly mensajePropuesta = signal<string | null>(null);

  // Formulario de nueva actividad (componente compartido, con su propia
  // validación y confirmación) y confirmación de la edición
  protected mostrarFormulario = signal(false);

  protected mostrarConfirmacion = signal(false);

  protected actividadEliminando: Actividad | null = null;
protected mostrarConfirmacionEliminacion = signal(false);

  // Editar actividad
  protected actividadEditandoId: number | null = null;

protected actividadEditando: ActividadRequest = {
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

  // Repetición de la actividad que se edita; si se desmarca, deja de repetirse.
  protected repetirEditando = false;
  protected readonly maxFrecuencia = MAX_FRECUENCIA_DIAS;
  protected readonly frecuencia = textoFrecuencia;

  // Modal de participantes
  protected actividadParticipantes = signal<Actividad | null>(null);
  protected participantes = signal<ParticipanteActividad[]>([])
  protected cargandoParticipantes = signal(false);

constructor(
  private actividadService: ActividadService,
  private authService: AuthService,
  private organizacionService: OrganizacionService,
  private route: ActivatedRoute
) {
    this.nombreUsuario.set(this.authService.getNombreUsuario());

    // Inscripciones nuevas, cambios en otra pestaña, etc. Si el modal de
    // participantes está abierto, también se actualiza.
    alCambiar(['actividades'], () => {
      this.cargarActividades(false);
      this.cargarPropuestas();

      const actividad = this.actividadParticipantes();
      if (actividad) {
        this.actividadService.listarParticipantes(actividad.idActividad).subscribe({
          next: (participantes) => this.participantes.set(participantes),
          error: (error) => console.error('Error al recargar participantes:', error)
        });
      }
    });

    alCambiar(['usuarios'], () => this.cargarInformacionOrganizacion());
  }

ngOnInit(): void {
  this.cargarActividades();
  this.cargarPropuestas();
  this.cargarInformacionOrganizacion();

  // Desde las acciones rápidas del inicio se llega con ?abrir=registrar.
  this.route.queryParams.subscribe(params => {
    if (params['abrir'] === 'registrar') {
      this.abrirFormulario();
    }
  });
}

  private cargarInformacionOrganizacion(): void {
    this.organizacionService.obtenerInformacion().subscribe({
      next: (data) => {
        this.nombreUsuario.set(data.nombre);
      },
      error: (error) => {
        console.error(
          'Error al cargar la información de la organización:',
          error
        );
      }
    });
  }

  /** Con mostrarCargando en false, la lista se actualiza sin parpadear (cambios en vivo). */
  private cargarActividades(mostrarCargando = true): void {
    if (mostrarCargando) {
      this.cargando.set(true);
    }
    this.error.set(null);

    this.actividadService.listarMias().subscribe({
      next: (actividades) => {
        const ordenadas = [...actividades].sort((a, b) => {
          if (!a.fecha && !b.fecha) return 0;
          if (!a.fecha) return 1;
          if (!b.fecha) return -1;

          return a.fecha.localeCompare(b.fecha);
        });

        this.actividades.set(ordenadas);
        this.cargando.set(false);
      },
      error: (error) => {
        console.error('Error al cargar actividades:', error);
        this.error.set('No se pudieron cargar las actividades');
        this.cargando.set(false);
      }
    });
  }

  private cargarPropuestas(): void {
    this.actividadService.listarPropuestasPendientes().subscribe({
      next: (propuestas) => this.propuestas.set(propuestas),
      error: (error) => console.error('Error al cargar las propuestas de actividades:', error)
    });
  }

  /** Al aceptarla, la actividad pasa a la lista de la organización y la ven sus personas mayores. */
  protected responderPropuesta(propuesta: PropuestaActividad, aceptar: boolean): void {
    this.error.set(null);
    this.mensajePropuesta.set(null);
    this.propuestaProcesando.set(propuesta.idActividad);

    const peticion = aceptar
      ? this.actividadService.aceptarPropuesta(propuesta.idActividad)
      : this.actividadService.rechazarPropuesta(propuesta.idActividad);

    peticion.subscribe({
      next: () => {
        this.propuestaProcesando.set(null);
        this.mensajePropuesta.set(aceptar
          ? `Aceptaste "${propuesta.nombre}". Ya aparece en tus actividades y la ven tus personas mayores.`
          : `Rechazaste "${propuesta.nombre}".`);
        this.cargarPropuestas();
        this.cargarActividades(false);
      },
      error: (error) => {
        console.error('Error al responder la propuesta:', error);
        this.propuestaProcesando.set(null);
        this.error.set(mensajeDeError(error, 'No se pudo responder la propuesta.'));
        this.cargarPropuestas();
      }
    });
  }

  protected actividadesProximas(): Actividad[] {
    return separarPorFecha(this.actividades()).proximas;
  }

  protected actividadesPasadas(): Actividad[] {
    return separarPorFecha(this.actividades()).pasadas;
  }

  protected fechaHoy(): string {
    return fechaHoy();
  }

  protected abrirFormulario(): void {
    this.mostrarFormulario.set(true);
  }

  protected cerrarFormulario(): void {
    this.mostrarFormulario.set(false);
  }

/** Guarda la actividad que ya validó y confirmó el formulario. */
protected crearActividad(datos: ActividadRequest): void {
  this.error.set(null);

  this.actividadService.crear(datos).subscribe({
    next: () => {
      this.cerrarFormulario();
      this.cargarActividades();
    },
    error: (error) => {
      console.error('Error al crear actividad:', error);
      this.error.set('No se pudo crear la actividad.');
    }
  });
}

/** Guarda la edición después de confirmarla. */
protected confirmarEdicion(): void {
  this.mostrarConfirmacion.set(false);

  if (this.actividadEditandoId === null) {
    return;
  }

  this.actividadService
    .actualizar(
      this.actividadEditandoId,
      {
        ...this.actividadEditando,
        frecuenciaDias: this.repetirEditando ? this.actividadEditando.frecuenciaDias : null
      }
    )
    .subscribe({
      next: () => {
        this.cancelarEdicion();
        this.cargarActividades();
      },
      error: (error) => {
        console.error('Error al actualizar actividad:', error);
        this.error.set('No se pudo actualizar la actividad');
      }
    });
}

protected cancelarConfirmacion(): void {
  this.mostrarConfirmacion.set(false);
}

  /** Pasa la actividad a modo edición. Las que ya pasaron no se pueden editar. */
  protected editarActividad(actividad: Actividad): void {
    if (actividad.fecha && actividad.fecha < this.fechaHoy()) {
      return;
    }

    this.actividadEditandoId = actividad.idActividad;

this.actividadEditando = {
  nombre: actividad.nombre,
  descripcion: actividad.descripcion,
  fecha: actividad.fecha,
  hora: actividad.hora,
  lugar: actividad.lugar,
  tipo: actividad.tipo,
  cupos: actividad.cupos,
  responsable: actividad.responsable,
  frecuenciaDias: actividad.frecuenciaDias ?? 7
};
    this.repetirEditando = actividad.frecuenciaDias !== null;
  }

  protected cancelarEdicion(): void {
    this.actividadEditandoId = null;

this.actividadEditando = {
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
    this.repetirEditando = false;
  }

/** Pide confirmación antes de guardar la edición. */
protected guardarActividad(): void {
  if (
    this.actividadEditandoId === null ||
    !this.actividadEditando.nombre.trim()
  ) {
    return;
  }

  if (this.repetirEditando) {
    const errorRepeticion = errorFrecuencia(this.actividadEditando.frecuenciaDias);
    if (errorRepeticion) {
      this.error.set(errorRepeticion);
      return;
    }
  }

  this.error.set(null);
  this.mostrarConfirmacion.set(true);
}

 /** Abre la confirmación para borrar la actividad. */
 protected eliminarActividad(actividad: Actividad): void {
  this.actividadEliminando = actividad;
  this.error.set(null);
  this.mostrarConfirmacionEliminacion.set(true);
}

protected confirmarEliminacion(): void {
  if (!this.actividadEliminando) {
    return;
  }

  const idActividad = this.actividadEliminando.idActividad;

  this.mostrarConfirmacionEliminacion.set(false);

  this.actividadService.eliminar(idActividad).subscribe({
    next: () => {
      this.actividadEliminando = null;
      this.cargarActividades();
    },
    error: (error) => {
      console.error('Error al eliminar actividad:', error);
      this.error.set('No se pudo eliminar la actividad');
      this.actividadEliminando = null;
    }
  });
}

protected cancelarEliminacion(): void {
  this.mostrarConfirmacionEliminacion.set(false);
  this.actividadEliminando = null;
}

  /** Abre el modal con los inscritos de la actividad. */
  protected verParticipantes(actividad: Actividad): void {
    this.actividadParticipantes.set(actividad);
    this.participantes.set([]);
    this.cargandoParticipantes.set(true);

    this.actividadService.listarParticipantes(
      actividad.idActividad
    ).subscribe({
next: (participantes) => {
  this.participantes.set(participantes);
  this.cargandoParticipantes.set(false);
},
      error: (error) => {
        console.error('Error al cargar participantes:', error);
        this.error.set('No se pudieron cargar los participantes');
        this.cargandoParticipantes.set(false);
      }
    });
  }

  protected cerrarParticipantes(): void {
    this.actividadParticipantes.set(null);
    this.participantes.set([]);
  }

  /**
   * Marca si un inscrito asistió. Solo se puede en actividades de hoy en
   * adelante; en las que ya pasaron la asistencia queda fija.
   */
  protected registrarAsistencia(
    participante: ParticipanteActividad,
    asistio: boolean
  ): void {

const actividad = this.actividadParticipantes();

if (!actividad) {
  return;
}

if (actividad.fecha && actividad.fecha < this.fechaHoy()) {
  return;
}

    this.actividadService
      .registrarAsistencia(
        actividad.idActividad,
        participante.idPersonaMayor,
        asistio
      )
      .subscribe({
        next: () => {
          participante.asistio = asistio;
          this.participantes.set([...this.participantes()]);
        },
        error: (error) => {
          console.error(
            'Error al registrar asistencia:',
            error
          );

          this.error.set(
            'No se pudo registrar la asistencia'
          );
        }
      });
  }

  protected contarAsistentes(): number {
    return this.participantes().filter(
      participante => participante.asistio === true
    ).length;
  }

  /** Número de inscritos. */
  protected contarConfirmados(): number {
    return this.participantes().length;
  }
}