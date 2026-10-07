import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URL } from '../api';

/**
 * Cita médica tal como la devuelve salud-service (y acompanante-service en
 * el seguimiento). fecha va en "yyyy-MM-dd" y hora en "HH:mm".
 */
export interface CitaMedica {
  idCita: number;
  titulo: string;
  lugar: string;
  consultorio: string | null;
  fecha: string;
  hora: string;
  observaciones: string | null;
}

/** Datos del formulario de cita médica. */
export interface CitaMedicaRequest {
  titulo: string;
  lugar: string;
  consultorio?: string;
  fecha: string;
  hora: string;
  observaciones?: string;
}

/**
 * Cuándo se envían los recordatorios. Debe coincidir con HORAS_AVISO_DIA y
 * MINUTOS_AVISO_HORA de CitaMedicaReminderScheduler en salud-service.
 */
export const TEXTO_AVISOS_CITA = 'un día antes y una hora antes';

/**
 * Consultorio para mostrar: "204" -> "Consultorio 204". Si ya empieza por
 * "consultorio" se deja como está. Igual que en los SMS de salud-service.
 */
export function formatearConsultorio(consultorio: string | null | undefined): string | null {
  const texto = consultorio?.trim();
  if (!texto) {
    return null;
  }
  return texto.toLowerCase().startsWith('consultorio') ? texto : `Consultorio ${texto}`;
}

/** Fecha YYYY-MM-DD en hora local; vive en core/fechas y se reexporta aquí por compatibilidad. */
export { fechaLocal } from '../fechas/fechas';

/** Lugar de la cita para mostrar: "Hospital San José · Consultorio 204". */
export function lugarDeCita(cita: CitaMedica): string | null {
  return [cita.lugar, formatearConsultorio(cita.consultorio)].filter(Boolean).join(' · ') || null;
}

/** Fecha y hora de la cita como Date local. */
export function momentoDeCita(cita: CitaMedica): Date {
  const [anio, mes, dia] = cita.fecha.split('-').map(Number);
  const [h, m] = (cita.hora || '00:00').split(':').map(Number);
  return new Date(anio, mes - 1, dia, h || 0, m || 0);
}

/**
 * Separa las citas en próximas (la más cercana primero) y pasadas (la más
 * reciente primero). Una cita pasa al historial cuando llega su hora.
 */
export function separarCitas(
  citas: CitaMedica[],
  ahora: Date
): { proximas: CitaMedica[]; pasadas: CitaMedica[] } {
  const ordenadas = [...citas].sort((a, b) => momentoDeCita(a).getTime() - momentoDeCita(b).getTime());

  return {
    proximas: ordenadas.filter((c) => momentoDeCita(c) > ahora),
    pasadas: ordenadas.filter((c) => momentoDeCita(c) <= ahora).reverse()
  };
}

/** "Hoy", "Mañana" o "jueves 2 de octubre" (con el año si no es el actual). */
export function formatearFechaCita(fecha: string, ahora: Date = new Date()): string {
  const [anio, mes, dia] = fecha.split('-').map(Number);
  const fechaCita = new Date(anio, mes - 1, dia);

  const hoy = new Date(ahora);
  hoy.setHours(0, 0, 0, 0);
  const diferenciaDias = Math.round((fechaCita.getTime() - hoy.getTime()) / 86_400_000);

  if (diferenciaDias === 0) {
    return 'Hoy';
  }
  if (diferenciaDias === 1) {
    return 'Mañana';
  }

  return fechaCita.toLocaleDateString('es-CO', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: anio !== ahora.getFullYear() ? 'numeric' : undefined
  });
}

/** Cuánto falta para la cita: "en 3 horas", "en 5 días"... o null si ya pasó. */
export function tiempoParaCita(cita: CitaMedica, ahora: Date = new Date()): string | null {
  const minutos = Math.round((momentoDeCita(cita).getTime() - ahora.getTime()) / 60_000);

  if (minutos <= 0) {
    return null;
  }
  if (minutos < 60) {
    return `en ${minutos} ${minutos === 1 ? 'minuto' : 'minutos'}`;
  }

  const horas = Math.round(minutos / 60);
  if (horas < 24) {
    return `en ${horas} ${horas === 1 ? 'hora' : 'horas'}`;
  }

  const hoy = new Date(ahora);
  hoy.setHours(0, 0, 0, 0);
  const [anio, mes, dia] = cita.fecha.split('-').map(Number);
  const dias = Math.round((new Date(anio, mes - 1, dia).getTime() - hoy.getTime()) / 86_400_000);
  return `en ${dias} ${dias === 1 ? 'día' : 'días'}`;
}

/**
 * Citas médicas (salud-service). Sin idPersonaMayor son las de la persona
 * mayor autenticada; con él, las de una persona mayor que gestiona el
 * acompañante autenticado.
 */
@Injectable({ providedIn: 'root' })
export class CitaMedicaService {

  private readonly apiUrl = `${API_URL}`;

  constructor(private http: HttpClient) {}

  private url(idPersonaMayor?: number | null): string {
    return idPersonaMayor
      ? `${this.apiUrl}/acompanante/personas-mayores/${idPersonaMayor}/citas-medicas`
      : `${this.apiUrl}/persona-mayor/citas-medicas`;
  }

  listar(idPersonaMayor?: number | null): Observable<CitaMedica[]> {
    return this.http.get<CitaMedica[]>(this.url(idPersonaMayor));
  }

  crear(request: CitaMedicaRequest, idPersonaMayor?: number | null): Observable<CitaMedica> {
    return this.http.post<CitaMedica>(this.url(idPersonaMayor), request);
  }

  actualizar(id: number, request: CitaMedicaRequest, idPersonaMayor?: number | null): Observable<CitaMedica> {
    return this.http.put<CitaMedica>(`${this.url(idPersonaMayor)}/${id}`, request);
  }

  eliminar(id: number, idPersonaMayor?: number | null): Observable<void> {
    return this.http.delete<void>(`${this.url(idPersonaMayor)}/${id}`);
  }
}
