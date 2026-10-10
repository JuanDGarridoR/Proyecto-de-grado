import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { fechaLocal } from '../fechas/fechas';
import { API_URL } from '../api';

/** Actividad tal como la devuelve actividad-service. */
export interface Actividad {
  idActividad: number;
  idOrganizacion: number;
  nombre: string;
  descripcion: string | null;
  fecha: string | null;
  hora: string | null;
  lugar: string | null;
  tipo: string | null;
  cupos: number | null;
  responsable: string | null;
  /** Cada cuántos días se repite; null si no se repite. */
  frecuenciaDias: number | null;
}

/** Actividad vista por la persona mayor; inscrito indica si ya se inscribió. */
export interface ActividadDisponible {
  idActividad: number;
  nombre: string;
  descripcion: string | null;
  fecha: string | null;
  hora: string | null;
  lugar: string | null;
  tipo: string | null;
  cupos: number | null;
  inscrito: boolean;
  frecuenciaDias: number | null;
}

/**
 * Datos para crear o editar una actividad. Si frecuenciaDias tiene valor,
 * cuando pasa el día de la actividad el backend crea la siguiente con los
 * mismos datos, frecuenciaDias días después.
 */
export interface ActividadRequest {
  nombre: string;
  descripcion: string | null;
  fecha: string | null;
  hora: string | null;
  lugar: string | null;
  tipo: string | null;
  cupos: number | null;
  responsable: string | null;
  frecuenciaDias: number | null;
}

/** Lo más espaciada que puede repetirse una actividad (igual que en el backend). */
export const MAX_FRECUENCIA_DIAS = 365;

/** Mensaje de error si los días de repetición no son válidos, o null si están bien. */
export function errorFrecuencia(dias: number | null): string | null {
  if (dias === null || !Number.isInteger(dias) || dias < 1 || dias > MAX_FRECUENCIA_DIAS) {
    return `Indica cada cuántos días se repite (entre 1 y ${MAX_FRECUENCIA_DIAS})`;
  }

  return null;
}

/** "Todos los días", "Cada semana", "Cada 3 días"... */
export function textoFrecuencia(dias: number): string {
  if (dias === 1) {
    return 'Todos los días';
  }

  if (dias % 7 === 0) {
    const semanas = dias / 7;
    return semanas === 1 ? 'Cada semana' : `Cada ${semanas} semanas`;
  }

  return `Cada ${dias} días`;
}

/** Estado de una actividad propuesta por un voluntario o una persona mayor. */
export type EstadoPropuesta = 'PENDIENTE' | 'ACEPTADA' | 'RECHAZADA';

/**
 * Actividad que un voluntario o una persona mayor propuso a una
 * organización. Quien la propuso ve a qué organización la presentó y la
 * organización ve quién la propuso (voluntario o persona mayor; el otro
 * queda en null).
 */
export interface PropuestaActividad {
  idActividad: number;
  idOrganizacion: number;
  nombreOrganizacion: string | null;
  idVoluntario: number | null;
  nombreVoluntario: string | null;
  idPersonaMayor: number | null;
  nombrePersonaMayor: string | null;
  estado: EstadoPropuesta;
  nombre: string;
  descripcion: string | null;
  fecha: string | null;
  hora: string | null;
  lugar: string | null;
  tipo: string | null;
  cupos: number | null;
  responsable: string | null;
  frecuenciaDias: number | null;
}

/** Persona inscrita en una actividad. asistio es null mientras no se registre la asistencia. */
export interface ParticipanteActividad {
  idPersonaMayor: number;
  nombre: string;
  celular: string | null;
  asistio: boolean | null;
}

/** Fecha de hoy YYYY-MM-DD en hora local (toISOString() usaría UTC). */
export function fechaHoy(): string {
  return fechaLocal(new Date());
}

/**
 * Separa las actividades en próximas (desde hoy) e historial, con el mismo
 * criterio en los paneles de todos los roles:
 *  - Próximas: de la más cercana a la más lejana. Las que no tienen fecha
 *    van al final.
 *  - Historial: de la más reciente a la más antigua.
 * Dentro del mismo día se ordena por hora.
 */
