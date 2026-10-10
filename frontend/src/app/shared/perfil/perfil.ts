import { Component, Input, OnInit, ChangeDetectorRef } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CampoContrasena } from '../campo-contrasena/campo-contrasena';
import { HttpClient } from '@angular/common/http';
import { timeout } from 'rxjs';

import { EliminarCuenta } from '../eliminar-cuenta/eliminar-cuenta';
import { GestorNotificaciones } from '../gestor-notificaciones/gestor-notificaciones';
import { DatosSalud } from '../datos-salud/datos-salud';

import { AuthService } from '../../core/auth/auth.service';

import {
  PersonaMayorService,
  PersonaMayorResponse,
} from '../../core/persona-mayor/persona-mayor.service';

import { AcompananteService, AcompanantePerfil } from '../../core/acompanantes/acompanante.service';

import {
  OrganizacionService,
  OrganizacionResponse,
} from '../../core/organizacion/organizacion.service';
import { API_URL } from '../../core/api';
import { Cargando } from '../cargando/cargando';

/** Rol del perfil que se muestra; llega en data.tipoPerfil de la ruta. */
export type TipoPerfil = 'PERSONA_MAYOR' | 'ACOMPANANTE' | 'VOLUNTARIO' | 'ORGANIZACION';

/** Datos que la página muestra y edita, comunes a todos los roles. */
interface PerfilInformacion {
  idUsuario?: number;
  nombre: string;
  correo: string;
  celular: string;
  fechaNacimiento?: string;
  genero?: string;
  direccion?: string;
  // Solo para la persona mayor.
  eps?: string;
  ips?: string;
  direccionIps?: string;
  /** Si vive sola; null si no lo ha indicado. */
  viveSolo?: boolean | null;
  tieneContrasena: boolean;
}

/**
 * Página "Mi información" de todos los roles: muestra los datos de la cuenta,
 * permite editarlos, cambiar la contraseña, elegir qué notificaciones
 * recibir y eliminar la cuenta. La persona mayor además registra sus datos
 * de salud (enfermedades, alergias y discapacidades). La
 * organización además edita su dirección en organizacion-service.
 *
 * Los campos no son signals: después de cada respuesta del backend se llama
 * a detectChanges() para que Angular vuelva a pintar la vista.
 */
@Component({
  selector: 'app-perfil',
  imports: [FormsModule, CampoContrasena, EliminarCuenta, GestorNotificaciones, DatosSalud, Cargando],
  templateUrl: './perfil.html',
  styleUrl: './perfil.css',
})
export class Perfil implements OnInit {
  @Input({ required: true })
  tipoPerfil!: TipoPerfil;

  informacion: PerfilInformacion | null = null;
  formulario: PerfilInformacion | null = null;

  errorGuardado = '';

  cargando = true;
  editando = false;
  mostrandoConfirmacion = false;

  guardandoInformacion = false;
  guardadoExitoso = false;

  // Modal de contraseña
  mostrandoContrasena = false;
  contrasenaActual = '';
  nuevaContrasena = '';
  confirmarContrasena = '';

  errorContrasena = '';
  mensajeContrasena = '';
  guardandoContrasena = false;

  constructor(
    private personaMayorService: PersonaMayorService,
    private acompananteService: AcompananteService,
    private organizacionService: OrganizacionService,
    private authService: AuthService,
    private http: HttpClient,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.cargarInformacion();
  }

  get esPersonaMayor(): boolean {
    return this.tipoPerfil === 'PERSONA_MAYOR';
  }

  get esOrganizacion(): boolean {
    return this.tipoPerfil === 'ORGANIZACION';
  }

  /** Solo el perfil de la persona mayor muestra los datos personales. */
  get tieneDatosPersonales(): boolean {
    return this.tipoPerfil === 'PERSONA_MAYOR';
  }

  get tituloInformacion(): string {
    return this.esOrganizacion ? 'Información de la organización' : 'Mi información';
  }

