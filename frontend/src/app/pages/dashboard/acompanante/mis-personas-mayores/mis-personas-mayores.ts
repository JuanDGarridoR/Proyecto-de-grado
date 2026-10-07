import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  AcompananteService,
  PersonaMayorAcompanada,
  SolicitudAcompanamiento
} from '../../../../core/acompanantes/acompanante.service';
import { SignoVitalResponse } from '../../../../core/signos-vitales/signos-vitales.services';
import { Icon } from '../../../../shared/icon/icon';
import { BuscadorNombre, filtrarPorNombre } from '../../../../shared/buscador-nombre/buscador-nombre';
import { PersonCard } from '../../../../shared/person-card/person-card';
import { CancelarAsociacion } from '../../../../shared/cancelar-asociacion/cancelar-asociacion';
import { SignosVitalesModal } from '../../../../shared/signos-vitales-modal/signos-vitales-modal';
import { AcompanantesModal, AcompananteResumen } from '../../../../shared/acompanantes-modal/acompanantes-modal';
import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';
import {
  SolicitudAcompanamientoModal,
  DatosSolicitudAcompanamiento
} from '../../../../shared/solicitud-acompanamiento/solicitud-acompanamiento';
import { Cargando } from '../../../../shared/cargando/cargando';

/**
 * Personas mayores del acompañante: solicitudes pendientes para aceptar o
 * rechazar, la lista de personas a cargo con sus últimos signos vitales y
 * sus acompañantes, y la opción de dejar de acompañar a alguien.
 */
@Component({
  selector: 'app-mis-personas-mayores',
  imports: [RouterLink, Icon, PersonCard, CancelarAsociacion, SignosVitalesModal, AcompanantesModal, SolicitudAcompanamientoModal, BuscadorNombre, Cargando],
  templateUrl: './mis-personas-mayores.html',
  styleUrl: './mis-personas-mayores.css'
})
export class MisPersonasMayores implements OnInit {

  personasMayores: PersonaMayorAcompanada[] = [];

  /** Búsqueda rápida en la lista de personas mayores que acompaña. */
  busqueda = '';

  get personasFiltradas(): PersonaMayorAcompanada[] {
    return filtrarPorNombre(this.personasMayores, this.busqueda);
  }
  solicitudes: SolicitudAcompanamiento[] = [];
  enviadas: SolicitudAcompanamiento[] = [];

  // Formulario para enviar una solicitud a una persona mayor
  mostrandoFormulario = false;
  enviandoSolicitud = false;
  errorSolicitud: string | null = null;

  cargando = true;
  mensaje = '';
  error = '';

  /** Persona del modal de "dejar de acompañar"; null si está cerrado. */
  personaACancelar: PersonaMayorAcompanada | null = null;

  /** Persona del modal de signos vitales; null si está cerrado. */
  personaSignosVitales: PersonaMayorAcompanada | null = null;
  signosVitales: SignoVitalResponse[] = [];
  cargandoSignosVitales = false;
  errorSignosVitales: string | null = null;

  personaAcompanantes: PersonaMayorAcompanada | null = null;
  acompanantes: AcompananteResumen[] = [];
  cargandoAcompanantes = false;
  errorAcompanantes: string | null = null;

  constructor(
    private acompananteService: AcompananteService,
    private cdr: ChangeDetectorRef
  ) {
    alCambiar(['acompanamientos', 'usuarios'], () => {
      this.cargarPersonasMayores();
      this.cargarSolicitudes();
      this.cargarEnviadas();

      // Si el modal de acompañantes está abierto, se actualiza en vivo.
      if (this.personaAcompanantes) {
        this.cargarAcompanantes(this.personaAcompanantes.idUsuario);
      }
    });

    // Si el modal de signos vitales está abierto, se actualiza en vivo.
    alCambiar(['signos-vitales'], () => {
      if (this.personaSignosVitales) {
        this.cargarSignosVitales(this.personaSignosVitales.idUsuario);
      }
    });
  }

  ngOnInit(): void {
    this.cargarPersonasMayores();
    this.cargarSolicitudes();
    this.cargarEnviadas();
  }

  cargarEnviadas(): void {
    this.acompananteService.obtenerSolicitudesEnviadas().subscribe({
      next: (data) => {
        this.enviadas = data;
        this.cdr.detectChanges();
      },
      error: (error) => {
        console.error('Error al cargar las solicitudes enviadas:', error);
      }
    });
  }

  mostrarFormulario(): void {
    this.mensaje = '';
    this.error = '';
    this.errorSolicitud = null;
    this.mostrandoFormulario = true;
  }

  cerrarFormulario(): void {
    this.mostrandoFormulario = false;
    this.errorSolicitud = null;
  }

  /** Envía la solicitud que ya validó el formulario; la persona mayor la acepta desde Contactos. */
  enviarSolicitud(datos: DatosSolicitudAcompanamiento): void {
    this.enviandoSolicitud = true;
    this.errorSolicitud = null;

    this.acompananteService.agregarPersonaMayor(datos).subscribe({
      next: (respuesta) => {
        this.enviandoSolicitud = false;
        this.mostrandoFormulario = false;
        this.mensaje = respuesta;
        this.cargarEnviadas();
        this.cdr.detectChanges();
      },
      error: (error) => {
        this.enviandoSolicitud = false;
        this.errorSolicitud = error?.error || 'No se pudo enviar la solicitud.';
        this.cdr.detectChanges();
      }
    });
  }

