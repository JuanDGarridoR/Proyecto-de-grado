import { DestroyRef, Injectable, effect, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subject, debounceTime, filter, map } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { API_URL } from '../api';

/**
 * Tipos de datos compartidos entre usuarios. El api-gateway avisa cuál
 * cambió (ver PublicarCambiosGlobalFilter en el backend).
 */
export type Recurso =
  | 'actividades'
  | 'medicamentos'
  | 'citas-medicas'
  | 'signos-vitales'
  | 'condiciones-salud' // enfermedades, alergias y discapacidades de la persona mayor
  | 'acompanamientos'   // persona mayor <-> acompañante, solicitudes, contactos
  | 'organizaciones'    // persona mayor <-> organización, solicitudes
  | 'voluntarios'       // voluntario <-> organización, solicitudes
  | 'gustos'
  | 'notificaciones'    // SMS de emergencia enviados, notificaciones leídas
  | 'usuarios';         // nombres/datos de perfil, registros nuevos

/** Aviso especial: recargar todo (cuenta eliminada o reconexión tras un corte). */
const TODOS = '*';

/** Espera máxima entre reintentos de conexión. */
const REINTENTO_MAXIMO_MS = 30_000;

/**
 * Mantiene abierta una conexión SSE con el gateway mientras hay sesión y
 * reparte los avisos de cambio. Los avisos solo dicen qué cambió; cada
 * página vuelve a pedir sus datos por los endpoints de siempre.
 *
 * Se usa fetch y no EventSource porque EventSource no permite enviar el
 * encabezado Authorization, y el token no debe ir en la URL.
 */
@Injectable({ providedIn: 'root' })
export class TiempoRealService {

  private readonly url = `${API_URL}/eventos`;

  private readonly avisos$ = new Subject<string>();

  private controlador: AbortController | null = null;
  private temporizadorReintento?: ReturnType<typeof setTimeout>;
  private intentos = 0;

  /**
   * Si ya hubo una conexión en esta sesión, al reconectar se pudieron
   * perder avisos, así que se pide recargar todo.
   */
  private conectadoAntes = false;

  constructor(private authService: AuthService) {
    effect(() => {
      if (this.authService.estaAutenticadoSignal()()) {
        this.conectar();
      } else {
        this.detener();
        this.conectadoAntes = false;
      }
    });
  }

  /** Emite (agrupando ráfagas) cada vez que cambia alguno de los recursos. */
  cambios(recursos: Recurso[]): Observable<void> {
    return this.avisos$.pipe(
      filter((recurso) => recurso === TODOS || recursos.includes(recurso as Recurso)),
      debounceTime(300),
      map(() => undefined)
    );
  }

  /** Abre la conexión SSE y lee avisos hasta que se cierre. */
  private async conectar(): Promise<void> {
    this.detener();

    const token = this.authService.getToken();
    if (!token) {
      return;
    }

    const controlador = new AbortController();
    this.controlador = controlador;

    try {
      const respuesta = await fetch(this.url, {
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'text/event-stream'
        },
        signal: controlador.signal
      });

      // Token vencido o inválido: reintentar no sirve.
      if (respuesta.status === 401) {
        return;
      }

      if (!respuesta.ok || !respuesta.body) {
        throw new Error(`Respuesta ${respuesta.status}`);
      }

      this.intentos = 0;
      if (this.conectadoAntes) {
        this.avisos$.next(TODOS);
      }
      this.conectadoAntes = true;

      await this.leer(respuesta.body);
    } catch {
      // Error de red o servidor caído: se reintenta abajo.
    }

    // Si no se canceló a propósito (logout o una conexión nueva), el
    // servidor la cerró o se cayó: se reintenta con espera creciente.
    if (!controlador.signal.aborted) {
      this.programarReintento();
    }
  }

  /** Cierra la conexión actual y cancela el reintento pendiente, si lo hay. */
  private detener(): void {
    clearTimeout(this.temporizadorReintento);
    this.controlador?.abort();
    this.controlador = null;
  }

  /** Espera 1 s, 2 s, 4 s... (hasta 30 s) antes de volver a conectar. */
  private programarReintento(): void {
    const espera = Math.min(REINTENTO_MAXIMO_MS, 1000 * 2 ** this.intentos);
    this.intentos++;
    this.temporizadorReintento = setTimeout(() => this.conectar(), espera);
  }

  /**
   * Lee el stream SSE: bloques separados por una línea en blanco, con
   * líneas "event: ..." y "data: ...". Las que empiezan por ":" son latidos.
   */
  private async leer(cuerpo: ReadableStream<Uint8Array>): Promise<void> {
    const lector = cuerpo.getReader();
    const decodificador = new TextDecoder();
    let pendiente = '';

    while (true) {
      const { value, done } = await lector.read();
      if (done) {
        return;
      }

      pendiente += decodificador.decode(value, { stream: true }).replace(/\r/g, '');

      let fin: number;
      while ((fin = pendiente.indexOf('\n\n')) >= 0) {
        this.procesarBloque(pendiente.slice(0, fin));
        pendiente = pendiente.slice(fin + 2);
      }
    }
  }

  /** Interpreta un bloque SSE y reparte el aviso si es de tipo "cambio". */
  private procesarBloque(bloque: string): void {
    let evento = 'message';
    const datos: string[] = [];

    for (const linea of bloque.split('\n')) {
      if (linea.startsWith('event:')) {
        evento = linea.slice(6).trim();
      } else if (linea.startsWith('data:')) {
        datos.push(linea.slice(5).trim());
      }
    }

    if (evento === 'cambio' && datos.length > 0) {
      this.avisos$.next(datos.join('\n'));
    }
  }
}

/**
 * Ejecuta `accion` cada vez que otro usuario (o esta misma sesión en otra
 * pestaña) modifica alguno de los recursos. Se deja de escuchar solo al
 * destruir el componente. Llamar desde el constructor del componente.
 */
export function alCambiar(recursos: Recurso[], accion: () => void): void {
  inject(TiempoRealService)
    .cambios(recursos)
    .pipe(takeUntilDestroyed(inject(DestroyRef)))
    .subscribe(() => accion());
}
