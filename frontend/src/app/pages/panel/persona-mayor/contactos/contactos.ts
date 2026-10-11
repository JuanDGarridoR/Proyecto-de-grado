import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AcompananteService,
  Acompanante
} from '../../../../core/acompanantes/acompanante.service';
import { EmergenciaService } from '../../../../core/emergencia/emergencia.service';
import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';
import { Icono } from '../../../../shared/icono/icono';
import { TarjetaPersona } from '../../../../shared/tarjeta-persona/tarjeta-persona';
import { CancelarAsociacion } from '../../../../shared/cancelar-asociacion/cancelar-asociacion';
import {
  SolicitudAcompanamientoModal,
  DatosSolicitudAcompanamiento
} from '../../../../shared/solicitud-acompanamiento/solicitud-acompanamiento';

/**
 * Contactos de la persona mayor: botón de emergencia, sus acompañantes, el
 * formulario para agregar uno nuevo por celular y las solicitudes que le
 * envían los acompañantes (para aceptarlas o rechazarlas).
 */
@Component({
  selector: 'app-contactos',
  standalone: true,
  imports: [Icono, FormsModule, TarjetaPersona, CancelarAsociacion, SolicitudAcompanamientoModal],
  templateUrl: './contactos.html',
  styleUrl: './contactos.css'
})
export class Contactos implements OnInit {

  protected readonly acompanantes = signal<Acompanante[]>([]);

  // Botón de emergencia
  protected readonly mostrandoConfirmacionEmergencia = signal(false);
  protected readonly enviandoEmergencia = signal(false);
  protected readonly mensajeEmergencia = signal<string | null>(null);
  protected readonly errorEmergencia = signal<string | null>(null);

  // Formulario para agregar un acompañante
  protected readonly mostrandoFormularioAcompanante = signal(false);

  protected readonly agregandoAcompanante = signal(false);
  protected readonly errorAcompanante = signal<string | null>(null);
  protected readonly mensajeAcompanante = signal<string | null>(null);

  // Solicitudes que le enviaron acompañantes
  protected readonly solicitudes = signal<Acompanante[]>([]);
  protected readonly solicitudProcesando = signal<number | null>(null);

  // Modal para quitar un acompañante
  protected readonly acompananteACancelar = signal<Acompanante | null>(null);
  protected readonly mensajeCancelacion = signal<string | null>(null);
  protected readonly errorCancelacion = signal<string | null>(null);

  constructor(
    private acompananteService: AcompananteService,
    private emergenciaService: EmergenciaService
  ) {
    alCambiar(['acompanamientos', 'usuarios'], () => {
      this.cargarAcompanantes();
      this.cargarSolicitudes();
    });
  }

  ngOnInit(): void {
    this.cargarAcompanantes();
    this.cargarSolicitudes();
  }

  cargarSolicitudes(): void {
    this.acompananteService.obtenerSolicitudesDeAcompanantes().subscribe({
      next: (solicitudes) => this.solicitudes.set(solicitudes),
      error: () => this.solicitudes.set([])
    });
  }

  /** Acepta o rechaza la solicitud que le envió un acompañante. */
  responderSolicitud(solicitud: Acompanante, aceptar: boolean): void {
    this.mensajeAcompanante.set(null);
    this.errorCancelacion.set(null);
    this.solicitudProcesando.set(solicitud.idUsuario);

    this.acompananteService
      .responderSolicitudDeAcompanante(solicitud.idUsuario, aceptar)
      .subscribe({
        next: (respuesta) => {
          this.solicitudProcesando.set(null);
          this.mensajeAcompanante.set(respuesta);
          this.cargarSolicitudes();
          this.cargarAcompanantes();
        },
        error: (error) => {
          this.solicitudProcesando.set(null);
          this.errorCancelacion.set(
            error?.error || 'No se pudo responder la solicitud.'
          );
          this.cargarSolicitudes();
        }
      });
  }

  cargarAcompanantes(): void {
    this.acompananteService.obtenerAcompanantes().subscribe({
      next: (acompanantes) => {
        this.acompanantes.set(acompanantes);
      },
      error: () => {
        this.acompanantes.set([]);
      }
    });
  }

  mostrarConfirmacionCancelacion(acompanante: Acompanante): void {
    this.acompananteACancelar.set(acompanante);
    this.mensajeCancelacion.set(null);
    this.errorCancelacion.set(null);
  }

  cerrarConfirmacionCancelacion(): void {
    this.acompananteACancelar.set(null);
  }

  /** Quita al acompañante del modal. */
  confirmarCancelacion(): void {
    const acompanante = this.acompananteACancelar();

    if (!acompanante) return;

    this.acompananteACancelar.set(null);

    this.acompananteService.cancelarAcompanante(acompanante.idUsuario).subscribe({
      next: (respuesta) => {
        this.mensajeCancelacion.set(respuesta);
        this.cargarAcompanantes();
      },
      error: (error) => {
        this.errorCancelacion.set(
          error?.error || 'No se pudo cancelar la asociación.'
        );
      }
    });
  }

  mostrarFormularioAcompanante(): void {
    this.mostrandoFormularioAcompanante.set(true);
    this.errorAcompanante.set(null);
    this.mensajeAcompanante.set(null);
  }

  cancelarFormularioAcompanante(): void {
    this.mostrandoFormularioAcompanante.set(false);
    this.errorAcompanante.set(null);
  }

/**
 * Envía la solicitud de acompañamiento que ya validó el formulario; el
 * acompañante la acepta desde su panel.
 */
agregarAcompanante(datos: DatosSolicitudAcompanamiento): void {
  this.errorAcompanante.set(null);
  this.mensajeAcompanante.set(null);
  this.agregandoAcompanante.set(true);

  this.acompananteService.agregarAcompanante(datos).subscribe({
    next: (respuesta) => {
      this.agregandoAcompanante.set(false);
      this.mostrandoFormularioAcompanante.set(false);
      this.mensajeAcompanante.set(respuesta);
      this.cargarAcompanantes();
    },

    error: (error) => {
      this.agregandoAcompanante.set(false);
      this.errorAcompanante.set(
        error?.error || 'No se pudo agregar el acompañante.'
      );
    }
  });
}

  activarConfirmacionEmergencia(): void {
    this.mostrandoConfirmacionEmergencia.set(true);
    this.mensajeEmergencia.set(null);
    this.errorEmergencia.set(null);
  }

  cancelarEmergencia(): void {
    this.mostrandoConfirmacionEmergencia.set(false);
  }

  /** Envía la alerta por SMS a los acompañantes y organizaciones. */
  confirmarEmergencia(): void {
    this.enviandoEmergencia.set(true);
    this.errorEmergencia.set(null);
    this.mensajeEmergencia.set(null);

    this.emergenciaService.activarEmergencia().subscribe({
      next: (respuesta) => {
        this.enviandoEmergencia.set(false);
        this.mostrandoConfirmacionEmergencia.set(false);
        this.mensajeEmergencia.set(respuesta);
      },
      error: (error) => {
        this.enviandoEmergencia.set(false);

        const mensaje =
          error?.error || 'No se pudo enviar la alerta de emergencia.';

        this.errorEmergencia.set(mensaje);
      }
    });
  }
}