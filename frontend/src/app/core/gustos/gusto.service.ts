import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URL } from '../api';

/** Categorías con las que se agrupan los gustos en pantalla. */
/** PROFESION: a qué se dedica o se dedicó la persona (profesión u oficio). */
export type CategoriaGusto = 'GUSTO' | 'TALENTO' | 'HOBBY' | 'PROFESION';

/** Gusto del catálogo. */
export interface Gusto {
  idGusto: number;
  nombre: string;
  categoria: CategoriaGusto;
}

/** Datos para agregar un gusto al catálogo. */
export interface GustoRequest {
  nombre: string;
  categoria: CategoriaGusto;
}

/** Catálogo de gustos y gustos marcados por cada persona mayor (persona-mayor-service). */
@Injectable({ providedIn: 'root' })
export class GustoService {

  private readonly apiUrl = `${API_URL}/gustos`;

  constructor(private http: HttpClient) {}

  listar(): Observable<Gusto[]> {
    return this.http.get<Gusto[]>(this.apiUrl);
  }

  /** Agrega al catálogo un gusto que no estaba (el catálogo no se edita ni se borra). */
  crear(request: GustoRequest): Observable<Gusto> {
    return this.http.post<Gusto>(this.apiUrl, request);
  }

  /** Gustos que tiene marcados una persona mayor. */
  listarAsignados(idPersonaMayor: number): Observable<Gusto[]> {
    return this.http.get<Gusto[]>(`${API_URL}/persona-mayor/${idPersonaMayor}/gustos`);
  }

  /** Reemplaza los gustos marcados de la persona mayor por los de la lista. */
  asignar(idPersonaMayor: number, idsGustos: number[]): Observable<Gusto[]> {
    return this.http.put<Gusto[]>(`${API_URL}/persona-mayor/${idPersonaMayor}/gustos`, {
      idsGustos
    });
  }
}