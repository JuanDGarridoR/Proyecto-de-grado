import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import {
  ArticuloInventario,
  DatosArticuloInventario,
  DonacionService,
  MovimientoInventario,
} from '../../../../core/donaciones/donacion.service';
import {
  CATEGORIAS,
  Categoria,
  MOTIVOS_ENTRADA,
  MOTIVOS_SALIDA,
  TipoMovimiento,
  UNIDADES_SUGERIDAS,
  formatearCantidad,
  formatearFechaCorta,
  iconoCategoria,
  textoCategoria,
  textoMotivo,
  tieneVencimiento,
} from '../../../../core/donaciones/catalogo';
import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';
import { fechaLocal } from '../../../../core/fechas/fechas';
import { mensajeDeError } from '../../../../core/formato/formato';
import { Icono } from '../../../../shared/icono/icono';
import { Cargando } from '../../../../shared/cargando/cargando';
import { BuscadorNombre } from '../../../../shared/buscador-nombre/buscador-nombre';

/** Días antes del vencimiento en que un artículo se marca "vence pronto". */
const DIAS_AVISO_VENCIMIENTO = 30;

type Alerta = 'agotado' | 'por-agotarse' | 'vencido' | 'vence-pronto';

interface FormularioArticulo {
  nombre: string;
  categoria: Categoria;
  unidad: string;
  cantidad: number | null;
  cantidadMinima: number | null;
  fechaVencimiento: string;
  motivoEntrada: string;
}

interface FormularioMovimiento {
  tipo: TipoMovimiento;
  cantidad: number | null;
  motivo: string;
  detalle: string;
  fechaVencimiento: string;
}

/**
 * Inventario de la organización: lo que tiene guardado (alimentos, dinero,
 * ropa, medicamentos, aseo...), con avisos de lo que se está agotando o
 * vence pronto. Las donaciones recibidas entran solas; lo demás se
 * registra como entradas y salidas, y cada artículo guarda su historial.
 */
@Component({
  selector: 'app-organizacion-inventario',
  imports: [FormsModule, RouterLink, Icono, Cargando, BuscadorNombre],
  templateUrl: './inventario.html',
  styleUrls: [
    '../../../../shared/tarjeta-persona/vinculos-pagina.css',
    '../../../../shared/tarjeta-actividad/actividades-modales.css',
    '../../../../shared/tarjeta-donacion/donaciones-pagina.css',
    './inventario.css',
  ],
})
export class OrganizacionInventario implements OnInit {
  private readonly donacionService = inject(DonacionService);

  protected readonly articulos = signal<ArticuloInventario[]>([]);
  protected readonly cargando = signal(true);
  protected readonly mensaje = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  // Filtros
  protected readonly busqueda = signal('');
  protected readonly categoria = signal<Categoria | null>(null);
  protected readonly soloAlertas = signal(false);

  protected readonly hoy = fechaLocal(new Date());
  private readonly limiteVencimiento = fechaLocal(
    new Date(Date.now() + DIAS_AVISO_VENCIMIENTO * 86_400_000),
  );

  protected readonly filtrados = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    const categoria = this.categoria();

