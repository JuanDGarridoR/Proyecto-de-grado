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

/** Datos de salud de la persona mayor autenticada (salud-service). */
@Injectable({ providedIn: 'root' })
export class CondicionSaludService {

  private readonly apiUrl = 'http://localhost:8080/api/persona-mayor/condiciones-salud';

  constructor(private http: HttpClient) {}

  catalogo(): Observable<CondicionSalud[]> {
    return this.http.get<CondicionSalud[]>(`${this.apiUrl}/catalogo`);
  }

  listar(): Observable<CondicionSaludRegistrada[]> {
    return this.http.get<CondicionSaludRegistrada[]>(this.apiUrl);
  }

  crear(request: CondicionSaludRequest): Observable<CondicionSaludRegistrada> {
    return this.http.post<CondicionSaludRegistrada>(this.apiUrl, request);
  }

  actualizar(id: number, request: CondicionSaludRequest): Observable<CondicionSaludRegistrada> {
    return this.http.put<CondicionSaludRegistrada>(`${this.apiUrl}/${id}`, request);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
