import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';

import {
  OrganizacionVoluntario,
  VoluntarioService
} from '../../../../core/voluntario/voluntario.service';
import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';
import { Icon } from '../../../../shared/icon/icon';
import { PersonCard } from '../../../../shared/person-card/person-card';
import { CancelarAsociacion } from '../../../../shared/cancelar-asociacion/cancelar-asociacion';
import { BuscadorNombre, filtrarPorNombre } from '../../../../shared/buscador-nombre/buscador-nombre';
import { RecomendacionesOrganizacionesComponent } from '../../../../shared/recomendaciones-organizaciones/recomendaciones-organizaciones';
import { mensajeDeError } from '../../../../core/formato/formato';
import { Cargando } from '../../../../shared/cargando/cargando';

/**
 * Organizaciones del voluntario: a cuáles está vinculado, sus solicitudes
 * (pendientes o rechazadas), las recomendadas según sus gustos y su
 * dirección (las mismas tarjetas que ve la persona mayor) y, en un
 * desplegable, todas las demás. Puede pedir vincularse a cualquiera y estar
 * vinculado a varias a la vez.
 */
@Component({
  selector: 'app-voluntario-organizaciones',
  standalone: true,
  imports: [FormsModule, Icon, PersonCard, CancelarAsociacion, RecomendacionesOrganizacionesComponent, BuscadorNombre, Cargando],
  templateUrl: './organizaciones.html',
  styleUrls: ['../../../../shared/person-card/vinculos-pagina.css', './organizaciones.css']
})
export class VoluntarioOrganizaciones implements OnInit {
  private voluntarioService = inject(VoluntarioService);

  protected readonly organizaciones = signal<OrganizacionVoluntario[]>([]);
  protected readonly cargando = signal(true);

  protected readonly mensaje = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  /** Organización sobre la que hay una petición en curso (deshabilita sus botones). */
  protected readonly procesando = signal<number | null>(null);

  protected readonly organizacionADesvincular = signal<OrganizacionVoluntario | null>(null);

  protected busqueda = '';
  protected readonly filtro = signal('');

  protected readonly vinculadas = computed(() =>
    this.organizaciones().filter((o) => o.estado === 'ACEPTADA')
  );

  /** Búsqueda rápida en "Mis organizaciones". */
  protected readonly busquedaVinculadas = signal('');

  protected readonly vinculadasFiltradas = computed(() =>
    filtrarPorNombre(this.vinculadas(), this.busquedaVinculadas())
  );

  protected readonly solicitudes = computed(() =>
    this.organizaciones().filter((o) => o.estado === 'PENDIENTE' || o.estado === 'RECHAZADA')
  );

  protected readonly disponibles = computed(() => {
    const filtro = this.filtro().trim().toLowerCase();

    return this.organizaciones().filter(
      (o) => o.estado === null && (!filtro || o.nombre.toLowerCase().includes(filtro))
    );
  });

  protected readonly hayDisponibles = computed(() =>
    this.organizaciones().some((o) => o.estado === null)
  );

  constructor() {
    // Una organización aceptó/rechazó la solicitud, o cambió de nombre
    alCambiar(['voluntarios', 'usuarios'], () => this.cargar());
  }

  ngOnInit(): void {
    this.cargar();
  }

  protected buscar(texto: string): void {
    this.filtro.set(texto);
  }

  protected solicitar(organizacion: OrganizacionVoluntario): void {
    this.ejecutar(organizacion, this.voluntarioService.solicitarVinculacion(organizacion.idOrganizacion),
      'No se pudo enviar la solicitud.');
  }

  /** Cancelar una solicitud pendiente o descartar una rechazada. */
  protected eliminarSolicitud(organizacion: OrganizacionVoluntario): void {
    this.ejecutar(organizacion, this.voluntarioService.eliminarVinculo(organizacion.idOrganizacion),
      'No se pudo actualizar la solicitud.');
  }

  protected confirmarDesvinculacion(): void {
    const organizacion = this.organizacionADesvincular();
    if (!organizacion) return;

    this.organizacionADesvincular.set(null);
    this.ejecutar(organizacion, this.voluntarioService.eliminarVinculo(organizacion.idOrganizacion),
      'No se pudo cancelar la vinculación.');
  }

  private ejecutar(
    organizacion: OrganizacionVoluntario,
    peticion: Observable<string>,
    errorPorDefecto: string
  ): void {
    this.mensaje.set(null);
    this.error.set(null);
    this.procesando.set(organizacion.idOrganizacion);

    peticion.subscribe({
      next: (respuesta) => {
        this.procesando.set(null);
        this.mensaje.set(respuesta);
        this.cargar();
      },
      error: (error) => {
        console.error('Error en la vinculación con la organización:', error);
        this.procesando.set(null);
        this.error.set(mensajeDeError(error, errorPorDefecto));
        this.cargar();
      }
    });
  }

  protected cargar(): void {
    this.voluntarioService.listarOrganizaciones().subscribe({
      next: (organizaciones) => {
        this.organizaciones.set(organizaciones);
        this.cargando.set(false);
      },
      error: (error) => {
        console.error('Error al cargar las organizaciones:', error);
        this.error.set('No se pudieron cargar las organizaciones.');
        this.cargando.set(false);
      }
    });
  }
}
