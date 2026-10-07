import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type TipoCondicionSalud = 'ENFERMEDAD' | 'ALERGIA' | 'DISCAPACIDAD';

export type SeveridadCondicion = 'LEVE' | 'MODERADA' | 'SEVERA';

/** Opción del catálogo de enfermedades, alergias y discapacidades. */
export interface CondicionSalud {
  idCondicionSalud: number;
  tipo: TipoCondicionSalud;
  nombre: string;
  categoria: string;
  /** Opción "Otra": la persona escribe en el detalle cuál es. */
  esOtra: boolean;
}

/** Enfermedad, alergia o discapacidad registrada por la persona mayor. */
export interface CondicionSaludRegistrada extends CondicionSalud {
  idPersonaMayorCondicionSalud: number;
  severidad: SeveridadCondicion | null;
  detalle: string | null;
  fechaRegistro: string;
}

/** Al editar, idCondicionSalud no se tiene en cuenta. */
export interface CondicionSaludRequest {
  idCondicionSalud?: number;
  severidad: SeveridadCondicion | null;
  detalle: string | null;
}

/**
 * Datos de salud (salud-service). Sin idPersonaMayor son los de la persona
 * mayor autenticada; con él, los de una persona mayor que gestiona el
 * acompañante autenticado.
 */
@Injectable({ providedIn: 'root' })
export class CondicionSaludService {

  private readonly apiUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  private url(idPersonaMayor?: number | null): string {
    return idPersonaMayor
      ? `${this.apiUrl}/acompanante/personas-mayores/${idPersonaMayor}/condiciones-salud`
      : `${this.apiUrl}/persona-mayor/condiciones-salud`;
  }

  /** El catálogo es el mismo para todos. */
  catalogo(): Observable<CondicionSalud[]> {
    return this.http.get<CondicionSalud[]>(`${this.apiUrl}/persona-mayor/condiciones-salud/catalogo`);
  }

  listar(idPersonaMayor?: number | null): Observable<CondicionSaludRegistrada[]> {
    return this.http.get<CondicionSaludRegistrada[]>(this.url(idPersonaMayor));
  }

  crear(request: CondicionSaludRequest, idPersonaMayor?: number | null): Observable<CondicionSaludRegistrada> {
    return this.http.post<CondicionSaludRegistrada>(this.url(idPersonaMayor), request);
  }

  actualizar(id: number, request: CondicionSaludRequest, idPersonaMayor?: number | null): Observable<CondicionSaludRegistrada> {
    return this.http.put<CondicionSaludRegistrada>(`${this.url(idPersonaMayor)}/${id}`, request);
  }

  eliminar(id: number, idPersonaMayor?: number | null): Observable<void> {
    return this.http.delete<void>(`${this.url(idPersonaMayor)}/${id}`);
  }
}