  /** Retira una solicitud que la persona mayor aún no responde. */
  cancelarSolicitudEnviada(solicitud: SolicitudAcompanamiento): void {
    this.acompananteService.cancelarAsociacionPersonaMayor(solicitud.idUsuario).subscribe({
      next: () => {
        this.mensaje = 'Solicitud cancelada correctamente';
        this.error = '';
        this.cargarEnviadas();
      },
      error: (error) => {
        console.error('Error al cancelar la solicitud:', error);
        this.error = error?.error || 'No se pudo cancelar la solicitud.';
        this.cdr.detectChanges();
      }
    });
  }

  cargarPersonasMayores(): void {
    this.acompananteService.obtenerPersonasMayores().subscribe({
      next: (data) => {
        this.personasMayores = data;
        this.cargando = false;
        this.cdr.detectChanges();
      },
      error: (error) => {
        console.error('Error al cargar personas mayores:', error);
        this.cargando = false;
        this.cdr.detectChanges();
      }
    });
  }

  cargarSolicitudes(): void {
    this.acompananteService.obtenerSolicitudes().subscribe({
      next: (data) => {
        this.solicitudes = data;
        this.cdr.detectChanges();
      },
      error: (error) => {
        console.error('Error al cargar solicitudes:', error);
      }
    });
  }

  aceptarSolicitud(idPersonaMayor: number): void {
    this.acompananteService.aceptarSolicitud(idPersonaMayor).subscribe({
      next: (respuesta) => {
        this.mensaje = respuesta;
        this.error = '';

        this.cargarSolicitudes();
        this.cargarPersonasMayores();
      },
      error: (error) => {
        console.error('Error al aceptar solicitud:', error);
        this.error = 'No fue posible aceptar la solicitud.';
        this.mensaje = '';
      }
    });
  }

  rechazarSolicitud(idPersonaMayor: number): void {
    this.acompananteService.rechazarSolicitud(idPersonaMayor).subscribe({
      next: (respuesta) => {
        this.mensaje = respuesta;
        this.error = '';

        this.cargarSolicitudes();
      },
      error: (error) => {
        console.error('Error al rechazar solicitud:', error);
        this.error = 'No fue posible rechazar la solicitud.';
        this.mensaje = '';
      }
    });
  }

  mostrarConfirmacionCancelacion(persona: PersonaMayorAcompanada): void {
    this.personaACancelar = persona;
    this.mensaje = '';
    this.error = '';
  }

  cerrarConfirmacionCancelacion(): void {
    this.personaACancelar = null;
  }

  /** Deja de acompañar a la persona del modal. */
  confirmarCancelacion(): void {
    const persona = this.personaACancelar;

    if (!persona) return;

    this.personaACancelar = null;

    this.acompananteService.cancelarAsociacionPersonaMayor(persona.idUsuario).subscribe({
      next: (respuesta) => {
        this.mensaje = respuesta;
        this.cargarPersonasMayores();
      },
      error: (error) => {
        console.error('Error al cancelar la asociación:', error);
        this.error = error?.error || 'No se pudo cancelar la asociación.';
        this.cdr.detectChanges();
      }
    });
  }

  mostrarSignosVitales(persona: PersonaMayorAcompanada): void {
    this.personaSignosVitales = persona;
    this.signosVitales = [];
    this.errorSignosVitales = null;
    this.cargandoSignosVitales = true;

    this.cargarSignosVitales(persona.idUsuario);
  }

  private cargarSignosVitales(idPersonaMayor: number): void {
    this.acompananteService.obtenerSignosVitalesSeguimiento(idPersonaMayor).subscribe({
      next: (registros) => {
        this.signosVitales = registros;
        this.cargandoSignosVitales = false;
        this.cdr.detectChanges();
      },
      error: (error) => {
        console.error('Error al cargar signos vitales:', error);
        this.errorSignosVitales = 'No se pudieron cargar los signos vitales.';
        this.cargandoSignosVitales = false;
        this.cdr.detectChanges();
      }
    });
  }

  cerrarSignosVitales(): void {
    this.personaSignosVitales = null;
    this.signosVitales = [];
  }

  mostrarAcompanantes(persona: PersonaMayorAcompanada): void {
    this.personaAcompanantes = persona;
    this.acompanantes = [];
    this.errorAcompanantes = null;
    this.cargandoAcompanantes = true;

    this.cargarAcompanantes(persona.idUsuario);
  }

  private cargarAcompanantes(idPersonaMayor: number): void {
    this.acompananteService.obtenerAcompanantesSeguimiento(idPersonaMayor).subscribe({
      next: (acompanantes) => {
        this.acompanantes = acompanantes;
        this.cargandoAcompanantes = false;
        this.cdr.detectChanges();
      },
      error: (error) => {
        console.error('Error al cargar acompañantes:', error);
        this.errorAcompanantes = 'No se pudieron cargar los acompañantes.';
        this.cargandoAcompanantes = false;
        this.cdr.detectChanges();
      }
    });
  }

  cerrarAcompanantes(): void {
    this.personaAcompanantes = null;
    this.acompanantes = [];
  }
}