  get descripcionInformacion(): string {
    return this.esOrganizacion
      ? 'Consulta y administra los datos de tu organización.'
      : 'Consulta y administra tus datos personales.';
  }

  get textoCarga(): string {
    return this.esOrganizacion
      ? 'Cargando información de la organización...'
      : 'Cargando tu información...';
  }

  /**
   * Carga los datos según el rol. La organización junta dos respuestas: la
   * de organizacion-service (dirección) y la de auth-service (id y
   * tieneContrasena).
   */
  cargarInformacion(): void {
    this.informacion = null;
    this.cargando = true;

    // Persona mayor, acompañante y voluntario: todo sale de auth-service.
    if (
      this.esPersonaMayor ||
      this.tipoPerfil === 'ACOMPANANTE' ||
      this.tipoPerfil === 'VOLUNTARIO'
    ) {
      this.http.get<any>(`${API_URL}/auth/informacion`).subscribe({
        next: (data) => {
          this.informacion = {
            idUsuario: data.idUsuario,
            nombre: data.nombre,
            celular: data.celular,
            correo: data.correo || '',
            fechaNacimiento: data.fechaNacimiento,
            genero: data.genero,
            direccion: data.direccion,
            eps: data.eps,
            ips: data.ips,
            direccionIps: data.direccionIps,
            viveSolo: data.viveSolo ?? null,
            tieneContrasena: data.tieneContrasena,
          };

          this.finalizarCarga();
        },
        error: (error) => {
          this.manejarErrorCarga(error);
        },
      });

      return;
    }

    // Organización
    if (this.esOrganizacion) {
      this.organizacionService.obtenerInformacion().subscribe({
        next: (data) => {
          this.http.get<any>(`${API_URL}/auth/informacion`).subscribe({
            next: (identidad) => {
              this.informacion = this.normalizarOrganizacion(data, identidad);

              this.finalizarCarga();
            },
            error: (error) => {
              this.manejarErrorCarga(error);
            },
          });
        },
        error: (error) => {
          this.manejarErrorCarga(error);
        },
      });

      return;
    }
  }

  /** Hoy no se usa: el perfil de la persona mayor se lee de auth-service. */
  private normalizarPersonaMayor(data: PersonaMayorResponse): PerfilInformacion {
    return {
      idUsuario: data.idUsuario,
      nombre: data.nombre,
      celular: data.celular,
      correo: data.correo || '',
      fechaNacimiento: data.fechaNacimiento,
      genero: data.genero,
      direccion: data.direccion,
      tieneContrasena: data.tieneContrasena,
    };
  }

  /** Hoy no se usa: el perfil del acompañante se lee de auth-service. */
  private normalizarAcompanante(data: AcompanantePerfil): PerfilInformacion {
    return {
      idUsuario: data.idUsuario,
      nombre: data.nombre,
      celular: data.celular,
      correo: data.correo || '',
      tieneContrasena: data.tieneContrasena,
    };
  }

  /** Une los datos de la organización con los de su cuenta. */
  private normalizarOrganizacion(data: OrganizacionResponse, identidad: any): PerfilInformacion {
    return {
      idUsuario: identidad.idUsuario,
      nombre: data.nombre,
      celular: data.celular,
      correo: data.correo || '',
      direccion: data.direccion,
      tieneContrasena: identidad.tieneContrasena,
    };
  }

  private finalizarCarga(): void {
    this.cargando = false;
    this.cdr.detectChanges();
  }

  private manejarErrorCarga(error: any): void {
    console.error('Error al cargar la información:', error);

    this.cargando = false;
    this.cdr.detectChanges();
  }

  /** Abre el formulario con una copia de los datos actuales. */
  abrirEdicion(): void {
    if (!this.informacion) {
      return;
    }

    this.formulario = {
      ...this.informacion,
    };

    this.errorGuardado = '';
    this.editando = true;
  }

