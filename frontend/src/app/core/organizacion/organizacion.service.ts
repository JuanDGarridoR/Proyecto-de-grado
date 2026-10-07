import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, switchMap } from 'rxjs';

/** Datos de la organización para "Mi información". */
export interface OrganizacionResponse {
  idOrganizacion: number;
  nombre: string;
  direccion: string;
  celular: string;
  correo: string;
}

/** Persona mayor vinculada a la organización. */
export interface PersonaMayorOrganizacion {
  idUsuario: number;
  nombre: string;
  celular: string;
}

/** Organización vinculada a la persona mayor, o que le envió una solicitud. */
export interface OrganizacionSolicitud {
  idOrganizacion: number;
  nombre: string;
  celular: string;
  correo: string;
  direccion: string;
}

/** Voluntario vinculado a la organización, o que le envió una solicitud. */
export interface VoluntarioOrganizacion {
  idUsuario: number;
  nombre: string;
  celular: string | null;
  correo: string | null;
}

/** Acompañante de una persona mayor, visto por la organización. */
export interface AcompanantePersonaMayor {
  idUsuario: number;
  nombre: string;
  celular: string;
  relacion: string | null;
}

/**
 * Vínculos de la organización con personas mayores (vistos desde los dos
 * lados) y con voluntarios, y el perfil de la organización.
 */
@Injectable({
  providedIn: 'root',
})
export class OrganizacionService {
  private readonly apiUrl = 'http://localhost:8080/api/organizacion';
  private readonly authInformacionUrl = 'http://localhost:8080/api/auth/informacion';

  constructor(private http: HttpClient) {}

  obtenerInformacion(): Observable<OrganizacionResponse> {
    return this.http.get<OrganizacionResponse>(`${this.apiUrl}/informacion`);
  }

  /**
   * El nombre y el correo son de la cuenta (auth-service); organizacion-service
   * solo guarda la dirección. Primero se guarda la cuenta para que la
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
      'http://localhost:8080/api/persona-mayor/organizaciones/solicitudes',
    );
  }

  aceptarSolicitudOrganizacion(idOrganizacion: number): Observable<string> {
    return this.http.put(
      `http://localhost:8080/api/persona-mayor/organizaciones/solicitudes/${idOrganizacion}/aceptar`,
      {},
      { responseType: 'text' },
    );
  }

  rechazarSolicitudOrganizacion(idOrganizacion: number): Observable<string> {
    return this.http.put(
      `http://localhost:8080/api/persona-mayor/organizaciones/solicitudes/${idOrganizacion}/rechazar`,
      {},
      { responseType: 'text' },
    );
  }
  /** Lado de la persona mayor: pide unirse a una organización (la organización responde). */
  solicitarVinculacionOrganizacion(idOrganizacion: number): Observable<string> {
    return this.http.post(
      `http://localhost:8080/api/persona-mayor/organizaciones/${idOrganizacion}/solicitud`,
      {},
      { responseType: 'text' },
    );
  }

  /** Lado de la persona mayor: solicitudes que envió y la organización aún no responde. */
  obtenerSolicitudesEnviadasOrganizaciones(): Observable<OrganizacionSolicitud[]> {
    return this.http.get<OrganizacionSolicitud[]>(
      'http://localhost:8080/api/persona-mayor/organizaciones/solicitudes/enviadas',
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
      'http://localhost:8080/api/persona-mayor/organizaciones',
    );
  }
  /** La persona mayor deshace el vínculo con una organización. */
  cancelarAsociacionOrganizacion(idOrganizacion: number): Observable<string> {
    return this.http.delete(
      `http://localhost:8080/api/persona-mayor/organizaciones/${idOrganizacion}`,
      { responseType: 'text' },
    );
  }

  /** Acompañantes de una persona mayor vinculada (lo atiende persona-mayor-service). */
  obtenerAcompanantesPersonaMayor(idPersonaMayor: number): Observable<AcompanantePersonaMayor[]> {
    return this.http.get<AcompanantePersonaMayor[]>(
      `${this.apiUrl}/personas-mayores/${idPersonaMayor}/acompanantes`,
    );
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
