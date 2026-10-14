import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { API_URL } from '../api';
import { Categoria, EstadoDonacion, TipoMovimiento } from './catalogo';

/** Organización a la que el acompañante o voluntario puede donar. */
export interface OrganizacionDonable {
  idOrganizacion: number;
  nombre: string;
  direccion: string | null;
  celular: string | null;
  fotoPerfil: string | null;
}

export interface ArticuloDonacion {
  idArticulo: number;
  categoria: Categoria;
  descripcion: string;
  cantidad: number;
  unidad: string;
  fechaVencimiento: string | null;
  /** Lo que de verdad llegó; null hasta que la organización la recibe. */
  cantidadRecibida: number | null;
}

export interface Donacion {
  idDonacion: number;
  estado: EstadoDonacion;
  fechaEntrega: string;
  horaEntrega: string | null;
  observaciones: string | null;
  notaOrganizacion: string | null;
  fechaRegistro: string;
  fechaRespuesta: string | null;
  idOrganizacion: number;
  nombreOrganizacion: string;
  direccionOrganizacion: string | null;
  fotoOrganizacion: string | null;
  idDonante: number;
  nombreDonante: string | null;
  celularDonante: string | null;
  fotoDonante: string | null;
  rolDonante: 'ACOMPANANTE' | 'VOLUNTARIO';
  articulos: ArticuloDonacion[];
}

export interface NuevoArticulo {
  categoria: Categoria;
  descripcion: string;
  cantidad: number;
  unidad: string;
  fechaVencimiento: string | null;
}

export interface NuevaDonacion {
  idOrganizacion: number;
  fechaEntrega: string;
  horaEntrega: string | null;
  observaciones: string | null;
  articulos: NuevoArticulo[];
}

export interface ArticuloInventario {
  idInventario: number;
  nombre: string;
  categoria: Categoria;
  unidad: string;
  cantidad: number;
  cantidadMinima: number | null;
  fechaVencimiento: string | null;
  fechaActualizacion: string;
}

export interface DatosArticuloInventario {
  nombre: string;
  categoria: Categoria;
  unidad: string;
  cantidadMinima: number | null;
  fechaVencimiento: string | null;
  /** Solo al crear: existencia inicial y su motivo. */
  cantidad?: number;
  motivoEntrada?: string;
}

export interface MovimientoInventario {
  idMovimiento: number;
  tipo: TipoMovimiento;
  cantidad: number;
  motivo: string;
  detalle: string | null;
  idDonacion: number | null;
  registradoPor: string | null;
  fecha: string;
}

export interface NuevoMovimiento {
  tipo: TipoMovimiento;
  cantidad: number;
  motivo: string;
  detalle: string | null;
  fechaVencimiento: string | null;
}

/**
 * Donaciones e inventario (donacion-service). Todo pasa por el gateway, que
 * valida el token y agrega el X-User-Id.
 */
@Injectable({ providedIn: 'root' })
export class DonacionService {
  private readonly http = inject(HttpClient);

  // Donante (acompañante o voluntario)

  organizacionesDisponibles(): Observable<OrganizacionDonable[]> {
    return this.http.get<OrganizacionDonable[]>(`${API_URL}/donaciones/organizaciones`);
  }

  misDonaciones(): Observable<Donacion[]> {
    return this.http.get<Donacion[]>(`${API_URL}/donaciones`);
  }

  programar(donacion: NuevaDonacion): Observable<Donacion> {
    return this.http.post<Donacion>(`${API_URL}/donaciones`, donacion);
  }

  cancelar(idDonacion: number): Observable<Donacion> {
    return this.http.put<Donacion>(`${API_URL}/donaciones/${idDonacion}/cancelar`, {});
  }

  // Organización: donaciones

  donacionesRecibidas(): Observable<Donacion[]> {
    return this.http.get<Donacion[]>(`${API_URL}/organizacion/donaciones`);
  }

  /** cantidades: lo que llegó de cada artículo (idArticulo -> cantidad). */
  marcarRecibida(idDonacion: number, cantidades: Record<number, number>): Observable<Donacion> {
    return this.http.put<Donacion>(`${API_URL}/organizacion/donaciones/${idDonacion}/recibir`, {
      articulos: Object.entries(cantidades).map(([idArticulo, cantidad]) => ({
        idArticulo: Number(idArticulo),
        cantidad,
      })),
    });
  }

  marcarNoEntregada(idDonacion: number, nota: string | null): Observable<Donacion> {
    return this.http.put<Donacion>(
      `${API_URL}/organizacion/donaciones/${idDonacion}/no-entregada`,
      { nota },
    );
  }

  // Organización: inventario

  inventario(): Observable<ArticuloInventario[]> {
    return this.http.get<ArticuloInventario[]>(`${API_URL}/organizacion/inventario`);
  }

  crearArticulo(datos: DatosArticuloInventario): Observable<ArticuloInventario> {
    return this.http.post<ArticuloInventario>(`${API_URL}/organizacion/inventario`, datos);
  }

  editarArticulo(idInventario: number, datos: DatosArticuloInventario): Observable<ArticuloInventario> {
    return this.http.put<ArticuloInventario>(`${API_URL}/organizacion/inventario/${idInventario}`, datos);
  }

  eliminarArticulo(idInventario: number): Observable<string> {
    return this.http.delete(`${API_URL}/organizacion/inventario/${idInventario}`, {
      responseType: 'text',
    });
  }

  movimientos(idInventario: number): Observable<MovimientoInventario[]> {
    return this.http.get<MovimientoInventario[]>(
      `${API_URL}/organizacion/inventario/${idInventario}/movimientos`,
    );
  }

  registrarMovimiento(idInventario: number, movimiento: NuevoMovimiento): Observable<ArticuloInventario> {
    return this.http.post<ArticuloInventario>(
      `${API_URL}/organizacion/inventario/${idInventario}/movimientos`,
      movimiento,
    );
  }
}