  /** Antes de guardar se pide confirmación. */
  guardarCambios(): void {
    if (!this.formulario) {
      return;
    }

    this.guardandoInformacion = false;
    this.guardadoExitoso = false;
    this.errorGuardado = '';
    this.mostrandoConfirmacion = true;
  }

  /** Guarda según el rol. Si el backend tarda más de 10 segundos, se muestra el error. */
  confirmarGuardado(): void {
    if (!this.formulario || this.guardandoInformacion) {
      return;
    }

    this.guardandoInformacion = true;
    this.guardadoExitoso = false;
    this.errorGuardado = '';
    this.cdr.detectChanges();

    if (
      this.esPersonaMayor ||
      this.tipoPerfil === 'ACOMPANANTE' ||
      this.tipoPerfil === 'VOLUNTARIO'
    ) {
      this.http
        .put<any>(`${API_URL}/auth/informacion`, {
          nombre: this.formulario.nombre,
          correo: this.formulario.correo || null,
          fechaNacimiento: this.formulario.fechaNacimiento || null,
          genero: this.formulario.genero || null,
          direccion: this.formulario.direccion || null,
          eps: this.formulario.eps || null,
          ips: this.formulario.ips || null,
          direccionIps: this.formulario.direccionIps || null,
          viveSolo: this.formulario.viveSolo ?? null,
        })
        .pipe(timeout(10000))
        .subscribe({
          next: (data) => {
            this.informacion = {
              idUsuario: data.idUsuario,
              nombre: data.nombre,
              celular: data.celular,
              correo: data.correo || '',
              fechaNacimiento: data.fechaNacimiento,
              genero: data.genero,
              direccion: data.direccion,
              eps: data.eps,
              ips: data.ips,
              direccionIps: data.direccionIps,
              viveSolo: data.viveSolo ?? null,
              tieneContrasena: data.tieneContrasena,
            };

            this.finalizarGuardado(data.nombre);
          },
          error: (error) => {
            this.manejarErrorGuardado(error);
          },
        });

      return;
    }

    if (this.esOrganizacion) {
      const datos: OrganizacionResponse = {
        idOrganizacion: this.formulario.idUsuario ?? 0,

        nombre: this.formulario.nombre,
        direccion: this.formulario.direccion || '',
        celular: this.formulario.celular,
        correo: this.formulario.correo,
      };

      this.organizacionService
        .actualizarInformacion(datos)
        .pipe(timeout(10000))
        .subscribe({
          next: (data) => {
            this.http
              .get<any>(`${API_URL}/auth/informacion`)
              .pipe(timeout(10000))
              .subscribe({
                next: (identidad) => {
                  this.informacion = this.normalizarOrganizacion(data, identidad);

                  this.finalizarGuardado(identidad.nombre);
                },

                error: (error) => {
                  this.manejarErrorGuardado(error);
                },
              });
          },

          error: (error) => {
            this.manejarErrorGuardado(error);
          },
        });

      return;
    }

    // No se llega aquí: los cuatro roles se atienden arriba.
    this.http
      .put<any>(`${API_URL}/auth/informacion`, {
        nombre: this.formulario.nombre,
        correo: this.formulario.correo,
      })
      .subscribe({
        next: (data) => {
          this.informacion = {
            ...this.informacion!,
            nombre: data.nombre,
            correo: data.correo || '',
          };

          this.finalizarGuardado(data.nombre);
        },

        error: (error) => {
          this.manejarErrorGuardado(error);
        },
      });
  }

  /**
   * Muestra el mensaje de éxito un momento y cierra el formulario. También
   * actualiza el nombre que se ve en el panel.
   */
  private finalizarGuardado(nombre: string): void {
    this.guardandoInformacion = false;
    this.guardadoExitoso = true;

    this.authService.actualizarNombreUsuario(nombre);

    this.cdr.detectChanges();

    setTimeout(() => {
      this.formulario = null;
      this.editando = false;
      this.mostrandoConfirmacion = false;
      this.guardadoExitoso = false;

      this.cdr.detectChanges();
    }, 1500);
  }

