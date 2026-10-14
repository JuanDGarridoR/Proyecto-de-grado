import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import {
  Donacion,
  DonacionService,
  NuevaDonacion,
  OrganizacionDonable,
} from '../../core/donaciones/donacion.service';
import {
  CATEGORIAS,
  Categoria,
  UNIDADES_SUGERIDAS,
  formatearCantidad,
  formatearFecha,
  textoCategoria,
  tieneVencimiento,
} from '../../core/donaciones/catalogo';
import { alCambiar } from '../../core/tiempo-real/tiempo-real.service';
import { fechaLocal } from '../../core/fechas/fechas';
import { mensajeDeError } from '../../core/formato/formato';
import { Icono } from '../icono/icono';
import { Avatar } from '../avatar/avatar';
import { Cargando } from '../cargando/cargando';
import { TarjetaDonacion } from '../tarjeta-donacion/tarjeta-donacion';

/** Un artículo mientras se llena el formulario (la cantidad puede estar vacía). */
interface ArticuloFormulario {
  categoria: Categoria;
  descripcion: string;
  cantidad: number | null;
  unidad: string;
  fechaVencimiento: string;
}

const MAX_ARTICULOS = 20;

/**
 * Donaciones del acompañante y del voluntario: programar una donación para
 * una de sus organizaciones (qué lleva, cuándo y a qué hora), ver las que
 * están programadas, cancelarlas y consultar el historial.
 */
@Component({
  selector: 'app-donaciones-donante',
  imports: [FormsModule, RouterLink, Icono, Avatar, Cargando, TarjetaDonacion],
  templateUrl: './donaciones-donante.html',
  styleUrls: [
    '../tarjeta-persona/vinculos-pagina.css',
    '../tarjeta-actividad/actividades-modales.css',
    '../tarjeta-donacion/donaciones-pagina.css',
  ],
})
export class DonacionesDonante implements OnInit {
  private readonly donacionService = inject(DonacionService);

  /** Llega en data.rol de la ruta. */
  readonly rol = input<'ACOMPANANTE' | 'VOLUNTARIO'>('VOLUNTARIO');

  protected readonly organizaciones = signal<OrganizacionDonable[]>([]);
  protected readonly donaciones = signal<Donacion[]>([]);
  protected readonly cargando = signal(true);

  protected readonly mensaje = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly programadas = computed(() =>
    this.donaciones()
      .filter((d) => d.estado === 'PROGRAMADA')
      .sort((a, b) => a.fechaEntrega.localeCompare(b.fechaEntrega)),
  );

  protected readonly historial = computed(() => this.donaciones().filter((d) => d.estado !== 'PROGRAMADA'));

  // Formulario de nueva donación
  protected readonly formularioAbierto = signal(false);
  protected readonly confirmando = signal(false);
  protected readonly guardando = signal(false);
  protected readonly errorFormulario = signal<string | null>(null);

  protected idOrganizacion: number | null = null;
  protected fechaEntrega = '';
  protected horaEntrega = '';
  protected observaciones = '';
  protected articulos: ArticuloFormulario[] = [];

  protected readonly hoy = fechaLocal(new Date());

  // Cancelación
  protected readonly donacionACancelar = signal<Donacion | null>(null);
  protected readonly cancelando = signal(false);

  protected readonly CATEGORIAS = CATEGORIAS;
  protected readonly UNIDADES_SUGERIDAS = UNIDADES_SUGERIDAS;
  protected readonly MAX_ARTICULOS = MAX_ARTICULOS;
  protected readonly tieneVencimiento = tieneVencimiento;
  protected readonly formatearCantidad = formatearCantidad;
  protected readonly formatearFecha = formatearFecha;
  protected readonly textoCategoria = textoCategoria;

  constructor() {
    // La organización marcó una donación recibida, o cambió un vínculo.
    alCambiar(['donaciones', 'organizaciones', 'voluntarios', 'acompanamientos', 'usuarios'], () =>
      this.cargar(),
    );
  }

  ngOnInit(): void {
    this.cargar();
  }

  protected get esVoluntario(): boolean {
    return this.rol() === 'VOLUNTARIO';
  }

  protected organizacionElegida(): OrganizacionDonable | undefined {
    return this.organizaciones().find((o) => o.idOrganizacion === this.idOrganizacion);
  }

  // Formulario

  protected abrirFormulario(): void {
    this.idOrganizacion =
      this.organizaciones().length === 1 ? this.organizaciones()[0].idOrganizacion : null;
    this.fechaEntrega = '';
    this.horaEntrega = '';
    this.observaciones = '';
    this.articulos = [this.articuloVacio()];
    this.errorFormulario.set(null);
    this.confirmando.set(false);
    this.formularioAbierto.set(true);
  }

  protected cerrarFormulario(): void {
    if (this.guardando()) return;
    this.formularioAbierto.set(false);
  }

  protected agregarArticulo(): void {
    if (this.articulos.length < MAX_ARTICULOS) {
      this.articulos.push(this.articuloVacio());
    }
  }

