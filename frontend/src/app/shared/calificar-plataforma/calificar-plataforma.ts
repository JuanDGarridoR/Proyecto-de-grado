import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

import { API_URL } from '../../core/api';
import { mensajeDeError } from '../../core/formato/formato';
import { Icono } from '../icono/icono';

/** Calificación que el usuario ya dejó (auth-service). */
interface Calificacion {
  estrellas: number;
  comentario: string | null;
  fechaActualizacion: string;
}

const MAX_COMENTARIO = 1000;

/** Texto de cada calificación, de 0 a 5 estrellas. */
const TEXTOS = ['Muy mala', 'Mala', 'Regular', 'Buena', 'Muy buena', 'Excelente'];

/**
 * Bloque del perfil para calificar la plataforma, para cualquier rol: de 0
 * a 5 estrellas y una observación u oportunidad de mejora opcional. Cada
 * usuario tiene una sola calificación; si vuelve a calificar, se actualiza.
 */
@Component({
  selector: 'app-calificar-plataforma',
  imports: [FormsModule, Icono],
  templateUrl: './calificar-plataforma.html',
  styleUrl: './calificar-plataforma.css',
})
export class CalificarPlataforma implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/auth/calificacion`;

  protected readonly estrellas = signal<number | null>(null);
  /** Estrella bajo el puntero, para mostrar cómo quedaría. */
  protected readonly vistaPrevia = signal<number | null>(null);
  protected comentario = '';

  /** Lo último guardado, para saber si hay cambios. */
  protected readonly guardada = signal<Calificacion | null>(null);

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);
  protected readonly mensaje = signal('');
  protected readonly error = signal('');

  protected readonly MAX_COMENTARIO = MAX_COMENTARIO;
  protected readonly posiciones = [1, 2, 3, 4, 5];

  /** Las estrellas que se pintan: la vista previa si hay, si no la elegida. */
  protected readonly pintadas = computed(() => this.vistaPrevia() ?? this.estrellas() ?? 0);

  protected readonly textoActual = computed(() => {
    const valor = this.vistaPrevia() ?? this.estrellas();
    return valor === null ? 'Sin calificar' : `${valor} de 5 · ${TEXTOS[valor]}`;
  });

  ngOnInit(): void {
    this.http.get<Calificacion | null>(this.url).subscribe({
      next: (calificacion) => {
        if (calificacion) {
          this.guardada.set(calificacion);
          this.estrellas.set(calificacion.estrellas);
          this.comentario = calificacion.comentario ?? '';
        }
        this.cargando.set(false);
      },
      // Si no carga, igual se puede calificar.
      error: () => this.cargando.set(false),
    });
  }

  /** Volver a tocar la misma estrella la quita (así se llega a 0). */
  protected elegir(valor: number): void {
    this.estrellas.set(this.estrellas() === valor ? valor - 1 : valor);
    this.mensaje.set('');
  }

  /** Flechas izquierda/derecha dentro del grupo de estrellas. */
  protected teclado(evento: KeyboardEvent): void {
    const actual = this.estrellas() ?? 0;

    if (evento.key === 'ArrowRight' || evento.key === 'ArrowUp') {
      this.estrellas.set(Math.min(5, actual + 1));
    } else if (evento.key === 'ArrowLeft' || evento.key === 'ArrowDown') {
      this.estrellas.set(Math.max(0, actual - 1));
    } else {
      return;
    }

    evento.preventDefault();
    this.mensaje.set('');
  }

  protected get hayCambios(): boolean {
    const guardada = this.guardada();
    return (
      this.estrellas() !== null &&
      (!guardada ||
        guardada.estrellas !== this.estrellas() ||
        (guardada.comentario ?? '') !== this.comentario.trim())
    );
  }

  protected guardar(): void {
    const estrellas = this.estrellas();
    if (estrellas === null || this.guardando()) return;

    this.guardando.set(true);
    this.error.set('');
    this.mensaje.set('');

    this.http
      .put<Calificacion>(this.url, { estrellas, comentario: this.comentario.trim() || null })
      .subscribe({
        next: (calificacion) => {
          const primeraVez = this.guardada() === null;
          this.guardada.set(calificacion);
          this.comentario = calificacion.comentario ?? '';
          this.guardando.set(false);
          this.mensaje.set(
            primeraVez ? '¡Gracias por calificar VITA+!' : 'Tu calificación quedó actualizada.',
          );
        },
        error: (err) => {
          this.guardando.set(false);
          this.error.set(mensajeDeError(err, 'No se pudo guardar tu calificación. Intenta de nuevo.'));
        },
      });
  }
}
