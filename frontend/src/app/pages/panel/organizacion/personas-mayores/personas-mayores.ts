import { Component, OnInit, computed, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';

import {
  OrganizacionService,
  PersonaMayorOrganizacion,
  AcompanantePersonaMayor,
  RetiroPersonaMayor
} from '../../../../core/organizacion/organizacion.service';
import { textoRazonRetiro } from '../../../../core/auth/razones-retiro';

import {
  SignosVitalesService,
  SignoVitalResponse
} from '../../../../core/signos-vitales/signos-vitales.services';

import { Icono } from '../../../../shared/icono/icono';
import { TarjetaPersona } from '../../../../shared/tarjeta-persona/tarjeta-persona';
import { CancelarAsociacion } from '../../../../shared/cancelar-asociacion/cancelar-asociacion';
import { ModalSignosVitales } from '../../../../shared/modal-signos-vitales/modal-signos-vitales';
import { ModalAcompanantes } from '../../../../shared/modal-acompanantes/modal-acompanantes';
import { BuscadorNombre, filtrarPorNombre } from '../../../../shared/buscador-nombre/buscador-nombre';
import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';
import { mensajeDeError } from '../../../../core/formato/formato';

/**
 * Personas mayores de la organización: lista de vinculadas, solicitud de
 * vínculo por celular, cancelación del vínculo, dos modales de consulta
 * (acompañantes y últimos signos vitales) y, al final, las que dejaron la
 * plataforma y por qué.
 */
@Component({
  selector: 'app-personas-mayores',
  standalone: true,
  imports: [Icono, FormsModule, DatePipe, TarjetaPersona, CancelarAsociacion, ModalSignosVitales, ModalAcompanantes, BuscadorNombre],
  templateUrl: './personas-mayores.html',
  styleUrl: './personas-mayores.css'
})
export class PersonasMayores implements OnInit {

  protected readonly personasMayores =
    signal<PersonaMayorOrganizacion[]>([]);

  /** true hasta que llega la primera respuesta (así no se muestra "No tienes personas" mientras carga). */
  protected readonly cargandoPersonas = signal(true);

  /** true si no se pudo cargar la lista. */
  protected readonly errorPersonas = signal(false);

  /** Búsqueda rápida en la lista de personas mayores vinculadas. */
  protected readonly busqueda = signal('');

  protected readonly personasFiltradas = computed(() =>
    filtrarPorNombre(this.personasMayores(), this.busqueda())
  );

  /** Personas mayores que pidieron unirse a la organización. */
  protected readonly solicitudes =
    signal<PersonaMayorOrganizacion[]>([]);

  /** Personas mayores que eliminaron su cuenta y por qué (por ejemplo, fallecimiento). */
  protected readonly retiros =
    signal<RetiroPersonaMayor[]>([]);

  protected readonly textoRazon = textoRazonRetiro;

  /** Solicitud que se está respondiendo; deshabilita sus botones. */
  protected readonly respondiendo =
    signal<number | null>(null);

  protected readonly mostrandoFormulario =
    signal(false);

  protected readonly agregandoPersonaMayor =
    signal(false);

  protected readonly mensaje =
    signal<string | null>(null);

  protected readonly mostrandoAcompanantes =
  signal(false);

protected readonly cargandoAcompanantes =
  signal(false);

protected readonly acompanantes =
  signal<AcompanantePersonaMayor[]>([]);

/** Persona de los modales de acompañantes o signos vitales. */
protected personaMayorSeleccionada:
  PersonaMayorOrganizacion | null = null;

protected readonly mostrandoSignosVitales =
  signal(false);

protected readonly cargandoSignosVitales =
  signal(false);

protected readonly errorSignosVitales =
  signal<string | null>(null);

protected readonly signosVitales =
  signal<SignoVitalResponse[]>([]);

  protected readonly error = signal<string | null>(null);
  protected readonly mostrandoConfirmacion = signal(false);
  /** Persona del modal de cancelar el vínculo. */
  protected personaSeleccionada: PersonaMayorOrganizacion | null = null;
  protected celular = '';

constructor(
  private organizacionService: OrganizacionService,
  private signosVitalesService: SignosVitalesService,
  private route: ActivatedRoute
) {
  alCambiar(['organizaciones', 'usuarios'], () => {
    this.cargarPersonasMayores();
    this.cargarSolicitudes();
    this.cargarRetiros();
  });

  // Si hay un modal abierto, se actualiza en vivo sin mostrar "cargando".
  alCambiar(['acompanamientos', 'usuarios'], () => {
    const persona = this.personaMayorSeleccionada;
    if (this.mostrandoAcompanantes() && persona) {
      this.organizacionService.obtenerAcompanantesPersonaMayor(persona.idUsuario).subscribe({
        next: (acompanantes) => this.acompanantes.set(acompanantes),
        error: (error) => console.error('Error al recargar acompañantes:', error)
      });
    }
  });

  alCambiar(['signos-vitales'], () => {
    const persona = this.personaMayorSeleccionada;
    if (this.mostrandoSignosVitales() && persona) {
      this.signosVitalesService.listarUltimos(persona.idUsuario).subscribe({
        next: (registros) => this.signosVitales.set(registros),
        error: (error) => console.error('Error al recargar signos vitales:', error)
      });
    }
  });
}

ngOnInit(): void {
  this.cargarPersonasMayores();
  this.cargarSolicitudes();
  this.cargarRetiros();

  // Desde las acciones rápidas del inicio se llega con ?abrir=registrar.
  this.route.queryParams.subscribe(params => {
    if (params['abrir'] === 'registrar') {
      this.mostrarFormulario();
    }
  });
}

  cargarRetiros(): void {
    this.organizacionService.obtenerRetiros().subscribe({
      next: (retiros) => this.retiros.set(retiros),
      error: () => this.retiros.set([])
    });
  }

  cargarSolicitudes(): void {
    this.organizacionService.obtenerSolicitudesPersonasMayores().subscribe({
      next: (solicitudes) => this.solicitudes.set(solicitudes),
      error: () => this.solicitudes.set([])
    });
  }

  /** Acepta o rechaza la solicitud de una persona mayor que pidió unirse. */
  responderSolicitud(persona: PersonaMayorOrganizacion, aceptar: boolean): void {
    this.respondiendo.set(persona.idUsuario);
    this.mensaje.set(null);
    this.error.set(null);

    const peticion = aceptar
      ? this.organizacionService.aceptarSolicitudPersonaMayor(persona.idUsuario)
      : this.organizacionService.rechazarSolicitudPersonaMayor(persona.idUsuario);

    peticion.subscribe({
      next: () => {
        this.respondiendo.set(null);
        this.mensaje.set(aceptar
          ? `${persona.nombre} ahora hace parte de tu organización.`
          : `Rechazaste la solicitud de ${persona.nombre}.`);
        this.cargarSolicitudes();
        this.cargarPersonasMayores();
      },
      error: (error) => {
        this.respondiendo.set(null);
        this.error.set(mensajeDeError(error, 'No se pudo responder la solicitud.'));
        this.cargarSolicitudes();
      }
    });
  }

  cargarPersonasMayores(): void {

    this.organizacionService.obtenerPersonasMayores().subscribe({
      next: (personas) => {
        this.personasMayores.set(personas);
        this.errorPersonas.set(false);
        this.cargandoPersonas.set(false);
      },

      error: (error) => {
        console.error(
          'Error al cargar personas mayores:',
          error
        );

        this.personasMayores.set([]);
        this.errorPersonas.set(true);
        this.cargandoPersonas.set(false);
      }
    });
  }


  mostrarFormulario(): void {

    this.mostrandoFormulario.set(true);

    this.celular = '';

    this.mensaje.set(null);
    this.error.set(null);
  }


  cancelarFormulario(): void {

    this.mostrandoFormulario.set(false);

    this.celular = '';

    this.mensaje.set(null);
    this.error.set(null);
  }


  /** Envía la solicitud de vínculo a la persona mayor con ese celular (10 dígitos, sin +57). */
  asociarPersonaMayor(): void {

    this.mensaje.set(null);
    this.error.set(null);

    const celularIngresado =
      this.celular.trim();


    if (!celularIngresado) {

      this.error.set(
        'Por favor ingresa el celular de la persona mayor.'
      );

      return;
    }


    if (!/^\d{10}$/.test(celularIngresado)) {

      this.error.set(
        'Ingresa un número de celular válido de 10 dígitos.'
      );

      return;
    }


    // El backend guarda el celular con el indicativo de Colombia.
    const celular =
      '+57' + celularIngresado;


    this.agregandoPersonaMayor.set(true);


    this.organizacionService
      .asociarPersonaMayor(celular)
      .subscribe({

        next: (respuesta) => {

          this.agregandoPersonaMayor.set(false);

          this.mostrandoFormulario.set(false);

          this.celular = '';

          this.mensaje.set(respuesta);

          this.cargarPersonasMayores();
        },

        error: (error) => {

          this.agregandoPersonaMayor.set(false);

          const mensaje =
            error?.error ||
            'No se pudo enviar la solicitud.';

          this.error.set(mensaje);
        }

      });
  }
mostrarConfirmacion(persona: PersonaMayorOrganizacion): void {
  this.personaSeleccionada = persona;
  this.mostrandoConfirmacion.set(true);
  this.mensaje.set(null);
  this.error.set(null);
}

cerrarConfirmacion(): void {
  this.mostrandoConfirmacion.set(false);
  this.personaSeleccionada = null;
}

/** Cancela el vínculo con la persona del modal. */
confirmarCancelacion(): void {
  if (!this.personaSeleccionada) return;

  const persona = this.personaSeleccionada;

  this.mensaje.set(null);
  this.error.set(null);
  this.mostrandoConfirmacion.set(false);

  this.organizacionService
    .cancelarAsociacionPersonaMayor(persona.idUsuario)
    .subscribe({
      next: (respuesta) => {
        this.personaSeleccionada = null;
        this.mensaje.set(respuesta);
        this.cargarPersonasMayores();
      },
      error: (error) => {
        console.error('Error al cancelar la asociación:', error);
        const mensaje =
          error?.error || 'No se pudo cancelar la asociación.';
        this.error.set(mensaje);
        this.personaSeleccionada = null;
      }
    });
}

/** Abre el modal con los acompañantes de la persona mayor. */
mostrarAcompanantes(persona: PersonaMayorOrganizacion): void {

  this.personaMayorSeleccionada = persona;

  this.acompanantes.set([]);

  this.mostrandoAcompanantes.set(true);

  this.cargandoAcompanantes.set(true);

  this.organizacionService
    .obtenerAcompanantesPersonaMayor(persona.idUsuario)
    .subscribe({

      next: (acompanantes) => {

        this.acompanantes.set(acompanantes);

        this.cargandoAcompanantes.set(false);

      },

      error: (error) => {

        console.error(
          'Error al cargar acompañantes:',
          error
        );

        this.acompanantes.set([]);

        this.cargandoAcompanantes.set(false);

      }

    });
}

cerrarAcompanantes(): void {

  this.mostrandoAcompanantes.set(false);

  this.personaMayorSeleccionada = null;

  this.acompanantes.set([]);
}

/** Abre el modal con los últimos 10 registros de signos vitales. */
mostrarSignosVitales(persona: PersonaMayorOrganizacion): void {

  this.personaMayorSeleccionada = persona;

  this.signosVitales.set([]);

  this.errorSignosVitales.set(null);

  this.mostrandoSignosVitales.set(true);

  this.cargandoSignosVitales.set(true);

  this.signosVitalesService
    .listarUltimos(persona.idUsuario)
    .subscribe({

      next: (registros) => {

        this.signosVitales.set(registros);

        this.cargandoSignosVitales.set(false);

      },

      error: (error) => {

        console.error(
          'Error al cargar signos vitales:',
          error
        );

        this.errorSignosVitales.set(
          'No se pudieron cargar los signos vitales.'
        );

        this.cargandoSignosVitales.set(false);

      }

    });
}

cerrarSignosVitales(): void {

  this.mostrandoSignosVitales.set(false);

  this.personaMayorSeleccionada = null;

  this.signosVitales.set([]);
}

}