    return this.articulos().filter(
      (a) =>
        (!texto || a.nombre.toLowerCase().includes(texto)) &&
        (!categoria || a.categoria === categoria) &&
        (!this.soloAlertas() || this.alertas(a).length > 0),
    );
  });

  protected readonly resumen = computed(() => {
    const articulos = this.articulos();
    return {
      total: articulos.length,
      porAgotarse: articulos.filter((a) => this.alertas(a).some((x) => x === 'agotado' || x === 'por-agotarse')).length,
      porVencer: articulos.filter((a) => this.alertas(a).some((x) => x === 'vencido' || x === 'vence-pronto')).length,
      dinero: articulos.filter((a) => a.categoria === 'DINERO').reduce((suma, a) => suma + a.cantidad, 0),
    };
  });

  // Crear / editar artículo
  protected readonly articuloEditado = signal<ArticuloInventario | null>(null);
  protected readonly formularioArticuloAbierto = signal(false);
  protected formArticulo: FormularioArticulo = this.articuloVacio();

  // Entrada / salida
  protected readonly articuloMovimiento = signal<ArticuloInventario | null>(null);
  protected formMovimiento: FormularioMovimiento = this.movimientoVacio('ENTRADA');

  // Historial
  protected readonly articuloHistorial = signal<ArticuloInventario | null>(null);
  protected readonly historial = signal<MovimientoInventario[] | null>(null);

  // Eliminar
  protected readonly articuloAEliminar = signal<ArticuloInventario | null>(null);

  protected readonly procesando = signal(false);
  protected readonly errorModal = signal<string | null>(null);

  protected readonly CATEGORIAS = CATEGORIAS;
  protected readonly MOTIVOS_ENTRADA = MOTIVOS_ENTRADA;
  protected readonly MOTIVOS_SALIDA = MOTIVOS_SALIDA;
  protected readonly UNIDADES_SUGERIDAS = UNIDADES_SUGERIDAS;
  protected readonly formatearCantidad = formatearCantidad;
  protected readonly formatearFechaCorta = formatearFechaCorta;
  protected readonly iconoCategoria = iconoCategoria;
  protected readonly textoCategoria = textoCategoria;
  protected readonly textoMotivo = textoMotivo;
  protected readonly tieneVencimiento = tieneVencimiento;

  constructor() {
    // Una donación recibida suma al inventario; otra sesión puede editarlo.
    alCambiar(['inventario', 'donaciones', 'usuarios'], () => this.cargar());
  }

  ngOnInit(): void {
    this.cargar();
  }

  /** Avisos del artículo: agotado o por agotarse, vencido o por vencer. */
  protected alertas(articulo: ArticuloInventario): Alerta[] {
    const alertas: Alerta[] = [];

    if (articulo.cantidad <= 0) {
      alertas.push('agotado');
    } else if (articulo.cantidadMinima !== null && articulo.cantidad <= articulo.cantidadMinima) {
      alertas.push('por-agotarse');
    }

    if (articulo.fechaVencimiento && articulo.cantidad > 0) {
      if (articulo.fechaVencimiento < this.hoy) {
        alertas.push('vencido');
      } else if (articulo.fechaVencimiento <= this.limiteVencimiento) {
        alertas.push('vence-pronto');
      }
    }

    return alertas;
  }

  protected textoAlerta(alerta: Alerta): string {
    return {
      agotado: 'Agotado',
      'por-agotarse': 'Por agotarse',
      vencido: 'Vencido',
      'vence-pronto': 'Vence pronto',
    }[alerta];
  }

  // Crear / editar

  protected abrirNuevo(): void {
    this.formArticulo = this.articuloVacio();
    this.articuloEditado.set(null);
    this.errorModal.set(null);
    this.formularioArticuloAbierto.set(true);
  }

  protected abrirEditar(articulo: ArticuloInventario): void {
    this.formArticulo = {
      nombre: articulo.nombre,
      categoria: articulo.categoria,
      unidad: articulo.unidad,
      cantidad: null,
      cantidadMinima: articulo.cantidadMinima,
      fechaVencimiento: articulo.fechaVencimiento ?? '',
      motivoEntrada: 'COMPRA',
    };
    this.articuloEditado.set(articulo);
    this.errorModal.set(null);
    this.formularioArticuloAbierto.set(true);
  }

  protected cambioCategoria(): void {
    if (this.formArticulo.categoria === 'DINERO') {
      this.formArticulo.unidad = 'COP';
    } else if (this.formArticulo.unidad === 'COP') {
      this.formArticulo.unidad = '';
    }

    if (!tieneVencimiento(this.formArticulo.categoria)) {
      this.formArticulo.fechaVencimiento = '';
    }
  }

  protected guardarArticulo(): void {
    if (this.procesando()) return;

    const f = this.formArticulo;
    const esDinero = f.categoria === 'DINERO';

    if (!f.nombre.trim()) {
      this.errorModal.set('Escribe el nombre del artículo.');
      return;
    }

    if (!esDinero && !f.unidad.trim()) {
      this.errorModal.set('Indica la unidad (kg, unidades, litros...).');
      return;
    }

    if ((f.cantidad !== null && f.cantidad < 0) || (f.cantidadMinima !== null && f.cantidadMinima < 0)) {
      this.errorModal.set('Las cantidades no pueden ser negativas.');
      return;
    }

    const datos: DatosArticuloInventario = {
      nombre: f.nombre.trim(),
      categoria: f.categoria,
      unidad: esDinero ? 'COP' : f.unidad.trim(),
      cantidadMinima: f.cantidadMinima,
      fechaVencimiento: tieneVencimiento(f.categoria) && f.fechaVencimiento ? f.fechaVencimiento : null,
    };

    const editado = this.articuloEditado();
    const peticion = editado
      ? this.donacionService.editarArticulo(editado.idInventario, datos)
      : this.donacionService.crearArticulo({ ...datos, cantidad: f.cantidad ?? 0, motivoEntrada: f.motivoEntrada });

    this.ejecutar(
      peticion,
      editado ? `${datos.nombre} quedó actualizado.` : `${datos.nombre} quedó en el inventario.`,
      'No se pudo guardar el artículo.',
    );
  }

  // Entrada / salida

  protected abrirMovimiento(articulo: ArticuloInventario, tipo: TipoMovimiento): void {
    this.formMovimiento = this.movimientoVacio(tipo);
    this.articuloMovimiento.set(articulo);
    this.errorModal.set(null);
  }

  protected cambioTipoMovimiento(): void {
    this.formMovimiento.motivo = this.formMovimiento.tipo === 'ENTRADA' ? 'COMPRA' : 'ENTREGA';
  }

  protected guardarMovimiento(): void {
    const articulo = this.articuloMovimiento();
    if (!articulo || this.procesando()) return;

    const f = this.formMovimiento;
    if (f.cantidad === null || !(f.cantidad > 0)) {
      this.errorModal.set('Indica una cantidad mayor que cero.');
      return;
    }

    if (f.tipo === 'SALIDA' && f.cantidad > articulo.cantidad) {
      this.errorModal.set(
        `Solo hay ${formatearCantidad(articulo.cantidad, articulo.unidad)} de ${articulo.nombre}.`,
      );
      return;
    }

    this.ejecutar(
      this.donacionService.registrarMovimiento(articulo.idInventario, {
        tipo: f.tipo,
        cantidad: f.cantidad,
        motivo: f.motivo,
        detalle: f.detalle.trim() || null,
        fechaVencimiento:
          f.tipo === 'ENTRADA' && tieneVencimiento(articulo.categoria) && f.fechaVencimiento
            ? f.fechaVencimiento
            : null,
      }),
      f.tipo === 'ENTRADA'
        ? `Entrada registrada: ${formatearCantidad(f.cantidad, articulo.unidad)} de ${articulo.nombre}.`
        : `Salida registrada: ${formatearCantidad(f.cantidad, articulo.unidad)} de ${articulo.nombre}.`,
      'No se pudo registrar el movimiento.',
    );
  }

  // Historial

  protected abrirHistorial(articulo: ArticuloInventario): void {
    this.historial.set(null);
    this.articuloHistorial.set(articulo);

    this.donacionService.movimientos(articulo.idInventario).subscribe({
      next: (movimientos) => this.historial.set(movimientos),
      error: (err) => {
        console.error('Error al cargar el historial:', err);
        this.historial.set([]);
      },
    });
  }

  protected formatearFechaHora(fecha: string): string {
    return new Date(fecha).toLocaleString('es-CO', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    });
  }

  // Eliminar

  protected confirmarEliminar(): void {
    const articulo = this.articuloAEliminar();
    if (!articulo || this.procesando()) return;

    this.ejecutar(
      this.donacionService.eliminarArticulo(articulo.idInventario),
      `${articulo.nombre} se eliminó del inventario.`,
      'No se pudo eliminar el artículo.',
    );
  }

  protected cerrarModales(): void {
    if (this.procesando()) return;
    this.formularioArticuloAbierto.set(false);
    this.articuloMovimiento.set(null);
    this.articuloHistorial.set(null);
    this.articuloAEliminar.set(null);
  }

  private ejecutar(peticion: Observable<unknown>, exito: string, porDefecto: string): void {
    this.procesando.set(true);
    this.errorModal.set(null);

    peticion.subscribe({
      next: () => {
        this.procesando.set(false);
        this.cerrarModales();
        this.mensaje.set(exito);
        this.error.set(null);
        this.cargar();
      },
      error: (err) => {
        console.error('Error en el inventario:', err);
        this.procesando.set(false);
        this.errorModal.set(mensajeDeError(err, porDefecto));
      },
    });
  }

  private articuloVacio(): FormularioArticulo {
    return {
      nombre: '',
      categoria: 'ALIMENTOS',
      unidad: '',
      cantidad: null,
      cantidadMinima: null,
      fechaVencimiento: '',
      motivoEntrada: 'COMPRA',
    };
  }

  private movimientoVacio(tipo: TipoMovimiento): FormularioMovimiento {
    return {
      tipo,
      cantidad: null,
      motivo: tipo === 'ENTRADA' ? 'COMPRA' : 'ENTREGA',
      detalle: '',
      fechaVencimiento: '',
    };
  }

  private cargar(): void {
    this.donacionService.inventario().subscribe({
      next: (articulos) => {
        this.articulos.set(articulos);
        this.cargando.set(false);
      },
      error: (err) => {
        console.error('Error al cargar el inventario:', err);
        this.error.set('No se pudo cargar el inventario.');
        this.cargando.set(false);
      },
    });
  }
}
