import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, switchMap } from 'rxjs';
import { API_URL } from '../api';

/** Datos de la organización para "Mi información". */
export interface OrganizacionResponse {
  idOrganizacion: number;
  nombre: string;
  direccion: string;
  /** Sede en el mapa (la calcula analitica-service con la dirección); null si aún no se ubica. */
  latitud?: number | null;
  longitud?: number | null;
  celular: string;
  correo: string;
}

/** Persona mayor vinculada a la organización. */
export interface PersonaMayorOrganizacion {
  idUsuario: number;
  nombre: string;
  celular: string;
  /** URL de la foto de perfil, o null si no ha subido una. */
  fotoPerfil?: string | null;
}

/** Organización vinculada a la persona mayor, o que le envió una solicitud. */
export interface OrganizacionSolicitud {
  idOrganizacion: number;
  nombre: string;
  celular: string;
  correo: string;
  direccion: string;
  /** Sede en el mapa (la calcula analitica-service con la dirección); null si aún no se ubica. */
  latitud?: number | null;
  longitud?: number | null;
  /** URL de la foto de perfil, o null si no ha subido una. */
  fotoPerfil?: string | null;
}

/** Voluntario vinculado a la organización, o que le envió una solicitud. */
export interface VoluntarioOrganizacion {
  idUsuario: number;
  nombre: string;
  celular: string | null;
  correo: string | null;
  /** URL de la foto de perfil, o null si no ha subido una. */
  fotoPerfil?: string | null;
}

/**
 * Persona mayor de la organización que eliminó su cuenta. razon es un
 * código de RAZONES_RETIRO (ver core/auth/razones-retiro.ts).
 */
export interface RetiroPersonaMayor {
  nombre: string;
  razon: string;
  comentario: string | null;
  fecha: string;
}

/** Nota privada de la organización sobre una de sus personas mayores. */
export interface NotaPersonaMayor {
  idNota: number;
  texto: string;
  /** Cuenta de la organización que la escribió o la editó por última vez. */
  autor: string | null;
  fechaCreacion: string;
  fechaActualizacion: string;
  editada: boolean;
}

/** Acompañante de una persona mayor, visto por la organización. */
export interface AcompanantePersonaMayor {
  idUsuario: number;
  nombre: string;
  celular: string;
  relacion: string | null;
  /** URL de la foto de perfil, o null si no ha subido una. */
  fotoPerfil?: string | null;
}

/**
 * Vínculos de la organización con personas mayores (vistos desde los dos
 * lados) y con voluntarios, y el perfil de la organización.
 */
@Injectable({
  providedIn: 'root',
})
export class OrganizacionService {
  private readonly apiUrl = `${API_URL}/organizacion`;
  private readonly authInformacionUrl = `${API_URL}/auth/informacion`;

  constructor(private http: HttpClient) {}

  obtenerInformacion(): Observable<OrganizacionResponse> {
    return this.http.get<OrganizacionResponse>(`${this.apiUrl}/informacion`);
  }

  /**
   * El nombre y el correo son de la cuenta (auth-service); organizacion-service
   * guarda la dirección (la ubicación en el mapa la calcula analitica-service). Primero se guarda la cuenta para que la
   * respuesta de organizacion-service ya traiga el nombre nuevo.
   */
  actualizarInformacion(informacion: OrganizacionResponse): Observable<OrganizacionResponse> {
    return this.http
      .put(this.authInformacionUrl, {
        nombre: informacion.nombre,
        correo: informacion.correo,
      })
      .pipe(
        switchMap(() =>
          this.http.put<OrganizacionResponse>(`${this.apiUrl}/informacion`, {
            direccion: informacion.direccion,
          }),
        ),
      );
  }
  /** Personas mayores con vínculo aceptado. */
  obtenerPersonasMayores(): Observable<PersonaMayorOrganizacion[]> {
    return this.http.get<PersonaMayorOrganizacion[]>(`${this.apiUrl}/personas-mayores`);
  }

