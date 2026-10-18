import { Injectable, Pipe, PipeTransform, effect, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of, shareReplay } from 'rxjs';

import { API_URL } from '../api';
import { AuthService } from '../auth/auth.service';

/**
 * Fotos de perfil. Están en un bucket privado: el backend guarda su ruta
 * ({idUsuario}/{archivo}) y las entrega en /api/auth/fotos/{ruta} solo con
 * sesión iniciada. Como una etiqueta img no puede mandar el token, la foto
 * se pide con HttpClient (el interceptor agrega el token) y se muestra desde
 * memoria con una URL blob:.
 *
 * Cada foto se pide una sola vez por sesión. Al cerrar sesión se olvidan.
 */
@Injectable({ providedIn: 'root' })
export class FotoPerfilService {

  private readonly http = inject(HttpClient);
  private readonly fotos = new Map<string, Observable<string | null>>();
  private readonly urlsCreadas: string[] = [];

  constructor() {
    const authService = inject(AuthService);
    effect(() => {
      if (!authService.estaAutenticadoSignal()()) {
        this.olvidarTodas();
      }
    });
  }

  /** URL que puede mostrar un img, o null si no hay foto o no se pudo cargar. */
  url(foto: string | null | undefined): Observable<string | null> {
    if (!foto) {
      return of(null);
    }

    // Ya es una URL que el navegador puede mostrar tal cual.
    if (/^(https?:|blob:|data:)/.test(foto)) {
      return of(foto);
    }

    let url$ = this.fotos.get(foto);
    if (!url$) {
      url$ = this.http.get(`${API_URL}/auth/fotos/${foto}`, { responseType: 'blob' }).pipe(
        map((imagen) => {
          const url = URL.createObjectURL(imagen);
          this.urlsCreadas.push(url);
          return url;
        }),
        // Sin foto se ve la inicial o el ícono; se reintenta la próxima vez.
        catchError(() => {
          this.fotos.delete(foto);
          return of(null);
        }),
        shareReplay(1)
      );
      this.fotos.set(foto, url$);
    }
    return url$;
  }

  private olvidarTodas(): void {
    this.urlsCreadas.splice(0).forEach((url) => URL.revokeObjectURL(url));
    this.fotos.clear();
  }
}

/**
 * Ruta de una foto de perfil → URL para un img, usando FotoPerfilService.
 * Se usa con async: [src]="(foto | fotoPerfil | async)".
 */
@Pipe({ name: 'fotoPerfil' })
export class FotoPerfilPipe implements PipeTransform {

  private readonly fotoPerfilService = inject(FotoPerfilService);

  transform(foto: string | null | undefined): Observable<string | null> {
    return this.fotoPerfilService.url(foto);
  }
}
