import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SignoVitalResponse } from '../signos-vitales/signos-vitales.services';
import { CitaMedica } from '../citas-medicas/cita-medica.service';
import { AcompananteResumen } from '../../shared/acompanantes-modal/acompanantes-modal';

/** Acompañante de una persona mayor, con su parentesco. */
export interface Acompanante {
  idUsuario: number;
  nombre: string;
  celular: string;
  relacion: string;
}

/** Persona mayor que el acompañante tiene a cargo. */
export interface PersonaMayorAcompanada {
  idUsuario: number;
  nombre: string;
  celular: string;
}

/** Solicitud de acompañamiento pendiente: datos de la persona mayor (que la envió o a quien se le envió). */
export interface SolicitudAcompanamiento {
  idUsuario: number;
  nombre: string;
  celular: string;
}

/** Datos de "Mi información" del acompañante. */
export interface AcompanantePerfil {
  idUsuario: number;
  nombre: string;
  celular: string;
  correo: string | null;
  tieneContrasena: boolean;
}

export interface ActualizarAcompananteRequest {
  nombre: string;
  correo?: string;
}

export interface CambiarContrasenaRequest {
  contrasenaActual?: string;
  nuevaContrasena: string;
}

/** Medicamento de una persona mayor, visto por su acompañante. */
export interface MedicamentoSeguimiento {
  idMedicamento: number;
  nombre: string;
  dosis: string | null;
  frecuencia: string | null;
  intervaloHoras: number;
  hora: string | null;
  fechaInicio: string | null;
  fechaFin: string | null;
  proximaToma: string | null;
  ultimaToma: string | null;
  activo: boolean;
}

/** Perfil de una persona mayor que el acompañante puede ver y editar (auth-service). */
export interface PerfilPersonaMayor {
  idUsuario: number;
  nombre: string;
  celular: string;
  correo: string | null;
  fechaNacimiento: string | null;
  genero: string | null;
  direccion: string | null;
  eps: string | null;
  ips: string | null;
  direccionIps: string | null;
}

/** El correo y el celular no se envían: solo la persona mayor los cambia. */
export interface ActualizarPerfilPersonaMayorRequest {
  nombre: string;
  fechaNacimiento: string | null;
  genero: string | null;
  direccion: string | null;
  eps: string | null;
  ips: string | null;
  direccionIps: string | null;
}

/** Resumen de una actividad que puede ver el acompañante. */
export interface Actividad {
  idActividad: number;
  idOrganizacion: number;
  nombre: string;
  fecha: string | null;
  lugar: string | null;
  tipo: string | null;
}

/**
 * Llamadas al backend sobre los vínculos entre personas mayores y
 * acompañantes, y el seguimiento que hace el acompañante (medicamentos,
 * citas médicas, signos vitales, contactos y actividades).
 */
@Injectable({
  providedIn: 'root'
})
export class AcompananteService {

  private readonly apiUrl = 'http://localhost:8080/api';
private readonly authUrl = 'http://localhost:8080/api/auth';

  constructor(private http: HttpClient) {}

  /** Acompañantes de la persona mayor autenticada. */
  obtenerAcompanantes(): Observable<Acompanante[]> {
    return this.http.get<Acompanante[]>(
      `${this.apiUrl}/persona-mayor/acompanantes`
    );
  }

  /** La persona mayor envía una solicitud de acompañamiento por celular. */
  agregarAcompanante(
    datos: {
      celular: string;
      relacion: string;
    }
  ): Observable<string> {
    return this.http.post(
      `${this.apiUrl}/persona-mayor/acompanantes`,
      datos,
      {
        responseType: 'text'
      }
    );
  }

  /** Solicitudes que le enviaron acompañantes a la persona mayor y aún no responde. */
  obtenerSolicitudesDeAcompanantes(): Observable<Acompanante[]> {
    return this.http.get<Acompanante[]>(
      `${this.apiUrl}/persona-mayor/acompanantes/solicitudes`
    );
  }

  /** La persona mayor acepta o rechaza la solicitud de un acompañante. */
  responderSolicitudDeAcompanante(
    idAcompanante: number,
    aceptar: boolean
  ): Observable<string> {
    return this.http.put(
      `${this.apiUrl}/persona-mayor/acompanantes/solicitudes/${idAcompanante}/${aceptar ? 'aceptar' : 'rechazar'}`,
      {},
      {
        responseType: 'text'
      }
    );
  }

  /** La persona mayor quita a uno de sus acompañantes. */
  cancelarAcompanante(
    idAcompanante: number
  ): Observable<string> {
    return this.http.delete(
      `${this.apiUrl}/persona-mayor/acompanantes/${idAcompanante}`,
      {
        responseType: 'text'
      }
    );
  }

  /** Personas mayores que acompaña el usuario. */
  obtenerPersonasMayores(): Observable<PersonaMayorAcompanada[]> {
    return this.http.get<PersonaMayorAcompanada[]>(
      `${this.apiUrl}/acompanante/personas-mayores`
    );
  }