  /** Personas mayores que eliminaron su cuenta y por qué, de la más reciente a la más antigua. */
  obtenerRetiros(): Observable<RetiroPersonaMayor[]> {
    return this.http.get<RetiroPersonaMayor[]>(`${this.apiUrl}/retiros`);
  }

  /** Envía una solicitud de vínculo a la persona mayor con ese celular. */
  asociarPersonaMayor(celular: string): Observable<string> {
    return this.http.post(`${this.apiUrl}/personas-mayores`, { celular }, { responseType: 'text' });
  }

  cancelarAsociacionPersonaMayor(idPersonaMayor: number): Observable<string> {
    return this.http.delete(`${this.apiUrl}/personas-mayores/${idPersonaMayor}`, {
      responseType: 'text',
    });
  }

  /** Lado de la persona mayor: solicitudes de organizaciones que no ha respondido. */
  obtenerSolicitudesOrganizaciones(): Observable<OrganizacionSolicitud[]> {
    return this.http.get<OrganizacionSolicitud[]>(
      `${API_URL}/persona-mayor/organizaciones/solicitudes`,
    );
  }

  aceptarSolicitudOrganizacion(idOrganizacion: number): Observable<string> {
    return this.http.put(
      `${API_URL}/persona-mayor/organizaciones/solicitudes/${idOrganizacion}/aceptar`,
      {},
      { responseType: 'text' },
    );
  }

  rechazarSolicitudOrganizacion(idOrganizacion: number): Observable<string> {
    return this.http.put(
      `${API_URL}/persona-mayor/organizaciones/solicitudes/${idOrganizacion}/rechazar`,
      {},
      { responseType: 'text' },
    );
  }
  /** Lado de la persona mayor: pide unirse a una organización (la organización responde). */
  solicitarVinculacionOrganizacion(idOrganizacion: number): Observable<string> {
    return this.http.post(
      `${API_URL}/persona-mayor/organizaciones/${idOrganizacion}/solicitud`,
      {},
      { responseType: 'text' },
    );
  }

  /** Lado de la persona mayor: organizaciones a las que puede pedir unirse (sin vínculo ni solicitud pendiente). */
  obtenerOrganizacionesDisponibles(): Observable<OrganizacionSolicitud[]> {
    return this.http.get<OrganizacionSolicitud[]>(
      `${API_URL}/persona-mayor/organizaciones/disponibles`,
    );
  }

  /** Lado de la persona mayor: solicitudes que envió y la organización aún no responde. */
  obtenerSolicitudesEnviadasOrganizaciones(): Observable<OrganizacionSolicitud[]> {
    return this.http.get<OrganizacionSolicitud[]>(
      `${API_URL}/persona-mayor/organizaciones/solicitudes/enviadas`,
    );
  }

  /** Lado de la organización: personas mayores que pidieron unirse y aún no tienen respuesta. */
  obtenerSolicitudesPersonasMayores(): Observable<PersonaMayorOrganizacion[]> {
    return this.http.get<PersonaMayorOrganizacion[]>(`${this.apiUrl}/personas-mayores/solicitudes`);
  }

  aceptarSolicitudPersonaMayor(idPersonaMayor: number): Observable<string> {
    return this.http.put(
      `${this.apiUrl}/personas-mayores/solicitudes/${idPersonaMayor}/aceptar`,
      {},
      { responseType: 'text' },
    );
  }

  rechazarSolicitudPersonaMayor(idPersonaMayor: number): Observable<string> {
    return this.http.put(
      `${this.apiUrl}/personas-mayores/solicitudes/${idPersonaMayor}/rechazar`,
      {},
      { responseType: 'text' },
    );
  }

  /** Lado de la persona mayor: organizaciones con vínculo aceptado. */
  obtenerOrganizaciones(): Observable<OrganizacionSolicitud[]> {
    return this.http.get<OrganizacionSolicitud[]>(
      `${API_URL}/persona-mayor/organizaciones`,
    );
  }
  /** La persona mayor deshace el vínculo con una organización. */
  cancelarAsociacionOrganizacion(idOrganizacion: number): Observable<string> {
    return this.http.delete(
      `${API_URL}/persona-mayor/organizaciones/${idOrganizacion}`,
      { responseType: 'text' },
    );
  }

