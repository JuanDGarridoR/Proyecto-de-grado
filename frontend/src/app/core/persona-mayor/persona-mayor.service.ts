import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, forkJoin, map, switchMap } from 'rxjs';
import { API_URL } from '../api';

/**
 * Perfil completo de la persona mayor: datos de la cuenta (auth-service) más
 * fecha de nacimiento, género y dirección (persona-mayor-service).
 */
export interface PersonaMayorResponse {
  idUsuario: number;
  nombre: string;
  celular: string;
  correo: string;
  fechaNacimiento: string;
  genero: string;
  direccion: string;
  tieneContrasena: boolean;
}

/** Cambio de contraseña; contrasenaActual solo hace falta si ya tenía una. */
export interface CambiarContrasenaRequest {
  contrasenaActual?: string;
  nuevaContrasena: string;
}

/**
 * Perfil de la persona mayor. Une dos endpoints: la cuenta en auth-service y
 * el perfil en persona-mayor-service.
 */
@Injectable({ providedIn: 'root' })
export class PersonaMayorService {

  private readonly authUrl = `${API_URL}/auth/informacion`;
  private readonly perfilUrl = `${API_URL}/persona-mayor/perfil`;
  private readonly contrasenaUrl = `${API_URL}/auth/contrasena`;

  constructor(private http: HttpClient) {}

  /** Pide los dos endpoints en paralelo y une las respuestas. */
  obtenerInformacion(): Observable<PersonaMayorResponse> {
    return forkJoin({
      identidad: this.http.get<any>(this.authUrl),
      perfil: this.http.get<any>(this.perfilUrl)
    }).pipe(
      map(({ identidad, perfil }) => ({
        idUsuario: identidad.idUsuario,
        nombre: identidad.nombre,
        celular: identidad.celular,
        correo: identidad.correo,
        tieneContrasena: identidad.tieneContrasena,
        fechaNacimiento: perfil.fechaNacimiento,
        genero: perfil.genero,
        direccion: perfil.direccion
      }))
    );
  }

  /**
   * Primero se guarda la cuenta (nombre y correo): si el correo es rechazado,
   * el perfil no queda guardado a medias.
   */
  actualizarInformacion(informacion: PersonaMayorResponse): Observable<PersonaMayorResponse> {
    return this.http.put<any>(this.authUrl, {
      nombre: informacion.nombre,
      correo: informacion.correo
    }).pipe(
      switchMap((identidad) =>
        this.http.put<any>(this.perfilUrl, {
          fechaNacimiento: informacion.fechaNacimiento,
          genero: informacion.genero,
          direccion: informacion.direccion
        }).pipe(map((perfil) => ({ identidad, perfil })))
      ),
      map(({ identidad, perfil }) => ({
        idUsuario: identidad.idUsuario,
        nombre: identidad.nombre,
        celular: identidad.celular,
        correo: identidad.correo,
        tieneContrasena: identidad.tieneContrasena,
        fechaNacimiento: perfil.fechaNacimiento,
        genero: perfil.genero,
        direccion: perfil.direccion
      }))
    );
  }

  cambiarContrasena(request: CambiarContrasenaRequest): Observable<string> {
    return this.http.put(this.contrasenaUrl, request, { responseType: 'text' });
  }
}