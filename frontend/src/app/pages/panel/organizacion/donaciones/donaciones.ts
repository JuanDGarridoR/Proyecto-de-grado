import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Donacion, DonacionService } from '../../../../core/donaciones/donacion.service';
import { formatearCantidad, formatearFecha } from '../../../../core/donaciones/catalogo';
import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';
import { mensajeDeError } from '../../../../core/formato/formato';
import { Icono } from '../../../../shared/icono/icono';
import { Cargando } from '../../../../shared/cargando/cargando';
import { TarjetaDonacion } from '../../../../shared/tarjeta-donacion/tarjeta-donacion';
import { BuscadorNombre } from '../../../../shared/buscador-nombre/buscador-nombre';

/** Una fila del modal "Marcar recibida": lo anunciado y lo que de verdad llegó. */
interface CantidadRecibida {
  idArticulo: number;
  descripcion: string;
  unidad: string;
  anunciada: number;
  recibida: number | null;
}

/**
 * Donaciones que los acompañantes y voluntarios programaron para la
 * organización. Al llegar, se marcan recibidas (lo que llegó entra al
 * inventario) o no entregadas.
 */
@Component({
  selector: 'app-organizacion-donaciones',
  imports: [FormsModule, RouterLink, Icono, Cargando, TarjetaDonacion, BuscadorNombre],
  templateUrl: './donaciones.html',
  styleUrls: [
    '../../../../shared/tarjeta-persona/vinculos-pagina.css',
    '../../../../shared/tarjeta-actividad/actividades-modales.css',
    '../../../../shared/tarjeta-donacion/donaciones-pagina.css',
    './donaciones.css',
  ],
})
export class OrganizacionDonaciones implements OnInit {
  private readonly donacionService = inject(DonacionService);

  protected readonly donaciones = signal<Donacion[]>([]);
  protected readonly cargando = signal(true);
  protected readonly mensaje = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly porRecibir = computed(() =>
    this.donaciones().filter((d) => d.estado === 'PROGRAMADA'),
  );

  /** Búsqueda por donante en el historial. */
  protected readonly busqueda = signal('');

  protected readonly historial = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();

    return this.donaciones()
      .filter((d) => d.estado !== 'PROGRAMADA')
      .filter((d) => !texto || (d.nombreDonante ?? '').toLowerCase().includes(texto))
      .sort((a, b) => (b.fechaRespuesta ?? '').localeCompare(a.fechaRespuesta ?? ''));
  });

  protected readonly totalHistorial = computed(
    () => this.donaciones().filter((d) => d.estado !== 'PROGRAMADA').length,
  );

  // Marcar recibida
  protected readonly donacionARecibir = signal<Donacion | null>(null);
  protected cantidades: CantidadRecibida[] = [];

  // Marcar no entregada
  protected readonly donacionNoEntregada = signal<Donacion | null>(null);
  protected nota = '';

  protected readonly procesando = signal(false);
  protected readonly errorModal = signal<string | null>(null);

  protected readonly formatearCantidad = formatearCantidad;
  protected readonly formatearFecha = formatearFecha;

  constructor() {
    // Nuevas donaciones, cancelaciones o cambios de nombre de los donantes.
    alCambiar(['donaciones', 'usuarios'], () => this.cargar());
  }

  ngOnInit(): void {
    this.cargar();
  }

  protected abrirRecibir(donacion: Donacion): void {
    this.cantidades = donacion.articulos.map((a) => ({
      idArticulo: a.idArticulo,
      descripcion: a.descripcion,
      unidad: a.unidad,
      anunciada: a.cantidad,
      recibida: a.cantidad,
    }));
    this.errorModal.set(null);
    this.donacionARecibir.set(donacion);
  }

  protected confirmarRecibida(): void {
    const donacion = this.donacionARecibir();
    if (!donacion || this.procesando()) return;

    if (this.cantidades.some((c) => c.recibida === null || c.recibida < 0)) {
      this.errorModal.set('Indica cuánto llegó de cada artículo (0 si no llegó).');
      return;
    }

    if (this.cantidades.every((c) => c.recibida === 0)) {
      this.errorModal.set('Si no llegó nada, marca la donación como no entregada.');
      return;
    }

    const recibidas: Record<number, number> = {};
    for (const c of this.cantidades) {
      recibidas[c.idArticulo] = c.recibida ?? 0;
    }

    this.ejecutar(
      this.donacionService.marcarRecibida(donacion.idDonacion, recibidas),
      `Donación de ${donacion.nombreDonante ?? 'el donante'} recibida: lo que llegó ya está en el inventario.`,
      'No se pudo marcar la donación como recibida.',
    );
  }

  protected abrirNoEntregada(donacion: Donacion): void {
    this.nota = '';
    this.errorModal.set(null);
    this.donacionNoEntregada.set(donacion);
  }

  protected confirmarNoEntregada(): void {
    const donacion = this.donacionNoEntregada();
    if (!donacion || this.procesando()) return;

    this.ejecutar(
      this.donacionService.marcarNoEntregada(donacion.idDonacion, this.nota.trim() || null),
      'La donación quedó marcada como no entregada.',
      'No se pudo actualizar la donación.',
    );
  }

  protected cerrarModales(): void {
    if (this.procesando()) return;
    this.donacionARecibir.set(null);
    this.donacionNoEntregada.set(null);
  }

  private ejecutar(peticion: ReturnType<DonacionService['marcarRecibida']>, exito: string, porDefecto: string): void {
    this.procesando.set(true);
    this.errorModal.set(null);

    peticion.subscribe({
      next: () => {
        this.procesando.set(false);
        this.donacionARecibir.set(null);
        this.donacionNoEntregada.set(null);
        this.mensaje.set(exito);
        this.error.set(null);
        this.cargar();
      },
      error: (err) => {
        console.error('Error al procesar la donación:', err);
        this.procesando.set(false);
        this.errorModal.set(mensajeDeError(err, porDefecto));
      },
    });
  }

  private cargar(): void {
    this.donacionService.donacionesRecibidas().subscribe({
      next: (donaciones) => {
        this.donaciones.set(donaciones);
        this.cargando.set(false);
      },
      error: (err) => {
        console.error('Error al cargar las donaciones:', err);
        this.error.set('No se pudieron cargar las donaciones.');
        this.cargando.set(false);
      },
    });
  }
}
