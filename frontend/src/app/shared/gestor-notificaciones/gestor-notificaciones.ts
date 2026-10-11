import { Component, OnInit, computed, inject, input, signal } from '@angular/core';

import {
  NotificacionService,
  PreferenciasNotificacion,
  TipoNotificacion
} from '../../core/notificaciones/notificacion.service';
import type { TipoPerfil } from '../perfil/perfil';

/** Un tipo de notificación tal como se le explica a un rol. */
interface OpcionNotificacion {
  tipo: TipoNotificacion;
  titulo: string;
  descripcion: string;
  /** Aviso que se muestra mientras el tipo está apagado. */
  advertencia?: string;
}

/**
 * Notificaciones que le pueden llegar a cada rol, con el texto adaptado a
 * quién las recibe. Debe coincidir con quién envía cada tipo en el backend.
 */
const OPCIONES: Record<TipoPerfil, OpcionNotificacion[]> = {
  PERSONA_MAYOR: [
    { tipo: 'MEDICAMENTO', titulo: 'Recordatorios de medicamentos',
      descripcion: 'Un aviso unos minutos antes y otro a la hora de cada toma.' },
    { tipo: 'CITA_MEDICA', titulo: 'Recordatorios de citas médicas',
      descripcion: 'Un aviso un día antes y otro una hora antes de cada cita.' },
    { tipo: 'ACTIVIDAD', titulo: 'Recordatorios de actividades',
      descripcion: 'Un aviso una hora antes de las actividades a las que te inscribiste.' },
    { tipo: 'CUMPLEANOS', titulo: 'Felicitación de cumpleaños',
      descripcion: 'Un saludo de VITA+ el día de tu cumpleaños.' }
  ],
  ACOMPANANTE: [
    { tipo: 'EMERGENCIA', titulo: 'Alertas de emergencia',
      descripcion: 'Cuando una persona mayor que acompañas activa el botón de emergencia.',
      advertencia: 'No te enterarás si una persona mayor que acompañas pide ayuda con el botón de emergencia.' },
    { tipo: 'MEDICAMENTO', titulo: 'Recordatorios de medicamentos',
      descripcion: 'Avisos de las tomas de medicamentos de las personas mayores que acompañas.' },
    { tipo: 'CITA_MEDICA', titulo: 'Recordatorios de citas médicas',
      descripcion: 'Avisos de las citas médicas de las personas mayores que acompañas.' },
    { tipo: 'CUMPLEANOS_PERSONA_MAYOR', titulo: 'Cumpleaños de tus personas mayores',
      descripcion: 'Un aviso el día del cumpleaños de cada persona mayor que acompañas.' },
    { tipo: 'CUMPLEANOS', titulo: 'Felicitación de cumpleaños',
      descripcion: 'Un saludo de VITA+ el día de tu cumpleaños.' }
  ],
  VOLUNTARIO: [
    { tipo: 'CUMPLEANOS', titulo: 'Felicitación de cumpleaños',
      descripcion: 'Un saludo de VITA+ el día de tu cumpleaños.' }
  ],
  ORGANIZACION: [
    { tipo: 'EMERGENCIA', titulo: 'Alertas de emergencia',
      descripcion: 'Cuando una persona mayor asociada activa el botón de emergencia.',
      advertencia: 'La organización no se enterará si una persona mayor asociada pide ayuda con el botón de emergencia.' },
    { tipo: 'CUMPLEANOS_PERSONA_MAYOR', titulo: 'Cumpleaños de las personas mayores',
      descripcion: 'Un aviso el día del cumpleaños de cada persona mayor asociada.' }
  ]
};

/**
 * Sección "Gestor de notificaciones" del perfil: cada usuario ve los tipos
 * de notificación que le pueden llegar según su rol y los activa o
 * desactiva. Los cambios se guardan al momento en mensajeria-service, que
 * no envía los tipos desactivados.
 */
@Component({
  selector: 'app-gestor-notificaciones',
  templateUrl: './gestor-notificaciones.html',
  styleUrl: './gestor-notificaciones.css'
})
export class GestorNotificaciones implements OnInit {

  private notificacionService = inject(NotificacionService);

  readonly tipoPerfil = input.required<TipoPerfil>();

  protected readonly opciones = computed(() => OPCIONES[this.tipoPerfil()]);

  protected readonly preferencias = signal<PreferenciasNotificacion | null>(null);
  protected readonly cargando = signal(true);
  protected readonly errorCarga = signal(false);

  /** Tipo que se está guardando (su interruptor queda deshabilitado). */
  protected readonly guardando = signal<TipoNotificacion | null>(null);
  protected readonly errorGuardado = signal('');

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.errorCarga.set(false);

    this.notificacionService.preferencias().subscribe({
      next: (preferencias) => {
        this.preferencias.set(preferencias);
        this.cargando.set(false);
      },
      error: (error) => {
        console.error('Error al cargar las preferencias de notificación:', error);
        this.errorCarga.set(true);
        this.cargando.set(false);
      }
    });
  }

  protected activa(tipo: TipoNotificacion): boolean {
    return this.preferencias()?.[tipo] ?? true;
  }

  /** Cambia el interruptor de inmediato y lo devuelve si el servidor falla. */
  protected cambiar(tipo: TipoNotificacion): void {
    const actual = this.preferencias();
    if (!actual || this.guardando()) {
      return;
    }

    const nueva = !actual[tipo];
    this.preferencias.set({ ...actual, [tipo]: nueva });
    this.guardando.set(tipo);
    this.errorGuardado.set('');

    this.notificacionService.cambiarPreferencia(tipo, nueva).subscribe({
      next: () => this.guardando.set(null),
      error: (error) => {
        console.error('Error al guardar la preferencia de notificación:', error);
        this.preferencias.set({ ...this.preferencias()!, [tipo]: !nueva });
        this.guardando.set(null);
        this.errorGuardado.set('No se pudo guardar el cambio. Intenta de nuevo.');
      }
    });
  }
}