  /** Si el backend responde con un texto (por ejemplo, correo rechazado), se muestra tal cual. */
  private manejarErrorGuardado(error: any): void {
    console.error('Error al actualizar la información:', error);

    this.guardandoInformacion = false;
    this.guardadoExitoso = false;

    this.errorGuardado =
      typeof error.error === 'string' && error.error
        ? error.error
        : 'No se pudo guardar la información. Intenta de nuevo.';

    this.cdr.detectChanges();
  }

  cancelarGuardado(): void {
    this.mostrandoConfirmacion = false;
  }

  cancelarEdicion(): void {
    this.editando = false;
    this.formulario = null;
    this.errorGuardado = '';
  }

  abrirModalContrasena(): void {
    // Para tener contraseña hay que tener un correo registrado.
    if (!this.informacion?.correo?.trim()) {
      this.errorContrasena =
        'Para cambiar tu contraseña primero debes registrar un correo electrónico.';

      this.mensajeContrasena = '';

      return;
    }

    this.contrasenaActual = '';
    this.nuevaContrasena = '';
    this.confirmarContrasena = '';

    this.errorContrasena = '';
    this.mensajeContrasena = '';

    this.mostrandoContrasena = true;
  }

  cerrarModalContrasena(): void {
    if (this.guardandoContrasena) {
      return;
    }

    this.mostrandoContrasena = false;

    this.contrasenaActual = '';
    this.nuevaContrasena = '';
    this.confirmarContrasena = '';

    this.errorContrasena = '';
    this.mensajeContrasena = '';
  }

  /** Revisa en el navegador las mismas reglas del backend y guarda la contraseña. */
  guardarContrasena(): void {
    this.errorContrasena = '';
    this.mensajeContrasena = '';

    if (!this.informacion) {
      return;
    }

    if (this.informacion.tieneContrasena) {
      if (!this.contrasenaActual.trim()) {
        this.errorContrasena = 'Ingresa tu contraseña actual.';

        return;
      }
    }

    if (!this.nuevaContrasena.trim()) {
      this.errorContrasena = 'Ingresa una nueva contraseña.';

      return;
    }

    if (this.nuevaContrasena.length < 6) {
      this.errorContrasena = 'La contraseña debe tener mínimo 6 caracteres.';

      return;
    }

    if (this.nuevaContrasena !== this.confirmarContrasena) {
      this.errorContrasena = 'Las contraseñas no coinciden.';

      return;
    }

    this.guardandoContrasena = true;

    this.http
      .put(
        `${API_URL}/auth/contrasena`,
        {
          contrasenaActual: this.informacion.tieneContrasena ? this.contrasenaActual : undefined,

          nuevaContrasena: this.nuevaContrasena,
        },
        {
          responseType: 'text',
        },
      )
      .subscribe({
        next: () => {
          this.guardandoContrasena = false;

          if (this.informacion) {
            this.informacion.tieneContrasena = true;
          }

          this.mensajeContrasena = 'Contraseña guardada correctamente.';

          this.contrasenaActual = '';
          this.nuevaContrasena = '';
          this.confirmarContrasena = '';

          this.cdr.detectChanges();
        },

        error: (error) => {
          console.error('Error al cambiar contraseña:', error);

          this.guardandoContrasena = false;

          if (error.status === 401) {
            this.errorContrasena = 'La contraseña actual es incorrecta.';
          } else if (error.error) {
            this.errorContrasena =
              typeof error.error === 'string'
                ? error.error
                : 'No se pudo actualizar la contraseña.';
          } else {
            this.errorContrasena = 'No se pudo actualizar la contraseña.';
          }

          this.cdr.detectChanges();
        },
      });
  }
}
