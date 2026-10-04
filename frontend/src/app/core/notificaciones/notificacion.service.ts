import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** SMS enviado al celular del usuario (emergencias, recordatorios...). */
export interface Notificacion {
  id: number;
  mensaje: string;
  fechaEnvio: string;
  leida: boolean;
}

/** Respuesta de /api/notificaciones. */
export interface NotificacionesResponse {
  noLeidas: number;
  notificaciones: Notificacion[];
}

/** Tipos de notificación (TipoNotificacion en messaging-service). */
export type TipoNotificacion =
  | 'EMERGENCIA'
  | 'CUMPLEANOS'
  | 'CUMPLEANOS_PERSONA_MAYOR'
  | 'ACTIVIDAD'
  | 'CITA_MEDICA'
  | 'MEDICAMENTO';

/** Si cada tipo está activo para el usuario. */
export type PreferenciasNotificacion = Record<TipoNotificacion, boolean>;

/** Notificaciones de la campanita del panel y preferencias (messaging-service). */
@Injectable({ providedIn: 'root' })
export class NotificacionService {

  private readonly apiUrl = 'http://localhost:8080/api/notificaciones';

  constructor(private http: HttpClient) {}

  /** Las 20 más recientes y cuántas faltan por leer. */
  listar(): Observable<NotificacionesResponse> {
    return this.http.get<NotificacionesResponse>(this.apiUrl);
  }

  /** Marca todas como leídas; se llama al abrir la campanita. */
  marcarLeidas(): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/leidas`, {});
  }

  /** Qué tipos de notificación tiene activos el usuario. */
  preferencias(): Observable<PreferenciasNotificacion> {
    return this.http.get<PreferenciasNotificacion>(`${this.apiUrl}/preferencias`);
  }

  /** Activa o desactiva un tipo de notificación. */
  cambiarPreferencia(tipo: TipoNotificacion, activa: boolean): Observable<unknown> {
    return this.http.put(`${this.apiUrl}/preferencias/${tipo}`, { activa });
  }
}
