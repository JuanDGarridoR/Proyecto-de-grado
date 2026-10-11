import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';

import {
  OrganizacionService,
  VoluntarioOrganizacion
} from '../../../../core/organizacion/organizacion.service';
import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';
import { Icono } from '../../../../shared/icono/icono';
import { TarjetaPersona } from '../../../../shared/tarjeta-persona/tarjeta-persona';
import { CancelarAsociacion } from '../../../../shared/cancelar-asociacion/cancelar-asociacion';
import { mensajeDeError } from '../../../../core/formato/formato';
import { Cargando } from '../../../../shared/cargando/cargando';
import { BuscadorNombre, filtrarPorNombre } from '../../../../shared/buscador-nombre/buscador-nombre';

/**
 * Voluntarios de la organización: solicitudes de vinculación pendientes
 * (aceptar o rechazar) y voluntarios ya vinculados, con búsqueda por nombre.
 */
@Component({
  selector: 'app-organizacion-voluntarios',
  standalone: true,
  imports: [Icono, TarjetaPersona, CancelarAsociacion, Cargando, BuscadorNombre],
  templateUrl: './voluntarios.html',
  styleUrls: ['../../../../shared/tarjeta-persona/vinculos-pagina.css']
})
export class Voluntarios implements OnInit {
  private organizacionService = inject(OrganizacionService);

  protected readonly voluntarios = signal<VoluntarioOrganizacion[]>([]);
  protected readonly solicitudes = signal<VoluntarioOrganizacion[]>([]);
  protected readonly cargando = signal(true);

  // Búsqueda por nombre en los voluntarios vinculados
  protected readonly busqueda = signal('');
  protected readonly voluntariosFiltrados = computed(() =>
    filtrarPorNombre(this.voluntarios(), this.busqueda())
  );

  protected readonly mensaje = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  /** Voluntario sobre el que hay una petición en curso (deshabilita sus botones). */
  protected readonly procesando = signal<number | null>(null);

  protected readonly voluntarioADesvincular = signal<VoluntarioOrganizacion | null>(null);

  constructor() {
    // Nuevas solicitudes, solicitudes canceladas o cambios de nombre
    alCambiar(['voluntarios', 'usuarios'], () => this.cargar());
  }

  ngOnInit(): void {
    this.cargar();
  }

  protected aceptar(voluntario: VoluntarioOrganizacion): void {
    this.ejecutar(voluntario, this.organizacionService.aceptarSolicitudVoluntario(voluntario.idUsuario),
      'No se pudo aceptar la solicitud.');
  }

  protected rechazar(voluntario: VoluntarioOrganizacion): void {
    this.ejecutar(voluntario, this.organizacionService.rechazarSolicitudVoluntario(voluntario.idUsuario),
      'No se pudo rechazar la solicitud.');
  }

  protected confirmarDesvinculacion(): void {
    const voluntario = this.voluntarioADesvincular();
    if (!voluntario) return;

    this.voluntarioADesvincular.set(null);
    this.ejecutar(voluntario, this.organizacionService.desvincularVoluntario(voluntario.idUsuario),
      'No se pudo desvincular al voluntario.');
  }

  private ejecutar(
    voluntario: VoluntarioOrganizacion,
    peticion: Observable<string>,
    errorPorDefecto: string
  ): void {
    this.mensaje.set(null);
    this.error.set(null);
    this.procesando.set(voluntario.idUsuario);

    peticion.subscribe({
      next: (respuesta) => {
        this.procesando.set(null);
        this.mensaje.set(respuesta);
        this.cargar();
      },
      error: (error) => {
        console.error('Error gestionando el voluntario:', error);
        this.procesando.set(null);
        this.error.set(mensajeDeError(error, errorPorDefecto));
        this.cargar();
      }
    });
  }

  private cargar(): void {
    this.organizacionService.obtenerSolicitudesVoluntarios().subscribe({
      next: (solicitudes) => this.solicitudes.set(solicitudes),
      error: (error) => console.error('Error al cargar las solicitudes de voluntarios:', error)
    });

    this.organizacionService.obtenerVoluntarios().subscribe({
      next: (voluntarios) => {
        this.voluntarios.set(voluntarios);
        this.cargando.set(false);
      },
      error: (error) => {
        console.error('Error al cargar los voluntarios:', error);
        this.error.set('No se pudieron cargar los voluntarios.');
        this.cargando.set(false);
      }
    });
  }
}