export function separarPorFecha<T extends { fecha: string | null; hora: string | null }>(
  actividades: T[]
): { proximas: T[]; pasadas: T[] } {
  const hoy = fechaHoy();
  // "9:00" -> "09:00" para que la comparación de texto respete la hora
  const clave = (a: T) =>
    `${a.fecha ?? '9999-12-31'}T${a.hora ? a.hora.padStart(5, '0') : '99:99'}`;

  const proximas = actividades
    .filter((a) => a.fecha === null || a.fecha >= hoy)
    .sort((a, b) => clave(a).localeCompare(clave(b)));

  const pasadas = actividades
    .filter((a) => a.fecha !== null && a.fecha < hoy)
    .sort((a, b) => clave(b).localeCompare(clave(a)));

  return { proximas, pasadas };
}

/**
 * Llamadas a actividad-service. El backend decide qué actividades ve cada
 * usuario según su rol.
 */
@Injectable({
  providedIn: 'root'
})
export class ActividadService {

  private readonly apiUrl = `${API_URL}/actividades`;

  constructor(private http: HttpClient) {}

  /** Actividades que puede ver el usuario según su rol. */
  listar(): Observable<Actividad[]> {
    return this.http.get<Actividad[]>(this.apiUrl);
  }

  /** Actividades de la organización del usuario. */
  listarMias(): Observable<Actividad[]> {
    return this.http.get<Actividad[]>(
      `${this.apiUrl}/mias`
    );
  }

  /** Actividades que puede ver la persona mayor, marcando en cuáles está inscrita. */
  listarDisponibles(): Observable<ActividadDisponible[]> {
    return this.http.get<ActividadDisponible[]>(
      `${this.apiUrl}/disponibles`
    );
  }

  inscribirse(id: number): Observable<void> {
    return this.http.post<void>(
      `${this.apiUrl}/${id}/inscribirse`,
      {}
    );
  }

  cancelarInscripcion(id: number): Observable<void> {
    return this.http.delete<void>(
      `${this.apiUrl}/${id}/inscribirse`
    );
  }

  crear(request: ActividadRequest): Observable<Actividad> {
    return this.http.post<Actividad>(
      this.apiUrl,
      request
    );
  }

  actualizar(
    id: number,
    request: ActividadRequest
  ): Observable<Actividad> {
    return this.http.put<Actividad>(
      `${this.apiUrl}/${id}`,
      request
    );
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(
      `${this.apiUrl}/${id}`
    );
  }

  /** Inscritos en una actividad (solo para la organización dueña). */
  listarParticipantes(
    id: number
  ): Observable<ParticipanteActividad[]> {
    return this.http.get<ParticipanteActividad[]>(
      `${this.apiUrl}/${id}/participantes`
    );
  }

  // =========================================================
  // PROPUESTAS DE VOLUNTARIOS Y PERSONAS MAYORES
  // =========================================================

  /** El voluntario o la persona mayor presenta una actividad a una de sus organizaciones; queda PENDIENTE. */
  proponer(request: ActividadRequest & { idOrganizacion: number | null }): Observable<PropuestaActividad> {
    return this.http.post<PropuestaActividad>(`${this.apiUrl}/propuestas`, request);
  }

  /** Propuestas del voluntario o la persona mayor en todos sus estados. */
  listarPropuestasMias(): Observable<PropuestaActividad[]> {
    return this.http.get<PropuestaActividad[]>(`${this.apiUrl}/propuestas/mias`);
  }

  /** Propuestas pendientes que recibió la organización. */
  listarPropuestasPendientes(): Observable<PropuestaActividad[]> {
    return this.http.get<PropuestaActividad[]>(`${this.apiUrl}/propuestas`);
  }

  aceptarPropuesta(id: number): Observable<PropuestaActividad> {
    return this.http.put<PropuestaActividad>(`${this.apiUrl}/propuestas/${id}/aceptar`, {});
  }

  rechazarPropuesta(id: number): Observable<PropuestaActividad> {
    return this.http.put<PropuestaActividad>(`${this.apiUrl}/propuestas/${id}/rechazar`, {});
  }

  /** Marca si una persona inscrita asistió (solo para la organización dueña). */
  registrarAsistencia(
    idActividad: number,
    idPersonaMayor: number,
    asistio: boolean
  ): Observable<void> {
    return this.http.put<void>(
      `${this.apiUrl}/${idActividad}/participantes/${idPersonaMayor}/asistencia`,
      { asistio }
    );
  }
}