  /** El acompañante deja de acompañar a una persona mayor. */
  cancelarAsociacionPersonaMayor(
    idPersonaMayor: number
  ): Observable<string> {
    return this.http.delete(
      `${this.apiUrl}/acompanante/personas-mayores/${idPersonaMayor}`,
      {
        responseType: 'text'
      }
    );
  }

  /** El acompañante envía una solicitud de acompañamiento a una persona mayor por celular. */
  agregarPersonaMayor(
    datos: {
      celular: string;
      relacion: string;
    }
  ): Observable<string> {
    return this.http.post(
      `${this.apiUrl}/acompanante/personas-mayores`,
      datos,
      {
        responseType: 'text'
      }
    );
  }

  /** Solicitudes que envió el acompañante y la persona mayor aún no responde. */
  obtenerSolicitudesEnviadas(): Observable<SolicitudAcompanamiento[]> {
    return this.http.get<SolicitudAcompanamiento[]>(
      `${this.apiUrl}/acompanante/personas-mayores/solicitudes/enviadas`
    );
  }

  /** Solicitudes que le enviaron personas mayores al acompañante y aún no responde. */
  obtenerSolicitudes(): Observable<SolicitudAcompanamiento[]> {
    return this.http.get<SolicitudAcompanamiento[]>(
      `${this.apiUrl}/acompanante/personas-mayores/solicitudes`
    );
  }

  aceptarSolicitud(
    idPersonaMayor: number
  ): Observable<string> {
    return this.http.put(
      `${this.apiUrl}/acompanante/personas-mayores/solicitudes/${idPersonaMayor}/aceptar`,
      {},
      {
        responseType: 'text'
      }
    );
  }

  rechazarSolicitud(
    idPersonaMayor: number
  ): Observable<string> {
    return this.http.put(
      `${this.apiUrl}/acompanante/personas-mayores/solicitudes/${idPersonaMayor}/rechazar`,
      {},
      {
        responseType: 'text'
      }
    );
  }

  /** "Mi información" del acompañante (auth-service). */
  obtenerInformacion(): Observable<AcompanantePerfil> {
  return this.http.get<AcompanantePerfil>(
    `${this.authUrl}/informacion`
  );
}

actualizarInformacion(
  datos: ActualizarAcompananteRequest
): Observable<AcompanantePerfil> {
  return this.http.put<AcompanantePerfil>(
    `${this.authUrl}/informacion`,
    datos
  );
}

  cambiarContrasena(
  datos: CambiarContrasenaRequest
): Observable<string> {
  return this.http.put(
    `${this.authUrl}/contrasena`,
    datos,
    {
      responseType: 'text'
    }
  );
}

  /** Medicamentos de una persona mayor que el usuario acompaña. */
  obtenerMedicamentosSeguimiento(
    idPersonaMayor: number
  ): Observable<MedicamentoSeguimiento[]> {
    return this.http.get<MedicamentoSeguimiento[]>(
      `${this.apiUrl}/acompanante/seguimiento/${idPersonaMayor}/medicamentos`
    );
  }

  /** Citas médicas (pasadas y futuras) de una persona mayor que el usuario acompaña. */
  obtenerCitasMedicasSeguimiento(
    idPersonaMayor: number
  ): Observable<CitaMedica[]> {
    return this.http.get<CitaMedica[]>(
      `${this.apiUrl}/acompanante/seguimiento/${idPersonaMayor}/citas-medicas`
    );
  }

  /** Últimos 10 registros de la persona mayor, del más reciente al más antiguo. */
  obtenerSignosVitalesSeguimiento(
    idPersonaMayor: number
  ): Observable<SignoVitalResponse[]> {
    return this.http.get<SignoVitalResponse[]>(
      `${this.apiUrl}/acompanante/seguimiento/${idPersonaMayor}/signos-vitales`
    );
  }

  /** Acompañantes con relación aceptada de la persona mayor. */
  obtenerAcompanantesSeguimiento(
    idPersonaMayor: number
  ): Observable<AcompananteResumen[]> {
    return this.http.get<AcompananteResumen[]>(
      `${this.apiUrl}/acompanante/seguimiento/${idPersonaMayor}/contactos`
    );
  }

  /** Perfil de una persona mayor que el usuario acompaña. */
  obtenerPerfilPersonaMayor(idPersonaMayor: number): Observable<PerfilPersonaMayor> {
    return this.http.get<PerfilPersonaMayor>(
      `${this.apiUrl}/acompanante/personas-mayores/${idPersonaMayor}/informacion`
    );
  }

  actualizarPerfilPersonaMayor(
    idPersonaMayor: number,
    datos: ActualizarPerfilPersonaMayorRequest
  ): Observable<PerfilPersonaMayor> {
    return this.http.put<PerfilPersonaMayor>(
      `${this.apiUrl}/acompanante/personas-mayores/${idPersonaMayor}/informacion`,
      datos
    );
  }

  /** Actividades de las organizaciones de las personas mayores que acompaña. */
  obtenerActividades(): Observable<Actividad[]> {
    return this.http.get<Actividad[]>(
      `${this.apiUrl}/actividades`
    );
  }
}