  /** Organizaciones con las que la persona mayor pausó el vínculo. */
  obtenerOrganizacionesInactivas(): Observable<OrganizacionSolicitud[]> {
    return this.http.get<OrganizacionSolicitud[]>(`${API_URL}/persona-mayor/organizaciones/inactivas`);
  }

  /** Pausa el vínculo: la organización deja de ver sus datos y ella sus actividades. */
  inactivarAsociacionOrganizacion(idOrganizacion: number): Observable<string> {
    return this.http.put(
      `${API_URL}/persona-mayor/organizaciones/${idOrganizacion}/inactivar`,
      {},
      { responseType: 'text' },
    );
  }

  reactivarAsociacionOrganizacion(idOrganizacion: number): Observable<string> {
    return this.http.put(
      `${API_URL}/persona-mayor/organizaciones/${idOrganizacion}/reactivar`,
      {},
      { responseType: 'text' },
    );
  }

  /** Acompañantes de una persona mayor vinculada (lo atiende persona-mayor-service). */
  obtenerAcompanantesPersonaMayor(idPersonaMayor: number): Observable<AcompanantePersonaMayor[]> {
    return this.http.get<AcompanantePersonaMayor[]>(
      `${this.apiUrl}/personas-mayores/${idPersonaMayor}/acompanantes`,
    );
  }

  // Notas sobre cada persona mayor (privadas de la organización)

  obtenerNotas(idPersonaMayor: number): Observable<NotaPersonaMayor[]> {
    return this.http.get<NotaPersonaMayor[]>(`${this.apiUrl}/personas-mayores/${idPersonaMayor}/notas`);
  }

  crearNota(idPersonaMayor: number, texto: string): Observable<NotaPersonaMayor> {
    return this.http.post<NotaPersonaMayor>(`${this.apiUrl}/personas-mayores/${idPersonaMayor}/notas`, { texto });
  }

  editarNota(idPersonaMayor: number, idNota: number, texto: string): Observable<NotaPersonaMayor> {
    return this.http.put<NotaPersonaMayor>(
      `${this.apiUrl}/personas-mayores/${idPersonaMayor}/notas/${idNota}`,
      { texto },
    );
  }

  eliminarNota(idPersonaMayor: number, idNota: number): Observable<string> {
    return this.http.delete(`${this.apiUrl}/personas-mayores/${idPersonaMayor}/notas/${idNota}`, {
      responseType: 'text',
    });
  }

  // Voluntarios (los atiende voluntario-service vía el gateway)

  /** Voluntarios con vínculo aceptado. */
  obtenerVoluntarios(): Observable<VoluntarioOrganizacion[]> {
    return this.http.get<VoluntarioOrganizacion[]>(`${this.apiUrl}/voluntarios`);
  }

  /** Solicitudes de voluntarios que la organización no ha respondido. */
  obtenerSolicitudesVoluntarios(): Observable<VoluntarioOrganizacion[]> {
    return this.http.get<VoluntarioOrganizacion[]>(`${this.apiUrl}/voluntarios/solicitudes`);
  }

  aceptarSolicitudVoluntario(idVoluntario: number): Observable<string> {
    return this.http.put(
      `${this.apiUrl}/voluntarios/solicitudes/${idVoluntario}/aceptar`,
      {},
      { responseType: 'text' },
    );
  }

  rechazarSolicitudVoluntario(idVoluntario: number): Observable<string> {
    return this.http.put(
      `${this.apiUrl}/voluntarios/solicitudes/${idVoluntario}/rechazar`,
      {},
      { responseType: 'text' },
    );
  }

  desvincularVoluntario(idVoluntario: number): Observable<string> {
    return this.http.delete(`${this.apiUrl}/voluntarios/${idVoluntario}`, {
      responseType: 'text',
    });
  }
}
