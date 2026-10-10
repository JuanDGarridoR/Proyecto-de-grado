import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URL } from '../api';

/**
 * Perfil de la persona mayor tal como lo devuelve auth-service: la fecha de
 * nacimiento, el género y la dirección están en la tabla usuario.
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
 * Perfil y contraseña de la persona mayor (auth-service). La edición del
 * perfil la hace la página de perfil compartida con /auth/informacion.
 */
@Injectable({ providedIn: 'root' })
export class PersonaMayorService {

  private readonly authUrl = `${API_URL}/auth/informacion`;
  private readonly contrasenaUrl = `${API_URL}/auth/contrasena`;

  constructor(private http: HttpClient) {}

  obtenerInformacion(): Observable<PersonaMayorResponse> {
    return this.http.get<PersonaMayorResponse>(this.authUrl);
  }

  cambiarContrasena(request: CambiarContrasenaRequest): Observable<string> {
    return this.http.put(this.contrasenaUrl, request, { responseType: 'text' });
  }
}
