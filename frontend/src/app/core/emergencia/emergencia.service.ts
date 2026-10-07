import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URL } from '../api';

/** Botón de emergencia de la persona mayor. */
@Injectable({
  providedIn: 'root'
})
export class EmergenciaService {

  private readonly apiUrl =
    `${API_URL}/persona-mayor/emergencia`;

  constructor(private http: HttpClient) {}

  /**
   * Envía la alerta por SMS a sus acompañantes y organizaciones. Responde
   * con un texto que dice a cuántos se avisó.
   */
  activarEmergencia(): Observable<string> {
    return this.http.post(
      this.apiUrl,
      {},
      {
        responseType: 'text'
      }
    );
  }
}