  protected quitarArticulo(indice: number): void {
    this.articulos.splice(indice, 1);
  }

  /** Al cambiar de categoría se limpian los campos que no aplican. */
  protected cambioCategoria(articulo: ArticuloFormulario): void {
    if (articulo.categoria === 'DINERO') {
      articulo.unidad = 'COP';
    } else if (articulo.unidad === 'COP') {
      articulo.unidad = '';
    }

    if (!tieneVencimiento(articulo.categoria)) {
      articulo.fechaVencimiento = '';
    }
  }

  /** Revisa en el navegador lo mismo que el backend y pasa a la confirmación. */
  protected revisar(): void {
    const error = this.errorDelFormulario();
    this.errorFormulario.set(error);

    if (!error) {
      this.confirmando.set(true);
    }
  }

  protected volverAEditar(): void {
    this.confirmando.set(false);
  }

  protected guardar(): void {
    if (this.guardando() || this.idOrganizacion === null) return;

    const donacion: NuevaDonacion = {
      idOrganizacion: this.idOrganizacion,
      fechaEntrega: this.fechaEntrega,
      horaEntrega: this.horaEntrega || null,
      observaciones: this.observaciones.trim() || null,
      articulos: this.articulos.map((a) => ({
        categoria: a.categoria,
        descripcion: a.descripcion.trim(),
        cantidad: a.cantidad ?? 0,
        unidad: a.categoria === 'DINERO' ? 'COP' : a.unidad.trim(),
        fechaVencimiento: tieneVencimiento(a.categoria) && a.fechaVencimiento ? a.fechaVencimiento : null,
      })),
    };

    this.guardando.set(true);
    this.errorFormulario.set(null);

    this.donacionService.programar(donacion).subscribe({
      next: (guardada) => {
        this.guardando.set(false);
        this.formularioAbierto.set(false);
        this.mensaje.set(`Tu donación para ${guardada.nombreOrganizacion} quedó programada.`);
        this.error.set(null);
        this.cargar();
      },
      error: (err) => {
        console.error('Error al programar la donación:', err);
        this.guardando.set(false);
        this.confirmando.set(false);
        this.errorFormulario.set(mensajeDeError(err, 'No se pudo programar la donación. Intenta de nuevo.'));
      },
    });
  }

  // Cancelación

  protected confirmarCancelacion(): void {
    const donacion = this.donacionACancelar();
    if (!donacion || this.cancelando()) return;

    this.cancelando.set(true);

    this.donacionService.cancelar(donacion.idDonacion).subscribe({
      next: () => {
        this.cancelando.set(false);
        this.donacionACancelar.set(null);
        this.mensaje.set('La donación quedó cancelada.');
        this.error.set(null);
        this.cargar();
      },
      error: (err) => {
        console.error('Error al cancelar la donación:', err);
        this.cancelando.set(false);
        this.donacionACancelar.set(null);
        this.error.set(mensajeDeError(err, 'No se pudo cancelar la donación.'));
      },
    });
  }

  private articuloVacio(): ArticuloFormulario {
    return { categoria: 'ALIMENTOS', descripcion: '', cantidad: null, unidad: '', fechaVencimiento: '' };
  }

  private errorDelFormulario(): string | null {
    if (this.idOrganizacion === null) {
      return 'Elige la organización a la que vas a donar.';
    }

    if (!this.fechaEntrega) {
      return 'Indica la fecha en que llevarás la donación.';
    }

    if (this.fechaEntrega < this.hoy) {
      return 'La fecha de entrega no puede ser anterior a hoy.';
    }

    if (this.articulos.length === 0) {
      return 'Agrega al menos una cosa para donar.';
    }

    for (const articulo of this.articulos) {
      const esDinero = articulo.categoria === 'DINERO';
      const nombre = articulo.descripcion.trim() || (esDinero ? 'el dinero' : 'cada artículo');

      if (!esDinero && !articulo.descripcion.trim()) {
        return 'Escribe qué vas a donar en cada artículo.';
      }

      if (articulo.cantidad === null || !(articulo.cantidad > 0)) {
        return `Indica una cantidad mayor que cero para ${nombre}.`;
      }

      if (!esDinero && !articulo.unidad.trim()) {
        return `Indica la unidad de ${nombre} (kg, unidades, litros...).`;
      }

      if (articulo.fechaVencimiento && articulo.fechaVencimiento < this.hoy) {
        return `${articulo.descripcion.trim()} ya está vencido: no se puede donar.`;
      }
    }

    return null;
  }

  private cargar(): void {
    this.donacionService.organizacionesDisponibles().subscribe({
      next: (organizaciones) => this.organizaciones.set(organizaciones),
      error: (err) => console.error('Error al cargar las organizaciones:', err),
    });

    this.donacionService.misDonaciones().subscribe({
      next: (donaciones) => {
        this.donaciones.set(donaciones);
        this.cargando.set(false);
      },
      error: (err) => {
        console.error('Error al cargar las donaciones:', err);
        this.error.set('No se pudieron cargar tus donaciones.');
        this.cargando.set(false);
      },
    });
  }
}
