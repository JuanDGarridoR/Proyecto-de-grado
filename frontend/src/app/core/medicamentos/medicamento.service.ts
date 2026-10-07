import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Medicamento tal como lo devuelve salud-service. hora va en "HH:mm";
 * proximaToma y ultimaToma, en "yyyy-MM-ddTHH:mm".
 */
export interface Medicamento {
  idMedicamento: number;
  nombre: string;
  dosis: string;
  frecuencia: string;
  intervaloHoras: number;
  hora: string;
  fechaInicio: string;
  fechaFin: string | null;
  proximaToma: string;
  ultimaToma: string | null;
  activo: boolean;
}

/** Datos del formulario de medicamento. frecuencia es la descripción opcional. */
export interface MedicamentoRequest {
  nombre: string;
  dosis: string;
  frecuencia: string;
  intervaloHoras: number;
  hora: string;
  fechaInicio?: string;
  fechaFin?: string;
}

/**
 * Minutos antes de cada toma en que se envía el primer aviso. Debe
 * coincidir con MINUTOS_AVISO_PREVIO de salud-service.
 */
export const MINUTOS_AVISO_PREVIO = 15;

/** Hora para mostrar: "08:00" -> "8:00 a. m." */
export function formatearHora(hora: string | null | undefined): string {
  if (!hora) {
    return '';
  }
  const [h, m] = hora.split(':').map(Number);
  const fecha = new Date();
  fecha.setHours(h, m, 0, 0);
  return fecha.toLocaleTimeString('es-CO', { hour: 'numeric', minute: '2-digit' });
}

/** Próxima toma para mostrar: "Hoy, 4:00 p. m.", "Mañana, ..." o "2 oct, ...". */
export function formatearProximaToma(proximaToma: string | null | undefined): string {
  if (!proximaToma) {
    return '';
  }
  const fecha = new Date(proximaToma);
  const hora = fecha.toLocaleTimeString('es-CO', { hour: 'numeric', minute: '2-digit' });

  const hoy = new Date();
  hoy.setHours(0, 0, 0, 0);
  const dia = new Date(fecha);
  dia.setHours(0, 0, 0, 0);
  const diferenciaDias = Math.round((dia.getTime() - hoy.getTime()) / 86_400_000);

  if (diferenciaDias === 0) {
    return `Hoy, ${hora}`;
  }
  if (diferenciaDias === 1) {
    return `Mañana, ${hora}`;
  }
  return `${fecha.toLocaleDateString('es-CO', { day: 'numeric', month: 'short' })}, ${hora}`;
}

/**
 * Medicamentos (salud-service). Sin idPersonaMayor son los de la persona
 * mayor autenticada; con él, los de una persona mayor que gestiona el
 * acompañante autenticado.
 */
@Injectable({ providedIn: 'root' })
export class MedicamentoService {

  private readonly apiUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  private url(idPersonaMayor?: number | null): string {
    return idPersonaMayor
      ? `${this.apiUrl}/acompanante/personas-mayores/${idPersonaMayor}/medicamentos`
      : `${this.apiUrl}/persona-mayor/medicamentos`;
  }

  listar(idPersonaMayor?: number | null): Observable<Medicamento[]> {
    return this.http.get<Medicamento[]>(this.url(idPersonaMayor));
  }

  crear(request: MedicamentoRequest, idPersonaMayor?: number | null): Observable<Medicamento> {
    return this.http.post<Medicamento>(this.url(idPersonaMayor), request);
  }

  actualizar(id: number, request: MedicamentoRequest, idPersonaMayor?: number | null): Observable<Medicamento> {
    return this.http.put<Medicamento>(`${this.url(idPersonaMayor)}/${id}`, request);
  }

  eliminar(id: number, idPersonaMayor?: number | null): Observable<void> {
    return this.http.delete<void>(`${this.url(idPersonaMayor)}/${id}`);
  }
}