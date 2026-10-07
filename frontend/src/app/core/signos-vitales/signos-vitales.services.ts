import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URL } from '../api';

/** Una medición nueva; cada valor es opcional. */
export interface SignoVitalRequest {
  presionSistolica: number | null;
  presionDiastolica: number | null;
  frecuenciaCardiaca: number | null;
  temperatura: number | null;
  saturacionOxigeno: number | null;
  frecuenciaRespiratoria: number | null;
  peso: number | null;
  estatura: number | null;
  observaciones: string;
}

/** Medición tal como la devuelve salud-service; fechaHora va en "yyyy-MM-ddTHH:mm". */
export interface SignoVitalResponse {
  idSignoVital: number;
  fechaHora: string;
  presionSistolica: number | null;
  presionDiastolica: number | null;
  frecuenciaCardiaca: number | null;
  temperatura: number | null;
  saturacionOxigeno: number | null;
  frecuenciaRespiratoria: number | null;
  peso: number | null;
  estatura: number | null;
  observaciones: string | null;
}

/** Persona mayor vinculada a la organización. */
export interface PersonaMayor {
  idUsuario: number;
  nombre: string;
  celular: string | null;
  correo: string | null;
}

/**
 * Signos vitales: la organización los registra y consulta, y la persona
 * mayor ve su historial y registra sus propias mediciones. Todo pasa por el
 * gateway (8080), que valida el token, agrega el X-User-Id y maneja el CORS.
 */
@Injectable({
  providedIn: 'root',
})
export class SignosVitalesService {
  private http = inject(HttpClient);

  private apiUrl = `${API_URL}/organizacion/signos-vitales`;
  private personasApiUrl = `${API_URL}/organizacion/personas-mayores`;
  private personaMayorApiUrl = `${API_URL}/persona-mayor/signos-vitales`;

  /** Historial completo de la persona mayor autenticada, del más reciente al más antiguo. */
  listarPropios(): Observable<SignoVitalResponse[]> {
    return this.http.get<SignoVitalResponse[]>(this.personaMayorApiUrl);
  }

  /** La persona mayor autenticada registra sus propios signos vitales. */
  registrarPropio(datos: SignoVitalRequest): Observable<SignoVitalResponse> {
    return this.http.post<SignoVitalResponse>(this.personaMayorApiUrl, datos);
  }

  /** La organización registra una medición de una persona mayor vinculada. */
  registrar(idPersonaMayor: number, datos: SignoVitalRequest): Observable<SignoVitalResponse> {
    return this.http.post<SignoVitalResponse>(`${this.apiUrl}/${idPersonaMayor}`, datos);
  }

  /** Últimos 10 registros de la persona mayor, del más reciente al más antiguo. */
  listarUltimos(idPersonaMayor: number): Observable<SignoVitalResponse[]> {
    return this.http.get<SignoVitalResponse[]>(`${this.apiUrl}/${idPersonaMayor}`);
  }

  /** Personas mayores vinculadas a la organización (organizacion-service). */
  listarPersonasMayores(): Observable<PersonaMayor[]> {
    return this.http.get<PersonaMayor[]>(this.personasApiUrl);
  }
}
