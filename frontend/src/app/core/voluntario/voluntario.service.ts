import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Gusto } from '../gustos/gusto.service';
import { API_URL } from '../api';

/** INACTIVA: el voluntario pausó el vínculo y la organización no lo ve. */
export type EstadoVinculo = 'PENDIENTE' | 'ACEPTADA' | 'RECHAZADA' | 'INACTIVA';

/** Organización vista por el voluntario, con el estado de su vínculo (null = sin solicitud). */
export interface OrganizacionVoluntario {
  idOrganizacion: number;
  nombre: string;
  direccion: string | null;
  celular: string | null;
  correo: string | null;
  estado: EstadoVinculo | null;
}

/**
 * Vínculos del voluntario con organizaciones y sus gustos. Todo pasa por el gateway
 * (8080), que valida el token y agrega el X-User-Id del voluntario.
 */
@Injectable({
  providedIn: 'root',
})
export class VoluntarioService {
  private http = inject(HttpClient);

  private apiUrl = `${API_URL}/voluntario/organizaciones`;
  private gustosUrl = `${API_URL}/voluntario/gustos`;

  /** Todas las organizaciones con el estado del vínculo del voluntario. */
  listarOrganizaciones(): Observable<OrganizacionVoluntario[]> {
    return this.http.get<OrganizacionVoluntario[]>(this.apiUrl);
  }

  solicitarVinculacion(idOrganizacion: number): Observable<string> {
    return this.http.post(`${this.apiUrl}/${idOrganizacion}/solicitud`, {}, { responseType: 'text' });
  }

  /** Cancela una solicitud pendiente, descarta una rechazada o desvincula. */
  eliminarVinculo(idOrganizacion: number): Observable<string> {
    return this.http.delete(`${this.apiUrl}/${idOrganizacion}`, { responseType: 'text' });
  }

  /** Pausa el vínculo: la organización deja de verlo y él deja de ver lo de la organización. */
  inactivarVinculo(idOrganizacion: number): Observable<string> {
    return this.http.put(`${this.apiUrl}/${idOrganizacion}/inactivar`, {}, { responseType: 'text' });
  }

  reactivarVinculo(idOrganizacion: number): Observable<string> {
    return this.http.put(`${this.apiUrl}/${idOrganizacion}/reactivar`, {}, { responseType: 'text' });
  }

  /** Gustos que marcó el voluntario (del mismo catálogo que las personas mayores). */
  listarGustos(): Observable<Gusto[]> {
    return this.http.get<Gusto[]>(this.gustosUrl);
  }

  /** Reemplaza los gustos del voluntario por los de la lista. */
  guardarGustos(idsGustos: number[]): Observable<Gusto[]> {
    return this.http.put<Gusto[]>(this.gustosUrl, { idsGustos });
  }
}
